package com.hypcro.pathfinding

import com.hypcro.HypCroMod
import com.hypcro.movement.CentralMovementCoordinator
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.PriorityQueue
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object AStar3DSmoothedPathfinder : IPathfinder {

    private const val MAX_EXPANSIONS = 60000
    private const val DEFAULT_STEP_SIZE = 1.0
    private const val FINE_STEP_SIZE = 0.5
    private const val COARSE_STEP_SIZE = 2.5
    private const val HASH_RESOLUTION = FINE_STEP_SIZE
    private const val AIR_CHECK_RADIUS = 2.0

    private val BASE_DIRECTIONS = Array(26) { Vec3.ZERO }.also { arr ->
        var idx = 0
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    arr[idx++] = Vec3(dx.toDouble(), dy.toDouble(), dz.toDouble())
                }
            }
        }
    }

    private val OFFSETS_FINE = Array(26) { BASE_DIRECTIONS[it].scale(FINE_STEP_SIZE) }
    private val OFFSETS_DEFAULT = Array(26) { BASE_DIRECTIONS[it].scale(DEFAULT_STEP_SIZE) }
    private val OFFSETS_COARSE = Array(26) { BASE_DIRECTIONS[it].scale(COARSE_STEP_SIZE) }

    private class Node(
        val pos: Vec3,
        var gCost: Double,
        var hCost: Double,
        var parent: Node? = null,
        val isForward: Boolean = true
    ) : Comparable<Node> {
        val fCost: Double get() = gCost + 1.25 * hCost
        override fun compareTo(other: Node): Int = this.fCost.compareTo(other.fCost)
    }

    override fun computePath(level: Level, start: Vec3, destination: Vec3): List<Vec3> {
        val startTime = System.currentTimeMillis()
        PathfindingVisualizer.clearDebug()

        val targetDest = ThetaStarPathfinder.findPassableDestination(level, destination)
        if (targetDest == null) {
            HypCroMod.logWarn("A*: destination is fully enclosed in solid blocks")
            PathfindingVisualizer.setPath("3D A* (Obstructed)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }
        val effectiveStart = ThetaStarPathfinder.findPassableStart(level, start)
        if (effectiveStart == null) {
            HypCroMod.logWarn("A*: starting position is fully enclosed in solid blocks")
            PathfindingVisualizer.setPath("3D A* (Blocked Start)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }

        // Direct Line of Sight Fast-Path
        if (effectiveStart.distanceTo(targetDest) <= 1.2 || ThetaStarPathfinder.hasLineOfSight(level, effectiveStart, targetDest)) {
            val directPath = listOf(effectiveStart, targetDest)
            recordChosenPathDebug(directPath)
            PathfindingVisualizer.setPath("3D A* with Smoothing", directPath, System.currentTimeMillis() - startTime)
            return directPath
        }

        // Close-range high precision mode: < 8 blocks distance uses 0.5b fine resolution
        val totalDist = effectiveStart.distanceTo(targetDest)
        val isCloseRange = totalDist <= 8.0
        val baseStepSize = if (isCloseRange) FINE_STEP_SIZE else DEFAULT_STEP_SIZE

        // Dual Frontiers for Bidirectional Search
        val openForward = PriorityQueue<Node>()
        val openBackward = PriorityQueue<Node>()

        val closedForward = HashSet<Long>()
        val closedBackward = HashSet<Long>()

        val visitedForward = HashMap<Long, Node>()
        val visitedBackward = HashMap<Long, Node>()

        val startNode = Node(effectiveStart, 0.0, totalDist, null, isForward = true)
        val destNode = Node(targetDest, 0.0, totalDist, null, isForward = false)

        openForward.add(startNode)
        openBackward.add(destNode)

        val startHash = posHash(effectiveStart, HASH_RESOLUTION)
        val destHash = posHash(targetDest, HASH_RESOLUTION)
        visitedForward[startHash] = startNode
        visitedBackward[destHash] = destNode

        var bestMeetingCost = Double.MAX_VALUE
        var bestForwardMeetNode: Node? = null
        var bestBackwardMeetNode: Node? = null

        var closestForwardNode = startNode
        var expansions = 0

        while (openForward.isNotEmpty() && openBackward.isNotEmpty()) {
            if (CentralMovementCoordinator.isAbortTriggered()) {
                PathfindingVisualizer.clearDebug()
                return emptyList()
            }
            if (++expansions > MAX_EXPANSIONS) {
                break
            }

            // Termination check: if best meeting cost is lower than smallest possible f-cost
            val minForwardF = openForward.peek()?.fCost ?: Double.MAX_VALUE
            val minBackwardF = openBackward.peek()?.fCost ?: Double.MAX_VALUE
            if (bestMeetingCost < Double.MAX_VALUE && (minForwardF + minBackwardF) * 0.5 >= bestMeetingCost) {
                break
            }

            // Step Forward Frontier
            val expandForward = openForward.size <= openBackward.size
            val activeOpen = if (expandForward) openForward else openBackward
            val activeClosed = if (expandForward) closedForward else closedBackward
            val activeVisited = if (expandForward) visitedForward else visitedBackward
            val oppositeVisited = if (expandForward) visitedBackward else visitedForward
            val targetGoal = if (expandForward) targetDest else effectiveStart

            val current = activeOpen.poll() ?: break
            val currentHash = posHash(current.pos, HASH_RESOLUTION)
            if (activeClosed.contains(currentHash)) continue
            activeClosed.add(currentHash)

            if (expandForward && current.hCost < closestForwardNode.hCost) {
                closestForwardNode = current
            }

            // Early raycast connection to opposite start/goal if within line of sight
            if (current.pos.distanceTo(targetGoal) <= 1.5 || ThetaStarPathfinder.hasLineOfSight(level, current.pos, targetGoal)) {
                val fullPath = if (expandForward) {
                    reconstructDirect(level, current, targetGoal, forward = true)
                } else {
                    reconstructDirect(level, current, targetGoal, forward = false)
                }
                val smoothedPath = smoothPath(level, fullPath, targetDest)
                recordChosenPathDebug(smoothedPath)
                PathfindingVisualizer.setPath("3D A* with Smoothing", smoothedPath, System.currentTimeMillis() - startTime)
                return smoothedPath
            }

            // Adaptive Coarse-to-Fine Step Evaluation
            val isClearSky = !isCloseRange && isClearAirVolume(level, current.pos, AIR_CHECK_RADIUS)
            val currentStep = if (isClearSky) COARSE_STEP_SIZE else baseStepSize

            val neighborOffsets = getNeighborOffsets(currentStep)
            val sortedOffsets = neighborOffsets.clone()
            sortedOffsets.sortWith(Comparator { o1, o2 ->
                val d1 = current.pos.add(o1).distanceToSqr(targetGoal)
                val d2 = current.pos.add(o2).distanceToSqr(targetGoal)
                d1.compareTo(d2)
            })

            for (offset in sortedOffsets) {
                val neighborPos = current.pos.add(offset)
                val neighborHash = posHash(neighborPos, HASH_RESOLUTION)
                if (activeClosed.contains(neighborHash)) continue

                PathfindingVisualizer.addDebugBranch(current.pos, neighborPos, PathfindingVisualizer.SegmentType.YELLOW_SEARCHING)

                // Must be passable and have clear line of sight
                if (!isPassable(level, neighborPos) || !ThetaStarPathfinder.hasLineOfSight(level, current.pos, neighborPos)) {
                    PathfindingVisualizer.addDebugBranch(current.pos, neighborPos, PathfindingVisualizer.SegmentType.RED_UNREACHABLE)
                    continue
                }

                PathfindingVisualizer.addDebugBranch(current.pos, neighborPos, PathfindingVisualizer.SegmentType.GREEN_REACHABLE)

                val clearancePenalty = calculateClearanceCost(level, neighborPos, effectiveStart, targetDest)
                val stepCost = current.pos.distanceTo(neighborPos) + clearancePenalty
                val newGCost = current.gCost + stepCost
                val hCost = neighborPos.distanceTo(targetGoal)

                val existingNode = activeVisited[neighborHash]
                if (existingNode == null || newGCost < existingNode.gCost) {
                    val neighborNode = Node(neighborPos, newGCost, hCost, current, isForward = expandForward)
                    activeVisited[neighborHash] = neighborNode
                    activeOpen.add(neighborNode)

                    // Check for frontier meeting
                    val oppositeNode = oppositeVisited[neighborHash]
                    if (oppositeNode != null) {
                        val totalPathCost = newGCost + oppositeNode.gCost
                        if (totalPathCost < bestMeetingCost) {
                            bestMeetingCost = totalPathCost
                            if (expandForward) {
                                bestForwardMeetNode = neighborNode
                                bestBackwardMeetNode = oppositeNode
                            } else {
                                bestForwardMeetNode = oppositeNode
                                bestBackwardMeetNode = neighborNode
                            }
                        }
                    }
                }
            }
        }

        if (CentralMovementCoordinator.isAbortTriggered()) {
            PathfindingVisualizer.clearDebug()
            return emptyList()
        }

        // Path Reconstruction from Bidirectional Meeting
        if (bestForwardMeetNode != null && bestBackwardMeetNode != null) {
            val rawPath = reconstructBidirectional(level, bestForwardMeetNode, bestBackwardMeetNode)
            val smoothedPath = smoothPath(level, rawPath, targetDest)
            recordChosenPathDebug(smoothedPath)
            PathfindingVisualizer.setPath("3D A* with Smoothing", smoothedPath, System.currentTimeMillis() - startTime)
            return smoothedPath
        }

        if (openForward.isEmpty() || openBackward.isEmpty()) {
            HypCroMod.logWarn("A*: destination is unreachable - search frontiers exhausted")
            PathfindingVisualizer.setPath("3D A* (Unreachable)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }

        HypCroMod.logWarn("A*: expansion budget exhausted, using partial path to closest reached node")
        val rawPath = reconstructDirect(level, closestForwardNode, targetDest, forward = true)
        val smoothed = smoothPath(level, rawPath, targetDest)
        recordChosenPathDebug(smoothed)
        PathfindingVisualizer.setPath("3D A* with Smoothing", smoothed, System.currentTimeMillis() - startTime)
        return smoothed
    }

    fun isPassable(level: Level, pos: Vec3): Boolean {
        return ThetaStarPathfinder.isPassable(level, pos)
    }

    fun isClearAirVolume(level: Level, pos: Vec3, radius: Double): Boolean {
        val minX = floor(pos.x - radius).toInt()
        val maxX = floor(pos.x + radius).toInt()
        val minY = floor(pos.y - 0.2).toInt()
        val maxY = floor(pos.y + 1.8 + radius).toInt()
        val minZ = floor(pos.z - radius).toInt()
        val maxZ = floor(pos.z + radius).toInt()

        for (bx in minX..maxX) {
            for (bz in minZ..maxZ) {
                if (!level.hasChunk(bx shr 4, bz shr 4)) return false
                for (by in minY..maxY) {
                    val bp = BlockPos(bx, by, bz)
                    val state = level.getBlockState(bp)
                    if (!state.isAir && !state.getCollisionShape(level, bp).isEmpty) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun calculateClearanceCost(level: Level, pos: Vec3, start: Vec3, destination: Vec3): Double {
        val distToGoal = pos.distanceTo(destination)
        val distToStart = pos.distanceTo(start)
        val taper = min(1.0, min(distToGoal, distToStart) / 3.0)
        if (taper <= 0.0) return 0.0

        var cost = 0.0
        val baseBX = floor(pos.x).toInt()
        val baseBY = floor(pos.y).toInt()
        val baseBZ = floor(pos.z).toInt()

        var obstacleProximityCount = 0
        for (dx in -1..1) {
            for (dz in -1..1) {
                val bx = baseBX + dx
                val bz = baseBZ + dz
                if (!level.hasChunk(bx shr 4, bz shr 4)) continue
                for (dy in 0..2) {
                    val bp = BlockPos(bx, baseBY + dy, bz)
                    val state = level.getBlockState(bp)
                    if (!state.isAir && !state.getCollisionShape(level, bp).isEmpty) {
                        obstacleProximityCount++
                    }
                }
            }
        }

        val groundBP = BlockPos(baseBX, floor(pos.y - 0.7).toInt(), baseBZ)
        if (level.hasChunk(groundBP.x shr 4, groundBP.z shr 4)) {
            val state = level.getBlockState(groundBP)
            if (!state.isAir && !state.getCollisionShape(level, groundBP).isEmpty) {
                cost += 2.0 * taper
            }
        }

        if (obstacleProximityCount > 0) {
            cost += (obstacleProximityCount * 0.8) * taper
        }

        return cost
    }

    private fun getNeighborOffsets(step: Double): Array<Vec3> {
        return when (step) {
            FINE_STEP_SIZE -> OFFSETS_FINE
            COARSE_STEP_SIZE -> OFFSETS_COARSE
            else -> OFFSETS_DEFAULT
        }
    }

    private fun posHash(pos: Vec3, stepSize: Double): Long {
        val ix = floor(pos.x / stepSize).toLong()
        val iy = floor(pos.y / stepSize).toLong()
        val iz = floor(pos.z / stepSize).toLong()
        return (ix and 0x3FFFFF) or ((iz and 0x3FFFFF) shl 22) or ((iy and 0xFFFFF) shl 44)
    }

    private fun reconstructDirect(level: Level, endNode: Node, goal: Vec3, forward: Boolean): List<Vec3> {
        val path = ArrayList<Vec3>()
        var curr: Node? = endNode
        while (curr != null) {
            path.add(curr.pos)
            curr = curr.parent
        }
        if (forward) {
            path.reverse()
            if (path.isNotEmpty() && path.last().distanceTo(goal) > 0.1) {
                if (ThetaStarPathfinder.hasLineOfSight(level, path.last(), goal)) {
                    path.add(goal)
                }
            }
        } else {
            if (path.isNotEmpty() && path.first().distanceTo(goal) > 0.1) {
                if (ThetaStarPathfinder.hasLineOfSight(level, goal, path.first())) {
                    path.add(0, goal)
                }
            }
        }
        return path
    }

    private fun reconstructBidirectional(level: Level, forwardMeet: Node, backwardMeet: Node): List<Vec3> {
        val forwardPath = ArrayList<Vec3>()
        var currF: Node? = forwardMeet
        while (currF != null) {
            forwardPath.add(currF.pos)
            currF = currF.parent
        }
        forwardPath.reverse()

        val backwardPath = ArrayList<Vec3>()
        val startB = if (forwardMeet.pos.distanceTo(backwardMeet.pos) < 0.1) backwardMeet.parent else backwardMeet
        var currB: Node? = startB
        while (currB != null) {
            backwardPath.add(currB.pos)
            currB = currB.parent
        }

        val combined = ArrayList<Vec3>(forwardPath.size + backwardPath.size)
        combined.addAll(forwardPath)
        combined.addAll(backwardPath)
        return combined
    }

    // Funnel / raycast string-pulling smoothing: guarantees line-of-sight and preserves flight clearance
    fun smoothPath(level: Level, raw: List<Vec3>, destination: Vec3 = raw.lastOrNull() ?: Vec3.ZERO): List<Vec3> {
        if (raw.size <= 2) return raw

        val smoothed = ArrayList<Vec3>()
        smoothed.add(raw.first())

        var anchor = 0
        while (anchor < raw.size - 1) {
            var furthest = anchor + 1
            for (check in raw.size - 1 downTo anchor + 1) {
                val candidateA = raw[anchor]
                val candidateB = raw[check]
                if (ThetaStarPathfinder.hasLineOfSight(level, candidateA, candidateB)) {
                    // Check if shortcut dips too low to ground prematurely
                    val mid = candidateA.add(candidateB).scale(0.5)
                    if (candidateB.distanceTo(destination) <= 1.8 || calculateClearanceCost(level, mid, raw.first(), destination) == 0.0) {
                        furthest = check
                        break
                    }
                }
            }
            smoothed.add(raw[furthest])
            anchor = furthest
        }
        return smoothed
    }

    private fun recordChosenPathDebug(path: List<Vec3>) {
        if (!PathfindingVisualizer.isVerbose || path.size < 2) return
        for (i in 0 until path.size - 1) {
            PathfindingVisualizer.addDebugBranch(path[i], path[i + 1], PathfindingVisualizer.SegmentType.CYAN_CHOSEN)
        }
    }
}


