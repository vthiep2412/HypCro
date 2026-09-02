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

    private const val MAX_DETECTION_RADIUS: Double = 128.0

    private val MINIBOSS_NAMES = setOf(
        "Lost Adventurer",
        "Diamond Guy",
        "Shadow Assassin",
        "King Midas",
        "Spirit Bear"
    )

    private fun isAnyFeatureEnabled(): Boolean {
        val cfg = ConfigManager.config.dungeon
        return cfg.batEsp || cfg.starMobsEsp || cfg.minibossEsp
    }

    private fun isRealPlayer(player: Player): Boolean {
        return player.uuid.version() == 4
    }

    private fun isBaseHealth(entity: LivingEntity, health: Float): Boolean {
        val current = entity.health
        return current >= health && current % health == 0f
    }

    private fun isSecretBat(bat: Bat, client: Minecraft): Boolean {
        return isBaseHealth(bat, 100.0f) && !GardenStateReader.isInBossRoom(client, "4")
    }

    private fun isMiniboss(entity: Entity, client: Minecraft): Boolean {
        if (entity is Player && !isRealPlayer(entity)) {
            val name = entity.name.string.trim()
            if (!MINIBOSS_NAMES.contains(name)) {
                return false
            }
            return if (GardenStateReader.isInBossRoom(client, "4")) {
                entity.position().y < 76.0
            } else {
                name != "Spirit Bear"
            }
        }
        return false
    }

    private fun isStarred(name: String): Boolean {
        val star = "✯"
        val index = name.indexOf(star)
        return index != -1 && index == name.lastIndexOf(star)
    }

    private fun isDungeonMob(entity: Entity, minibosses: Set<Entity>): Boolean {
        if (entity is ArmorStand) return false
        if (!entity.isAlive) return false
        if (entity is Player) {
            return !isRealPlayer(entity) && !minibosses.contains(entity)
        }
        return entity is LivingEntity && !minibosses.contains(entity)
    }

    private fun findNametagOwner(armorStand: ArmorStand, candidates: List<Entity>): Entity? {
        var entity: Entity? = null
        var lowestDist = 2.5f
        val armorPos = armorStand.position()
        val maxY = armorPos.y
        for (ent in candidates) {
            if (ent is ArmorStand) continue
            val entPos = ent.position()
            val dy = maxY - entPos.y
            // Nametags float directly above the mob's feet (0.0 through 3.5 blocks)
            if (dy in 0.0..3.5) {
                val dx = entPos.x - armorPos.x
                val dz = entPos.z - armorPos.z
                val dist3d = sqrt(dx * dx + dy * dy + dz * dz).toFloat()
                if (dist3d < lowestDist) {
                    entity = ent
                    lowestDist = dist3d
                }
            }
        }
        return entity
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

        // 2. Throttle entity scan to centralized interval (5ms / 200 Hz)
        if (now - lastScanTimeMs < com.hypcro.util.EspHelper.SCAN_INTERVAL_MS) {
            return
        }
        lastScanTimeMs = now

        val cfg = ConfigManager.config.dungeon
        val center = player.position()
        val radiusSq = MAX_DETECTION_RADIUS * MAX_DETECTION_RADIUS
        val renderedEntities = level.entitiesForRendering()

        val minibossEntities = mutableSetOf<Entity>()
        val armorStandsWithStar = mutableListOf<ArmorStand>()
        val candidateLivingMobs = mutableListOf<Entity>()
        val results = mutableListOf<TrackedDungeonEntity>()

        for (entity in renderedEntities) {
            if (entity.isRemoved) continue
            if (entity.position().distanceToSqr(center) > radiusSq) continue

            // 1. Miniboss checks
            if (cfg.minibossEsp && isMiniboss(entity, client)) {
                minibossEntities.add(entity)
                results.add(TrackedDungeonEntity(entity, cfg.minibossColor))
                continue
            }

            // 2. Secret Bat ESP
            if (entity is Bat && cfg.batEsp && isSecretBat(entity, client)) {
                results.add(TrackedDungeonEntity(entity, cfg.batEspColor))
                continue
            }

            // 3. Collect ArmorStands and candidate living mobs for Starred Mobs check
            if (cfg.starMobsEsp) {
                if (entity is ArmorStand) {
                    val rawName = entity.customName?.string ?: ""
                    val cleanName = GardenStateReader.stripColor(rawName)
                    if (isStarred(cleanName)) {
                        armorStandsWithStar.add(entity)
                    }
                } else if (isDungeonMob(entity, minibossEntities)) {
                    candidateLivingMobs.add(entity)
                }
            }
        }

        // Map Starred ArmorStands to closest living mob below them
        if (cfg.starMobsEsp && armorStandsWithStar.isNotEmpty() && candidateLivingMobs.isNotEmpty()) {
            for (stand in armorStandsWithStar) {
                val owner = findNametagOwner(stand, candidateLivingMobs)
                if (owner != null && !minibossEntities.contains(owner) && results.none { it.entity == owner }) {
                    results.add(TrackedDungeonEntity(owner, cfg.starMobsEspColor))
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

        val partialTicks = com.hypcro.util.EspHelper.getPartialTicks()
        val player = Minecraft.getInstance().player
        val playerPos = if (player != null) com.hypcro.util.EspHelper.getInterpolatedPosition(player, partialTicks) else null

        for (tracked in cachedEntities) {
            if (tracked.entity.isRemoved) continue

            val (r, g, b) = parseRgb(tracked.hexColor)
            val espStyle = GizmoStyle.strokeAndFill(
                ARGB.color(255, r, g, b),
                2.5f,
                ARGB.color(70, r, g, b)
            )

            val rawBox = com.hypcro.util.EspHelper.getInterpolatedBoundingBox(tracked.entity, partialTicks)

            if (tracked.entity is Bat) {
                // Double the bounding box size around its center
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
                Gizmos.cuboid(rawBox, espStyle).setAlwaysOnTop()
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
