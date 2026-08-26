package com.hypcro.movement

import com.hypcro.config.ConfigManager
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object MouseMovementEngine {

    @Volatile
    var isRotating = false
        private set

    @Volatile
    private var flightYawVel: Float = 0.0f
    @Volatile
    private var flightPitchVel: Float = 0.0f

    fun resetFlightVelocity() {
        flightYawVel = 0.0f
        flightPitchVel = 0.0f
    }

    /**
     * Smooth in-flight continuous camera tracking using critically-damped spring physics and GCD quantization.
     */
    fun smoothStepFlight(
        client: Minecraft,
        desiredYaw: Float,
        desiredPitch: Float,
        dt: Float = 0.01f
    ) {
        val player = client.player ?: return
        val config = ConfigManager.config.generalConfig.mouseMovement
        val gcd = computeGcd(client)

        val yawDiff = Mth.wrapDegrees(desiredYaw - player.yRot)
        val pitchDiff = (desiredPitch - player.xRot).coerceIn(-89.9f - player.xRot, 89.9f - player.xRot)

        // Critically damped spring physics
        val speedFactor = config.dpiSpeed.coerceIn(1, 20)
        val omega = 7.0f + (speedFactor / 20.0f) * 9.0f // Natural frequency based on DPI Speed
        val damping = 1.0f // Critical damping (smooth acceleration & zero overshoot oscillation)

        val fYaw = 1.0f + 2.0f * dt * damping * omega
        val fPitch = 1.0f + 2.0f * dt * damping * omega
        val oo = omega * omega
        val hoo = dt * oo
        val hhoo = dt * hoo
        val detInvYaw = 1.0f / (fYaw + hhoo)
        val detInvPitch = 1.0f / (fPitch + hhoo)

        val newYawPos = (fYaw * player.yRot + dt * flightYawVel + hhoo * (player.yRot + yawDiff)) * detInvYaw
        flightYawVel = (flightYawVel + hoo * yawDiff) * detInvYaw

        val newPitchPos = (fPitch * player.xRot + dt * flightPitchVel + hhoo * (player.xRot + pitchDiff)) * detInvPitch
        flightPitchVel = (flightPitchVel + hoo * pitchDiff) * detInvPitch

        val steppedYaw = applyGcd(newYawPos, player.yRot, gcd = gcd)
        val steppedPitch = applyGcd(newPitchPos, player.xRot, min = -89.9f, max = 89.9f, gcd = gcd)

        player.yRot = steppedYaw
        player.xRot = steppedPitch
    }

    fun computeGcd(client: Minecraft): Double {
        val sensitivity = client.options.sensitivity().get()
        val multiplier = sensitivity * 0.6 + 0.2
        return multiplier * multiplier * multiplier * 1.2
    }

    fun applyGcd(
        targetRot: Float,
        previousRot: Float,
        min: Float? = null,
        max: Float? = null,
        gcd: Double
    ): Float {
        val delta = Mth.wrapDegrees(targetRot - previousRot)
        val roundedDelta = Math.round(delta / gcd) * gcd
        var result = (previousRot + roundedDelta).toFloat()

        if (max != null && result > max) {
            result -= gcd.toFloat()
        }
        if (min != null && result < min) {
            result += gcd.toFloat()
        }
        return result
    }

    suspend fun rotateTo(
        client: Minecraft,
        targetYaw: Float,
        targetPitch: Float,
        customDurationMs: Long? = null
    ) {
        val player = client.player ?: return
        isRotating = true

        try {
            val config = ConfigManager.config.generalConfig.mouseMovement
            val gcd = computeGcd(client)
            val rng = ThreadLocalRandom.current()

            val startYaw = player.yRot
            val startPitch = player.xRot

            // 1. Imperfect Aim: Built-in human variance
            val randDist = if (config.humanize) {
                if (config.highPrecision) {
                    rng.nextFloat(0.05f, 0.15f)
                } else {
                    rng.nextFloat(1.0f, 6.0f)
                }
            } else {
                0.0f
            }
            val randAngle = rng.nextDouble(0.0, Math.PI * 2.0)
            val imperfectTargetYaw = targetYaw + (randDist * kotlin.math.cos(randAngle)).toFloat()
            val imperfectTargetPitch = Mth.clamp(targetPitch + (randDist * kotlin.math.sin(randAngle)).toFloat(), -89.9f, 89.9f)

            val yawDiff = Mth.wrapDegrees(imperfectTargetYaw - startYaw)
            val pitchDiff = imperfectTargetPitch - startPitch
            val angularDist = sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff)

            if (angularDist < 0.08f) {
                return
            }

            // Duration calculation based on DPI Speed (1 to 20)
            val speedFactor = config.dpiSpeed.coerceIn(1, 20)
            val baseDuration = customDurationMs ?: run {
                val msPerDegree = (21 - speedFactor) * 0.55f + 1.5f
                (angularDist * msPerDegree).toLong().coerceIn(150L, 1400L)
            }

            // 2. Strong Lateral Bezier Control Points in Delta Space (prevents 360-degree flip bugs)
            // Compute perpendicular lateral vector for genuine curve arching
            val perpX = -pitchDiff
            val perpY = yawDiff
            val perpLen = sqrt(perpX * perpX + perpY * perpY).coerceAtLeast(0.01f)
            val curveSign = if (rng.nextBoolean()) 1.0f else -1.0f
            val curveMagnitude = (angularDist * rng.nextFloat(0.18f, 0.35f)).coerceIn(1.5f, 15.0f) * curveSign

            val cp1YawDelta = yawDiff * 0.25f + (perpX / perpLen) * curveMagnitude
            val cp1PitchDelta = pitchDiff * 0.25f + (perpY / perpLen) * curveMagnitude
            val cp2YawDelta = yawDiff * 0.70f + (perpX / perpLen) * (curveMagnitude * 0.6f)
            val cp2PitchDelta = pitchDiff * 0.70f + (perpY / perpLen) * (curveMagnitude * 0.6f)

            // 3. Genuine Overshoot parameters
            val hasOvershoot = config.overshoot && angularDist > 4.0f
            val overshootMag = if (hasOvershoot) rng.nextFloat(1.5f, 3.5f) else 0.0f
            val overshootDirYaw = yawDiff / angularDist
            val overshootDirPitch = pitchDiff / angularDist

            var lastTime = System.currentTimeMillis()
            var virtualElapsed = 0.0
            var prevYaw = startYaw
            var prevPitch = startPitch
            var hasTriggeredPause = false
            var pauseUntil = 0L

            // Vibration state: 10Hz (100ms) to 20Hz (50ms) randomized intervals
            var nextTremorTime = lastTime + rng.nextLong(50L, 101L)
            var currentTremorYaw = 0.0f
            var currentTremorPitch = 0.0f

            while (true) {
                val now = System.currentTimeMillis()
                val deltaRealMs = (now - lastTime).coerceAtLeast(0L)
                lastTime = now

                // Check remaining total angle to imperfect target
                val remainingYaw = abs(Mth.wrapDegrees(imperfectTargetYaw - prevYaw))
                val remainingPitch = abs(imperfectTargetPitch - prevPitch)
                val remainingDist = sqrt(remainingYaw * remainingYaw + remainingPitch * remainingPitch)

                // If already within high precision tolerance (< 0.15 deg), finish early
                if (config.humanize && config.highPrecision && remainingDist < 0.15f) {
                    break
                }

                val isInHighPrecisionSlowdown = config.humanize && config.highPrecision && remainingDist <= 15.0f

                // Trigger explicit 300ms (6 ticks) pause timer when first crossing the 15 degree threshold
                if (isInHighPrecisionSlowdown && !hasTriggeredPause) {
                    hasTriggeredPause = true
                    pauseUntil = now + 300L
                }

                val isCurrentlyPaused = now < pauseUntil

                // Speed multiplier: 0 while paused, 0.05 during creeping, 1.0 normally
                val speedMultiplier = when {
                    isCurrentlyPaused -> 0.0
                    isInHighPrecisionSlowdown -> 0.05 // 20 times slower
                    else -> 1.0
                }

                virtualElapsed += deltaRealMs * speedMultiplier
                val t = (virtualElapsed.toFloat() / baseDuration.toFloat()).coerceIn(0.0f, 1.0f)

                var currentTargetYaw: Float
                var currentTargetPitch: Float

                when (config.movementType.uppercase()) {
                    "SIMPLE" -> {
                        // Smooth linear progression
                        val easedT = t * t * (3.0f - 2.0f * t)
                        currentTargetYaw = startYaw + yawDiff * easedT
                        currentTargetPitch = startPitch + pitchDiff * easedT
                    }
                    "BEZIER" -> {
                        // Dramatic cubic Bezier curve in pure continuous delta space
                        val easedT = t * t * (3.0f - 2.0f * t)
                        val u = 1.0f - easedT
                        val tt = easedT * easedT
                        val uu = u * u
                        val ttt = tt * easedT

                        val deltaYaw = 3 * uu * easedT * cp1YawDelta + 3 * u * tt * cp2YawDelta + ttt * yawDiff
                        val deltaPitch = 3 * uu * easedT * cp1PitchDelta + 3 * u * tt * cp2PitchDelta + ttt * pitchDiff

                        currentTargetYaw = startYaw + deltaYaw
                        currentTargetPitch = startPitch + deltaPitch
                    }
                    else -> { // "GCD" (Simulated physics kinematics with momentum & braking)
                        // Acceleration then deceleration
                        val kinematicT = if (t < 0.5f) 2.0f * t * t else 1.0f - (-2.0f * t + 2.0f) * (-2.0f * t + 2.0f) / 2.0f
                        currentTargetYaw = startYaw + yawDiff * kinematicT
                        currentTargetPitch = startPitch + pitchDiff * kinematicT
                    }
                }

                // Apply dynamic overshoot curve (swings past target at ~85% of time, then hooks back)
                if (hasOvershoot) {
                    val overshootWave = kotlin.math.sin(t * Math.PI) * (if (t > 0.65f) (1.0f - t) * 3.0f else t * 1.5f)
                    currentTargetYaw += (overshootDirYaw * overshootMag * overshootWave).toFloat()
                    currentTargetPitch += (overshootDirPitch * overshootMag * overshootWave).toFloat()
                }

                // 4. Noticeable Human Micro-Vibration Tremors (8Hz when in high precision range, otherwise 10Hz - 20Hz)
                if (config.humanize && t < 0.95f) {
                    if (now >= nextTremorTime) {
                        currentTremorYaw = rng.nextFloat(-0.16f, 0.16f)
                        currentTremorPitch = rng.nextFloat(-0.10f, 0.10f)
                        val intervalMs = if (isInHighPrecisionSlowdown) {
                            125L // 8Hz (1000ms / 8)
                        } else {
                            rng.nextLong(50L, 101L) // 10Hz to 20Hz
                        }
                        nextTremorTime = now + intervalMs
                    }
                    currentTargetYaw += currentTremorYaw
                    currentTargetPitch += currentTremorPitch
                }

                // Quantize to hardware GCD
                val steppedYaw = applyGcd(currentTargetYaw, prevYaw, gcd = gcd)
                val steppedPitch = applyGcd(currentTargetPitch, prevPitch, min = -89.9f, max = 89.9f, gcd = gcd)

                client.execute {
                    val p = client.player ?: return@execute
                    p.yRot = steppedYaw
                    p.xRot = steppedPitch
                    p.yRotO = steppedYaw
                    p.xRotO = steppedPitch
                }

                prevYaw = steppedYaw
                prevPitch = steppedPitch

                if (t >= 1.0f) break
                delay(10) // High frequency sampling
            }
        } finally {
            isRotating = false
        }
    }
}
