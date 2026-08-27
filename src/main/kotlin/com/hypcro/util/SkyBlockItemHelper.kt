package com.hypcro.util

import com.hypcro.config.CropType
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import kotlin.math.abs

data class VacuumTierInfo(
    val tier: Int,
    val name: String,
    val baseDurationMs: Long,
    val attackRange: Double
)

object SkyBlockItemHelper {

    fun getExtraAttributeId(stack: ItemStack): String {
        if (stack.isEmpty) return ""
        val customData = stack.get(DataComponents.CUSTOM_DATA) ?: return ""
        val nbt = customData.copyTag()
        if (!nbt.contains("ExtraAttributes")) return ""
        val ea = nbt.getCompound("ExtraAttributes").orElse(null) ?: return ""
        return ea.getString("id").orElse("")
    }

    fun hasExtraAttributeId(stack: ItemStack, prefix: String): Boolean {
        val id = getExtraAttributeId(stack)
        return id.startsWith(prefix, ignoreCase = true)
    }

    fun isFarmingTool(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val id = getExtraAttributeId(stack).uppercase()
        if (id.contains("HOE") || id.contains("CUTTER") || id.contains("DICER") || id.contains("KNIFE") || id.contains("CHOPPER")) {
            return true
        }
        val name = stack.hoverName.string.uppercase()
        if (name.contains("HOE") || name.contains("CUTTER") || name.contains("DICER") || name.contains("CHOPPER")) {
            return true
        }
        if (stack.item.toString().contains("hoe", ignoreCase = true)) {
            return true
        }
        return false
    }

    fun findToolSlot(client: Minecraft, crop: CropType? = null): Int? {
        val player = client.player ?: return null
        val curSlot = player.inventory.selectedSlot
        val slotsByProximity = (0..8).sortedBy { abs(it - curSlot) }

        // 1. If crop is provided, prioritize specific crop tool
        if (crop != null) {
            val prefix = when (crop) {
                CropType.WHEAT -> "THEORETICAL_HOE_WHEAT"
                CropType.CARROT -> "THEORETICAL_HOE_CARROT"
                CropType.POTATO -> "THEORETICAL_HOE_POTATO"
                CropType.NETHER_WART -> "THEORETICAL_HOE_WARTS"
                CropType.MUSHROOM -> "FUNGI_CUTTER"
            }
            for (slot in slotsByProximity) {
                val stack = player.inventory.getItem(slot)
                if (hasExtraAttributeId(stack, prefix)) {
                    return slot
                }
            }
        }

        // 2. Fallback: choose nearest farming tool in hotbar
        for (slot in slotsByProximity) {
            val stack = player.inventory.getItem(slot)
            if (isFarmingTool(stack)) {
                return slot
            }
        }

        return null
    }

    fun findMousematSlot(client: Minecraft): Int? {
        val player = client.player ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            if (getExtraAttributeId(stack).equals("SQUEAKY_MOUSEMAT", ignoreCase = true)) {
                return slot
            }
            if (stack.hoverName.string.contains("Squeaky Mousemat", ignoreCase = true)) {
                return slot
            }
        }
        return null
    }

    fun findVacuumSlot(client: Minecraft): Int? {
        val player = client.player ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val id = getExtraAttributeId(stack)
            if (id.contains("VACUUM", ignoreCase = true)) {
                return slot
            }
            if (stack.hoverName.string.contains("Vacuum", ignoreCase = true)) {
                return slot
            }
        }
        return null
    }

    fun getVacuumTierInfo(client: Minecraft, slot: Int): VacuumTierInfo {
        val player = client.player ?: return VacuumTierInfo(1, "Basic Vacuum", 8000L, 3.0)
        val stack = player.inventory.getItem(slot)
        val itemId = getExtraAttributeId(stack).uppercase()

        return when {
            itemId.contains("INFINI_VACUUM_HOOVERIUS") || itemId.contains("HOOVERIUS") ->
                VacuumTierInfo(5, "InfiniVacuum™ Hooverius", 4000L, 7.0)
            itemId.contains("INFINI_VACUUM") || itemId.contains("INFINIVACUUM") ->
                VacuumTierInfo(4, "InfiniVacuum™", 5000L, 6.0)
            itemId.contains("SKYMART_HYPER_VACUUM") || itemId.contains("HYPER_VACUUM") ->
                VacuumTierInfo(3, "SkyMart Hyper Vacuum", 6000L, 5.0)
            itemId.contains("SKYMART_TURBO_VACUUM") || itemId.contains("TURBO_VACUUM") ->
                VacuumTierInfo(2, "SkyMart Turbo Vacuum", 7000L, 4.0)
            else ->
                VacuumTierInfo(1, "SkyMart Vacuum", 8000L, 3.0)
        }
    }

    fun findBeachBallSlot(client: Minecraft): Int? {
        val player = client.player ?: return null
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val id = getExtraAttributeId(stack)
            if (id.contains("BOUNCY_BEACH_BALL", ignoreCase = true) || id.contains("BEACH_BALL", ignoreCase = true)) {
                return slot
            }
            if (stack.hoverName.string.contains("Bouncy Beach Ball", ignoreCase = true) || stack.hoverName.string.contains("Beach Ball", ignoreCase = true)) {
                return slot
            }
        }
        return null
    }
}
