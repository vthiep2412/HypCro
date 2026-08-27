package com.hypcro.farming

import com.hypcro.HypCroMod
import com.hypcro.input.CommandHelper
import com.hypcro.movement.MouseMovementEngine
import com.hypcro.util.AngleUtils
import com.hypcro.util.ChatHistoryTracker
import com.hypcro.util.SkyBlockItemHelper
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import kotlinx.coroutines.delay

object MousematHelper {

    fun findMousematSlot(client: Minecraft): Int? {
        return SkyBlockItemHelper.findMousematSlot(client)
    }

    fun readMousematAngles(stack: ItemStack): Pair<Float, Float>? {
        val loreComponent = stack.get(DataComponents.LORE)
        var yaw: Float? = null
        var pitch: Float? = null

        if (loreComponent != null) {
            for (text in loreComponent.lines()) {
                val line = text.string.replace("§[0-9a-fk-or]".toRegex(), "")
                if (line.contains("Selected Yaw:", ignoreCase = true)) {
                    yaw = line.substringAfter("Selected Yaw:").trim().toFloatOrNull()
                }
                if (line.contains("Selected Pitch:", ignoreCase = true)) {
                    pitch = line.substringAfter("Selected Pitch:").trim().toFloatOrNull()
                }
            }
        }

        return if (yaw != null && pitch != null) Pair(yaw, pitch) else null
    }

    suspend fun alignAngles(client: Minecraft, targetYaw: Float, targetPitch: Float): Boolean {
        val player = client.player ?: return false
        val mousematSlot = findMousematSlot(client)
        if (mousematSlot == null) {
            HypCroMod.logWarn("Squeaky Mousemat not found on hotbar (0-8)!")
            return false
        }
        val originalSlot = player.inventory.selectedSlot

        // 1. Switch to mousemat & wait 1s (human looking at item / preparing to type)
        client.execute { player.inventory.selectedSlot = mousematSlot }
        delay(1000)

        val stack = player.inventory.getItem(mousematSlot)
        val currentAngles = readMousematAngles(stack)

        val needSet = currentAngles == null ||
            !AngleUtils.areAnglesClose(
                currentAngles.first,
                currentAngles.second,
                targetYaw,
                targetPitch,
                tolerance = 0.05f
            )

        if (needSet) {
            CommandHelper.sendCommandHumanized(client, "setyaw $targetYaw")
            CommandHelper.sendCommandHumanized(client, "setpitch $targetPitch")
        }

        // 2. Perform left-click alignment attempts with chat cooldown detection
        for (attempt in 1..5) {
            val clickTime = System.currentTimeMillis()

            client.execute {
                // Trigger genuine left-click swing animation and attack packet
                player.swing(InteractionHand.MAIN_HAND)
                client.options.keyAttack.setDown(true)
            }
            delay(50)
            client.execute {
                client.options.keyAttack.setDown(false)
            }

            // 300ms response window
            delay(300)

            val pYaw = player.yRot
            val pPitch = player.xRot

            // Check if server snapped the player's rotation with 0.5 deg tolerance
            if (AngleUtils.areAnglesClose(pYaw, pPitch, targetYaw, targetPitch, tolerance = 0.5f)) {
                HypCroMod.logSuccess("Snapped to Squeaky Mousemat!")
                client.execute { player.inventory.selectedSlot = originalSlot }
                delay(200)
                return true
            }

            if (attempt == 5) {
                break
            }

            // Check fresh chat messages received strictly after clickTime
            val recentMessages = ChatHistoryTracker.getMessagesSince(clickTime)
            val hasCooldownMessage = recentMessages.any { entry ->
                entry.message.contains("This ability is on cooldown for ")
            }

            if (hasCooldownMessage) {
                HypCroMod.logWarn("Mousemat ability on cooldown. Waiting exact 3s before retrying...")
                delay(3000)
            } else {
                HypCroMod.logWarn("Mousemat did not snap and not on cooldown. Rotating pitch to 80°...")
                MouseMovementEngine.rotateTo(client, player.yRot, 80.0f)
                delay(1200)
            }
        }

        client.execute { player.inventory.selectedSlot = originalSlot }
        return false
    }
}
