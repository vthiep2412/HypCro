package com.hypcro.failsafe

import com.hypcro.HypCroMod
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.Vec3
import kotlinx.coroutines.*
import kotlin.math.abs

object HypcroWatchdog {
    private val watchdogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastPos: Vec3? = null
    private var expectedToolSlot: Int? = null
    @Volatile
    private var isWatchdogActive: Boolean = false

    @Volatile
    var isAlarmActive: Boolean = false
        private set

    private var alarmJob: Job? = null
    private var restartJob: Job? = null

    val restartRegex = "(?i).*(?:server (?:closing: scheduled restart|reboot in)|(?:scheduled server restart|rebooting in)).*".toRegex()

    private var slotMismatchTicks: Int = 0
    private var requiredSlotMismatchTicks: Int = 3

    private val recentYaws = ArrayDeque<Float>()
    private val recentPitches = ArrayDeque<Float>()

    private const val TELEPORT_DISTANCE_THRESHOLD = 6.0

    @Volatile
    private var isBpsDropArmed: Boolean = false
    @Volatile
    private var bpsDropStartTime: Long = 0L

    fun start(toolSlot: Int?) {
        stop()
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        lastPos = player.position()
        expectedToolSlot = toolSlot
        isWatchdogActive = true
        slotMismatchTicks = 0
        requiredSlotMismatchTicks = kotlin.random.Random.nextInt(2, 5) // 2 to 4 ticks (~100 to 200 ms)
        
        recentYaws.clear()
        recentPitches.clear()
        recentYaws.add(player.yRot)
        recentPitches.add(player.xRot)
        isBpsDropArmed = false
        bpsDropStartTime = 0L
    }

    fun stop() {
        isWatchdogActive = false
        lastPos = null
        expectedToolSlot = null
        isDebouncingRotation = false
        rotationDebounceStartTime = 0L
        slotMismatchTicks = 0
        recentYaws.clear()
        recentPitches.clear()
        isBpsDropArmed = false
        bpsDropStartTime = 0L
    }

    fun silenceAlarm() {
        isAlarmActive = false
        alarmJob?.cancel()
        alarmJob = null
    }

    @Volatile
    var lastWarningTimeMs: Long = 0L
        private set

    fun triggerWarning() {
        lastWarningTimeMs = System.currentTimeMillis()
    }

    fun hasRecentWarning(): Boolean {
        return (System.currentTimeMillis() - lastWarningTimeMs) < 3000L
    }

    private var rotationDebounceStartTime: Long = 0L
    @Volatile
    private var isDebouncingRotation: Boolean = false

    fun onClientTick(client: Minecraft) {
        if (!isWatchdogActive || !com.hypcro.farming.MacroController.isRunning) return
        checkFailsafes(client)
    }

    private fun checkFailsafes(client: Minecraft) {
        val player = client.player
        val level = client.level
        val watchdogConfig = com.hypcro.config.ConfigManager.config.generalConfig.watchdog
        
        if (player == null || level == null) {
            return
        }

        val currentPos = player.position()
        val currentSlot = player.inventory.selectedSlot

        // 1. Hotbar Slot Switch Check (staff check disarm or unexpected slot swap with 2-4 tick debounce)
        if (watchdogConfig.checkHotbarSlot) {
            expectedToolSlot?.let { expected ->
                if (currentSlot != expected) {
                    slotMismatchTicks++
                    if (slotMismatchTicks >= requiredSlotMismatchTicks) {
                        slotMismatchTicks = 0
                        potentialStaffCheck("Hotbar slot switched from ${expected + 1} to ${currentSlot + 1}")
                        return
                    }
                } else {
                    slotMismatchTicks = 0
                    requiredSlotMismatchTicks = kotlin.random.Random.nextInt(2, 5)
                }
            }
        } else {
            slotMismatchTicks = 0
        }

        // 2. Multi-Tier Server Rotation Check based on rolling 5-check baseline
        if (watchdogConfig.checkRotation) {
            val baselineYaw = if (recentYaws.isNotEmpty()) {
                var sinSum = 0.0
                var cosSum = 0.0
                for (yaw in recentYaws) {
                    val rad = Math.toRadians(yaw.toDouble())
                    sinSum += kotlin.math.sin(rad)
                    cosSum += kotlin.math.cos(rad)
                }
                Math.toDegrees(kotlin.math.atan2(sinSum, cosSum)).toFloat()
            } else {
                player.yRot
            }
            val baselinePitch = if (recentPitches.isNotEmpty()) recentPitches.average().toFloat() else player.xRot

            val yawDiff = com.hypcro.util.AngleUtils.yawDifference(player.yRot, baselineYaw)
            val pitchDiff = com.hypcro.util.AngleUtils.pitchDifference(player.xRot, baselinePitch)
            val maxDelta = maxOf(yawDiff, pitchDiff)

            if (maxDelta > 5.0f) {
                // Large Rotation Spike (> 5.0 degrees)
                if (watchdogConfig.debounceRotation) {
                    val now = System.currentTimeMillis()
                    if (!isDebouncingRotation) {
                        isDebouncingRotation = true
                        rotationDebounceStartTime = now
                    } else if (now - rotationDebounceStartTime >= 400L) {
                        // 400ms expired and player is STILL rotated away (> 5.0 deg) -> confirmed staff check
                        isDebouncingRotation = false
                        potentialStaffCheck("Forced Rotation Detected (Δ: ${String.format("%.1f", maxDelta)}°)")
                        return
                    }
                } else {
                    potentialStaffCheck("Forced Rotation Detected (Δ: ${String.format("%.1f", maxDelta)}°)")
                    return
                }
            } else {
                if (isDebouncingRotation) {
                    // Player angle dropped back to <= 5.0 deg during debounce window (Server rotated player back)
                    isDebouncingRotation = false
                    HypCroMod.log("§e[Watchdog] Rotation failsafe was triggered but the admin rotated you back. DO NOT REACT.")
                }
                // Normal state: Record sample to rolling 5-check history
                recentYaws.addLast(player.yRot)
                recentPitches.addLast(player.xRot)
                while (recentYaws.size > 5) recentYaws.removeFirst()
                while (recentPitches.size > 5) recentPitches.removeFirst()
            }
        }

        // 3. Suddenly Low BPS Check (Staff barrier obstacle or dead-air teleport)
        if (watchdogConfig.checkFarmingInterruption && watchdogConfig.checkBpsDrop) {
            val currentBps = com.hypcro.util.CropBpsTracker.getCurrentBps()
            if (!isBpsDropArmed) {
                if (currentBps >= 18.0) {
                    isBpsDropArmed = true
                    bpsDropStartTime = 0L
                }
            } else {
                if (currentBps < 18.0) {
                    val now = System.currentTimeMillis()
                    if (bpsDropStartTime == 0L) {
                        bpsDropStartTime = now
                    } else if (now - bpsDropStartTime >= 1200L) {
                        bpsDropStartTime = 0L
                        isBpsDropArmed = false
                        potentialStaffCheck("Farming Interruption: Suddenly Low BPS")
                        return
                    }
                } else {
                    bpsDropStartTime = 0L
                }
            }
        }

        lastPos = currentPos
    }

    fun onPacketTeleport(packet: net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket) {
        val client = Minecraft.getInstance()
        val player = client.player
        val prev = player?.position()

        // Calculate target location from packet
        val targetPos = if (prev != null) {
            val changePos = packet.change().position()
            val relatives = packet.relatives()
            val x = if (relatives.contains(net.minecraft.world.entity.Relative.X)) prev.x + changePos.x else changePos.x
            val y = if (relatives.contains(net.minecraft.world.entity.Relative.Y)) prev.y + changePos.y else changePos.y
            val z = if (relatives.contains(net.minecraft.world.entity.Relative.Z)) prev.z + changePos.z else changePos.z
            net.minecraft.world.phys.Vec3(x, y, z)
        } else {
            packet.change().position()
        }

        val dist = if (prev != null) prev.distanceTo(targetPos) else 0.0

        val isMacroRunning = com.hypcro.farming.MacroInputController.isAnyMacroRunning()
        val isWatchdogChecking = isWatchdogActive && com.hypcro.config.ConfigManager.config.generalConfig.watchdog.checkTeleport

        if (isMacroRunning && isWatchdogChecking) {
            // While macroing: 6b+ threshold for failsafe staff check (automatically disables FreeLook in potentialStaffCheck)
            if (dist > TELEPORT_DISTANCE_THRESHOLD) {
                potentialStaffCheck("Teleport Packet Received (instant move ${String.format("%.2f", dist)} blocks to $targetPos)")
            }
        }
    }

    fun handleChatMessage(message: String) {
        if (!com.hypcro.farming.MacroController.isRunning) return

        if (restartRegex.matches(message)) {
            HypCroMod.logWatchdog("Server restart announced. Stopping macro and warping to hub...")
            com.hypcro.farming.MacroController.stopMacro(reason = "Server Restart")
            restartJob?.cancel()
            restartJob = watchdogScope.launch {
                delay(2000)
                Minecraft.getInstance().connection?.sendCommand("hub")
            }
        }
    }

    fun potentialStaffCheck(reason: String) {
        // Disable Free Look immediately so player returns to normal perspective
        val client = Minecraft.getInstance()
        if (com.hypcro.camera.FreeLookManager.isFreeLookActive) {
            com.hypcro.camera.FreeLookManager.disable(client)
        }
        com.hypcro.farming.MacroController.abortScript(reason)
        HypCroMod.logAlarmBanner(reason)
        playFailsafeAlarm()
    }

    private fun playFailsafeAlarm() {
        val client = Minecraft.getInstance()
        isAlarmActive = true
        alarmJob?.cancel()

        alarmJob = watchdogScope.launch {
            while (isActive && isAlarmActive) {
                client.execute {
                    // Play non-positional UI sound (0% distance attenuation, full volume anywhere!)
                    client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.GHAST_SCREAM, 1.0f, 2.0f))
                    client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0f, 2.0f))
                }
                delay(500) // 0.5s interval to protect hearing
            }
        }
    }
}

