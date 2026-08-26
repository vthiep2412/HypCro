package com.hypcro.pest

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.Vec3
import java.util.*
import kotlin.math.sqrt

data class TrackedPest(
    val entity: Entity,
    val skullMarker: ArmorStand?,
    val position: Vec3
)

object PestTargetTracker {

    private val confirmedPestUuids = Collections.synchronizedSet(mutableSetOf<UUID>())

    fun clearSessionMemory() {
        confirmedPestUuids.clear()
    }

    val KNOWN_PEST_NAMES = listOf(
        "FLY", "CRICKET", "LOCUST", "RAT", "MOUSE", "FIELD MOUSE",
        "MOSQUITO", "EARTHWORM", "WORM", "TAPEWORM", "MITE", "MOTH",
        "SLUG", "BEETLE", "DRAGONFLY", "FIREFLY", "MANTIS", "PRAYING MANTIS"
    )

    fun isPestNameMatching(rawName: String?): Boolean {
        if (rawName.isNullOrBlank()) return false
        val clean = PestTabReader.stripColor(rawName).uppercase()
        if (clean.contains("ൠ") || clean.contains("\u0D70")) return true
        return KNOWN_PEST_NAMES.any { clean.contains(it) }
    }

    fun findPestsInRadius(client: Minecraft, center: Vec3, radius: Double): List<TrackedPest> {
        val level = client.level ?: return emptyList()
        val results = mutableListOf<TrackedPest>()
        val foundEntityIds = mutableSetOf<Int>()

        val loadedEntities = level.entitiesForRendering()

        // 1. Collect all ArmorStands within vicinity
        val armorStands = mutableListOf<ArmorStand>()
        for (entity in loadedEntities) {
            if (entity is ArmorStand && !entity.isRemoved) {
                armorStands.add(entity)
            }
        }

        // 2. Scan only Bats and Silverfish
        for (entity in loadedEntities) {
            if (entity.isRemoved) continue
            if (entity !is Bat && entity !is Silverfish) continue
            if (entity.position().distanceTo(center) > radius) continue

            val uuid = entity.uuid
            var matchingSkull: ArmorStand? = null

            // Check if already confirmed in session memory
            if (confirmedPestUuids.contains(uuid)) {
                matchingSkull = armorStands.minByOrNull { it.distanceTo(entity) }
                if (matchingSkull != null && matchingSkull.distanceTo(entity) > 2.5) {
                    matchingSkull = null
                }
            } else {
                // Check mob's own name or nearby ArmorStands (tolerance <= 1.5b)
                val entityName = entity.customName?.string
                val nearbyArmorStands = armorStands.filter { it.distanceTo(entity) <= 1.5 }
                val hasMatchingArmorStand = nearbyArmorStands.firstOrNull { isPestNameMatching(it.customName?.string) }

                if (isPestNameMatching(entityName) || hasMatchingArmorStand != null) {
                    confirmedPestUuids.add(uuid)
                    matchingSkull = hasMatchingArmorStand
                }
            }

            if (confirmedPestUuids.contains(uuid) && !foundEntityIds.contains(entity.id)) {
                foundEntityIds.add(entity.id)
                results.add(TrackedPest(entity, matchingSkull, entity.position()))
            }
        }

        return results
    }

    fun isInsidePlotBounds(pos: Vec3, plotCenter: Vec3, maxDistance: Double = 46.0): Boolean {
        val dx = kotlin.math.abs(pos.x - plotCenter.x)
        val dz = kotlin.math.abs(pos.z - plotCenter.z)
        return dx <= maxDistance && dz <= maxDistance
    }

    fun findPestsInPlot(client: Minecraft, plotCenter: Vec3, maxDistance: Double = 46.0): List<TrackedPest> {
        val allNearby = findPestsInRadius(client, plotCenter, radius = maxDistance * 1.42)
        return allNearby.filter { isInsidePlotBounds(it.position, plotCenter, maxDistance) }
    }

    fun forgetPest(uuid: UUID) {
        confirmedPestUuids.remove(uuid)
    }

    fun isPestDeadOrRemoved(client: Minecraft, pest: TrackedPest): Boolean {
        if (pest.entity.isRemoved) {
            forgetPest(pest.entity.uuid)
            return true
        }
        if (pest.entity is LivingEntity && (pest.entity.isDeadOrDying || pest.entity.health <= 0.0f)) {
            forgetPest(pest.entity.uuid)
            return true
        }
        if (pest.skullMarker != null && pest.skullMarker.isRemoved) {
            forgetPest(pest.entity.uuid)
            return true
        }
        val level = client.level ?: return true
        val entityById = level.getEntity(pest.entity.id)
        if (entityById == null || entityById.isRemoved) {
            forgetPest(pest.entity.uuid)
            return true
        }
        val stillInWorld = level.entitiesForRendering().any { it.id == pest.entity.id && !it.isRemoved }
        if (!stillInWorld) {
            forgetPest(pest.entity.uuid)
            return true
        }
        return false
    }

    fun getSafeHoverY(client: Minecraft, x: Double, startY: Double, z: Double, minAirClearance: Double = 0.15): Double {
        val level = client.level ?: return startY
        val bx = kotlin.math.floor(x).toInt()
        val bz = kotlin.math.floor(z).toInt()
        val startBY = kotlin.math.floor(startY + 2.0).toInt().coerceIn(-64, 320)
        val minBY = kotlin.math.max(kotlin.math.floor(startY - 15.0).toInt(), -64)

        val mutablePos = net.minecraft.core.BlockPos.MutableBlockPos()
        for (by in startBY downTo minBY) {
            mutablePos.set(bx, by, bz)
            if (level.hasChunk(bx shr 4, bz shr 4)) {
                val state = level.getBlockState(mutablePos)
                val shape = state.getCollisionShape(level, mutablePos)
                if (!shape.isEmpty) {
                    val maxBlockY = by + shape.max(net.minecraft.core.Direction.Axis.Y)
                    return kotlin.math.max(startY, maxBlockY + minAirClearance)
                }
            }
        }
        return startY
    }

    fun findSafeAttackPosition(
        client: Minecraft,
        playerPos: Vec3,
        targetPos: Vec3,
        otherPests: List<TrackedPest>,
        keepPestActive: Boolean,
        targetEntityId: Int? = null,
        attackRange: Double = 7.0,
        plotCenter: Vec3? = null,
        maxPlotOffset: Double = 46.0
    ): Vec3 {
        fun clampToPlot(v: Vec3): Vec3 {
            if (plotCenter == null) return v
            val minX = plotCenter.x - maxPlotOffset
            val maxX = plotCenter.x + maxPlotOffset
            val minZ = plotCenter.z - maxPlotOffset
            val maxZ = plotCenter.z + maxPlotOffset
            val clampedX = v.x.coerceIn(minX, maxX)
            val clampedZ = v.z.coerceIn(minZ, maxZ)
            return Vec3(clampedX, v.y, clampedZ)
        }

        val toTarget = targetPos.subtract(playerPos)
        val dist = sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)

        val defaultPos = if (dist > 0.1) {
            val rawX = targetPos.x - (toTarget.x / dist) * attackRange
            val rawZ = targetPos.z - (toTarget.z / dist) * attackRange
            val safeY = getSafeHoverY(client, rawX, targetPos.y + 0.5, rawZ, minAirClearance = 0.15)
            clampToPlot(Vec3(rawX, safeY, rawZ))
        } else {
            val safeY = getSafeHoverY(client, targetPos.x + attackRange, targetPos.y + 0.5, targetPos.z, minAirClearance = 0.15)
            clampToPlot(Vec3(targetPos.x + attackRange, safeY, targetPos.z))
        }

        if (!keepPestActive || otherPests.isEmpty()) {
            return defaultPos
        }

        // Search 16 radial angles around target to find an approach angle with > 25b clearance from other pests in line of fire
        var bestCandidate = defaultPos
        var maxClearanceDistance = -1.0

        for (i in 0 until 16) {
            val angle = i * (Math.PI / 8.0)
            val candidateX = targetPos.x + attackRange * kotlin.math.cos(angle)
            val candidateZ = targetPos.z + attackRange * kotlin.math.sin(angle)
            if (plotCenter != null && !isInsidePlotBounds(Vec3(candidateX, targetPos.y, candidateZ), plotCenter, maxPlotOffset)) {
                continue
            }
            val safeY = getSafeHoverY(client, candidateX, targetPos.y + 0.5, candidateZ, minAirClearance = 0.15)
            val candidatePos = Vec3(candidateX, safeY, candidateZ)

            val fireVec = targetPos.subtract(candidatePos).normalize()
            var hasCollateral = false
            var minOtherDist = Double.MAX_VALUE

            for (other in otherPests) {
                if (targetEntityId != null && other.entity.id == targetEntityId) continue
                val toOther = other.position.subtract(candidatePos)
                val otherDist = toOther.length()
                if (otherDist < minOtherDist) minOtherDist = otherDist

                // Check if other pest is within 25 blocks and inside the ~40 degree forward firing cone
                if (otherDist <= 25.0) {
                    val toOtherNorm = toOther.normalize()
                    val dot = fireVec.x * toOtherNorm.x + fireVec.y * toOtherNorm.y + fireVec.z * toOtherNorm.z
                    if (dot > 0.7) { // ~45 deg cone
                        hasCollateral = true
                        break
                    }
                }
            }

            if (!hasCollateral) {
                return candidatePos
            }

            if (minOtherDist > maxClearanceDistance) {
                maxClearanceDistance = minOtherDist
                bestCandidate = candidatePos
            }
        }

        return bestCandidate
    }
}
