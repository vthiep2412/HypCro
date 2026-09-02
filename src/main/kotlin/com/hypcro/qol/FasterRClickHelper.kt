package com.hypcro.qol

import com.hypcro.config.ConfigManager
import com.hypcro.mixins.MinecraftAccessor
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import kotlin.random.Random

object FasterRClickHelper {

    private var cachedSlot: Int = -1
    private var cachedStackHash: Int = 0
    private var isWeaponEligible: Boolean = false

    private var holdStartTime: Long = 0L
    private var lastClickTime: Long = 0L
    private var nextIntervalMs: Long = 100L

    /**
     * Inspects the currently held item's tooltip/lore for case-sensitive
     * "SWORD" and "RIGHT CLICK" keywords. Caches the result per slot/item hash.
     */
    private fun checkWeaponEligibility(stack: ItemStack, slot: Int): Boolean {
        if (stack.isEmpty) return false
        val stackHash = stack.hashCode()
        if (slot == cachedSlot && stackHash == cachedStackHash) {
            return isWeaponEligible
        }

        cachedSlot = slot
        cachedStackHash = stackHash

        val lines = mutableListOf<String>()
        lines.add(stack.hoverName.string)

        val lore = stack.get(DataComponents.LORE)
        if (lore != null) {
            for (line in lore.lines) {
                lines.add(line.string)
            }
        }

        val fullText = lines.joinToString(" ")
        // Must contain case-sensitive "SWORD" and "RIGHT CLICK"
        isWeaponEligible = fullText.contains("SWORD") && fullText.contains("RIGHT CLICK")
        return isWeaponEligible
    }

    /**
     * Called on each client tick / frame to drive the accelerated 9 to 13 CPS burst
     * when holding down right click with an eligible sword weapon.
     */
    fun onClientTick(client: Minecraft) {
        if (!ConfigManager.config.qolConfig.fasterRClick) {
            holdStartTime = 0L
            return
        }

        // Only active in-game without open GUI screens
        if (client.screen != null) {
            holdStartTime = 0L
            return
        }

        val player = client.player ?: return
        val currentSlot = player.inventory.selectedSlot
        val heldItem = player.mainHandItem

        if (!checkWeaponEligibility(heldItem, currentSlot)) {
            holdStartTime = 0L
            return
        }

        // Check if the user is holding down Right Click
        val isUseDown = client.options.keyUse.isDown
        val now = System.currentTimeMillis()

        if (!isUseDown) {
            holdStartTime = 0L
            return
        }

        // User is holding down right click
        if (holdStartTime == 0L) {
            holdStartTime = now
            lastClickTime = now
            nextIntervalMs = 105L
            return
        }

        val heldDuration = now - holdStartTime
        // Do not intercept single clicks or quick taps (under 160ms)
        if (heldDuration < 160L) {
            return
        }

        // 1. Acceleration ramp phase: smoothly ramps from 2.0 CPS up to 9.0 CPS over 440ms
        // 2. Sustained hold phase: holds with humanized micro-jitter randomized between 9.0 and 13.0 CPS
        val targetCps = if (heldDuration < 600L) {
            val rampProgress = ((heldDuration - 160L) / 440.0).coerceIn(0.0, 1.0)
            val rampCps = 2.0 + (rampProgress * 7.0) // 2.0 -> 9.0 CPS ramp
            val rampJitter = Random.nextDouble(-0.3, 0.3)
            (rampCps + rampJitter).coerceIn(2.0, 9.5)
        } else {
            // Full hold: randomized jitter between 9.0 and 13.0 CPS
            Random.nextDouble(9.0, 13.0)
        }

        if (now - lastClickTime >= nextIntervalMs) {
            lastClickTime = now
            nextIntervalMs = (1000.0 / targetCps).toLong()

            val accessor = client as? com.hypcro.mixins.MinecraftAccessor ?: return
            accessor.rightClickDelay = 0
            accessor.invokeStartUseItem()
        }
    }
}
