package com.hypcro.farming

import com.hypcro.config.ConfigManager
import com.hypcro.util.GardenStateReader
import com.hypcro.util.SkyBlockItemHelper
import net.minecraft.client.Minecraft

object MacroController {

    @Volatile
    private var activeSessionEngine: IFarmEngine? = null

    private fun resolveConfiguredEngine(): IFarmEngine {
        val method = ConfigManager.config.activeMethod.uppercase()
        return when (method) {
            "VERTICAL" -> VerticalCropFarmEngine
            else -> WSFarmEngine
        }
    }

    val currentEngine: IFarmEngine
        get() = activeSessionEngine ?: resolveConfiguredEngine()

    val isRunning: Boolean
        get() = currentEngine.isRunning

    val isFarmingActive: Boolean
        get() = currentEngine.isFarmingActive

    val currentTargetAngles: Pair<Float, Float>?
        get() = currentEngine.currentTargetAngles

    @JvmStatic
    fun isAnyMacroActive(): Boolean {
        return isRunning ||
            com.hypcro.pest.PestDestroyerEngine.isRunning ||
            com.hypcro.bouncy.AutoBouncyBall.isRunning ||
            com.hypcro.movement.CentralMovementCoordinator.isNavigating ||
            com.hypcro.movement.CentralMovementCoordinator.isPathfinding ||
            com.hypcro.HypCroMod.hasActiveTest()
    }

    @Synchronized
    fun startMacro(): Boolean {
        if (isAnyMacroActive()) return false
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            com.hypcro.HypCroMod.logWarn("Macro halted: Player or world not loaded.")
            return false
        }
        if (!GardenStateReader.isInGarden(client)) {
            com.hypcro.HypCroMod.logWarn("Macro halted: Player is not in Area: Garden.")
            return false
        }

        val engine = resolveConfiguredEngine()

        // Validate farming tool upfront (prioritizing detected crop, then nearest farming tool)
        val detectedCrop = engine.detectCrop(client)
        val toolSlot = SkyBlockItemHelper.findToolSlot(client, detectedCrop)
        if (toolSlot == null) {
            if (detectedCrop != null) {
                com.hypcro.HypCroMod.logWarn("Macro halted: Missing farming tool for ${detectedCrop.displayName} on hotbar (0-8)!")
            } else {
                com.hypcro.HypCroMod.logWarn("Macro halted: No farming tool found on hotbar (0-8)!")
            }
            return false
        }

        // Validate Squeaky Mousemat upfront for farming macros
        val mousematSlot = SkyBlockItemHelper.findMousematSlot(client)
        if (mousematSlot == null) {
            com.hypcro.HypCroMod.logWarn("Macro halted: Squeaky Mousemat not found on hotbar (0-8)!")
            return false
        }

        // If Auto Pester is enabled, validate vacuum upfront so farm won't abort unexpectedly mid-cycle
        if (ConfigManager.config.autoActivePest) {
            val vacuumSlot = SkyBlockItemHelper.findVacuumSlot(client)
            if (vacuumSlot == null) {
                com.hypcro.HypCroMod.logWarn("Macro halted: Auto Pester is enabled but no Vacuum found on hotbar!")
                return false
            }
        }

        val started = engine.startMacro()
        if (started) {
            activeSessionEngine = engine
        }
        return started
    }

    @Synchronized
    fun stopMacro(reason: String = "Manual") {
        val engine = activeSessionEngine ?: resolveConfiguredEngine()
        engine.stopMacro(reason)
        com.hypcro.pest.PestDestroyerEngine.stop()
        if (!engine.isRunning) {
            activeSessionEngine = null
        }
    }

    @Synchronized
    fun abortScript(message: String) {
        val engine = activeSessionEngine ?: resolveConfiguredEngine()
        engine.abortScript(message)
        com.hypcro.pest.PestDestroyerEngine.stop()
        if (!engine.isRunning) {
            activeSessionEngine = null
        }
        com.hypcro.camera.FreecamManager.disable()
    }

    @Synchronized
    fun stopAllMacros(reason: String = "Manual"): Boolean {
        val now = System.currentTimeMillis()
        com.hypcro.movement.CentralMovementCoordinator.isAbortRequested = true
        com.hypcro.movement.CentralMovementCoordinator.lastAbortTimestamp = now

        var stoppedAny = false
        if (com.hypcro.failsafe.HypcroWatchdog.isAlarmActive) {
            com.hypcro.failsafe.HypcroWatchdog.silenceAlarm()
            stoppedAny = true
        }
        if (com.hypcro.HypCroMod.hasActiveTest() || com.hypcro.movement.CentralMovementCoordinator.isNavigating || com.hypcro.movement.CentralMovementCoordinator.isPathfinding) {
            com.hypcro.HypCroMod.stopAllTests()
            com.hypcro.movement.CentralMovementCoordinator.stopNavigation()
            stoppedAny = true
        }
        if (com.hypcro.pest.PestDestroyerEngine.isRunning) {
            com.hypcro.pest.PestDestroyerEngine.stop()
            stoppedAny = true
        }
        if (com.hypcro.bouncy.AutoBouncyBall.isRunning) {
            com.hypcro.bouncy.AutoBouncyBall.stop()
            stoppedAny = true
        }
        if (isRunning) {
            stopMacro(reason)
            stoppedAny = true
        }
        // if (stoppedAny && com.hypcro.camera.FreecamManager.isFreecamActive) {
        //     com.hypcro.camera.FreecamManager.disable()
        // }
        // Ensure detached camera mode is always turned off on master stop
        com.hypcro.camera.FreecamManager.disable()
        com.hypcro.pathfinding.PathfindingVisualizer.clearIfNotVerbose()
        MacroInputController.releaseAll()
        return stoppedAny
    }

    fun onClientTick(client: Minecraft) {
        currentEngine.onClientTick(client)
    }
}
