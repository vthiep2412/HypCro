package com.hypcro.farming

import com.hypcro.HypCroMod
import com.hypcro.config.CropType
import net.minecraft.client.Minecraft

object VerticalCropFarmEngine : IFarmEngine {
    override val engineName: String = "Vertical Crop Farming"
    override var isRunning: Boolean = false
        private set
    override var isFarmingActive: Boolean = false
        private set
    override var currentTargetAngles: Pair<Float, Float>? = null
        private set

    override fun detectCrop(client: Minecraft): CropType? {
        // Vertical layout crop detection placeholder
        return null
    }

    override fun startMacro(): Boolean {
        HypCroMod.logWarn("Farm mode not supported... Yet >:D ")
        return false
    }

    override fun stopMacro(reason: String) {
        isRunning = false
        isFarmingActive = false
    }

    override fun abortScript(message: String) {
        isRunning = false
        isFarmingActive = false
    }

    override fun onClientTick(client: Minecraft) {
        // Not implemented yet
    }
}
