package com.hypcro.farming

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec2

object MacroInputController {

    @Volatile
    var forward: Boolean = false
    @Volatile
    var backward: Boolean = false
    @Volatile
    var left: Boolean = false
    @Volatile
    var right: Boolean = false
    @Volatile
    var jump: Boolean = false
    @Volatile
    var shift: Boolean = false
    @Volatile
    var sprint: Boolean = false

    @Volatile
    var attack: Boolean = false
    @Volatile
    var useItem: Boolean = false

    fun setKey(key: Char, isDown: Boolean) {
        when (key.uppercaseChar()) {
            'W' -> forward = isDown
            'S' -> backward = isDown
            'A' -> left = isDown
            'D' -> right = isDown
            ' ' -> jump = isDown
        }
    }

    fun pressKey(key: Char) = setKey(key, true)
    fun releaseKey(key: Char) = setKey(key, false)

    fun holdW() {
        forward = true
        backward = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUp.setDown(true)
            client.options.keyDown.setDown(false)
        }
    }

    fun releaseW() {
        forward = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUp.setDown(false)
        }
    }

    fun holdS() {
        forward = false
        backward = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUp.setDown(false)
            client.options.keyDown.setDown(true)
        }
    }

    fun releaseS() {
        backward = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyDown.setDown(false)
        }
    }

    fun holdA() {
        left = true
        right = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyLeft.setDown(true)
            client.options.keyRight.setDown(false)
        }
    }

    fun holdD() {
        left = false
        right = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(true)
        }
    }

    fun releaseStrafe() {
        left = false
        right = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(false)
        }
    }

    fun holdJump() {
        jump = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyJump.setDown(true)
        }
    }

    fun releaseJump() {
        jump = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyJump.setDown(false)
        }
    }

    fun holdShift() {
        shift = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyShift.setDown(true)
        }
    }

    fun releaseShift() {
        shift = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyShift.setDown(false)
        }
    }

    fun holdSprint() {
        sprint = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keySprint.setDown(true)
        }
    }

    fun releaseSprint() {
        sprint = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keySprint.setDown(false)
            client.player?.isSprinting = false
        }
    }

    fun holdAttack() {
        attack = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyAttack.setDown(true)
        }
    }

    fun releaseAttack() {
        attack = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyAttack.setDown(false)
        }
    }

    fun holdUseItem() {
        useItem = true
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUse.setDown(true)
        }
    }

    fun releaseUseItem() {
        useItem = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUse.setDown(false)
        }
    }

    fun releaseAllMovement() {
        forward = false
        backward = false
        left = false
        right = false
        jump = false
        shift = false
        sprint = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUp.setDown(false)
            client.options.keyDown.setDown(false)
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(false)
            client.options.keyJump.setDown(false)
            client.options.keyShift.setDown(false)
            client.options.keySprint.setDown(false)
            client.player?.isSprinting = false
        }
    }

    fun releaseAll() {
        releaseAllMovement()
        attack = false
        useItem = false
        val client = Minecraft.getInstance()
        client.execute {
            client.options.keyUp.setDown(false)
            client.options.keyDown.setDown(false)
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(false)
            client.options.keyJump.setDown(false)
            client.options.keyShift.setDown(false)
            client.options.keySprint.setDown(false)
            client.options.keyAttack.setDown(false)
            client.options.keyUse.setDown(false)
        }
    }

    fun canPenetrateScreen(): Boolean {
        val screen = Minecraft.getInstance().screen
        return screen == null || screen is ChatScreen
    }

    @JvmStatic
    fun isAnyMacroRunning(): Boolean {
        return MacroController.isRunning || com.hypcro.pest.PestDestroyerEngine.isRunning || com.hypcro.bouncy.AutoBouncyBall.isRunning
    }

    fun isInputAllowed(): Boolean {
        return (isAnyMacroRunning() || com.hypcro.movement.CentralMovementCoordinator.isNavigating) && canPenetrateScreen()
    }

    fun createInput(): Input {
        if (!isInputAllowed()) {
            return Input.EMPTY
        }
        return Input(forward, backward, left, right, jump, shift, sprint)
    }

    fun calculateMoveVector(): Vec2 {
        if (!isInputAllowed()) {
            return Vec2.ZERO
        }
        var forwardImpulse = 0.0f
        if (forward) forwardImpulse += 1.0f
        if (backward) forwardImpulse -= 1.0f

        var leftImpulse = 0.0f
        if (left) leftImpulse += 1.0f
        if (right) leftImpulse -= 1.0f

        return Vec2(leftImpulse, forwardImpulse)
    }
}
