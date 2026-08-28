package com.hypcro.bouncy

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.farming.MacroInputController
import com.hypcro.movement.MouseMovementEngine
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.util.ARGB
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object AutoBouncyBall {

    @Volatile
    var isRunning: Boolean = false
        private set

    var bounceCount: Int = 0
        private set

    var ballCount: Int = 0
        private set

    private var sessionStartTimeMs: Long = 0L
    val sessionUptimeMs: Long
        get() = if (isRunning && sessionStartTimeMs > 0) System.currentTimeMillis() - sessionStartTimeMs else 0L

    var currentStatusText: String = "Inactive"
        private set

    var lastTargetDistance: Double = 0.0
        private set

    private var activeJob: Job? = null
    private var activePredictor: Predictor? = null
    private var activeBallEntityId: Int? = null
    private var lastBallSeenMs: Long = 0L
    private var initialStartingPos: Vec3? = null
    private var previousAutoJump: Boolean? = null

    fun start() {
        if (isRunning) return
        val client = Minecraft.getInstance()
        val player = client.player
        val level = client.level
        if (player == null || level == null) {
            HypCroMod.logWarn("Auto Bouncy Ball halted: Player or world not loaded.")
            return
        }

        val ballSlot = com.hypcro.util.SkyBlockItemHelper.findBeachBallSlot(client)
        if (ballSlot == null) {
            HypCroMod.logWarn("Cannot start Auto Bouncy Ball: No Bouncy Beach Ball found on hotbar (0-8)!")
            return
        }

        isRunning = true
        bounceCount = 0
        ballCount = 0
        sessionStartTimeMs = System.currentTimeMillis()
        currentStatusText = "Initializing..."
        lastTargetDistance = 0.0
        activePredictor = null
        activeBallEntityId = null
        lastBallSeenMs = System.currentTimeMillis()
        initialStartingPos = player.position()

        // Capture previous auto-jump preference and enable native auto-jump
        if (previousAutoJump == null) {
            previousAutoJump = try {
                client.options.autoJump().get()
            } catch (_: Exception) {
                null
            }
        }
        client.execute {
            client.options.autoJump().set(true)
        }

        HypCroMod.logSuccess("Auto Bouncy Ball started!")

        activeJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                executeBouncyLoop()
            } catch (e: CancellationException) {
                // Clean cancellation
            } catch (e: Exception) {
                HypCroMod.logWarn("Auto Bouncy Ball error: ${e.message}")
            } finally {
                stopInternal()
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        stopInternal()
        HypCroMod.log("Auto Bouncy Ball stopped.")
    }

    private fun stopInternal() {
        isRunning = false
        currentStatusText = "Stopped"
        activePredictor = null
        activeBallEntityId = null
        initialStartingPos = null
        activeJob?.cancel()
        activeJob = null

        // Restore player original auto-jump preference
        val client = Minecraft.getInstance()
        val toRestore = previousAutoJump
        previousAutoJump = null
        if (toRestore != null) {
            client.execute {
                client.options.autoJump().set(toRestore)
            }
        }

        MacroInputController.releaseAllMovement()
    }

    private suspend fun returnToStartAndPlaceNext(client: Minecraft, ballTexture: String?) {
        val startPos = initialStartingPos
        val goBack = ConfigManager.config.bouncyBall.goBackToStart

        if (goBack && startPos != null) {
            currentStatusText = "Returning to start position..."
            var lastSurveillanceTime = System.currentTimeMillis()
            var lastSurveillancePos = client.player?.position()

            while (isRunning) {
                val player = client.player ?: break
                val pPos = player.position()
                val dx = startPos.x - pPos.x
                val dz = startPos.z - pPos.z
                val dist = sqrt(dx * dx + dz * dz)

                if (dist < 0.20) {
                    // Reached starting position
                    MacroInputController.releaseAllMovement()
                    break
                }

                // 400ms surveillance: if moved < 0.2b while still obstructed, trigger staff failsafe
                val now = System.currentTimeMillis()
                if (now - lastSurveillanceTime >= 400L) {
                    val prevPos = lastSurveillancePos ?: pPos
                    val moved = prevPos.distanceTo(pPos)
                    if (moved < 0.20 && dist > 0.35) {
                        stop()
                        com.hypcro.failsafe.HypcroWatchdog.potentialStaffCheck("Auto Bouncy Ball: Stuck returning to start position (moved < 0.2b in 400ms).")
                        return
                    }
                    lastSurveillanceTime = now
                    lastSurveillancePos = pPos
                }

                // Relative WASD movement towards startPos
                val targetAngle = (atan2(dz, dx) * 180.0 / Math.PI).toFloat() - 90.0f
                val diff = Mth.wrapDegrees(targetAngle - player.yRot)

                val moveForward = diff in -67.5f..67.5f
                val moveBackward = diff > 112.5f || diff < -112.5f
                val moveLeft = diff in -157.5f..-22.5f
                val moveRight = diff in 22.5f..157.5f

                if (moveForward) MacroInputController.holdW() else if (moveBackward) MacroInputController.holdS() else {
                    MacroInputController.releaseW()
                    MacroInputController.releaseS()
                }

                if (moveLeft) MacroInputController.holdA() else if (moveRight) MacroInputController.holdD() else {
                    MacroInputController.releaseStrafe()
                }

                delay(20L)
            }
        }

        MacroInputController.releaseAllMovement()

        // Wait for active ball to disappear/pop
        currentStatusText = "Waiting for ball to pop..."
        val waitStart = System.currentTimeMillis()
        while (isRunning && System.currentTimeMillis() - waitStart < 4000L) {
            val ball = findActiveBeachBall(client, ballTexture)
            if (ball == null || ball.isRemoved) break
            delay(50L)
        }

        // Wait 250ms delay
        currentStatusText = "Preparing next ball..."
        delay(250L)
    }

    private suspend fun executeBouncyLoop() {
        val client = Minecraft.getInstance()

        while (isRunning) {
            val player = client.player ?: break
            val level = client.level ?: break

            // 1. Verify and equip Bouncy Beach Ball
            val ballSlot = findBeachBallSlot(client)
            if (ballSlot == null) {
                HypCroMod.logWarn("Cannot find Bouncy Beach Ball in hotbar! Stopping.")
                currentStatusText = "No Ball in Hotbar"
                break
            }

            // Extract ball texture from hotbar item
            val ballStack = player.inventory.getItem(ballSlot)
            val ballTexture = getSkullTexture(ballStack)

            // Switch to ball slot
            if (player.inventory.selectedSlot != ballSlot) {
                client.execute {
                    client.player?.inventory?.selectedSlot = ballSlot
                }
                delay(100L)
            }

            // 2. Search for existing ball ArmorStand in vicinity
            val existingBall = findActiveBeachBall(client, ballTexture)
            var newlyPlaced = false
            if (existingBall == null) {
                // Point downward humanly using GCD quantization before placing
                currentStatusText = "Placing Ball..."
                MouseMovementEngine.rotateTo(client, player.yRot, 90.0f)
                delay(80L)

                MacroInputController.holdUseItem()
                delay(60L)
                MacroInputController.releaseUseItem()
                newlyPlaced = true
                delay(350L)
            }

            // 3. Track ball and predict landing
            val trackStartMs = System.currentTimeMillis()
            var currentBall = findActiveBeachBall(client, ballTexture)
            while (currentBall == null && isRunning && System.currentTimeMillis() - trackStartMs < 2000L) {
                delay(50L)
                currentBall = findActiveBeachBall(client, ballTexture)
            }

            if (currentBall == null) {
                currentStatusText = "Waiting for Ball..."
                delay(200L)
                continue
            }

            if (newlyPlaced) {
                ballCount++
            }

            activeBallEntityId = currentBall.id
            activePredictor = Predictor(currentBall.position())
            lastBallSeenMs = System.currentTimeMillis()

            var currentForwardKey = 0 // 1 = W, -1 = S, 0 = none
            var currentStrafeKey = 0  // -1 = A, 1 = D, 0 = none
            var lastKeySwitchMs = 0L
            var stuckStartTimeMs = 0L
            var posAtStuckStart: Vec3? = null

            // 4. Autonomous Catch Loop (Pure WASD Keyboard Strafe, Zero Camera Rotation)
            while (isRunning) {
                val livePlayer = client.player ?: break
                val liveLevel = client.level ?: break
                val targetEntity = liveLevel.getEntity(activeBallEntityId ?: -1) as? ArmorStand

                if (targetEntity == null || targetEntity.isRemoved) {
                    // Ball popped or disappeared
                    if (System.currentTimeMillis() - lastBallSeenMs > 1200L) {
                        currentStatusText = "Ball Popped!"
                        MacroInputController.releaseAllMovement()
                        currentForwardKey = 0
                        currentStrafeKey = 0
                        stuckStartTimeMs = 0L
                        posAtStuckStart = null
                        returnToStartAndPlaceNext(client, ballTexture)
                        break
                    }
                } else {
                    lastBallSeenMs = System.currentTimeMillis()
                    val predictor = activePredictor ?: Predictor(targetEntity.position()).also { activePredictor = it }
                    predictor.newData(targetEntity.position(), livePlayer.position().y)
                    bounceCount = predictor.bounceCounter

                    // Check if Target Bounces reached (10..45, where 46 is Forever)
                    val targetBounces = ConfigManager.config.bouncyBall.targetBounces
                    if (targetBounces in 10..45 && bounceCount >= targetBounces) {
                        currentStatusText = "Target ($targetBounces) reached! Letting drop..."
                        MacroInputController.releaseAllMovement()
                        currentForwardKey = 0
                        currentStrafeKey = 0
                        stuckStartTimeMs = 0L
                        posAtStuckStart = null
                        returnToStartAndPlaceNext(client, ballTexture)
                        break
                    }

                    val landingPos = predictor.predictedLandingPos
                    if (landingPos != null) {
                        val pPos = livePlayer.position()
                        val dx = landingPos.x - pPos.x
                        val dz = landingPos.z - pPos.z
                        val dist = sqrt(dx * dx + dz * dz)
                        lastTargetDistance = dist

                        val isAggressive = ConfigManager.config.bouncyBall.aggressive
                        val now = System.currentTimeMillis()

                        // Obstacle stuck detection (staff check failsafe if stuck for >800ms)
                        if (livePlayer.horizontalCollision && dist > 0.35) {
                            if (stuckStartTimeMs == 0L) {
                                stuckStartTimeMs = now
                                posAtStuckStart = pPos
                            } else if (now - stuckStartTimeMs > 800L) {
                                val moved = posAtStuckStart?.distanceTo(pPos) ?: 0.0
                                if (moved < 0.20) {
                                    stop()
                                    com.hypcro.failsafe.HypcroWatchdog.potentialStaffCheck("Auto Bouncy Ball: Stuck against obstacle/barrier for >800ms.")
                                    break
                                }
                            }
                        } else {
                            stuckStartTimeMs = 0L
                            posAtStuckStart = null
                        }

                        if (isAggressive) {
                            // Aggressive Mode (50Hz immediate key response)
                            if (dist < 0.10) {
                                currentStatusText = "Positioned (Catches: $bounceCount)"
                                MacroInputController.releaseAllMovement()
                            } else {
                                currentStatusText = "Moving to Spot (${String.format("%.2f", dist)}b)"

                                val targetAngle = (atan2(dz, dx) * 180.0 / Math.PI).toFloat() - 90.0f
                                val diff = Mth.wrapDegrees(targetAngle - livePlayer.yRot)

                                val moveForward = diff in -67.5f..67.5f
                                val moveBackward = diff > 112.5f || diff < -112.5f
                                val moveLeft = diff in -157.5f..-22.5f
                                val moveRight = diff in 22.5f..157.5f

                                if (moveForward) MacroInputController.holdW() else if (moveBackward) MacroInputController.holdS() else {
                                    MacroInputController.releaseW()
                                    MacroInputController.releaseS()
                                }

                                if (moveLeft) MacroInputController.holdA() else if (moveRight) MacroInputController.holdD() else {
                                    MacroInputController.releaseStrafe()
                                }

                                if (moveForward && dist > 2.5) {
                                    MacroInputController.holdSprint()
                                } else {
                                    MacroInputController.releaseSprint()
                                }
                            }
                        } else {
                            // Smooth / Humanized Mode (Debounce, 25 deg hysteresis, coasting)
                            if (dist < 0.18) {
                                currentStatusText = "Positioned (Catches: $bounceCount)"
                                MacroInputController.releaseAllMovement()
                                currentForwardKey = 0
                                currentStrafeKey = 0
                            } else {
                                currentStatusText = "Moving to Spot (${String.format("%.2f", dist)}b)"
                                val targetAngle = (atan2(dz, dx) * 180.0 / Math.PI).toFloat() - 90.0f
                                val diff = Mth.wrapDegrees(targetAngle - livePlayer.yRot)

                                // Minimum 120ms key hold debounce
                                if (now - lastKeySwitchMs >= 120L) {
                                    val moveForward = diff in -67.5f..67.5f
                                    val moveBackward = diff > 112.5f || diff < -112.5f
                                    val moveLeft = diff in -157.5f..-22.5f
                                    val moveRight = diff in 22.5f..157.5f

                                    val newForward = if (moveForward) 1 else if (moveBackward) -1 else 0
                                    val newStrafe = if (moveLeft) -1 else if (moveRight) 1 else 0

                                    if (newForward != currentForwardKey || newStrafe != currentStrafeKey) {
                                        currentForwardKey = newForward
                                        currentStrafeKey = newStrafe
                                        lastKeySwitchMs = now

                                        if (newForward == 1) MacroInputController.holdW()
                                        else if (newForward == -1) MacroInputController.holdS()
                                        else {
                                            MacroInputController.releaseW()
                                            MacroInputController.releaseS()
                                        }

                                        if (newStrafe == -1) MacroInputController.holdA()
                                        else if (newStrafe == 1) MacroInputController.holdD()
                                        else MacroInputController.releaseStrafe()
                                    }
                                }

                                if (currentForwardKey == 1 && dist > 3.0) {
                                    MacroInputController.holdSprint()
                                } else {
                                    MacroInputController.releaseSprint()
                                }
                            }
                        }
                    } else {
                        currentStatusText = "Tracking Ball..."
                        MacroInputController.releaseAllMovement()
                        currentForwardKey = 0
                        currentStrafeKey = 0
                    }
                }

                delay(20L) // 50 Hz control loop
            }
        }
    }

    private fun getSkullTexture(stack: ItemStack): String? {
        if (stack.item != Items.PLAYER_HEAD) return null
        val profile = stack.get(DataComponents.PROFILE) ?: return null
        return profile.partialProfile().properties.get("textures").firstOrNull()?.value
    }

    private fun findBeachBallSlot(client: Minecraft): Int? {
        return com.hypcro.util.SkyBlockItemHelper.findBeachBallSlot(client)
    }

    private fun findActiveBeachBall(client: Minecraft, expectedTexture: String?): ArmorStand? {
        val player = client.player ?: return null
        val level = client.level ?: return null

        val entities = level.entitiesForRendering()
        val pPos = player.position()

        var nearestStand: ArmorStand? = null
        var nearestDist = 14.0

        for (entity in entities) {
            if (entity is ArmorStand && !entity.isRemoved) {
                // Must not be a pet (pets have [Lvl] nametags)
                val cleanName = entity.name.string.uppercase()
                if (cleanName.contains("[LVL") || cleanName.contains("LVL ") || cleanName.contains("PET")) continue

                val headItem = entity.getItemBySlot(EquipmentSlot.HEAD)
                if (headItem.isEmpty || headItem.item != Items.PLAYER_HEAD) continue

                val headTexture = getSkullTexture(headItem)
                if (expectedTexture != null && headTexture != null) {
                    if (headTexture != expectedTexture) continue
                }

                val dist = entity.position().distanceTo(pPos)
                if (dist < nearestDist) {
                    nearestDist = dist
                    nearestStand = entity
                }
            }
        }
        return nearestStand
    }

    fun renderWorld() {
        val cfg = ConfigManager.config.bouncyBall
        val predictor = activePredictor ?: return

        val landing = predictor.predictedLandingPos
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        // 1. Render Trajectory
        if (cfg.visualizeTrajectory && predictor.predictedPath.isNotEmpty()) {
            val path = predictor.predictedPath
            val lineColor = ARGB.color(255, 56, 189, 248)
            for (i in 0 until path.size - 1) {
                Gizmos.line(path[i], path[i + 1], lineColor, 2.5f)
            }
        }

        // 2. Render Landing Spot Box
        if (cfg.visualizeLandingBox && landing != null) {
            val pPos = player.position()
            val dist = sqrt((landing.x - pPos.x) * (landing.x - pPos.x) + (landing.z - pPos.z) * (landing.z - pPos.z))

            val (boxColor, strokeColor) = when {
                dist < 0.3 -> ARGB.color(100, 34, 197, 94) to ARGB.color(255, 34, 197, 94) // Green
                dist < 0.9 -> ARGB.color(100, 249, 115, 22) to ARGB.color(255, 249, 115, 22) // Orange
                else -> ARGB.color(100, 239, 68, 68) to ARGB.color(255, 239, 68, 68) // Red
            }

            val boxStyle = GizmoStyle.strokeAndFill(strokeColor, 2.0f, boxColor)
            val aabb = AABB(
                landing.x - 0.3, landing.y - 0.1, landing.z - 0.3,
                landing.x + 0.3, landing.y + 0.5, landing.z + 0.3
            )
            Gizmos.cuboid(aabb, boxStyle).setAlwaysOnTop()
        }
    }

    // =========================================================================
    // SKYHANNI QUADRATIC POLYNOMIAL REGRESSION ENSEMBLE PREDICTOR
    // =========================================================================
    private class Predictor(start: Vec3) {
        private val data = mutableListOf<Vec3>()
        private var startIndex = 0
        private var minY = 0.0
        private var updated = 0
        var lastPosition: Vec3 = start
        var bounceCounter = 0
        private var lastDirectionChangeMs = System.currentTimeMillis()
        private var isAscending = true

        var predictedPath = emptyList<Vec3>()
            private set
        var predictedLandingPos: Vec3? = null
            private set

        init {
            newData(start, start.y)
        }

        fun newData(newPos: Vec3, playerY: Double) {
            updateDirection(newPos)
            data.add(newPos)

            // Cap sample history to prevent unbounded memory growth while keeping enough trajectory context
            if (data.size > 200) {
                val toDrop = data.size - 100
                repeat(toDrop) { data.removeAt(0) }
                startIndex = maxOf(0, startIndex - toDrop)
            }

            val distToGround = abs(newPos.y - playerY)
            if (distToGround < 2.1) {
                startIndex = data.lastIndex
                minY = playerY
            }

            predictedPath = if (predictedPath.isEmpty()) emptyList() else predictedPath.drop(1)
            updated++

            // Recompute full polynomial prediction every 3 ticks to avoid jitter
            if (updated >= 3) {
                val fullPath = predict(startIndex, minY)
                predictedPath = fullPath
                predictedLandingPos = fullPath.lastOrNull()
                updated = 0
            }
        }

        private fun updateDirection(newPos: Vec3) {
            val dist = newPos.distanceTo(lastPosition)
            if (dist < 0.3) return
            val now = System.currentTimeMillis()
            if (now - lastDirectionChangeMs < 800L) return

            val diff = newPos.y - lastPosition.y
            val ascending = diff > 0.0
            if (ascending && !isAscending) {
                bounceCounter++
                lastDirectionChangeMs = now
            }
            isAscending = ascending
            lastPosition = newPos
        }

        private fun predict(startIndex: Int, minY: Double): List<Vec3> {
            val presentValues = data.lastIndex - startIndex
            val models = listOf(
                SmallPoly(data) to 1,
                AveragePoly(data) to 2,
                SpreadPoly(data) to 1
            ).filter { it.first.minimumToPredict <= presentValues }

            if (models.isEmpty()) return listOf(data.last())

            val predictions = models.map { (model, weight) ->
                model.predict(startIndex, data.lastIndex, minY) to weight
            }.filter { (path, _) ->
                val lastY = path.last().y
                minY - 1.0 < lastY && lastY < minY + 1.0
            }

            if (predictions.isEmpty()) return listOf(data.last())

            var totalWeight = 0.0
            var sumX = 0.0
            var sumZ = 0.0

            for ((path, weight) in predictions) {
                val end = path.last()
                sumX += end.x * weight
                sumZ += end.z * weight
                totalWeight += weight
            }

            val avgX = sumX / totalWeight
            val avgZ = sumZ / totalWeight

            val bestPath = predictions.minByOrNull { (path, _) ->
                val end = path.last()
                val dX = end.x - avgX
                val dZ = end.z - avgZ
                dX * dX + dZ * dZ
            }?.first ?: listOf(data.last())

            return bestPath
        }
    }

    private abstract class PolyModel(val given: List<Vec3>) {
        abstract val minimumToPredict: Int
        abstract fun getT1(start: Int, current: Int, minY: Double): Int
        abstract fun getT2(start: Int, current: Int, minY: Double): Int
        abstract fun getT3(start: Int, current: Int, minY: Double): Int

        open fun yTransform(t: Int): Double = given[t].y
        open fun dX(start: Int, current: Int, minY: Double): Double = given[current].x - given[current - 1].x
        open fun dZ(start: Int, current: Int, minY: Double): Double = given[current].z - given[current - 1].z

        fun predict(start: Int, current: Int, minY: Double): List<Vec3> {
            val t1 = getT1(start, current, minY)
            val t2 = getT2(start, current, minY)
            val t3 = getT3(start, current, minY)
            val y1 = yTransform(t1)
            val y2 = yTransform(t2)
            val y3 = yTransform(t3)

            val l1 = t1.toLong()
            val l2 = t2.toLong()
            val l3 = t3.toLong()

            val denominator = ((l3 * l3 - l1 * l1) * (l2 - l1) + (l2 * l2 - l1 * l1) * (l1 - l3)).toDouble()
            if (abs(denominator) < 1e-6) return listOf(given[current])

            val a = ((y3 - y1) * (l2 - l1) + (y2 - y1) * (l1 - l3)) / denominator
            val b = ((y2 - y1) - a * (l2 * l2 - l1 * l1)) / (l2 - l1)
            val c = y1 - b * l1 - a * l1 * l1

            fun poly(t: Int): Double {
                val lt = t.toLong()
                return a * lt * lt + b * lt + c
            }

            val dx = dX(start, current, minY)
            val dz = dZ(start, current, minY)

            val result = mutableListOf<Vec3>()
            var prev = given[current]

            for (t in (current + 1)..(current + 300)) {
                val y = poly(t)
                val nextPos = Vec3(prev.x + dx, y, prev.z + dz)
                result.add(nextPos)
                prev = nextPos
                if (y <= minY) break
            }
            return result
        }
    }

    private class SmallPoly(given: List<Vec3>) : PolyModel(given) {
        override val minimumToPredict = 3
        override fun getT1(start: Int, current: Int, minY: Double): Int = current
        override fun getT2(start: Int, current: Int, minY: Double): Int = current - 1
        override fun getT3(start: Int, current: Int, minY: Double): Int = current - 2
    }

    private class AveragePoly(given: List<Vec3>) : PolyModel(given) {
        override val minimumToPredict = 7
        override fun getT1(start: Int, current: Int, minY: Double): Int = current - 1
        override fun getT2(start: Int, current: Int, minY: Double): Int = current - 3
        override fun getT3(start: Int, current: Int, minY: Double): Int = current - 5
        override fun yTransform(t: Int): Double = (given[t - 1].y + given[t].y + given[t + 1].y) / 3.0
        override fun dX(start: Int, current: Int, minY: Double): Double =
            ((given[current].x - given[current - 1].x) + (given[current - 1].x - given[current - 2].x) + (given[current - 2].x - given[current - 3].x)) / 3.0
        override fun dZ(start: Int, current: Int, minY: Double): Double =
            ((given[current].z - given[current - 1].z) + (given[current - 1].z - given[current - 2].z) + (given[current - 2].z - given[current - 3].z)) / 3.0
    }

    private class SpreadPoly(given: List<Vec3>) : PolyModel(given) {
        override val minimumToPredict = 5
        override fun getT1(start: Int, current: Int, minY: Double): Int = current - 1
        override fun getT2(start: Int, current: Int, minY: Double): Int = (current - start) / 2 + start
        override fun getT3(start: Int, current: Int, minY: Double): Int = start + 1
        override fun yTransform(t: Int): Double = (given[t - 1].y + given[t].y + given[t + 1].y) / 3.0
    }
}
