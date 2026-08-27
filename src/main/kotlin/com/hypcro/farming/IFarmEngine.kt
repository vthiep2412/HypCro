package com.hypcro.farming

import com.hypcro.config.CropType
import net.minecraft.client.Minecraft

interface IFarmEngine {
    val engineName: String
    val isRunning: Boolean
    val isFarmingActive: Boolean
    val currentTargetAngles: Pair<Float, Float>?

    fun detectCrop(client: Minecraft): CropType?
    fun startMacro(): Boolean
    fun stopMacro(reason: String = "Manual")
    fun abortScript(message: String)
    fun onClientTick(client: Minecraft)
}
