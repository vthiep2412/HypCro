package com.hypcro.farming

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import kotlinx.coroutines.delay
import kotlin.math.abs

object MousematHelper {

    fun findMousematSlot(client: Minecraft): Int? {
        val player = client.player ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (isMousemat(stack)) {
                return slot
            }
        }
        return null
    }

    private fun isMousemat(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val customData = stack.get(DataComponents.CUSTOM_DATA)
        if (customData != null) {
            val nbt = customData.copyTag()
            if (nbt.contains("ExtraAttributes")) {
                val ea = nbt.getCompound("ExtraAttributes").orElse(null)
                if (ea != null && ea.getString("id").orElse("") == "SQUEAKY_MOUSEMAT") return true
            }
        }
        return stack.hoverName.string.contains("Squeaky Mousemat", ignoreCase = true)
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
        val mousematSlot = findMousematSlot(client) ?: return false
        val originalSlot = player.inventory.selectedSlot

        // 1. Switch to mousemat & wait
        client.execute { player.inventory.selectedSlot = mousematSlot }
        delay(300)

        val stack = player.inventory.getItem(mousematSlot)
        val currentAngles = readMousematAngles(stack)

        val needSet = currentAngles == null || 
            abs(currentAngles.first - targetYaw) > 0.05f || 
            abs(currentAngles.second - targetPitch) > 0.05f

        if (needSet) {
            client.connection?.sendCommand("setyaw $targetYaw")
            delay(350)
            client.connection?.sendCommand("setpitch $targetPitch")
            delay(350)
        }

        // 2. Perform genuine item swing / left-click with the Squeaky Mousemat
        for (attempt in 1..3) {
            client.execute {
                // Trigger genuine left-click swing animation and attack packet
                player.swing(InteractionHand.MAIN_HAND)
                client.options.keyAttack.setDown(true)
            }
            delay(50)
            client.execute {
                client.options.keyAttack.setDown(false)
            }
            delay(400) // Wait longer before checking if server snapped rotation

            val pYaw = player.yRot
            val pPitch = player.xRot
            val yawDelta = abs((((pYaw - targetYaw + 180f) % 360f + 360f) % 360f) - 180f)
            val pitchDelta = abs(pPitch - targetPitch)

            // Check if server snapped the player's rotation with 0.5 deg tolerance
            if (yawDelta < 0.5f && pitchDelta < 0.5f) {
                client.execute { player.inventory.selectedSlot = originalSlot }
                delay(200)
                return true
            }
            delay(400) // Wait longer before trying again
        }

        client.execute { player.inventory.selectedSlot = originalSlot }
        return false
    }
}
