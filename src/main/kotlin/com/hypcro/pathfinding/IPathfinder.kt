package com.hypcro.pathfinding

import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.sqrt

interface IPathfinder {
    /**
     * Computes a 3D flight path from [start] to [destination].
     *
     * @param ignoreHorizontalXZ When true, only the destination Y altitude has to be reached.
     * The destination X/Z are downgraded from a hard requirement to a soft preference: the search
     * terminates on the first node that satisfies the altitude goal, so once the path escapes
     * through a hole/opening into the sky it never backtracks horizontally to the starting X/Z.
     */
    fun computePath(level: Level, start: Vec3, destination: Vec3, ignoreHorizontalXZ: Boolean = false): List<Vec3>
}

/**
 * Shared semantics for altitude-only ("ignoreHorizontalXZ") navigation, used by every
 * pathfinder implementation and by the movement coordinator so all of them agree on
 * what "arrived" means and how strongly horizontal drift is discouraged.
 */
object VerticalGoal {

    /** Arrival tolerance: reaching within 0.5b below the target altitude counts as arrived. */
    const val ARRIVAL_TOLERANCE = 0.5

    /** Soft penalty per block of horizontal drift away from the starting column, per expanded node. */
    const val DRIFT_WEIGHT = 0.12

    fun isReached(y: Double, goalY: Double): Boolean = y >= goalY - ARRIVAL_TOLERANCE

    /** Remaining vertical climb, the only distance metric that matters in altitude-only mode. */
    fun heuristic(y: Double, goalY: Double): Double = max(0.0, goalY - y)

    fun horizontalDrift(px: Double, pz: Double, originX: Double, originZ: Double): Double {
        val dx = px - originX
        val dz = pz - originZ
        return sqrt(dx * dx + dz * dz)
    }

    /** Soft cost that keeps the path near the starting column without ever forbidding drift. */
    fun driftCost(px: Double, pz: Double, originX: Double, originZ: Double): Double {
        return horizontalDrift(px, pz, originX, originZ) * DRIFT_WEIGHT
    }

    /**
     * Cuts a path right after the first waypoint that already satisfies the altitude goal.
     * This removes any trailing nodes that would drag the player back horizontally after
     * it has already popped out into the sky.
     */
    fun truncateAtAltitude(path: List<Vec3>, goalY: Double): List<Vec3> {
        if (path.isEmpty()) return path
        val idx = path.indexOfFirst { isReached(it.y, goalY) }
        return if (idx >= 0) ArrayList(path.subList(0, idx + 1)) else path
    }
}
