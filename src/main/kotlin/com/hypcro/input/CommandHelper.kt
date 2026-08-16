package com.hypcro.input

import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import kotlin.random.Random

object CommandHelper {

    /**
     * Simulates realistic human typing delay before executing a Minecraft client chat/server command.
     */
    suspend fun sendCommandHumanized(client: Minecraft, command: String) {
        val len = command.length
        val baseDelay = if (len > 15) {
            1500L + ((len - 15) / 5) * 300L
        } else {
            1200L
        }

        // Humanize offset by +/- 100ms
        val jitter = Random.nextLong(-100L, 101L)
        val typingDuration = (baseDelay + jitter).coerceAtLeast(800L)

        delay(typingDuration) // Simulate typing time
        client.execute {
            client.connection?.sendCommand(command)
        }
        delay(300) // Brief pause after pressing enter
    }
}
