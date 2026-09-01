package com.hypcro.camera

import com.hypcro.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth

object FreecamManager {

    var isFreecamActive: Boolean = false
        private set

    var freecamYaw: Float = 0.0f
        private set

    var freecamPitch: Float = 0.0f
        private set

    var camX: Double = 0.0
        private set

    var camY: Double = 0.0
        private set

    var camZ: Double = 0.0
        private set

    var prevCamX: Double = 0.0
        private set

    var prevCamY: Double = 0.0
        private set

    var prevCamZ: Double = 0.0
        private set

    fun enable(client: Minecraft) {
        val player = client.player ?: return
        if (isFreecamActive) return

        if (FreeLookManager.isFreeLookActive) {
            FreeLookManager.disable(client)
        }

        isFreecamActive = true
        freecamYaw = player.yRot
        freecamPitch = player.xRot

        camX = player.x
        camY = player.eyeY
        camZ = player.z

        prevCamX = camX
        prevCamY = camY
        prevCamZ = camZ
    }

    fun disable(client: Minecraft? = null) {
        if (!isFreecamActive) return
        isFreecamActive = false
    }

    fun toggle(client: Minecraft) {
        if (isFreecamActive) {
            disable(client)
        } else {
            enable(client)
        }
    }

    fun getRenderX(partialTicks: Float): Double {
        return Mth.lerp(partialTicks.toDouble(), prevCamX, camX)
    }

    fun getRenderY(partialTicks: Float): Double {
        return Mth.lerp(partialTicks.toDouble(), prevCamY, camY)
    }

    fun getRenderZ(partialTicks: Float): Double {
        return Mth.lerp(partialTicks.toDouble(), prevCamZ, camZ)
    }

    fun onMouseTurn(dx: Double, dy: Double, sensitivity: Double, gameInvertX: Boolean = false, gameInvertY: Boolean = false) {
        if (!isFreecamActive) return

        val f = sensitivity * 0.6 + 0.2
        val g = f * f * f * 8.0
        val dX = dx * g
        val dY = dy * g

        val mode = ConfigManager.config.qolConfig.freeLookRespectInvertMouse
        val actualInvertX = when (mode.uppercase()) {
            "ALWAYS" -> true
            "ON" -> gameInvertX
            else -> false
        }
        val actualInvertY = when (mode.uppercase()) {
            "ALWAYS" -> true
            "ON" -> gameInvertY
            else -> false
        }

        val yawDelta = if (actualInvertX) -dX else dX
        val pitchDelta = if (actualInvertY) -dY else dY

        freecamYaw += (yawDelta * 0.15).toFloat()
        freecamPitch += (pitchDelta * 0.15).toFloat()
        freecamPitch = Mth.clamp(freecamPitch, -90.0f, 90.0f)
    }

    private fun isPhysicalKeyDown(client: Minecraft, keyMapping: net.minecraft.client.KeyMapping): Boolean {
        if (client.gui.screen() != null) return false
        val boundKey = (keyMapping as com.hypcro.mixins.KeyMappingAccessor).key ?: return false
        val code = boundKey.value
        if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return false
        return if (boundKey.type == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM) {
            com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.window, code)
        } else if (boundKey.type == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.window.handle(), code) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        } else {
            false
        }
    }

    fun onClientTick(client: Minecraft) {
        if (!isFreecamActive) return

        prevCamX = camX
        prevCamY = camY
        prevCamZ = camZ

        val options = client.options
        val forward = isPhysicalKeyDown(client, options.keyUp)
        val backward = isPhysicalKeyDown(client, options.keyDown)
        val left = isPhysicalKeyDown(client, options.keyLeft)
        val right = isPhysicalKeyDown(client, options.keyRight)
        val up = isPhysicalKeyDown(client, options.keyJump)
        val down = isPhysicalKeyDown(client, options.keyShift)
        val sprint = isPhysicalKeyDown(client, options.keySprint)

        val baseSpeed = (ConfigManager.config.qolConfig.freecamSpeed * 0.5).coerceIn(0.05, 5.0)
        val sprintMultiplier = if (sprint) 1.2 else 1.0
        val speed = baseSpeed * sprintMultiplier

        val radYaw = Math.toRadians(freecamYaw.toDouble())
        val radPitch = Math.toRadians(freecamPitch.toDouble())

        // 3D Forward vector
        val fwdX = -Math.sin(radYaw) * Math.cos(radPitch)
        val fwdY = -Math.sin(radPitch)
        val fwdZ = Math.cos(radYaw) * Math.cos(radPitch)

        // Strafe vector
        val strafeYaw = radYaw + Math.PI / 2.0
        val strX = -Math.sin(strafeYaw)
        val strZ = Math.cos(strafeYaw)

        var moveX = 0.0
        var moveY = 0.0
        var moveZ = 0.0

        if (forward) {
            moveX += fwdX
            moveY += fwdY
            moveZ += fwdZ
        }
        if (backward) {
            moveX -= fwdX
            moveY -= fwdY
            moveZ -= fwdZ
        }
        if (left) {
            moveX -= strX
            moveZ -= strZ
        }
        if (right) {
            moveX += strX
            moveZ += strZ
        }
        if (up) {
            moveY += 1.0
        }
        if (down) {
            moveY -= 1.0
        }

        val lenSq = moveX * moveX + moveY * moveY + moveZ * moveZ
        if (lenSq > 0.0001) {
            val len = Math.sqrt(lenSq)
            camX += (moveX / len) * speed
            camY += (moveY / len) * speed
            camZ += (moveZ / len) * speed
        }
    }
}
