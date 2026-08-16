package com.hypcro.failsafe

import com.hypcro.HypCroMod
import com.hypcro.farming.WSFarmEngine
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.Vec3
import kotlinx.coroutines.*
import kotlin.math.abs

object HypcroWatchdog {
    private var watchdogJob: Job? = null
    private var lastPos: Vec3? = null
    private var expectedToolSlot: Int? = null

    var isAlarmActive: Boolean = false
        private set

    private var alarmJob: Job? = null

    val restartRegex = "(?i).*(?:server (?:closing: scheduled restart|reboot in)|(?:scheduled server restart|rebooting in)).*".toRegex()

    fun start(toolSlot: Int?) {
        stop()
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        lastPos = player.position()
        expectedToolSlot = toolSlot

        watchdogJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && WSFarmEngine.isRunning) {
                delay(50)
                checkFailsafes()
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
        lastPos = null
        expectedToolSlot = null
    }

    fun silenceAlarm() {
        isAlarmActive = false
        alarmJob?.cancel()
        alarmJob = null
    }

    private suspend fun checkFailsafes() {
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        
        if (player == null || level == null) {
            potentialStaffCheck("Server Change or Disconnect Detected!")
            return
        }

        val currentPos = player.position()
        val currentSlot = player.inventory.selectedSlot
        val targetAngles = WSFarmEngine.currentTargetAngles

        // 1. Hotbar Slot Switch Check (staff check disarm or unexpected slot swap)
        expectedToolSlot?.let { expected ->
            if (currentSlot != expected) {
                potentialStaffCheck("Hotbar slot switched from $expected to $currentSlot")
                return
            }
        }

        // 2. Strict Teleport Check (> 4.0 blocks)
        lastPos?.let { prev ->
            val dist = prev.distanceTo(currentPos)
            if (dist > 4.0) {
                potentialStaffCheck("Teleport Detected (moved ${String.format("%.2f", dist)} blocks from $prev to $currentPos)")
                return
            }
        }

        // 3. Forced Server Rotation Spike Check (> 8.0 degrees jump away from target)
        if (targetAngles != null) {
            val yawDiff = abs(player.yRot - targetAngles.first)
            val pitchDiff = abs(player.xRot - targetAngles.second)
            if (yawDiff > 8.0f || pitchDiff > 8.0f) {
                potentialStaffCheck("Forced Rotation Detected (Yaw: ${player.yRot}, Pitch: ${player.xRot} vs Target: ${targetAngles.first}, ${targetAngles.second})")
                return
            }
        }

        lastPos = currentPos
    }

    fun handleChatMessage(message: String) {
        if (!WSFarmEngine.isRunning) return

        if (restartRegex.matches(message)) {
            HypCroMod.logWatchdog("Server restart announced. Stopping macro and warping to hub...")
            WSFarmEngine.stopMacro(reason = "Server Restart")
            CoroutineScope(Dispatchers.Default).launch {
                delay(2000)
                Minecraft.getInstance().connection?.sendCommand("hub")
            }
        }
    }

    fun potentialStaffCheck(reason: String) {
        WSFarmEngine.abortScript(reason)
        HypCroMod.logAlarmBanner(reason)
        playFailsafeAlarm()
    }

    private fun playFailsafeAlarm() {
        val client = Minecraft.getInstance()
        isAlarmActive = true
        alarmJob?.cancel()

        alarmJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isAlarmActive) {
                client.execute {
                    val player = client.player
                    if (player != null) {
                        player.playSound(SoundEvents.GHAST_SCREAM, 1.0f, 1.0f)
                        player.playSound(SoundEvents.ANVIL_LAND, 1.0f, 1.0f)
                    }
                }
                delay(500) // 0.5s interval to protect hearing
            }
        }
    }
}
