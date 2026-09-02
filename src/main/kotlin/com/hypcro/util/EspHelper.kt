package com.hypcro.util

import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object EspHelper {

    /**
     * Standardized scan rate across all moving ESP systems (8ms interval = 125 Hz).
     */
    const val SCAN_INTERVAL_MS: Long = 8L

    /**
     * Obtains the current render partial ticks for sub-tick frame interpolation.
     */
    fun getPartialTicks(): Float {
        return Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)
    }

    /**
     * Computes the exact sub-tick interpolated position for an entity.
     */
    fun getInterpolatedPosition(entity: Entity, partialTicks: Float = getPartialTicks()): Vec3 {
        return entity.getPosition(partialTicks)
    }

    /**
     * Computes the exact sub-tick interpolated eye position for an entity.
     */
    fun getInterpolatedEyePosition(entity: Entity, partialTicks: Float = getPartialTicks()): Vec3 {
        return entity.getEyePosition(partialTicks)
    }

    /**
     * Computes the exact sub-tick interpolated bounding box for an entity,
     * ensuring 100% framerate sync with zero 20-tick stepping jitter.
     */
    fun getInterpolatedBoundingBox(entity: Entity, partialTicks: Float = getPartialTicks()): AABB {
        val interpPos = entity.getPosition(partialTicks)
        val rawBox = entity.boundingBox
        val offsetX = interpPos.x - entity.x
        val offsetY = interpPos.y - entity.y
        val offsetZ = interpPos.z - entity.z
        return rawBox.move(offsetX, offsetY, offsetZ)
    }
}
