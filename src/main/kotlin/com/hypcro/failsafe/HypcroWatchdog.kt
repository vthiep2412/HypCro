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

    private const val TELEPORT_DISTANCE_THRESHOLD = 4.0

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
    }

    fun silenceAlarm() {
        isAlarmActive = false
        alarmJob?.cancel()
        alarmJob = null
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
            potentialStaffCheck("Server Change or Disconnect Detected!")
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

            val yawDiff = abs((((player.yRot - baselineYaw + 180f) % 360f + 360f) % 360f) - 180f)
            val pitchDiff = abs(player.xRot - baselinePitch)
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

        lastPos = currentPos
    }

    fun onPacketTeleport(packet: net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket) {
        if (!isWatchdogActive || !com.hypcro.farming.MacroController.isRunning) return
        if (!com.hypcro.config.ConfigManager.config.generalConfig.watchdog.checkTeleport) return

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
        if (dist >= TELEPORT_DISTANCE_THRESHOLD) {
            potentialStaffCheck("Teleport Packet Received (instant move ${String.format("%.2f", dist)} blocks to $targetPos)")
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

