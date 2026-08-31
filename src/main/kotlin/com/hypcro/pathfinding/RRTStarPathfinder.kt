// This is the Batch Informed Trees (BIT*) pathfinding implementation for 3D autonomous flight.
package com.hypcro.pathfinding

import com.hypcro.HypCroMod
import com.hypcro.movement.CentralMovementCoordinator
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object RRTStarPathfinder : IPathfinder {

    private const val INITIAL_BATCH_SIZE = 100
    private const val BATCH_SIZE = 80
    private const val MAX_BATCHES = 16
    private const val DEFAULT_CONNECTION_RADIUS = 4.5
    private const val FINE_CONNECTION_RADIUS = 2.2
    private const val OBSTACLE_CLEARANCE_BUFFER = 1.2
    private const val MIN_CLEARANCE_BUFFER = 0.35
    private const val EDGE_SAMPLE_STEP = 0.35

    private class Vertex(
        val pos: Vec3,
        var cost: Double = Double.POSITIVE_INFINITY,
        var parent: Vertex? = null
    )

    private class Edge(
        val source: Vertex,
        val target: Vertex,
        val estimatedCost: Double
    ) : Comparable<Edge> {
        override fun compareTo(other: Edge): Int = this.estimatedCost.compareTo(other.estimatedCost)
    }

    override fun computePath(level: Level, start: Vec3, destination: Vec3, ignoreHorizontalXZ: Boolean): List<Vec3> {
        return computePath(level, start, destination, null, ignoreHorizontalXZ)
    }

    fun computePath(level: Level, start: Vec3, destination: Vec3, maxTimeMs: Long? = null, ignoreHorizontalXZ: Boolean = false): List<Vec3> {
        val startTime = System.currentTimeMillis()
        val timeBudgetMs = maxTimeMs ?: (com.hypcro.config.ConfigManager.config.pestDestroyer.bitStarTimeSeconds * 1000.0).toLong().coerceAtLeast(100L)
        PathfindingVisualizer.clearDebug()

        val effectiveStart = ThetaStarPathfinder.findPassableStart(level, start)
        if (effectiveStart == null) {
            HypCroMod.logWarn("BIT*: starting position is fully enclosed in solid blocks")
            PathfindingVisualizer.setPath("BIT* (Blocked Start)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }

        val goalY = destination.y
        val targetDest: Vec3
        if (ignoreHorizontalXZ) {
            if (VerticalGoal.isReached(effectiveStart.y, goalY)) {
                val atAltitude = listOf(effectiveStart)
                PathfindingVisualizer.setPath("BIT* (Altitude Reached)", atAltitude, System.currentTimeMillis() - startTime)
                return atAltitude
            }
            targetDest = Vec3(effectiveStart.x, goalY, effectiveStart.z)
        } else {
            val passableDest = ThetaStarPathfinder.findPassableDestination(level, destination)
            if (passableDest == null) {
                HypCroMod.logWarn("BIT*: destination is fully enclosed in solid blocks")
                PathfindingVisualizer.setPath("BIT* (Obstructed)", emptyList(), System.currentTimeMillis() - startTime)
                return emptyList()
            }
            targetDest = passableDest
        }

        fun heuristicTo(pos: Vec3): Double =
            if (ignoreHorizontalXZ) VerticalGoal.heuristic(pos.y, goalY) else pos.distanceTo(targetDest)

        fun driftCostAt(pos: Vec3): Double =
            if (ignoreHorizontalXZ) VerticalGoal.driftCost(pos.x, pos.z, effectiveStart.x, effectiveStart.z) else 0.0

        // Direct Line of Sight shortcut
        if (effectiveStart.distanceTo(targetDest) <= 1.2 || checkFlightEdge(level, effectiveStart, targetDest, effectiveStart, targetDest)) {
            val directPath = listOf(effectiveStart, targetDest)
            recordChosenPathDebug(directPath)
            PathfindingVisualizer.setPath("BIT*", directPath, System.currentTimeMillis() - startTime)
            return directPath
        }

        val totalDist = heuristicTo(effectiveStart)
        val isCloseRange = totalDist <= 8.0
        val connectRadius = if (isCloseRange) FINE_CONNECTION_RADIUS else DEFAULT_CONNECTION_RADIUS

        val root = Vertex(effectiveStart, 0.0)
        val goalVertex = Vertex(targetDest, Double.POSITIVE_INFINITY)

        val treeVertices = mutableListOf(root)
        val unconnectedSamples = mutableListOf(goalVertex)

        val vertexComparator = Comparator<Vertex> { a, b ->
            val fa = a.cost + heuristicTo(a.pos)
            val fb = b.cost + heuristicTo(b.pos)
            fa.compareTo(fb)
        }
        val vertexQueue = PriorityQueue(vertexComparator)
        val edgeQueue = PriorityQueue<Edge>()

        var cBest = Double.POSITIVE_INFINITY
        val cMin = totalDist
        val rng = ThreadLocalRandom.current()

        // Symmetrical sampling corridor around start and goal for uninformed batches
        val margin = 8.0
        val minX = min(effectiveStart.x, targetDest.x) - margin
        val maxX = max(effectiveStart.x, targetDest.x) + margin
        val minY = min(effectiveStart.y, targetDest.y) - margin
        val maxY = max(effectiveStart.y, targetDest.y) + margin
        val minZ = min(effectiveStart.z, targetDest.z) - margin
        val maxZ = max(effectiveStart.z, targetDest.z) + margin

        // Prolate Hyperspheroid geometric properties
        val center = effectiveStart.add(targetDest).scale(0.5)
        val transverseVec = targetDest.subtract(effectiveStart)
        val rotationBasis = computeRotationBasis(transverseVec)

        // Seed initial direct edge towards goal
        edgeQueue.add(Edge(root, goalVertex, cMin))

        var batchCount = 0
        val maxBatches = maxOf(MAX_BATCHES, (timeBudgetMs / 30L).toInt())

        while (System.currentTimeMillis() - startTime < timeBudgetMs && batchCount < maxBatches) {
            if (CentralMovementCoordinator.isAbortTriggered()) {
                PathfindingVisualizer.clearDebug()
                return emptyList()
            }

            // 1. If both queues are exhausted, generate a new batch of samples
            if (edgeQueue.isEmpty() && vertexQueue.isEmpty()) {
                val samplesToGenerate = if (batchCount == 0) INITIAL_BATCH_SIZE else BATCH_SIZE
                var generated = 0
                var attempts = 0
                val maxAttempts = samplesToGenerate * 8

                if (cBest == Double.POSITIVE_INFINITY || ignoreHorizontalXZ) {
                    // Uninformed uniform sampling in corridor bounding box
                    while (generated < samplesToGenerate && attempts < maxAttempts) {
                        attempts++
                        val samplePos = Vec3(
                            rng.nextDouble(minX, maxX),
                            rng.nextDouble(minY, maxY),
                            rng.nextDouble(minZ, maxZ)
                        )
                        if (hasMinimumClearance(level, samplePos)) {
                            unconnectedSamples.add(Vertex(samplePos))
                            generated++
                        }
                    }
                } else {
                    // Informed Prolate Hyperspheroid (PHS) sampling strictly inside cBest
                    while (generated < samplesToGenerate && attempts < maxAttempts) {
                        attempts++
                        val samplePos = sampleInformedEllipsoid(rng, center, cBest, cMin, rotationBasis)
                        if (hasMinimumClearance(level, samplePos)) {
                            unconnectedSamples.add(Vertex(samplePos))
                            generated++
                        }
                    }
                }

                // Add all current tree vertices into vertexQueue for expansion to new samples
                for (v in treeVertices) {
                    vertexQueue.add(v)
                }

                batchCount++
            }

            // 2. Interleaved Vertex Expansion: expand vertices into edgeQueue until edgeQueue top is competitive
            while (System.currentTimeMillis() - startTime < timeBudgetMs && vertexQueue.isNotEmpty() && (edgeQueue.isEmpty() || (vertexQueue.peek().cost + heuristicTo(vertexQueue.peek().pos)) <= edgeQueue.peek().estimatedCost)) {
                if (CentralMovementCoordinator.isAbortTriggered()) {
                    PathfindingVisualizer.clearDebug()
                    return emptyList()
                }

                val bestVertex = vertexQueue.poll()
                val vEst = bestVertex.cost + heuristicTo(bestVertex.pos)
                if (vEst >= cBest) continue

                val vPos = bestVertex.pos

                // Candidate edges from bestVertex to unconnected samples
                for (sample in unconnectedSamples) {
                    val dist = vPos.distanceTo(sample.pos)
                    if (dist <= connectRadius) {
                        val estCost = bestVertex.cost + dist + heuristicTo(sample.pos)
                        if (estCost < cBest && bestVertex.cost + dist < sample.cost) {
                            edgeQueue.add(Edge(bestVertex, sample, estCost))
                        }
                    }
                }

                // Candidate rewiring edges to other tree vertices
                for (otherTreeV in treeVertices) {
                    if (otherTreeV === bestVertex) continue
                    val dist = vPos.distanceTo(otherTreeV.pos)
                    if (dist <= connectRadius) {
                        val estCost = bestVertex.cost + dist + heuristicTo(otherTreeV.pos)
                        if (estCost < cBest && bestVertex.cost + dist < otherTreeV.cost) {
                            edgeQueue.add(Edge(bestVertex, otherTreeV, estCost))
                        }
                    }
                }
            }

            // 3. Process best candidate edge from edgeQueue with Lazy Collision Evaluation
            if (edgeQueue.isNotEmpty()) {
                if (System.currentTimeMillis() - startTime >= timeBudgetMs) {
                    break
                }
                if (CentralMovementCoordinator.isAbortTriggered()) {
                    PathfindingVisualizer.clearDebug()
                    return emptyList()
                }

                val bestEdge = edgeQueue.poll()
                if (bestEdge.estimatedCost >= cBest) {
                    // No remaining edge in edgeQueue can beat the current best solution
                    edgeQueue.clear()
                    vertexQueue.clear()
                    continue
                }

                val source = bestEdge.source
                val target = bestEdge.target
                val edgeDist = source.pos.distanceTo(target.pos)
                val candidateCost = source.cost + edgeDist

                if (candidateCost + heuristicTo(target.pos) >= cBest) continue
                if (candidateCost >= target.cost) continue

                PathfindingVisualizer.addDebugBranch(source.pos, target.pos, PathfindingVisualizer.SegmentType.YELLOW_SEARCHING)

                // Lazy physical collision evaluation
                if (!checkFlightEdge(level, source.pos, target.pos, effectiveStart, targetDest)) {
                    PathfindingVisualizer.addDebugBranch(source.pos, target.pos, PathfindingVisualizer.SegmentType.RED_UNREACHABLE)
                    continue
                }

                PathfindingVisualizer.addDebugBranch(source.pos, target.pos, PathfindingVisualizer.SegmentType.GREEN_REACHABLE)

                val clearancePenalty = computeClearancePenalty(level, source.pos, target.pos, effectiveStart, targetDest)
                val driftPenalty = driftCostAt(target.pos)
                val finalTargetCost = candidateCost + clearancePenalty + driftPenalty

                if (finalTargetCost < target.cost) {
                    target.cost = finalTargetCost
                    target.parent = source

                    if (!treeVertices.contains(target)) {
                        treeVertices.add(target)
                        unconnectedSamples.remove(target)
                        vertexQueue.add(target)
                    }

                    // Check if target reached goal or has direct line of sight to goal / altitude reached
                    val isGoalReached = if (ignoreHorizontalXZ) {
                        VerticalGoal.isReached(target.pos.y, goalY)
                    } else {
                        target === goalVertex || target.pos.distanceTo(targetDest) <= 1.2 || checkFlightEdge(level, target.pos, targetDest, effectiveStart, targetDest)
                    }

                    if (isGoalReached) {
                        val toGoalCost = if (target === goalVertex || ignoreHorizontalXZ) 0.0 else target.pos.distanceTo(targetDest)
                        val totalSolCost = target.cost + toGoalCost
                        if (totalSolCost < cBest) {
                            cBest = totalSolCost
                            if (target !== goalVertex) {
                                goalVertex.cost = totalSolCost
                                goalVertex.parent = target
                            }
                            if (ignoreHorizontalXZ) break
                        }
                    }
                }
            }
        }

        if (CentralMovementCoordinator.isAbortTriggered()) {
            PathfindingVisualizer.clearDebug()
            return emptyList()
        }

        if (cBest == Double.POSITIVE_INFINITY || goalVertex.parent == null) {
            HypCroMod.logWarn("BIT*: destination is unreachable or iteration budget exhausted")
            PathfindingVisualizer.setPath("BIT* (Unreachable)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }

        // Reconstruct raw solution trajectory
        val rawPath = mutableListOf<Vec3>()
        var curr: Vertex? = goalVertex
        while (curr != null) {
            rawPath.add(curr.pos)
            curr = curr.parent
        }
        rawPath.reverse()

        // Apply string-pulling smoothing, obstacle clearance enforcement, and micro-kink filtering
        val smoothedPath = smoothFlightPath(level, rawPath, effectiveStart, targetDest)
        val finalPath = if (ignoreHorizontalXZ) VerticalGoal.truncateAtAltitude(smoothedPath, goalY) else smoothedPath
        recordChosenPathDebug(finalPath)
        PathfindingVisualizer.setPath("BIT*", finalPath, System.currentTimeMillis() - startTime)
        return finalPath
    }

    // --- Mathematical Helpers for Prolate Hyperspheroid (PHS) Informed Sampling ---

    private fun sampleUnitBall(rng: ThreadLocalRandom): Vec3 {
        val u = rng.nextDouble()
        val v = rng.nextDouble()
        val w = rng.nextDouble()
        val theta = 2.0 * Math.PI * u
        val cosPhi = 2.0 * v - 1.0
        val sinPhi = sqrt(max(0.0, 1.0 - cosPhi * cosPhi))
        val r = cbrt(w)
        return Vec3(
            r * sinPhi * cos(theta),
            r * sinPhi * sin(theta),
            r * cosPhi
        )
    }

    private fun computeRotationBasis(transverseVec: Vec3): Triple<Vec3, Vec3, Vec3> {
        val len = sqrt(transverseVec.x * transverseVec.x + transverseVec.y * transverseVec.y + transverseVec.z * transverseVec.z)
        val u1 = if (len < 1e-9) Vec3(1.0, 0.0, 0.0) else Vec3(transverseVec.x / len, transverseVec.y / len, transverseVec.z / len)
        val arbitrary = if (abs(u1.x) < 0.9) Vec3(1.0, 0.0, 0.0) else Vec3(0.0, 1.0, 0.0)
        val proj = arbitrary.x * u1.x + arbitrary.y * u1.y + arbitrary.z * u1.z
        val w2 = Vec3(arbitrary.x - proj * u1.x, arbitrary.y - proj * u1.y, arbitrary.z - proj * u1.z)
        val len2 = sqrt(w2.x * w2.x + w2.y * w2.y + w2.z * w2.z)
        val u2 = if (len2 < 1e-9) Vec3(0.0, 1.0, 0.0) else Vec3(w2.x / len2, w2.y / len2, w2.z / len2)
        val u3 = Vec3(
            u1.y * u2.z - u1.z * u2.y,
            u1.z * u2.x - u1.x * u2.z,
            u1.x * u2.y - u1.y * u2.x
        )
        return Triple(u1, u2, u3)
    }

    private fun sampleInformedEllipsoid(
        rng: ThreadLocalRandom,
        center: Vec3,
        cBest: Double,
        cMin: Double,
        basis: Triple<Vec3, Vec3, Vec3>
    ): Vec3 {
        val ballPt = sampleUnitBall(rng)
        val a1 = cBest / 2.0
        val minorRadius = 0.5 * sqrt(max(0.0, cBest * cBest - cMin * cMin))

        val e1 = ballPt.x * a1
        val e2 = ballPt.y * minorRadius
        val e3 = ballPt.z * minorRadius

        val (u1, u2, u3) = basis
        return Vec3(
            center.x + u1.x * e1 + u2.x * e2 + u3.x * e3,
            center.y + u1.y * e1 + u2.y * e2 + u3.y * e3,
            center.z + u1.z * e1 + u2.z * e2 + u3.z * e3
        )
    }

    // --- Collision and Obstacle Clearance Evaluation ---

    fun checkFlightEdge(level: Level, from: Vec3, to: Vec3, start: Vec3, dest: Vec3): Boolean {
        val dist = from.distanceTo(to)
        if (dist < 1e-5) return ThetaStarPathfinder.isPassable(level, from)

        val steps = max(1, (dist / EDGE_SAMPLE_STEP).toInt())
        val stepVec = to.subtract(from).scale(1.0 / steps)

        var curr = from.add(stepVec)
        for (i in 1..steps) {
            if (!ThetaStarPathfinder.isPassable(level, curr)) {
                return false
            }
            // Check hard clearance if away from endpoints
            val isNearEndpoint = curr.distanceTo(start) <= 1.8 || curr.distanceTo(dest) <= 1.8
            if (!isNearEndpoint && !hasMinimumClearance(level, curr)) {
                return false
            }
            curr = curr.add(stepVec)
        }
        return ThetaStarPathfinder.isPassable(level, to)
    }

    private fun hasMinimumClearance(level: Level, pos: Vec3): Boolean {
        val minBX = floor(pos.x - MIN_CLEARANCE_BUFFER).toInt()
        val maxBX = floor(pos.x + MIN_CLEARANCE_BUFFER).toInt()
        val minBY = floor(pos.y - 0.2).toInt()
        val maxBY = floor(pos.y + 1.8 + MIN_CLEARANCE_BUFFER).toInt()
        val minBZ = floor(pos.z - MIN_CLEARANCE_BUFFER).toInt()
        val maxBZ = floor(pos.z + MIN_CLEARANCE_BUFFER).toInt()

        for (bx in minBX..maxBX) {
            for (bz in minBZ..maxBZ) {
                for (by in minBY..maxBY) {
                    val bp = BlockPos(bx, by, bz)
                    if (!level.hasChunk(bp.x shr 4, bp.z shr 4)) return false
                    val state = level.getBlockState(bp)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(level, bp)
                    if (shape.isEmpty) continue
                    for (box in shape.toAabbs()) {
                        val closestX = Mth.clamp(pos.x, bx + box.minX, bx + box.maxX)
                        val closestY = Mth.clamp(pos.y + 0.9, by + box.minY, by + box.maxY)
                        val closestZ = Mth.clamp(pos.z, bz + box.minZ, bz + box.maxZ)
                        val dx = pos.x - closestX
                        val dy = (pos.y + 0.9) - closestY
                        val dz = pos.z - closestZ
                        if (dx * dx + dy * dy + dz * dz < MIN_CLEARANCE_BUFFER * MIN_CLEARANCE_BUFFER) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }

    private fun computeClearancePenalty(level: Level, from: Vec3, to: Vec3, start: Vec3, dest: Vec3): Double {
        val mid = from.add(to).scale(0.5)
        if (mid.distanceTo(start) <= 1.8 || mid.distanceTo(dest) <= 1.8) return 0.0
        return AStar3DSmoothedPathfinder.calculateClearanceCost(level, mid, start, dest)
    }

    // --- String-Pulling Smoothing & Micro-Kink Filtering ---

    fun smoothFlightPath(level: Level, raw: List<Vec3>, start: Vec3, dest: Vec3): List<Vec3> {
        if (raw.size <= 2) return raw

        val smoothed = mutableListOf<Vec3>()
        smoothed.add(raw.first())

        var anchor = 0
        while (anchor < raw.size - 1) {
            var furthest = anchor + 1
            for (check in raw.size - 1 downTo anchor + 1) {
                val candidateA = raw[anchor]
                val candidateB = raw[check]
                if (checkFlightEdge(level, candidateA, candidateB, start, dest)) {
                    furthest = check
                    break
                }
            }
            smoothed.add(raw[furthest])
            anchor = furthest
        }

        return filterMicroKinks(smoothed)
    }

    private fun filterMicroKinks(path: List<Vec3>): List<Vec3> {
        if (path.size <= 2) return path

        val filtered = mutableListOf<Vec3>()
        filtered.add(path.first())

        var i = 1
        while (i < path.size - 1) {
            val prev = filtered.last()
            val curr = path[i]
            val next = path[i + 1]

            val d1 = curr.subtract(prev)
            val d2 = next.subtract(curr)
            val len1 = sqrt(d1.x * d1.x + d1.y * d1.y + d1.z * d1.z)
            val len2 = sqrt(d2.x * d2.x + d2.y * d2.y + d2.z * d2.z)

            // Remove coincident or micro-segments (< 0.25b)
            if (len1 < 0.25 || len2 < 0.25) {
                i++
                continue
            }

            // Calculate directional turn angle (dot product of unit direction vectors)
            val dot = (d1.x * d2.x + d1.y * d2.y + d1.z * d2.z) / (len1 * len2)
            // If turn angle is < 4 degrees (cos > 0.9975), points are virtually collinear
            if (dot > 0.9975) {
                i++
                continue
            }

            filtered.add(curr)
            i++
        }

        filtered.add(path.last())
        return filtered
    }

    private fun recordChosenPathDebug(path: List<Vec3>) {
        if (!PathfindingVisualizer.isVerbose || path.size < 2) return
        for (i in 0 until path.size - 1) {
            PathfindingVisualizer.addDebugBranch(path[i], path[i + 1], PathfindingVisualizer.SegmentType.CYAN_CHOSEN)
        }
    }
}

