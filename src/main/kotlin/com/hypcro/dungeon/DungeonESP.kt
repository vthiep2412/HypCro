package com.hypcro.dungeon

import com.hypcro.config.ConfigManager
import com.hypcro.util.GardenStateReader
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import kotlin.math.sqrt

data class TrackedDungeonEntity(
    val entity: Entity,
    val hexColor: String
)

object DungeonESP {

    private var cachedIsInDungeons: Boolean = false
    private var cachedEntities: List<TrackedDungeonEntity> = emptyList()
    private var lastDungeonCheckMs: Long = 0L
    private var lastScanTimeMs: Long = 0L

    private const val SCAN_INTERVAL_MS: Long = 11L // ~90 Hz refresh rate (1000ms / 90 ≈ 11.1ms)
    private const val MAX_DETECTION_RADIUS: Double = 128.0

    private fun isAnyFeatureEnabled(): Boolean {
        val cfg = ConfigManager.config.dungeon
        return cfg.batEsp || cfg.starMobsEsp || cfg.lostAdventurerEsp || cfg.shadowAssassinEsp || cfg.diamondGuyEsp
    }

    private fun isRealPlayer(player: Player): Boolean {
        return player.uuid.version() == 4
    }

    fun tick(client: Minecraft) {
        if (!isAnyFeatureEnabled()) {
            cachedIsInDungeons = false
            cachedEntities = emptyList()
            return
        }

        val level = client.level
        val player = client.player
        if (level == null || player == null) {
            cachedIsInDungeons = false
            cachedEntities = emptyList()
            return
        }

        val now = System.currentTimeMillis()

        // 1. Refresh Dungeon presence once every 1000ms
        if (now - lastDungeonCheckMs >= 1000L) {
            lastDungeonCheckMs = now
            cachedIsInDungeons = GardenStateReader.isInDungeons(client)
        }

        if (!cachedIsInDungeons) {
            cachedEntities = emptyList()
            return
        }

        // 2. Throttle entity scan to ~90 Hz (11ms)
        if (now - lastScanTimeMs < SCAN_INTERVAL_MS) {
            return
        }
        lastScanTimeMs = now

        val cfg = ConfigManager.config.dungeon
        val center = player.position()
        val radiusSq = MAX_DETECTION_RADIUS * MAX_DETECTION_RADIUS
        val renderedEntities = level.entitiesForRendering()

        val armorStandsWithStar = mutableListOf<ArmorStand>()
        val candidateLivingMobs = mutableListOf<LivingEntity>()
        val results = mutableListOf<TrackedDungeonEntity>()

        for (entity in renderedEntities) {
            if (entity.isRemoved) continue
            if (entity.position().distanceToSqr(center) > radiusSq) continue

            // Bat ESP (Secret Bats)
            if (entity is Bat && cfg.batEsp) {
                results.add(TrackedDungeonEntity(entity, cfg.batEspColor))
                continue
            }

            // Miniboss checks (Lost Adventurer, Shadow Assassin, Diamond Guy)
            if (entity is Player && !isRealPlayer(entity) && entity !is AbstractClientPlayer && entity != player) {
                val name = entity.name.string.trim()
                if (name.equals("Lost Adventurer", ignoreCase = true) && cfg.lostAdventurerEsp) {
                    results.add(TrackedDungeonEntity(entity, cfg.lostAdventurerColor))
                    continue
                }
                if (name.equals("Shadow Assassin", ignoreCase = true) && cfg.shadowAssassinEsp) {
                    results.add(TrackedDungeonEntity(entity, cfg.shadowAssassinColor))
                    continue
                }
                if (name.equals("Diamond Guy", ignoreCase = true) && cfg.diamondGuyEsp) {
                    results.add(TrackedDungeonEntity(entity, cfg.diamondGuyColor))
                    continue
                }
            }

            // Collect ArmorStands and living mobs for Starred Mobs check
            if (cfg.starMobsEsp) {
                if (entity is ArmorStand) {
                    val customName = entity.customName?.string ?: ""
                    if (customName.contains("✯")) {
                        armorStandsWithStar.add(entity)
                    }
                } else if (entity is LivingEntity && entity !is ArmorStand) {
                    if (entity is Player && isRealPlayer(entity)) {
                        // Skip real players
                    } else {
                        candidateLivingMobs.add(entity)
                    }
                }
            }
        }

        // Map Starred ArmorStands to closest living mob below them
        if (cfg.starMobsEsp && armorStandsWithStar.isNotEmpty() && candidateLivingMobs.isNotEmpty()) {
            for (stand in armorStandsWithStar) {
                val standPos = stand.position()
                var closestMob: LivingEntity? = null
                var lowestDist = 2.0

                for (mob in candidateLivingMobs) {
                    val mobPos = mob.position()
                    if (mobPos.y <= standPos.y + 0.5 && mobPos.y >= standPos.y - 2.8) {
                        val dx = mobPos.x - standPos.x
                        val dz = mobPos.z - standPos.z
                        val horizDist = sqrt(dx * dx + dz * dz)
                        if (horizDist < lowestDist) {
                            lowestDist = horizDist
                            closestMob = mob
                        }
                    }
                }

                if (closestMob != null && results.none { it.entity == closestMob }) {
                    results.add(TrackedDungeonEntity(closestMob, cfg.starMobsEspColor))
                }
            }
        }

        cachedEntities = results
    }

    fun renderWorld() {
        if (!isAnyFeatureEnabled()) {
            cachedIsInDungeons = false
            cachedEntities = emptyList()
            return
        }

        if (!cachedIsInDungeons || cachedEntities.isEmpty()) return

        val player = Minecraft.getInstance().player
        val playerPos = player?.position()

        for (tracked in cachedEntities) {
            if (tracked.entity.isRemoved) continue

            val (r, g, b) = parseRgb(tracked.hexColor)
            val espStyle = GizmoStyle.strokeAndFill(
                ARGB.color(255, r, g, b),
                2.5f,
                ARGB.color(70, r, g, b)
            )

            if (tracked.entity is Bat) {
                // Double the bounding box size around its center
                val rawBox = tracked.entity.boundingBox
                val box = AABB.ofSize(rawBox.center, rawBox.xsize * 2.0, rawBox.ysize * 2.0, rawBox.zsize * 2.0)
                Gizmos.cuboid(box, espStyle).setAlwaysOnTop()

                // Calculate distance-based dynamic scaling up to 50 blocks
                val dist = if (playerPos != null) playerPos.distanceTo(box.center) else 6.0
                val clampedDist = dist.coerceIn(6.0, 50.0)
                val textScale = (0.32f * (clampedDist / 6.0).toFloat()).coerceIn(0.32f, 2.8f)
                val textStyle = TextGizmo.Style.whiteAndCentered().withScale(textScale)

                // Render billboard text in the middle of the box, always facing the player and visible through walls
                Gizmos.billboardText("§f§lBAT", box.center, textStyle).setAlwaysOnTop()
            } else {
                Gizmos.cuboid(tracked.entity.boundingBox, espStyle).setAlwaysOnTop()
            }
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
            Triple(245, 119, 56)
        }
    }
}
