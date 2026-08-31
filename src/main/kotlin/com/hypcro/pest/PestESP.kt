package com.hypcro.pest

import com.hypcro.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.ARGB
import net.minecraft.world.phys.AABB

object PestESP {

    private var cachedIsInGarden: Boolean = false
    private var cachedPests: List<TrackedPest> = emptyList()
    private var lastGardenCheckMs: Long = 0L
    private var lastScanTimeMs: Long = 0L

    fun tick(client: Minecraft) {
        if (!ConfigManager.config.pestDestroyer.pestEsp) {
            cachedIsInGarden = false
            cachedPests = emptyList()
            return
        }
        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            cachedIsInGarden = false
            cachedPests = emptyList()
            return
        }

        val now = System.currentTimeMillis()

        // 1. Refresh Garden presence once every 1000ms (1 second)
        if (now - lastGardenCheckMs >= 1000L) {
            lastGardenCheckMs = now
            cachedIsInGarden = PestTabReader.isInGarden(client)
        }

        if (!cachedIsInGarden) {
            cachedPests = emptyList()
            return
        }

        // 2. Refresh entity positions every 50ms (client entity tracking distance up to 128 blocks)
        cachedPests = PestTargetTracker.findPestsInRadius(client, player.position(), 128.0)
    }

    fun renderWorld() {
        if (!ConfigManager.config.pestDestroyer.pestEsp) {
            cachedIsInGarden = false
            cachedPests = emptyList()
            return
        }

        if (!cachedIsInGarden || cachedPests.isEmpty()) return

        val (r, g, b) = parseRgb(ConfigManager.config.pestDestroyer.pestEspColor)
        val espStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, r, g, b),  // Dynamic stroke
            2.5f,
            ARGB.color(70, r, g, b)    // Translucent fill
        )

        for (pest in cachedPests) {
            if (pest.entity.isRemoved) continue
            val eyePos = pest.entity.eyePosition

            // Expanded custom box wrapped around the pest head skull (2x size, renders through walls)
            val box = AABB(
                eyePos.x - 0.64, eyePos.y - 0.56, eyePos.z - 0.64,
                eyePos.x + 0.64, eyePos.y + 0.64, eyePos.z + 0.64
            )
            Gizmos.cuboid(box, espStyle).setAlwaysOnTop()
        }
    }

    fun parseRgb(hex: String): Triple<Int, Int, Int> {
        val clean = hex.removePrefix("#").trim()
        return try {
            val num = clean.toInt(16)
            val r = (num shr 16) and 0xFF
            val g = (num shr 8) and 0xFF
            val b = num and 0xFF
            Triple(r, g, b)
        } catch (_: Exception) {
            Triple(239, 68, 68)
        }
    }
}
