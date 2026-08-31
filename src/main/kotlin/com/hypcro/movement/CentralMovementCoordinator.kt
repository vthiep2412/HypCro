package com.hypcro.movement

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.failsafe.HypcroWatchdog
import com.hypcro.pathfinding.AStar3DSmoothedPathfinder
import com.hypcro.pathfinding.IPathfinder
import com.hypcro.pathfinding.RRTStarPathfinder
import com.hypcro.pathfinding.ThetaStarPathfinder
import com.hypcro.farming.MacroInputController
import kotlinx.coroutines.*
import com.mojang.blaze3d.platform.InputConstants
import org.lwjgl.glfw.GLFW
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

object CentralMovementCoordinator {

    @Volatile
    var isAbortRequested = false

    @Volatile
    var lastAbortTimestamp: Long = 0L

    @Volatile
    var isNavigating = false
        private set

    @Volatile
    var isNavigationInterrupted = false

    @Volatile
    var isPathfinding = false
        private set

    @Volatile
    var targetGuidanceYaw: Float = 0.0f

    @Volatile
    var targetGuidancePitch: Float = 0.0f

    @Volatile
    var consecutiveStuckCount: Int = 0
        private set

    fun resetStuckCount() {
        consecutiveStuckCount = 0
    }

    fun stopNavigation() {
        isNavigationInterrupted = true
        isNavigating = false
        MacroInputController.releaseAllMovement()
    }

    fun isAbortTriggered(client: Minecraft? = null): Boolean {
        if (isAbortRequested) return true
        val c = client ?: Minecraft.getInstance()
        val win = c.window
        if (c.isSameThread && InputConstants.isKeyDown(win, GLFW.GLFW_KEY_B)) {
            isAbortRequested = true
            lastAbortTimestamp = System.currentTimeMillis()
            return true
        }
        return false
    }

    suspend fun rotateCamera(client: Minecraft, targetYaw: Float, targetPitch: Float, customDurationMs: Long? = null) {
        MouseMovementEngine.rotateTo(client, targetYaw, targetPitch, customDurationMs)
    }

    fun getActivePathfinder(): IPathfinder {
        return when (ConfigManager.config.pestDestroyer.pathfindingAlgorithm.uppercase()) {
            "3D A* WITH SMOOTHING", "ASTAR", "A*" -> AStar3DSmoothedPathfinder
            "BIT*", "BIT_STAR", "BIT", "RRT*", "RRT_STAR" -> RRTStarPathfinder
            else -> ThetaStarPathfinder // Default Theta*
        }
    }

    fun parseCoordinate(input: String?, current: Double): Double? {
        if (input == null || input.isBlank()) return null
        if (input == "~") return current
        if (input.startsWith("~")) {
            val offset = input.substring(1).toDoubleOrNull() ?: 0.0
            return current + offset
        }
        return input.toDoubleOrNull()
    }

    suspend fun flyTo(
        client: Minecraft,
        targetX: Double? = null,
        targetY: Double? = null,
        targetZ: Double? = null,
        targetPitch: Float? = null,
        targetYaw: Float? = null,
        ignoreHorizontalXZ: Boolean = false
    ): Boolean {
        val player = client.player ?: return false
        val level = client.level ?: return false

        if (isAbortTriggered(client)) {
            return false
        }

        resetStuckCount()
        isNavigationInterrupted = false
        isNavigating = true

        try {
            // 1. Flight Activation with 2 retry attempts
            if (!player.abilities.flying) {
                var flightActive = false
                for (attempt in 1..2) {
                    // Execute double-jump keypress sequence via client key mapping
                    MacroInputController.holdJump()
                    delay(60)
                    MacroInputController.releaseJump()
                    delay(60)
                    MacroInputController.holdJump()
                    delay(60)
                    MacroInputController.releaseJump()
                    delay(200)

                    val p = client.player ?: return false
                    if (p.abilities.flying) {
                        flightActive = true
                        break
                    }
                }

                if (!flightActive) {
                    HypcroWatchdog.potentialStaffCheck("Flight activation failed after 2 attempts :(")
                    return false
                }
            }

            if (isAbortTriggered(client) || isNavigationInterrupted) {
                return false
            }

            val startPos = player.position()
            val destX = targetX ?: startPos.x
            val destY = targetY ?: startPos.y
            val destZ = targetZ ?: startPos.z
            val finalDestination = Vec3(destX, destY, destZ)

            // 2. Delegate Path Generation to Selected Algorithm
            val pathfinder = getActivePathfinder()
            val rawWaypoints = try {
                isPathfinding = true
                withContext(Dispatchers.Default) {
                    pathfinder.computePath(level, startPos, finalDestination, ignoreHorizontalXZ)
                }
            } finally {
                isPathfinding = false
            }

            val waypoints = rawWaypoints

            if (isAbortTriggered(client) || isNavigationInterrupted || waypoints.isEmpty()) {
                if (isAbortRequested) {
                    HypCroMod.logWarn("Pathfinding aborted by user!")
                } else if (!isNavigationInterrupted) {
                    HypCroMod.logWarn("Pathfinder returned empty path to $finalDestination")
                }
                return false
            }

            val isClassic = ConfigManager.config.pestDestroyer.flightEngineVersion.equals("CLASSIC", ignoreCase = true)
            if (isClassic) {
                return flyWaypointsClassic(client, level, pathfinder, startPos, waypoints, finalDestination, targetPitch, targetYaw, ignoreHorizontalXZ)
            } else {
                return flyWaypointsDecoupledV2(client, level, pathfinder, startPos, waypoints, finalDestination, targetPitch, targetYaw, ignoreHorizontalXZ)
            }
        } finally {
            MouseMovementEngine.resetFlightVelocity()
            MacroInputController.releaseAllMovement()
            isNavigating = false
            isNavigationInterrupted = false
        }
    }

    private suspend fun flyWaypointsDecoupledV2(
        client: Minecraft,
        level: net.minecraft.world.level.Level,
        pathfinder: IPathfinder,
        startPos: Vec3,
        waypoints: List<Vec3>,
        finalDestination: Vec3,
        targetPitch: Float?,
        targetYaw: Float?,
        ignoreHorizontalXZ: Boolean = false
    ): Boolean {
        var activeWaypoints = waypoints
        var currentWaypointIdx = if (waypoints.size > 1 && startPos.distanceTo(waypoints[0]) < 1.5) 1 else 0
        var lastDeviationCheckMs = System.currentTimeMillis()
        var lastStuckCheckMs = System.currentTimeMillis()
        var lastStuckCheckPos = startPos
        var isAscending = false
        var isDescending = false
        var lastJumpReleaseTime = 0L
        var currentStrafeState = 0 // -1: A (left), 0: none, 1: D (right)
        var routeRecalcs = 0

        MouseMovementEngine.resetFlightVelocity()

        targetGuidanceYaw = client.player?.yRot ?: 0.0f
        targetGuidancePitch = client.player?.xRot ?: 0.0f

        val cameraGuidanceJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isNavigating && !isAbortRequested && !isNavigationInterrupted) {
                client.execute {
                    MouseMovementEngine.smoothStepFlight(client, targetGuidanceYaw, targetGuidancePitch, dt = 0.01f)
                }
                delay(10)
            }
        }

        try {
            while (currentWaypointIdx < activeWaypoints.size && !isNavigationInterrupted) {
                val p = client.player ?: return false
                val pos = p.position()
                val nowMs = System.currentTimeMillis()

                // Stuck Detection (350ms < 0.25b)
                if (pos.distanceTo(finalDestination) > 1.2 && nowMs - lastStuckCheckMs >= 350L) {
                    val moved = pos.distanceTo(lastStuckCheckPos)
                    if (moved < 0.25) {
                        consecutiveStuckCount++
                        routeRecalcs++
                        HypCroMod.logWarn("Stuck detected (moved only ${String.format("%.2f", moved)}b in 350ms) - recomputing route (Attempt $consecutiveStuckCount/5)")
                        if (consecutiveStuckCount >= 5) {
                            HypCroMod.logWarn("Anti-stuck triggered 5 times consecutively during flight.")
                            MacroInputController.releaseAllMovement()
                            return false
                        }
                        MacroInputController.releaseAllMovement()
                        val recomputed = try {
                            isPathfinding = true
                            withContext(Dispatchers.Default) {
                                pathfinder.computePath(level, pos, finalDestination, ignoreHorizontalXZ)
                            }
                        } finally {
                            isPathfinding = false
                        }
                        if (isAbortTriggered(client) || recomputed.isEmpty()) return false
                        activeWaypoints = recomputed
                        currentWaypointIdx = if (recomputed.size > 1) 1 else 0
                        lastStuckCheckMs = System.currentTimeMillis()
                        lastStuckCheckPos = pos
                        lastDeviationCheckMs = System.currentTimeMillis()
                        continue
                    } else {
                        consecutiveStuckCount = 0
                    }
                    lastStuckCheckMs = nowMs
                    lastStuckCheckPos = pos
                }

                // Path deviation check (> 6 blocks drift)
                if (nowMs - lastDeviationCheckMs >= 1000L) {
                    lastDeviationCheckMs = nowMs
                    val closestSegDist = (0 until activeWaypoints.size - 1).minOfOrNull { i ->
                        distanceToSegment(pos, activeWaypoints[i], activeWaypoints[i + 1])
                    } ?: pos.distanceTo(finalDestination)
                    if (closestSegDist > 6.0) {
                        routeRecalcs++
                        HypCroMod.log("Path drift ${String.format("%.1f", closestSegDist)}b - recomputing route (Attempt $routeRecalcs)")
                        val recomputed = try {
                            isPathfinding = true
                            withContext(Dispatchers.Default) {
                                pathfinder.computePath(level, pos, finalDestination, ignoreHorizontalXZ)
                            }
                        } finally {
                            isPathfinding = false
                        }
                        if (isAbortTriggered(client) || recomputed.isEmpty()) return false
                        activeWaypoints = recomputed
                        currentWaypointIdx = if (recomputed.size > 1) 1 else 0
                        continue
                    }
                }

                if (isAbortTriggered(client)) {
                    HypCroMod.logWarn("Movement aborted by user via B key or GUI!")
                    return false
                }

                // Flight recovery check
                if (!p.abilities.flying) {
                    var flightRecovered = false
                    for (attempt in 1..2) {
                        MacroInputController.holdJump()
                        delay(60)
                        MacroInputController.releaseJump()
                        delay(60)
                        MacroInputController.holdJump()
                        delay(60)
                        MacroInputController.releaseJump()
                        delay(200)

                        val curP = client.player ?: return false
                        if (curP.abilities.flying) {
                            flightRecovered = true
                            break
                        }
                    }
                    if (!flightRecovered) {
                        HypcroWatchdog.potentialStaffCheck("Can't fly :(")
                        return false
                    }
                }

                val waypoint = activeWaypoints[currentWaypointIdx]
                val isFinalNode = (currentWaypointIdx == activeWaypoints.size - 1)

                val dx = waypoint.x - pos.x
                val dy = waypoint.y - pos.y
                val dz = waypoint.z - pos.z
                val horizontalDist = sqrt(dx * dx + dz * dz)
                val verticalDist = abs(dy)
                val distToWaypoint = sqrt(dx * dx + dy * dy + dz * dz)
                val distToFinal = pos.distanceTo(finalDestination)

                // Arrival check
                val isArrived = if (ignoreHorizontalXZ) {
                    if (finalDestination.y >= startPos.y) pos.y >= finalDestination.y - 0.5 else pos.y <= finalDestination.y + 0.5
                } else {
                    distToFinal <= 1.0 || (isFinalNode && distToWaypoint <= 1.0)
                }

                if (isArrived) {
                    break
                }

                // Important Corner Detection (turn >= 35°)
                var isImportantCorner = false
                if (currentWaypointIdx > 0 && currentWaypointIdx < activeWaypoints.size - 1) {
                    val prevW = activeWaypoints[currentWaypointIdx - 1]
                    val nextW = activeWaypoints[currentWaypointIdx + 1]
                    val v1 = waypoint.subtract(prevW)
                    val v2 = nextW.subtract(waypoint)
                    val len1 = v1.length()
                    val len2 = v2.length()
                    if (len1 > 1e-4 && len2 > 1e-4) {
                        val dot = (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z) / (len1 * len2)
                        val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * 180.0 / Math.PI
                        if (angleDeg >= 35.0) {
                            isImportantCorner = true
                        }
                    }
                }

                // Dynamic 1-Tick S-Braking on Corner Approach
                if (isImportantCorner && distToWaypoint in 0.35..1.2 && horizontalDist > 0.25) {
                    MacroInputController.releaseSprint()
                    MacroInputController.releaseW()
                    MacroInputController.holdS()
                    delay(50)
                    MacroInputController.releaseS()
                }

                // Intermediate Waypoint Advancement & Forward Node Skipping
                var advancedIdx = currentWaypointIdx
                for (k in activeWaypoints.size - 1 downTo currentWaypointIdx) {
                    val candidateNode = activeWaypoints[k]
                    val cHDist = sqrt((candidateNode.x - pos.x) * (candidateNode.x - pos.x) + (candidateNode.z - pos.z) * (candidateNode.z - pos.z))
                    val cVDist = abs(candidateNode.y - pos.y)
                    val maxH = if (isImportantCorner) 0.65 else if (ignoreHorizontalXZ) 1.2 else 0.85
                    val maxV = if (isImportantCorner) 0.80 else 0.95

                    if (cHDist <= maxH && cVDist <= maxV) {
                        advancedIdx = k + 1
                        break
                    }
                }

                if (advancedIdx > currentWaypointIdx) {
                    currentWaypointIdx = advancedIdx
                    if (currentWaypointIdx >= activeWaypoints.size) {
                        break
                    }
                    continue
                }

                // =========================================================================
                // V2 DECOUPLED 6-DOF BODY VECTOR LOCOMOTION
                // =========================================================================
                val radYaw = Math.toRadians(p.yRot.toDouble())
                val forwardUnitX = -kotlin.math.sin(radYaw)
                val forwardUnitZ = kotlin.math.cos(radYaw)
                val strafeUnitX = kotlin.math.cos(radYaw)
                val strafeUnitZ = kotlin.math.sin(radYaw)

                val forwardProj = dx * forwardUnitX + dz * forwardUnitZ
                val strafeProj = dx * strafeUnitX + dz * strafeUnitZ

                // Horizontal Propulsion (only if not ignoreHorizontalXZ on final node / pure ascent)
                val isPureAltitudeAscent = ignoreHorizontalXZ && isFinalNode && (if (finalDestination.y >= startPos.y) pos.y >= waypoint.y - 0.5 else pos.y <= waypoint.y + 0.5)
                if (isPureAltitudeAscent) {
                    MacroInputController.releaseW()
                    MacroInputController.releaseS()
                    MacroInputController.releaseStrafe()
                } else {
                    if (forwardProj > 0.15) {
                        MacroInputController.holdW()
                        MacroInputController.releaseS()
                    } else if (forwardProj < -0.15) {
                        MacroInputController.holdS()
                        MacroInputController.releaseW()
                    } else {
                        MacroInputController.releaseW()
                        MacroInputController.releaseS()
                    }

                    // Horizontal Strafe with Hysteresis & Straight Corridor Damping
                    val strafeThresholdEnter = 0.40
                    val strafeThresholdExit = 0.15
                    val isStraightRunway = forwardProj > 1.0 && kotlin.math.abs(strafeProj) < 0.35

                    if (isStraightRunway) {
                        currentStrafeState = 0
                        MacroInputController.releaseStrafe()
                    } else {
                        if (currentStrafeState == 0) {
                            if (strafeProj > strafeThresholdEnter) {
                                currentStrafeState = -1
                                MacroInputController.holdA()
                            } else if (strafeProj < -strafeThresholdEnter) {
                                currentStrafeState = 1
                                MacroInputController.holdD()
                            } else {
                                MacroInputController.releaseStrafe()
                            }
                        } else if (currentStrafeState == -1) {
                            // Currently strafing Left (A)
                            if (strafeProj < strafeThresholdExit) {
                                currentStrafeState = 0
                                MacroInputController.releaseStrafe()
                            } else {
                                MacroInputController.holdA()
                            }
                        } else if (currentStrafeState == 1) {
                            // Currently strafing Right (D)
                            if (strafeProj > -strafeThresholdExit) {
                                currentStrafeState = 0
                                MacroInputController.releaseStrafe()
                            } else {
                                MacroInputController.holdD()
                            }
                        }
                    }
                }

                // Vertical Locomotion with 350ms Double-Tap Protection & State Latching
                if (dy > 0.20) {
                    if (!isAscending) {
                        val nMs = System.currentTimeMillis()
                        if (nMs - lastJumpReleaseTime < 350L) {
                            delay(350L - (nMs - lastJumpReleaseTime))
                        }
                        MacroInputController.holdJump()
                        MacroInputController.releaseShift()
                        isAscending = true
                        isDescending = false
                    }
                } else if (dy < -0.20) {
                    if (!isDescending) {
                        MacroInputController.releaseJump()
                        lastJumpReleaseTime = System.currentTimeMillis()
                        MacroInputController.holdShift()
                        isDescending = true
                        isAscending = false
                    }
                } else {
                    if (isAscending) {
                        MacroInputController.releaseJump()
                        lastJumpReleaseTime = System.currentTimeMillis()
                        isAscending = false
                    }
                    if (isDescending) {
                        MacroInputController.releaseShift()
                        isDescending = false
                    }
                }

                // =========================================================================
                // V2 HIERARCHICAL NODE CLASSIFIER & GOAL-ORIENTED GAZE
                // =========================================================================
                val isVerticalClimb = verticalDist > 1.5 * horizontalDist
                val isMicroAdjust = horizontalDist < 3.5 && !isFinalNode

                if (isVerticalClimb) {
                    // Segment Type A: Pure vertical climb / descent -> hold level horizon pitch
                    targetGuidancePitch = 0.0f
                } else if (isMicroAdjust) {
                    // Segment Type B: Local micro-adjustment -> suppress camera movement completely
                } else {
                    // Segment Type C: Long flight corridor -> look ahead with 25° angular deadzone
                    var lookAheadPoint = waypoint
                    if (!isImportantCorner) {
                        var accumulatedDist = pos.distanceTo(waypoint)
                        for (k in (currentWaypointIdx + 1) until activeWaypoints.size) {
                            if (accumulatedDist >= 5.0) break
                            val nextW = activeWaypoints[k]
                            accumulatedDist += activeWaypoints[k - 1].distanceTo(nextW)
                            lookAheadPoint = nextW
                        }
                    }

                    val lookDx = lookAheadPoint.x - pos.x
                    val lookDy = lookAheadPoint.y - pos.y
                    val lookDz = lookAheadPoint.z - pos.z
                    val lookHorizDist = sqrt(lookDx * lookDx + lookDz * lookDz)

                    if (lookHorizDist > 0.2) {
                        val desiredYaw = (atan2(lookDz, lookDx) * 180.0 / Math.PI).toFloat() - 90.0f
                        val yawError = abs(Mth.wrapDegrees(desiredYaw - p.yRot))

                        // 25-degree Gaze Deadzone
                        if (yawError > 25.0f) {
                            targetGuidanceYaw = desiredYaw
                        }

                        // Horizon Pitch Clamping (-15° to +15°)
                        val rawPitch = (-(atan2(lookDy, lookHorizDist) * 180.0 / Math.PI).toFloat())
                        targetGuidancePitch = rawPitch.coerceIn(-15.0f, 15.0f)
                    }
                }

                // Straightaway Sprint Boost
                val isFacingForward = abs(Mth.wrapDegrees(targetGuidanceYaw - p.yRot)) < 15.0f
                if (!isImportantCorner && !isVerticalClimb && horizontalDist >= 8.0 && distToFinal >= 10.0 && isFacingForward) {
                    MacroInputController.holdSprint()
                } else {
                    MacroInputController.releaseSprint()
                }

                delay(50)
            }

            MacroInputController.releaseAllMovement()

            if (ConfigManager.config.pestDestroyer.stopAfterDestination && !ignoreHorizontalXZ) {
                MacroInputController.releaseSprint()
                MacroInputController.releaseW()
                MacroInputController.holdS()
                delay(100)
                MacroInputController.releaseS()
                MacroInputController.releaseAllMovement()
            }

            if (targetYaw != null && targetPitch != null) {
                rotateCamera(client, targetYaw, targetPitch)
            }

            return true
        } finally {
            cameraGuidanceJob.cancel()
            MouseMovementEngine.resetFlightVelocity()
            MacroInputController.releaseAllMovement()
        }
    }

    private suspend fun flyWaypointsClassic(
        client: Minecraft,
        level: net.minecraft.world.level.Level,
        pathfinder: IPathfinder,
        startPos: Vec3,
        waypoints: List<Vec3>,
        finalDestination: Vec3,
        targetPitch: Float?,
        targetYaw: Float?,
        ignoreHorizontalXZ: Boolean = false
    ): Boolean {
        var activeWaypoints = waypoints
        var currentWaypointIdx = if (waypoints.size > 1 && startPos.distanceTo(waypoints[0]) < 1.5) 1 else 0
        var lastDeviationCheckMs = System.currentTimeMillis()
        var lastStuckCheckMs = System.currentTimeMillis()
        var lastStuckCheckPos = startPos
        var isAscending = false
        var isDescending = false
        var routeRecalcs = 0
        var lastJumpReleaseTime = 0L

        MouseMovementEngine.resetFlightVelocity()

        targetGuidanceYaw = client.player?.yRot ?: 0.0f
        targetGuidancePitch = client.player?.xRot ?: 0.0f

        val cameraGuidanceJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isNavigating && !isAbortRequested && !isNavigationInterrupted) {
                client.execute {
                    MouseMovementEngine.smoothStepFlight(client, targetGuidanceYaw, targetGuidancePitch, dt = 0.01f)
                }
                delay(10)
            }
        }

        try {
            while (currentWaypointIdx < activeWaypoints.size && !isNavigationInterrupted) {
                val p = client.player ?: return false
                val pos = p.position()
                val nowMs = System.currentTimeMillis()

                // 350ms Stuck Detection: if bot moves < 0.25 blocks in 350ms while navigating, immediately recompute (ignored if already arriving within 0.9b)
                if (pos.distanceTo(finalDestination) > 1.2 && nowMs - lastStuckCheckMs >= 350L) {
                    val moved = pos.distanceTo(lastStuckCheckPos)
                    if (moved < 0.25) {
                        consecutiveStuckCount++
                        routeRecalcs++
                        HypCroMod.logWarn("Stuck detected (moved only ${String.format("%.2f", moved)}b in 350ms) - recomputing route (Attempt $consecutiveStuckCount/5)")
                        if (consecutiveStuckCount >= 5) {
                            HypCroMod.logWarn("Anti-stuck triggered 5 times consecutively during flight.")
                            MacroInputController.releaseAllMovement()
                            return false
                        }
                        MacroInputController.releaseAllMovement()
                        val recomputed = try {
                            isPathfinding = true
                            withContext(Dispatchers.Default) {
                                pathfinder.computePath(level, pos, finalDestination, ignoreHorizontalXZ)
                            }
                        } finally {
                            isPathfinding = false
                        }
                        if (isAbortTriggered(client) || recomputed.isEmpty()) return false
                        activeWaypoints = recomputed
                        currentWaypointIdx = if (recomputed.size > 1) 1 else 0
                        lastStuckCheckMs = System.currentTimeMillis()
                        lastStuckCheckPos = pos
                        lastDeviationCheckMs = System.currentTimeMillis()
                        continue
                    } else {
                        consecutiveStuckCount = 0
                    }
                    lastStuckCheckMs = nowMs
                    lastStuckCheckPos = pos
                }

                // Path deviation check: recompute route if bot drifts >6 blocks from precomputed path
                if (nowMs - lastDeviationCheckMs >= 1000L) {
                    lastDeviationCheckMs = nowMs
                    val closestSegDist = (0 until activeWaypoints.size - 1).minOfOrNull { i ->
                        distanceToSegment(pos, activeWaypoints[i], activeWaypoints[i + 1])
                    } ?: pos.distanceTo(finalDestination)
                    if (closestSegDist > 6.0) {
                        routeRecalcs++
                        HypCroMod.log("Path drift ${String.format("%.1f", closestSegDist)}b - recomputing route (Attempt $routeRecalcs)")
                        val recomputed = try {
                            isPathfinding = true
                            withContext(Dispatchers.Default) {
                                pathfinder.computePath(level, pos, finalDestination, ignoreHorizontalXZ)
                            }
                        } finally {
                            isPathfinding = false
                        }
                        if (isAbortTriggered(client) || recomputed.isEmpty()) return false
                        activeWaypoints = recomputed
                        currentWaypointIdx = if (recomputed.size > 1) 1 else 0
                        continue
                    }
                }

                if (isAbortTriggered(client)) {
                    HypCroMod.logWarn("Movement aborted by user via B key or GUI!")
                    return false
                }

                // Mid-flight loss of flight capability detection with 2 retry attempts
                if (!p.abilities.flying) {
                    var flightRecovered = false
                    for (attempt in 1..2) {
                        MacroInputController.holdJump()
                        delay(60)
                        MacroInputController.releaseJump()
                        delay(60)
                        MacroInputController.holdJump()
                        delay(60)
                        MacroInputController.releaseJump()
                        delay(200)

                        val curP = client.player ?: return false
                        if (curP.abilities.flying) {
                            flightRecovered = true
                            break
                        }
                    }

                    if (!flightRecovered) {
                        HypcroWatchdog.potentialStaffCheck("Can't fly :(")
                        return false
                    }
                }

                val waypoint = activeWaypoints[currentWaypointIdx]
                val isFinalNode = (currentWaypointIdx == activeWaypoints.size - 1)

                // Important Corner Detection: any turn >= 35° bend (<= 145° internal angle)
                var isImportantCorner = false
                if (currentWaypointIdx > 0 && currentWaypointIdx < activeWaypoints.size - 1) {
                    val prevW = activeWaypoints[currentWaypointIdx - 1]
                    val nextW = activeWaypoints[currentWaypointIdx + 1]
                    val v1 = waypoint.subtract(prevW)
                    val v2 = nextW.subtract(waypoint)
                    val len1 = v1.length()
                    val len2 = v2.length()
                    if (len1 > 1e-4 && len2 > 1e-4) {
                        val dot = (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z) / (len1 * len2)
                        val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * 180.0 / Math.PI
                        if (angleDeg >= 35.0) {
                            isImportantCorner = true
                        }
                    }
                }

                val dx = waypoint.x - pos.x
                val dy = waypoint.y - pos.y
                val dz = waypoint.z - pos.z
                val horizontalDist = sqrt(dx * dx + dz * dz)
                val distToWaypoint = sqrt(dx * dx + dy * dy + dz * dz)
                val distToFinal = pos.distanceTo(finalDestination)

                // Immediate Arrival Check: finish cleanly when within 1.2b of final destination or last node, or altitude reached in ignoreHorizontalXZ mode
                val isArrived = if (ignoreHorizontalXZ) {
                    if (finalDestination.y >= startPos.y) pos.y >= finalDestination.y - 0.5 else pos.y <= finalDestination.y + 0.5
                } else {
                    distToFinal <= 1.2 || (isFinalNode && distToWaypoint <= 1.2)
                }

                if (isArrived) {
                    break
                }

                // Intermediate Waypoint Advancement & Forward Node Skipping
                // Heightens vertical touch zone to 0.75b for corners and skips earlier nodes if player cuts ahead
                var advancedIdx = currentWaypointIdx
                for (k in activeWaypoints.size - 1 downTo currentWaypointIdx) {
                    val candidateNode = activeWaypoints[k]
                    val cHDist = sqrt((candidateNode.x - pos.x) * (candidateNode.x - pos.x) + (candidateNode.z - pos.z) * (candidateNode.z - pos.z))
                    val cVDist = abs(candidateNode.y - pos.y)

                    var candidateIsCorner = false
                    if (k > 0 && k < activeWaypoints.size - 1) {
                        val prevW = activeWaypoints[k - 1]
                        val nextW = activeWaypoints[k + 1]
                        val v1 = candidateNode.subtract(prevW)
                        val v2 = nextW.subtract(candidateNode)
                        val len1 = v1.length()
                        val len2 = v2.length()
                        if (len1 > 1e-4 && len2 > 1e-4) {
                            val dot = (v1.x * v2.x + v1.y * v2.y + v1.z * v2.z) / (len1 * len2)
                            val angleDeg = acos(dot.coerceIn(-1.0, 1.0)) * 180.0 / Math.PI
                            if (angleDeg >= 35.0) {
                                candidateIsCorner = true
                            }
                        }
                    }

                    val maxH = if (candidateIsCorner) 0.65 else if (ignoreHorizontalXZ) 1.2 else 0.85
                    val maxV = if (candidateIsCorner) 0.75 else 0.95

                    if (cHDist <= maxH && cVDist <= maxV) {
                        advancedIdx = k + 1
                        break
                    }
                }

                if (advancedIdx > currentWaypointIdx) {
                    currentWaypointIdx = advancedIdx
                    if (currentWaypointIdx >= activeWaypoints.size) {
                        break
                    }
                    continue
                }

                // 1. Look-Ahead Heading: Clamp look-ahead directly to waypoint if it's an Important Corner
                var lookAheadPoint = waypoint
                if (!isImportantCorner) {
                    var accumulatedDist = pos.distanceTo(waypoint)
                    for (k in (currentWaypointIdx + 1) until activeWaypoints.size) {
                        if (accumulatedDist >= 3.0) break
                        val nextW = activeWaypoints[k]
                        accumulatedDist += activeWaypoints[k - 1].distanceTo(nextW)
                        lookAheadPoint = nextW
                    }
                }

                val lookDx = lookAheadPoint.x - pos.x
                val lookDy = lookAheadPoint.y - pos.y
                val lookDz = lookAheadPoint.z - pos.z
                val lookHorizDist = sqrt(lookDx * lookDx + lookDz * lookDz)

                // Guidance Targets: only update if error is outside deadzone
                val desiredYaw = if (lookHorizDist > 0.1) {
                    (atan2(lookDz, lookDx) * 180.0 / Math.PI).toFloat() - 90.0f
                } else {
                    p.yRot
                }

                // Natural human pitch: clamped between -45° and +45° (never stares at ceiling/floor)
                val rawPitch = if (lookHorizDist > 0.1) {
                    (-(atan2(lookDy, lookHorizDist) * 180.0 / Math.PI).toFloat())
                } else {
                    0.0f
                }
                val desiredPitch = rawPitch.coerceIn(-45.0f, 45.0f)

                // Deadzone: only update guidance targets if error > 3.0 degrees
                if (abs(Mth.wrapDegrees(desiredYaw - p.yRot)) > 3.0f) {
                    targetGuidanceYaw = desiredYaw
                }
                if (abs(desiredPitch - p.xRot) > 3.0f) {
                    targetGuidancePitch = desiredPitch
                }

                // 2. Relative Steering & Propulsion
                val directYaw = if (horizontalDist > 0.05) {
                    (atan2(dz, dx) * 180.0 / Math.PI).toFloat() - 90.0f
                } else {
                    p.yRot
                }
                val relAngle = Mth.wrapDegrees(directYaw - p.yRot)
                val isNearPureVertical = (horizontalDist < 0.45 && abs(dy) > 0.8)

                // Active 1-Tick S-Key Braking on Corner Approach to eliminate flight glide momentum
                if (isImportantCorner && distToWaypoint in 0.35..1.2 && horizontalDist > 0.25) {
                    MacroInputController.releaseSprint()
                    MacroInputController.releaseW()
                    MacroInputController.holdS()
                    delay(50)
                    MacroInputController.releaseS()
                }

                val isPureAltitudeAscent = ignoreHorizontalXZ && isFinalNode && (if (finalDestination.y >= startPos.y) pos.y >= waypoint.y - 0.5 else pos.y <= waypoint.y + 0.5)

                if (isPureAltitudeAscent) {
                    MacroInputController.releaseSprint()
                    MacroInputController.releaseW()
                    MacroInputController.releaseS()
                    MacroInputController.releaseStrafe()
                } else if (isNearPureVertical) {
                    MacroInputController.releaseSprint()
                    if (horizontalDist > 0.20 && !ignoreHorizontalXZ) {
                        if (abs(relAngle) < 45.0f) {
                            MacroInputController.holdW()
                            MacroInputController.releaseStrafe()
                        } else if (abs(relAngle) > 135.0f) {
                            MacroInputController.holdS()
                            MacroInputController.releaseStrafe()
                        } else if (relAngle in 45.0f..135.0f) {
                            MacroInputController.releaseW()
                            MacroInputController.holdD()
                        } else {
                            MacroInputController.releaseW()
                            MacroInputController.holdA()
                        }
                    } else {
                        MacroInputController.releaseW()
                        MacroInputController.releaseS()
                        MacroInputController.releaseStrafe()
                    }
                } else {
                    MacroInputController.releaseStrafe()

                    // Forward & Backward Propulsion with distance-scaled alignment thresholds
                    val maxAllowedAngle = when {
                        distToWaypoint >= 20.0 -> 45.0f
                        distToWaypoint >= 10.0 -> 40.0f
                        distToWaypoint >= 5.0 -> 30.0f
                        else -> 15.0f
                    }

                    if (isFinalNode && distToWaypoint < 1.5) {
                        MacroInputController.releaseSprint()
                    }
                    if (abs(dy) > 1.0 && horizontalDist < 0.6) {
                        // Suppress forward propulsion during steep vertical climbs when horizontally aligned
                        MacroInputController.releaseW()
                    } else if (abs(relAngle) < maxAllowedAngle) {
                        MacroInputController.holdW()
                    } else if (abs(relAngle) > 125.0f && horizontalDist < 0.8) {
                        MacroInputController.holdS()
                    } else {
                        MacroInputController.releaseW()
                    }

                    // Straightaway Flight Sprint Boosting: disabled if near corner, destination, or misaligned
                    val lookYawError = abs(Mth.wrapDegrees(desiredYaw - p.yRot))
                    val directYawError = abs(Mth.wrapDegrees(directYaw - p.yRot))
                    val pathBendAngle = abs(Mth.wrapDegrees(desiredYaw - directYaw))

                    // Check for any upcoming sharp corners within 10 blocks
                    var hasUpcomingCorner = isImportantCorner
                    if (!hasUpcomingCorner) {
                        var scanDist = distToWaypoint
                        for (idx in (currentWaypointIdx + 1) until (activeWaypoints.size - 1)) {
                            if (scanDist >= 10.0) break
                            val prev = activeWaypoints[idx - 1]
                            val curr = activeWaypoints[idx]
                            val next = activeWaypoints[idx + 1]
                            val seg1 = curr.subtract(prev)
                            val seg2 = next.subtract(curr)
                            val l1 = seg1.length()
                            val l2 = seg2.length()
                            if (l1 > 1e-4 && l2 > 1e-4) {
                                val d = (seg1.x * seg2.x + seg1.y * seg2.y + seg1.z * seg2.z) / (l1 * l2)
                                if (acos(d.coerceIn(-1.0, 1.0)) * 180.0 / Math.PI >= 35.0) {
                                    hasUpcomingCorner = true
                                    break
                                }
                            }
                            scanDist += curr.distanceTo(next)
                        }
                    }

                    val isPathStraight = pathBendAngle < 3.0f
                    val isPlayerFacingStraight = lookYawError < 3.0f && directYawError < 3.0f
                    val isLevelFlight = abs(dy) < 0.4
                    val hasFarNode = distToWaypoint >= 5.0
                    val hasLongRunway = distToFinal >= 10.0 && (!isFinalNode || distToWaypoint >= 10.0)

                    if (!hasUpcomingCorner && isPathStraight && isPlayerFacingStraight && isLevelFlight && hasFarNode && hasLongRunway) {
                        MacroInputController.holdSprint()
                    } else {
                        MacroInputController.releaseSprint()
                    }
                }

                // Vertical locomotion
                if (dy > 0.15) {
                    if (!isAscending) {
                        val nMs = System.currentTimeMillis()
                        if (nMs - lastJumpReleaseTime < 350) {
                            delay(350 - (nMs - lastJumpReleaseTime))
                        }
                        MacroInputController.holdJump()
                        MacroInputController.releaseShift()
                        isAscending = true
                        isDescending = false
                    }
                } else if (dy < -0.15) {
                    if (!isDescending) {
                        MacroInputController.releaseJump()
                        lastJumpReleaseTime = System.currentTimeMillis()
                        MacroInputController.holdShift()
                        isDescending = true
                        isAscending = false
                    }
                } else {
                    if (isAscending) {
                        MacroInputController.releaseJump()
                        lastJumpReleaseTime = System.currentTimeMillis()
                        isAscending = false
                    }
                    if (isDescending) {
                        MacroInputController.releaseShift()
                        isDescending = false
                    }
                }

                delay(50)
            }

            // Release all flight keys upon arrival
            MacroInputController.releaseAllMovement()

            // Active S-Braking to eliminate forward flight momentum if enabled
            if (ConfigManager.config.pestDestroyer.stopAfterDestination && !ignoreHorizontalXZ) {
                MacroInputController.releaseSprint()
                MacroInputController.releaseW()
                MacroInputController.holdS()
                delay(100)
                MacroInputController.releaseS()
                MacroInputController.releaseAllMovement()
            }

            // 3. Automatic Rotation Lock at Destination
            if (targetYaw != null && targetPitch != null) {
                rotateCamera(client, targetYaw, targetPitch)
            }

            return true
        } finally {
            cameraGuidanceJob.cancel()
            MouseMovementEngine.resetFlightVelocity()
            MacroInputController.releaseAllMovement()
        }
    }

    private fun distanceToSegment(point: Vec3, a: Vec3, b: Vec3): Double {
        val ab = b.subtract(a)
        val ap = point.subtract(a)
        val lenSq = ab.lengthSqr()
        if (lenSq < 1e-10) return point.distanceTo(a)
        val t = (ap.dot(ab) / lenSq).coerceIn(0.0, 1.0)
        return point.distanceTo(a.add(ab.scale(t)))
    }
}

