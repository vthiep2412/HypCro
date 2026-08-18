package com.hypcro.failsafe

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.farming.MacroInputController
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.minecraft.client.Minecraft
import kotlin.coroutines.coroutineContext

object AntiStuckEngine {

    private fun isAirborne(client: Minecraft): Boolean {
        val player = client.player ?: return false
        return player.abilities.flying || !player.onGround()
    }

    /**
     * Checks if player is flying or airborne before macro activation and safely brings them to the ground.
     * Returns true if ground state is achieved or check was skipped, false if it failed.
     */
    suspend fun resolveFlyingState(client: Minecraft): Boolean {
        if (!ConfigManager.config.generalConfig.antiStuck.checkFlying) return true
        if (client.player == null) return false

        if (isAirborne(client)) {
            HypCroMod.log("§e[Anti-Stuck] Flying or airborne detected. Sneaking down to ground...")
            var attempts = 0
            try {
                while (coroutineContext.isActive && isAirborne(client) && attempts < 25) {
                    attempts++
                    MacroInputController.shift = true
                    client.execute { client.options.keyShift.setDown(true) }
                    val holdTime = kotlin.random.Random.nextLong(200, 600)
                    delay(holdTime)
                    MacroInputController.shift = false
                    client.execute { client.options.keyShift.setDown(false) }
                    delay(kotlin.random.Random.nextLong(100, 250))
                }
            } finally {
                MacroInputController.shift = false
                client.execute { client.options.keyShift.setDown(false) }
            }

            if (isAirborne(client)) {
                HypCroMod.log("§c[Anti-Stuck] Failed to reach ground safely.")
                return false
            }
        }
        return true
    }
}

