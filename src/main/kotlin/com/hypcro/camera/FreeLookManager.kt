package com.hypcro.camera

import com.hypcro.config.ConfigManager
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth

object FreeLookManager {

    const val MIN_ZOOM = 1.0f
    const val MAX_LIVE_ZOOM = 50.0f
    const val MAX_REMEMBERED_ZOOM = 25.0f

    var isFreeLookActive: Boolean = false
        private set

    var freeYaw: Float = 0.0f
        private set

    var freePitch: Float = 0.0f
        private set

    var cameraDistance: Float = 4.0f
        private set

    private var previousCameraType: CameraType? = null

    fun enable(client: Minecraft) {
        val player = client.player ?: return
        if (isFreeLookActive) return

        isFreeLookActive = true
        freeYaw = player.yRot
        freePitch = player.xRot

        val qol = ConfigManager.config.qolConfig
        cameraDistance = if (qol.freeLookRememberZoom) {
            qol.freeLookSavedZoom.coerceIn(MIN_ZOOM, MAX_REMEMBERED_ZOOM)
        } else {
            4.0f
        }

        previousCameraType = client.options.cameraType

        // Switch to third person back
        client.options.cameraType = CameraType.THIRD_PERSON_BACK
    }

    fun disable(client: Minecraft) {
        if (!isFreeLookActive) return

        val qol = ConfigManager.config.qolConfig
        if (qol.freeLookRememberZoom) {
            qol.freeLookSavedZoom = cameraDistance.coerceIn(MIN_ZOOM, MAX_REMEMBERED_ZOOM)
            ConfigManager.save()
        }

        isFreeLookActive = false
        previousCameraType?.let {
            client.options.cameraType = it
        }
        previousCameraType = null
    }

    fun reset(client: Minecraft) {
        if (isFreeLookActive) {
            disable(client)
        } else {
            previousCameraType?.let {
                client.options.cameraType = it
            }
            previousCameraType = null
        }
        cameraDistance = 4.0f
    }

    fun toggle(client: Minecraft) {
        if (isFreeLookActive) {
            disable(client)
        } else {
            enable(client)
        }
    }

    fun onMouseTurn(dx: Double, dy: Double, sensitivity: Double, gameInvertX: Boolean = false, gameInvertY: Boolean = false) {
        if (!isFreeLookActive) return

        // Use player sensitivity curve
        val f = sensitivity * 0.6 + 0.2
        val g = f * f * f * 8.0
        val dX = dx * g
        val dY = dy * g

        val mode = ConfigManager.config.qolConfig.freeLookRespectInvertMouse
        val actualInvertX = when (mode.uppercase()) {
            "ALWAYS" -> true
            "ON" -> gameInvertX
            else -> false // "OFF"
        }
        val actualInvertY = when (mode.uppercase()) {
            "ALWAYS" -> true
            "ON" -> gameInvertY
            else -> false // "OFF"
        }

        val yawDelta = if (actualInvertX) -dX else dX
        val pitchDelta = if (actualInvertY) -dY else dY

        freeYaw += (yawDelta * 0.15).toFloat()
        freePitch += (pitchDelta * 0.15).toFloat()
        freePitch = Mth.clamp(freePitch, -90.0f, 90.0f)
    }

    fun onMouseScroll(delta: Double) {
        if (!isFreeLookActive) return

        val invert = ConfigManager.config.qolConfig.freeLookInvertZoom
        val step = if (invert) -delta.toFloat() * 0.6f else delta.toFloat() * 0.6f

        // Allow deep custom zoom up to 50.0 blocks
        cameraDistance = Mth.clamp(cameraDistance - step, MIN_ZOOM, MAX_LIVE_ZOOM)
    }
}
