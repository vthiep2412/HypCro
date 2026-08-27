package com.hypcro.util

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.sqrt

object AngleUtils {

    fun normalizeYaw(yaw: Float): Float = Mth.wrapDegrees(yaw)

    fun yawDifference(yaw1: Float, yaw2: Float): Float {
        return abs(Mth.wrapDegrees(yaw1 - yaw2))
    }

    fun pitchDifference(pitch1: Float, pitch2: Float): Float {
        return abs(pitch1 - pitch2)
    }

    fun angularDistance(yaw1: Float, pitch1: Float, yaw2: Float, pitch2: Float): Float {
        val dy = Mth.wrapDegrees(yaw1 - yaw2)
        val dp = pitch1 - pitch2
        return sqrt(dy * dy + dp * dp)
    }

    fun areAnglesClose(yaw1: Float, pitch1: Float, yaw2: Float, pitch2: Float, tolerance: Float = 0.1f): Boolean {
        return yawDifference(yaw1, yaw2) < tolerance && pitchDifference(pitch1, pitch2) < tolerance
    }
}
