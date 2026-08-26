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

        // 2. Refresh entity positions every 50ms
        cachedPests = PestTargetTracker.findPestsInRadius(client, player.position(), 64.0)
    }

    fun renderWorld() {
        if (!ConfigManager.config.pestDestroyer.pestEsp) {
            cachedIsInGarden = false
            cachedPests = emptyList()
            return
        }
        val client = Minecraft.getInstance()
        val now = System.currentTimeMillis()
        if (now - lastScanTimeMs >= 50L) {
            lastScanTimeMs = now
            tick(client)
        }

        if (!cachedIsInGarden || cachedPests.isEmpty()) return

        val espStyle = GizmoStyle.strokeAndFill(
            ARGB.color(255, 239, 68, 68),  // Bright red stroke
            2.5f,
            ARGB.color(70, 239, 68, 68)    // Translucent red fill
        )

        for (pest in cachedPests) {
            val pos = pest.position

            // 2x visual box expansion (renders through all walls via setAlwaysOnTop)
            val box = AABB(
                pos.x - 0.8, pos.y - 0.2, pos.z - 0.8,
                pos.x + 0.8, pos.y + 1.4, pos.z + 0.8
            )
            Gizmos.cuboid(box, espStyle).setAlwaysOnTop()
        }
    }
}
