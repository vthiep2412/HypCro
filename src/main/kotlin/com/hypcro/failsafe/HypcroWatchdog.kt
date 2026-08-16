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
            while (isActive && com.hypcro.farming.MacroController.isRunning) {
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
        val targetAngles = com.hypcro.farming.MacroController.currentTargetAngles

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
        if (!com.hypcro.farming.MacroController.isRunning) return

        if (restartRegex.matches(message)) {
            HypCroMod.logWatchdog("Server restart announced. Stopping macro and warping to hub...")
            com.hypcro.farming.MacroController.stopMacro(reason = "Server Restart")
            CoroutineScope(Dispatchers.Default).launch {
                delay(2000)
                Minecraft.getInstance().connection?.sendCommand("hub")
            }
        }
    }

    fun potentialStaffCheck(reason: String) {
        // Disable Free Look immediately so player returns to normal perspective
        val client = Minecraft.getInstance()
        client.execute {
            if (com.hypcro.camera.FreeLookManager.isFreeLookActive) {
                com.hypcro.camera.FreeLookManager.disable(client)
            }
        }

        com.hypcro.farming.MacroController.abortScript(reason)
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
                    // Play non-positional UI sound (0% distance attenuation, full volume anywhere!)
                    client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.GHAST_SCREAM, 1.0f, 2.0f))
                    client.soundManager.play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0f, 2.0f))
                }
                delay(500) // 0.5s interval to protect hearing
            }
        }
    }
}
