package com.hypcro.farming

import com.hypcro.config.ConfigManager
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

    @Synchronized
    fun startMacro(): Boolean {
        if (activeSessionEngine?.isRunning == true) return false
        val engine = resolveConfiguredEngine()
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
        if (!engine.isRunning) {
            activeSessionEngine = null
        }
    }

    @Synchronized
    fun abortScript(message: String) {
        val engine = activeSessionEngine ?: resolveConfiguredEngine()
        engine.abortScript(message)
        if (!engine.isRunning) {
            activeSessionEngine = null
        }
    }

    fun onClientTick(client: Minecraft) {
        currentEngine.onClientTick(client)
    }
}
