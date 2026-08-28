package com.hypcro.util

import com.hypcro.config.CropType
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.min
import kotlin.math.sqrt

object CropBpsTracker {

    private const val SESSION_IDLE_TIMEOUT_MS = 4 * 60 * 1000L // 4 minutes
    private const val AVG_WINDOW_MS = 5 * 60 * 1000L // 5 minutes rolling window for average BPS
    private const val AVG_POLL_INTERVAL_MS = 500L // 0.5s refresh polling for average BPS only

    @Volatile
    var isTracking: Boolean = false
        private set

    @Volatile
    var currentFarmedCrop: CropType? = null
        private set

    @Volatile
    var totalBlocksBroken: Int = 0
        private set

    @Volatile
    private var accumulatedActiveDurationMs: Long = 0L

    @Volatile
    private var currentSessionStartMs: Long = 0L

    @Volatile
    private var lastActiveTimeMs: Long = 0L

    @Volatile
    private var cachedAvgBps: Double = 0.0

    @Volatile
    private var lastAvgCalcTimeMs: Long = 0L

    // Rolling 5-minute window of block break timestamps
    private val breakTimestamps = ConcurrentLinkedDeque<Long>()

    // Movement tracking for future Watchdog BPS checks
    private var lastPlayerPos: Vec3? = null
    private val movementWindow = ConcurrentLinkedDeque<Pair<Long, Double>>()

    @Volatile
    var currentMovementBps: Double = 0.0
        private set

    private var isInitialized = false

    fun init() {
        if (isInitialized) return
        isInitialized = true

        ClientPlayerBlockBreakEvents.AFTER.register { _, player, _, _ ->
            val client = Minecraft.getInstance()
            if (player == client.player) {
                recordBlockBreak()
            }
        }
    }

    fun startOrResumeSession(crop: CropType) {
        val now = System.currentTimeMillis()
        val isIdleExpired = (lastActiveTimeMs > 0L && (now - lastActiveTimeMs) > SESSION_IDLE_TIMEOUT_MS)
        val isDifferentCrop = (currentFarmedCrop != null && currentFarmedCrop != crop)

        if (isIdleExpired || isDifferentCrop || lastActiveTimeMs == 0L) {
            // Reset session on timeout or different crop
            totalBlocksBroken = 0
            accumulatedActiveDurationMs = 0L
            cachedAvgBps = 0.0
            lastAvgCalcTimeMs = 0L
            breakTimestamps.clear()
        }

        currentFarmedCrop = crop
        isTracking = true
        currentSessionStartMs = now
        lastActiveTimeMs = now
    }

    fun pauseSession() {
        val now = System.currentTimeMillis()
        if (isTracking && currentSessionStartMs > 0L) {
            accumulatedActiveDurationMs += (now - currentSessionStartMs)
            currentSessionStartMs = 0L
        }
        isTracking = false
        lastActiveTimeMs = now
    }

    fun resetSession() {
        isTracking = false
        currentFarmedCrop = null
        totalBlocksBroken = 0
        accumulatedActiveDurationMs = 0L
        currentSessionStartMs = 0L
        lastActiveTimeMs = 0L
        cachedAvgBps = 0.0
        lastAvgCalcTimeMs = 0L
        breakTimestamps.clear()
        movementWindow.clear()
        lastPlayerPos = null
        currentMovementBps = 0.0
    }

    fun recordBlockBreak() {
        val now = System.currentTimeMillis()
        totalBlocksBroken++
        lastActiveTimeMs = now
        breakTimestamps.addLast(now)
        cleanOldTimestamps(now)
    }

    fun getCurrentBps(): Double {
        val now = System.currentTimeMillis()
        cleanOldTimestamps(now)
        val cutoff = now - 1000L
        var count = 0
        val iterator = breakTimestamps.descendingIterator()
        while (iterator.hasNext()) {
            if (iterator.next() >= cutoff) {
                count++
            } else {
                break
            }
        }
        return count.toDouble().coerceIn(0.0, 20.0)
    }

    fun getAverageBps(): Double {
        val now = System.currentTimeMillis()
        if (now - lastAvgCalcTimeMs >= AVG_POLL_INTERVAL_MS || cachedAvgBps == 0.0) {
            cleanOldTimestamps(now)
            val oldest = breakTimestamps.peekFirst()
            val windowSec = if (oldest != null) {
                min((now - oldest).toDouble() / 1000.0, AVG_WINDOW_MS / 1000.0)
            } else {
                0.0
            }

            cachedAvgBps = if (windowSec < 1.0 || breakTimestamps.isEmpty()) {
                getCurrentBps()
            } else {
                (breakTimestamps.size / windowSec).coerceIn(0.0, 20.0)
            }
            lastAvgCalcTimeMs = now
        }
        return cachedAvgBps
    }

    fun getSessionUptimeMs(): Long {
        val now = System.currentTimeMillis()
        return if (isTracking && currentSessionStartMs > 0L) {
            accumulatedActiveDurationMs + (now - currentSessionStartMs)
        } else {
            accumulatedActiveDurationMs
        }
    }

    fun onClientTick(client: Minecraft) {
        val now = System.currentTimeMillis()

        // Auto reset if player disconnected
        if (client.level == null || client.player == null) {
            if (lastActiveTimeMs > 0L || isTracking) {
                resetSession()
            }
            return
        }

        // Auto reset if idle for over 4 minutes
        if (!isTracking && lastActiveTimeMs > 0L && (now - lastActiveTimeMs) > SESSION_IDLE_TIMEOUT_MS) {
            resetSession()
            return
        }

        cleanOldTimestamps(now)

        // Movement velocity tracking for future Watchdog BPS
        val player = client.player
        if (player != null) {
            val currentPos = player.position()
            val prevPos = lastPlayerPos
            if (prevPos != null) {
                val dx = currentPos.x - prevPos.x
                val dz = currentPos.z - prevPos.z
                val horizontalDist = sqrt(dx * dx + dz * dz)
                movementWindow.addLast(Pair(now, horizontalDist))
            }
            lastPlayerPos = currentPos

            // Prune movement entries older than 1000ms
            while (movementWindow.isNotEmpty() && (now - movementWindow.peekFirst().first) > 1000L) {
                movementWindow.pollFirst()
            }

            val totalDist = movementWindow.sumOf { it.second }
            currentMovementBps = totalDist
        } else {
            lastPlayerPos = null
            movementWindow.clear()
            currentMovementBps = 0.0
        }
    }

    private fun cleanOldTimestamps(now: Long) {
        val cutoff = now - AVG_WINDOW_MS
        while (breakTimestamps.isNotEmpty() && breakTimestamps.peekFirst() < cutoff) {
            breakTimestamps.pollFirst()
        }
    }
}
