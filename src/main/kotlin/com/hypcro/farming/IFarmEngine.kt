package com.hypcro.farming

import net.minecraft.client.Minecraft

interface IFarmEngine {
    val engineName: String
    val isRunning: Boolean
    val isFarmingActive: Boolean
    val currentTargetAngles: Pair<Float, Float>?

    fun startMacro(): Boolean
    fun stopMacro(reason: String = "Manual")
    fun abortScript(message: String)
    fun onClientTick(client: Minecraft)
}
