package com.hypcro.pathfinding

import com.hypcro.HypCroMod
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

object ThetaStarPathfinder : IPathfinder {

    private const val X_OFFSET = 0x2000000L
    private const val Z_OFFSET = 0x2000000L
    private const val Y_OFFSET = 0x800L
    private const val HORIZONTAL_MASK = 0x3FFFFFFL
    private const val Y_MASK = 0xFFFL

    private const val CRUISE_ALTITUDE = 85.0
    private const val HIGH_SKY_ALTITUDE = CRUISE_ALTITUDE - 5.0
    private const val MAX_EXPANSIONS = 300000

    private val mutableBlockPos = ThreadLocal.withInitial { BlockPos.MutableBlockPos() }

    private val localNodePoolF = ThreadLocal.withInitial { NodePool(16384) }
    private val localNodePoolB = ThreadLocal.withInitial { NodePool(16384) }
    private val localHeapF = ThreadLocal.withInitial { PrimitiveMinHeap(localNodePoolF.get(), 16384) }
    private val localHeapB = ThreadLocal.withInitial { PrimitiveMinHeap(localNodePoolB.get(), 16384) }
    private val localClosedSetF = ThreadLocal.withInitial { LongOpenHashSet(16384) }
    private val localClosedSetB = ThreadLocal.withInitial { LongOpenHashSet(16384) }
    private val localOpenMapF = ThreadLocal.withInitial { Long2IntTable(16384) }
    private val localOpenMapB = ThreadLocal.withInitial { Long2IntTable(16384) }

    private val NEIGHBOR_DIRECTIONS = IntArray(26 * 3).also { arr ->
        var idx = 0
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    arr[idx++] = dx
                    arr[idx++] = dy
                    arr[idx++] = dz
                }
            }
        }
    }

    private class NodePool(initialCapacity: Int = 16384) {
        var packedPos = LongArray(initialCapacity)
        var gCost = DoubleArray(initialCapacity)
        var fCost = DoubleArray(initialCapacity)
        var parent = IntArray(initialCapacity)
        var count = 0
            private set

        fun clear() {
            count = 0
        }

        fun allocate(packed: Long, g: Double, f: Double, parentIdx: Int): Int {
            if (count >= packedPos.size) {
                val newCap = packedPos.size shl 1
                packedPos = packedPos.copyOf(newCap)
                gCost = gCost.copyOf(newCap)
                fCost = fCost.copyOf(newCap)
                parent = parent.copyOf(newCap)
            }
            val idx = count++
            packedPos[idx] = packed
            gCost[idx] = g
            fCost[idx] = f
            parent[idx] = parentIdx
            return idx
        }
    }

    private class PrimitiveMinHeap(private var pool: NodePool, initialCapacity: Int = 16384) {
        var heap = IntArray(initialCapacity)
        var size = 0
            private set

        fun setPool(newPool: NodePool) {
            this.pool = newPool
        }

        fun clear() {
            size = 0
        }

        fun isNotEmpty(): Boolean = size > 0

        fun peekF(): Double = if (size > 0) pool.fCost[heap[0]] else Double.POSITIVE_INFINITY

        fun push(nodeIdx: Int) {
            if (size >= heap.size) {
                heap = heap.copyOf(heap.size shl 1)
            }
            var i = size++
            heap[i] = nodeIdx
            siftUp(i)
        }

        fun pop(): Int {
            if (size == 0) return -1
            val minIdx = heap[0]
            val lastIdx = heap[--size]
            if (size > 0) {
                heap[0] = lastIdx
                siftDown(0)
            }
            return minIdx
        }

        private fun siftUp(index: Int) {
            var i = index
            val target = heap[i]
            val targetF = pool.fCost[target]
            while (i > 0) {
                val parent = (i - 1) ushr 1
                val parentIdx = heap[parent]
                if (targetF < pool.fCost[parentIdx]) {
                    heap[i] = parentIdx
                    i = parent
                } else {
                    break
                }
            }
            heap[i] = target
        }

        private fun siftDown(index: Int) {
            var i = index
            val target = heap[i]
            val targetF = pool.fCost[target]
            val half = size ushr 1
            while (i < half) {
                var child = (i shl 1) + 1
                var childIdx = heap[child]
                val right = child + 1
                if (right < size && pool.fCost[heap[right]] < pool.fCost[childIdx]) {
                    child = right
                    childIdx = heap[child]
                }
                if (pool.fCost[childIdx] < targetF) {
                    heap[i] = childIdx
                    i = child
                } else {
                    break
                }
            }
            heap[i] = target
        }
    }

    private class LongOpenHashSet(initialCapacity: Int = 16384) {
        private var table = LongArray(initialCapacity)
        private var mask = initialCapacity - 1
        var size = 0
            private set

        fun clear() {
            if (size > 0) {
                table.fill(0L)
                size = 0
            }
        }

        private fun hash(k: Long): Int {
            var h = k xor (k ushr 32)
            h = h xor (h ushr 16)
            h = h * 0x45d9f3b
            h = h xor (h ushr 16)
            return h.toInt() and mask
        }

        fun contains(key: Long): Boolean {
            val nonZeroKey = if (key == 0L) 1L else key
            var idx = hash(nonZeroKey)
            while (true) {
                val cur = table[idx]
                if (cur == nonZeroKey) return true
                if (cur == 0L) return false
                idx = (idx + 1) and mask
            }
        }

        fun add(key: Long): Boolean {
            val nonZeroKey = if (key == 0L) 1L else key
            if (size >= (table.size shr 1) + (table.size shr 2)) {
                rehash(table.size shl 1)
            }
            var idx = hash(nonZeroKey)
            while (true) {
                val cur = table[idx]
                if (cur == nonZeroKey) return false
                if (cur == 0L) {
                    table[idx] = nonZeroKey
                    size++
                    return true
                }
                idx = (idx + 1) and mask
            }
        }

        private fun rehash(newCap: Int) {
            val oldTable = table
            table = LongArray(newCap)
            mask = newCap - 1
            size = 0
            for (k in oldTable) {
                if (k != 0L) {
                    add(k)
                }
            }
        }
    }

    private class Long2IntTable(initialCapacity: Int = 16384) {
        private var keys = LongArray(initialCapacity)
        private var values = IntArray(initialCapacity) { -1 }
        private var mask = initialCapacity - 1
        var size = 0
            private set

        fun clear() {
            if (size > 0) {
                keys.fill(0L)
                values.fill(-1)
                size = 0
            }
        }

        private fun hash(k: Long): Int {
            var h = k xor (k ushr 32)
            h = h xor (h ushr 16)
            h = h * 0x45d9f3b
            h = h xor (h ushr 16)
            return h.toInt() and mask
        }

        fun get(key: Long): Int {
            val nonZeroKey = if (key == 0L) 1L else key
            var idx = hash(nonZeroKey)
            while (true) {
                if (values[idx] == -1) return -1
                if (keys[idx] == nonZeroKey) return values[idx]
                idx = (idx + 1) and mask
            }
        }

        fun put(key: Long, value: Int) {
            val nonZeroKey = if (key == 0L) 1L else key
            if (size >= (keys.size shr 1) + (keys.size shr 2)) {
                rehash(keys.size shl 1)
            }
            var idx = hash(nonZeroKey)
            while (values[idx] != -1) {
                if (keys[idx] == nonZeroKey) {
                    values[idx] = value
                    return
                }
                idx = (idx + 1) and mask
            }
            keys[idx] = nonZeroKey
            values[idx] = value
            size++
        }

        private fun rehash(newCap: Int) {
            val oldKeys = keys
            val oldValues = values
            keys = LongArray(newCap)
            values = IntArray(newCap) { -1 }
            mask = newCap - 1
            size = 0
            for (i in oldKeys.indices) {
                if (oldValues[i] != -1) {
                    put(oldKeys[i], oldValues[i])
                }
            }
        }
    }

    private fun packCoord(qx: Int, qy: Int, qz: Int): Long {
        val px = (qx.toLong() + X_OFFSET) and HORIZONTAL_MASK
        val pz = (qz.toLong() + Z_OFFSET) and HORIZONTAL_MASK
        val py = (qy.toLong() + Y_OFFSET) and Y_MASK
        return px or (pz shl 26) or (py shl 52)
    }

    private fun unpackQX(packed: Long): Int = ((packed and HORIZONTAL_MASK).toInt()) - X_OFFSET.toInt()
    private fun unpackQZ(packed: Long): Int = (((packed ushr 26) and HORIZONTAL_MASK).toInt()) - Z_OFFSET.toInt()
    private fun unpackQY(packed: Long): Int = (((packed ushr 52) and Y_MASK).toInt()) - Y_OFFSET.toInt()

    private fun unpackVec3(packed: Long): Vec3 {
        return Vec3(unpackQX(packed) * 0.5, unpackQY(packed) * 0.5, unpackQZ(packed) * 0.5)
    }

    override fun computePath(level: Level, start: Vec3, destination: Vec3): List<Vec3> {
        val startTime = System.currentTimeMillis()
        PathfindingVisualizer.clearDebug()

        val targetDest = findPassableDestination(level, destination)
        if (targetDest == null) {
            HypCroMod.logWarn("Theta*: destination is fully enclosed in solid blocks")
            PathfindingVisualizer.setPath("Theta* (Obstructed)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }
        val effectiveStart = findPassableStart(level, start)
        if (effectiveStart == null) {
            HypCroMod.logWarn("Theta*: starting position is fully enclosed in solid blocks")
            PathfindingVisualizer.setPath("Theta* (Blocked Start)", emptyList(), System.currentTimeMillis() - startTime)
            return emptyList()
        }

        // Direct line-of-sight shortcut
        if (effectiveStart.distanceTo(targetDest) <= 1.2 || hasLineOfSight(level, effectiveStart, targetDest)) {
            val directPath = listOf(effectiveStart, targetDest)
            recordChosenPathDebug(directPath)
            PathfindingVisualizer.setPath("Theta*", directPath, System.currentTimeMillis() - startTime)
            return directPath
        }

        val poolF = localNodePoolF.get().apply { clear() }
        val poolB = localNodePoolB.get().apply { clear() }
        val heapF = localHeapF.get().apply { setPool(poolF); clear() }
        val heapB = localHeapB.get().apply { setPool(poolB); clear() }
        val closedSetF = localClosedSetF.get().apply { clear() }
        val closedSetB = localClosedSetB.get().apply { clear() }
        val openMapF = localOpenMapF.get().apply { clear() }
        val openMapB = localOpenMapB.get().apply { clear() }

        // Start coordinates quantized to 0.5b base unit
        val startQX = floor(effectiveStart.x * 2.0 + 0.5).toInt()
        val startQY = floor(effectiveStart.y * 2.0 + 0.5).toInt()
        val startQZ = floor(effectiveStart.z * 2.0 + 0.5).toInt()
        val startPacked = packCoord(startQX, startQY, startQZ)

        val destQX = floor(targetDest.x * 2.0 + 0.5).toInt()
        val destQY = floor(targetDest.y * 2.0 + 0.5).toInt()
        val destQZ = floor(targetDest.z * 2.0 + 0.5).toInt()
        val destPacked = packCoord(destQX, destQY, destQZ)

        val initialDist = effectiveStart.distanceTo(targetDest)

        val startNodeIdx = poolF.allocate(startPacked, 0.0, 1.25 * initialDist, -1)
        heapF.push(startNodeIdx)
        openMapF.put(startPacked, startNodeIdx)

        val destNodeIdx = poolB.allocate(destPacked, 0.0, 1.25 * initialDist, -1)
        heapB.push(destNodeIdx)
        openMapB.put(destPacked, destNodeIdx)

        var closestNodeF = startNodeIdx
        var closestDistSqF = initialDist * initialDist
        var expansions = 0

        while (heapF.isNotEmpty() && heapB.isNotEmpty()) {
            if (com.hypcro.movement.CentralMovementCoordinator.isAbortTriggered()) {
                PathfindingVisualizer.clearDebug()
                return emptyList()
            }
            if (++expansions > MAX_EXPANSIONS) {
                break
            }

            // Alternate expansion based on smaller top f-cost
            val expandForward = heapF.peekF() <= heapB.peekF()
            val curPool = if (expandForward) poolF else poolB
            val curHeap = if (expandForward) heapF else heapB
            val curClosed = if (expandForward) closedSetF else closedSetB
            val otherClosed = if (expandForward) closedSetB else closedSetF
            val curOpenMap = if (expandForward) openMapF else openMapB
            val otherOpenMap = if (expandForward) openMapB else openMapF
            val targetPos = if (expandForward) targetDest else effectiveStart
            val originPos = if (expandForward) effectiveStart else targetDest

            val curIdx = curHeap.pop()
            val curPacked = curPool.packedPos[curIdx]

            if (curClosed.contains(curPacked)) continue

            val curQX = unpackQX(curPacked)
            val curQY = unpackQY(curPacked)
            val curQZ = unpackQZ(curPacked)
            val curVec = Vec3(curQX * 0.5, curQY * 0.5, curQZ * 0.5)

            // Lazy Theta* Evaluation: Validate line of sight from parent upon popping
            val parentIdx = curPool.parent[curIdx]
            if (parentIdx >= 0) {
                val parentPacked = curPool.packedPos[parentIdx]
                val parentVec = unpackVec3(parentPacked)

                if (!hasLineOfSight(level, parentVec, curVec)) {
                    // Lazy check failed: re-evaluate parent among closed neighbors across all step scales (1, 2, 5)
                    var bestG = Double.POSITIVE_INFINITY
                    var bestParent = -1
                    val stepScales = intArrayOf(1, 2, 5)
                    var scaleIdx = 0
                    while (scaleIdx < stepScales.size) {
                        val scale = stepScales[scaleIdx++]
                        var dirIdx = 0
                        while (dirIdx < NEIGHBOR_DIRECTIONS.size) {
                            val cdx = NEIGHBOR_DIRECTIONS[dirIdx++] * scale
                            val cdy = NEIGHBOR_DIRECTIONS[dirIdx++] * scale
                            val cdz = NEIGHBOR_DIRECTIONS[dirIdx++] * scale

                            val adjPacked = packCoord(curQX - cdx, curQY - cdy, curQZ - cdz)
                            if (curClosed.contains(adjPacked)) {
                                val adjIdx = curOpenMap.get(adjPacked)
                                if (adjIdx >= 0) {
                                    val adjVec = unpackVec3(adjPacked)
                                    if (hasLineOfSight(level, adjVec, curVec)) {
                                        val edgeDist = adjVec.distanceTo(curVec)
                                        val clearance = calculateClearanceCost(level, curVec.x, curVec.y, curVec.z, originPos, targetPos)
                                        val candidateG = curPool.gCost[adjIdx] + edgeDist + clearance
                                        if (candidateG < bestG) {
                                            bestG = candidateG
                                            bestParent = adjIdx
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (bestParent >= 0) {
                        curPool.gCost[curIdx] = bestG
                        curPool.fCost[curIdx] = bestG + 1.25 * curVec.distanceTo(targetPos)
                        curPool.parent[curIdx] = bestParent
                        curHeap.push(curIdx)
                        continue
                    } else {
                        // Truly unreachable phantom node
                        continue
                    }
                }
            }

            curClosed.add(curPacked)

            if (expandForward) {
                val dX = curVec.x - targetDest.x
                val dY = curVec.y - targetDest.y
                val dZ = curVec.z - targetDest.z
                val curDistSq = dX * dX + dY * dY + dZ * dZ
                if (curDistSq < closestDistSqF) {
                    closestDistSqF = curDistSq
                    closestNodeF = curIdx
                }
            }

            // Frontier Meeting Check: Did we intersect the opposite frontier?
            if (otherClosed.contains(curPacked) || otherOpenMap.get(curPacked) != -1) {
                val otherIdx = otherOpenMap.get(curPacked)
                if (otherIdx >= 0) {
                    val forwardNodeIdx = if (expandForward) curIdx else otherIdx
                    val backwardNodeIdx = if (expandForward) otherIdx else curIdx

                    val fPath = reconstructPathForward(poolF, forwardNodeIdx, effectiveStart)
                    val bPath = reconstructPathBackward(poolB, backwardNodeIdx, targetDest)
                    val merged = mergePaths(fPath, bPath, effectiveStart, targetDest)
                    val pruned = pruneCollinearWaypoints(level, merged, targetDest)
                    recordChosenPathDebug(pruned)
                    PathfindingVisualizer.setPath("Theta*", pruned, System.currentTimeMillis() - startTime)
                    return pruned
                }
            }

            // Direct line of sight arrival check
            if (curVec.distanceTo(targetPos) <= 1.4 || hasLineOfSight(level, curVec, targetPos)) {
                val rawPath = if (expandForward) {
                    reconstructPathForward(poolF, curIdx, effectiveStart).toMutableList().apply { add(targetDest) }
                } else {
                    reconstructPathBackward(poolB, curIdx, targetDest).toMutableList().apply { add(0, effectiveStart) }
                }
                val pruned = pruneCollinearWaypoints(level, rawPath, targetDest)
                recordChosenPathDebug(pruned)
                PathfindingVisualizer.setPath("Theta*", pruned, System.currentTimeMillis() - startTime)
                return pruned
            }

            // Adaptive Sky Leaping: In open sky (Y >= HIGH_SKY_ALTITUDE), leap 2.5 blocks per step
            val isHighSky = curVec.y >= HIGH_SKY_ALTITUDE && isClearAirVolume(level, curVec.x, curVec.y, curVec.z, 2.0)
            val distToGoal = curVec.distanceTo(targetPos)
            val distToStart = curVec.distanceTo(originPos)
            val isNearEndpoint = distToGoal <= 8.0 || distToStart <= 8.0

            val stepMultiplier = if (isHighSky && !isNearEndpoint) 5 else if (distToGoal <= 4.0 || distToStart <= 4.0) 1 else 2

            val effectiveParent = if (parentIdx >= 0) parentIdx else curIdx
            val pVec = unpackVec3(curPool.packedPos[effectiveParent])

            var deltaIdx = 0
            while (deltaIdx < NEIGHBOR_DIRECTIONS.size) {
                val ndx = NEIGHBOR_DIRECTIONS[deltaIdx++] * stepMultiplier
                val ndy = NEIGHBOR_DIRECTIONS[deltaIdx++] * stepMultiplier
                val ndz = NEIGHBOR_DIRECTIONS[deltaIdx++] * stepMultiplier

                val nqx = curQX + ndx
                val nqy = curQY + ndy
                val nqz = curQZ + ndz

                val nPacked = packCoord(nqx, nqy, nqz)
                if (curClosed.contains(nPacked)) continue

                val nX = nqx * 0.5
                val nY = nqy * 0.5
                val nZ = nqz * 0.5

                if (!isPassable(level, nX, nY, nZ)) continue

                val nVec = Vec3(nX, nY, nZ)
                PathfindingVisualizer.addDebugBranch(curVec, nVec, PathfindingVisualizer.SegmentType.YELLOW_SEARCHING)

                val clearanceCost = calculateClearanceCost(level, nX, nY, nZ, originPos, targetPos)

                // Lazy Theta* Optimistic Assignment: assume parent line of sight holds
                val edgeDist = pVec.distanceTo(nVec)
                val gCost = curPool.gCost[effectiveParent] + edgeDist + clearanceCost
                val hCost = nVec.distanceTo(targetPos)
                val fCost = gCost + 1.25 * hCost

                val existingIdx = curOpenMap.get(nPacked)
                if (existingIdx != -1) {
                    if (gCost < curPool.gCost[existingIdx]) {
                        curPool.gCost[existingIdx] = gCost
                        curPool.fCost[existingIdx] = fCost
                        curPool.parent[existingIdx] = effectiveParent
                        curHeap.push(existingIdx)
                    }
                } else {
                    val newIdx = curPool.allocate(nPacked, gCost, fCost, effectiveParent)
                    curOpenMap.put(nPacked, newIdx)
                    curHeap.push(newIdx)
                }

                PathfindingVisualizer.addDebugBranch(curVec, nVec, PathfindingVisualizer.SegmentType.GREEN_REACHABLE)
            }
        }

        if (com.hypcro.movement.CentralMovementCoordinator.isAbortTriggered()) {
            PathfindingVisualizer.clearDebug()
            return emptyList()
        }

        if (expansions > MAX_EXPANSIONS) {
            HypCroMod.logWarn("Theta*: expansion budget exhausted ($MAX_EXPANSIONS), using partial forward path")
        } else {
            HypCroMod.logWarn("Theta*: search frontier exhausted, using partial forward path")
        }
        val fallbackRaw = reconstructPathForward(poolF, closestNodeF, effectiveStart).toMutableList()
        val fallbackTail = fallbackRaw.lastOrNull()
        if (fallbackTail != null && hasLineOfSight(level, fallbackTail, targetDest)) {
            fallbackRaw.add(targetDest)
        }
        val fallbackPruned = pruneCollinearWaypoints(level, fallbackRaw, targetDest)
        recordChosenPathDebug(fallbackPruned)
        PathfindingVisualizer.setPath("Theta*", fallbackPruned, System.currentTimeMillis() - startTime)
        return fallbackPruned
    }

    fun isClearAirVolume(level: Level, px: Double, py: Double, pz: Double, radius: Double = 2.0): Boolean {
        val minBX = floor(px - radius).toInt()
        val maxBX = floor(px + radius).toInt()
        val minBY = floor(py - radius).toInt()
        val maxBY = floor(py + radius).toInt()
        val minBZ = floor(pz - radius).toInt()
        val maxBZ = floor(pz + radius).toInt()

        val mutablePos = mutableBlockPos.get()
        for (bx in minBX..maxBX) {
            for (bz in minBZ..maxBZ) {
                if (!level.hasChunk(bx shr 4, bz shr 4)) return false
                for (by in minBY..maxBY) {
                    mutablePos.set(bx, by, bz)
                    val state = level.getBlockState(mutablePos)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(level, mutablePos)
                    if (!shape.isEmpty) return false
                }
            }
        }
        return true
    }

    fun isPassable(level: Level, pos: Vec3): Boolean = isPassable(level, pos.x, pos.y, pos.z)

    fun isPassable(level: Level, px: Double, py: Double, pz: Double): Boolean {
        // Player bounding box: width 0.59b, height 1.78b, bottom clearance +0.02b to prevent ground clipping
        val pMinX = px - 0.295
        val pMaxX = px + 0.295
        val pMinY = py + 0.02
        val pMaxY = py + 1.80
        val pMinZ = pz - 0.295
        val pMaxZ = pz + 0.295

        val minBX = floor(pMinX).toInt()
        val maxBX = floor(pMaxX).toInt()
        val minBY = floor(pMinY).toInt() - 1
        val maxBY = floor(pMaxY).toInt()
        val minBZ = floor(pMinZ).toInt()
        val maxBZ = floor(pMaxZ).toInt()

        val mutablePos = mutableBlockPos.get()

        for (bx in minBX..maxBX) {
            for (bz in minBZ..maxBZ) {
                if (!level.hasChunk(bx shr 4, bz shr 4)) return false
                for (by in minBY..maxBY) {
                    mutablePos.set(bx, by, bz)
                    val state = level.getBlockState(mutablePos)
                    if (state.isAir) continue
                    val shape = state.getCollisionShape(level, mutablePos)
                    if (shape.isEmpty) continue
                    val bounds = shape.bounds()
                    if (bx + bounds.maxX > pMinX && bx + bounds.minX < pMaxX &&
                        by + bounds.maxY > pMinY && by + bounds.minY < pMaxY &&
                        bz + bounds.maxZ > pMinZ && bz + bounds.minZ < pMaxZ) {
                        return false
                    }
                }
            }
        }
        return true
    }

    fun findPassableDestination(level: Level, destination: Vec3): Vec3? {
        if (isPassable(level, destination)) return destination
        val radii = doubleArrayOf(0.1, 0.25, 0.5, 0.75, 1.0, 1.2)
        for (r in radii) {
            val upCandidate = destination.add(0.0, r, 0.0)
            if (isPassable(level, upCandidate)) return upCandidate
            val downCandidate = destination.add(0.0, -r, 0.0)
            if (isPassable(level, downCandidate)) return downCandidate
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val candidate = destination.add(dx * r, dy * r, dz * r)
                        if (isPassable(level, candidate)) {
                            return candidate
                        }
                    }
                }
            }
        }
        return null
    }

    fun findPassableStart(level: Level, start: Vec3): Vec3? {
        if (isPassable(level, start)) return start
        val radii = doubleArrayOf(0.1, 0.25, 0.5, 0.75, 1.0)
        for (r in radii) {
            val upCandidate = start.add(0.0, r, 0.0)
            if (isPassable(level, upCandidate)) return upCandidate
            val downCandidate = start.add(0.0, -r, 0.0)
            if (isPassable(level, downCandidate)) return downCandidate
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) continue
                        val candidate = start.add(dx * r, dy * r, dz * r)
                        if (isPassable(level, candidate)) {
                            return candidate
                        }
                    }
                }
            }
        }
        return null
    }

    fun hasLineOfSight(level: Level, from: Vec3, to: Vec3): Boolean {
        val x0 = from.x
        val y0 = from.y
        val z0 = from.z
        val x1 = to.x
        val y1 = to.y
        val z1 = to.z

        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0

        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq < 1e-8) {
            return isPassable(level, x0, y0, z0)
        }

        if (!isPassable(level, x0, y0, z0) || !isPassable(level, x1, y1, z1)) {
            return false
        }

        // 3D Amanatides-Woo DDA Fast Voxel Traversal
        val stepX = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val stepY = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val stepZ = if (dz > 0) 1 else if (dz < 0) -1 else 0

        val tDeltaX = if (dx != 0.0) abs(1.0 / dx) else Double.POSITIVE_INFINITY
        val tDeltaY = if (dy != 0.0) abs(1.0 / dy) else Double.POSITIVE_INFINITY
        val tDeltaZ = if (dz != 0.0) abs(1.0 / dz) else Double.POSITIVE_INFINITY

        var tMaxX = if (dx > 0) (floor(x0) + 1.0 - x0) / dx else if (dx < 0) (x0 - floor(x0)) / -dx else Double.POSITIVE_INFINITY
        var tMaxY = if (dy > 0) (floor(y0) + 1.0 - y0) / dy else if (dy < 0) (y0 - floor(y0)) / -dy else Double.POSITIVE_INFINITY
        var tMaxZ = if (dz > 0) (floor(z0) + 1.0 - z0) / dz else if (dz < 0) (z0 - floor(z0)) / -dz else Double.POSITIVE_INFINITY

        var t = 0.0
        while (t < 1.0) {
            val nextT = if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                val curT = tMaxX
                tMaxX += tDeltaX
                curT
            } else if (tMaxY <= tMaxZ) {
                val curT = tMaxY
                tMaxY += tDeltaY
                curT
            } else {
                val curT = tMaxZ
                tMaxZ += tDeltaZ
                curT
            }

            if (nextT >= 1.0) break
            t = nextT

            val sampleX = x0 + dx * t
            val sampleY = y0 + dy * t
            val sampleZ = z0 + dz * t

            if (!isPassable(level, sampleX, sampleY, sampleZ)) {
                return false
            }
        }

        return true
    }

    private fun calculateClearanceCost(
        level: Level,
        px: Double,
        py: Double,
        pz: Double,
        start: Vec3,
        dest: Vec3
    ): Double {
        val distToGoal = dest.distanceTo(Vec3(px, py, pz))
        val distToStart = start.distanceTo(Vec3(px, py, pz))

        // When within 6 blocks of endpoints, allow smooth takeoff and landing
        if (distToGoal < 6.0 || distToStart < 6.0) {
            return 0.0
        }

        var cost = 0.0

        // 1. Cruise altitude bias (Y=85)
        if (py < CRUISE_ALTITUDE) {
            cost += (CRUISE_ALTITUDE - py) * 0.18
        } else if (py > CRUISE_ALTITUDE + 10.0) {
            cost += (py - (CRUISE_ALTITUDE + 10.0)) * 0.25
        }

        // 2. Ground obstacle avoidance penalty
        val mutablePos = mutableBlockPos.get()
        val floorBX = floor(px).toInt()
        val floorBY = floor(py - 0.70).toInt()
        val floorBZ = floor(pz).toInt()
        if (level.hasChunk(floorBX shr 4, floorBZ shr 4)) {
            mutablePos.set(floorBX, floorBY, floorBZ)
            val state = level.getBlockState(mutablePos)
            if (!state.isAir && !state.getCollisionShape(level, mutablePos).isEmpty) {
                cost += 2.0
            }
        }

        return cost
    }

    private fun reconstructPathForward(pool: NodePool, endNodeIdx: Int, start: Vec3): List<Vec3> {
        val path = mutableListOf<Vec3>()
        var curr = endNodeIdx
        while (curr >= 0) {
            path.add(unpackVec3(pool.packedPos[curr]))
            curr = pool.parent[curr]
        }
        path.reverse()
        if (path.isEmpty()) return emptyList()
        path[0] = start
        return path
    }

    private fun reconstructPathBackward(pool: NodePool, endNodeIdx: Int, dest: Vec3): List<Vec3> {
        val path = mutableListOf<Vec3>()
        var curr = endNodeIdx
        while (curr >= 0) {
            path.add(unpackVec3(pool.packedPos[curr]))
            curr = pool.parent[curr]
        }
        if (path.isNotEmpty()) {
            path[path.size - 1] = dest
        }
        return path
    }

    private fun mergePaths(forward: List<Vec3>, backward: List<Vec3>, start: Vec3, dest: Vec3): List<Vec3> {
        val merged = mutableListOf<Vec3>()
        merged.addAll(forward)
        if (backward.isNotEmpty()) {
            // Drop duplicated meeting point
            merged.addAll(backward.drop(1))
        }
        if (merged.isEmpty()) return listOf(start, dest)
        merged[0] = start
        if (merged.last().distanceTo(dest) > 0.05) {
            merged.add(dest)
        }
        return merged
    }

    fun hasGroundObstacle(level: Level, pos: Vec3): Boolean {
        val floorBX = floor(pos.x).toInt()
        val floorBY = floor(pos.y - 0.70).toInt()
        val floorBZ = floor(pos.z).toInt()
        if (level.hasChunk(floorBX shr 4, floorBZ shr 4)) {
            val mutablePos = mutableBlockPos.get()
            mutablePos.set(floorBX, floorBY, floorBZ)
            val state = level.getBlockState(mutablePos)
            return !state.isAir && !state.getCollisionShape(level, mutablePos).isEmpty
        }
        return false
    }

    private fun pruneCollinearWaypoints(level: Level, raw: List<Vec3>, destination: Vec3): List<Vec3> {
        if (raw.size <= 2) return raw

        // 1. Raycast String-Pulling
        val smoothed = mutableListOf<Vec3>()
        smoothed.add(raw.first())

        var anchor = 0
        while (anchor < raw.size - 1) {
            var furthest = anchor + 1
            for (check in raw.size - 1 downTo anchor + 1) {
                val candidateA = raw[anchor]
                val candidateB = raw[check]
                if (hasLineOfSight(level, candidateA, candidateB)) {
                    val mid = candidateA.add(candidateB).scale(0.5)
                    if (candidateB.distanceTo(destination) <= 1.8 || !hasGroundObstacle(level, mid)) {
                        furthest = check
                        break
                    }
                }
            }
            smoothed.add(raw[furthest])
            anchor = furthest
        }

        // 2. Collinear Waypoint Reduction (3-point line check)
        if (smoothed.size <= 2) return smoothed
        val pruned = mutableListOf<Vec3>()
        pruned.add(smoothed.first())

        for (i in 1 until smoothed.size - 1) {
            val prev = pruned.last()
            val curr = smoothed[i]
            val next = smoothed[i + 1]

            val v1 = curr.subtract(prev).normalize()
            val v2 = next.subtract(curr).normalize()
            val dot = v1.x * v2.x + v1.y * v2.y + v1.z * v2.z

            // If angle deviation is less than 0.8 degrees (dot > 0.9999), drop redundant waypoint
            if (dot < 0.9999) {
                pruned.add(curr)
            }
        }
        pruned.add(smoothed.last())

        return pruned
    }

    private fun recordChosenPathDebug(path: List<Vec3>) {
        if (!PathfindingVisualizer.isVerbose || path.size < 2) return
        for (i in 0 until path.size - 1) {
            PathfindingVisualizer.addDebugBranch(path[i], path[i + 1], PathfindingVisualizer.SegmentType.CYAN_CHOSEN)
        }
    }
}

