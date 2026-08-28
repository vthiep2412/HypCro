package com.hypcro.pest

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.failsafe.HypcroWatchdog
import com.hypcro.farming.IFarmEngine
import com.hypcro.farming.MacroController
import com.hypcro.farming.MacroInputController
import com.hypcro.input.CommandHelper
import com.hypcro.movement.CentralMovementCoordinator
import com.hypcro.movement.MouseMovementEngine
import com.hypcro.pathfinding.ThetaStarPathfinder
import com.hypcro.util.VacuumTierInfo
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

enum class PestCallerSource {
    MANUAL_USER,
    WS_FARM_ENGINE,
    VERTICAL_FARM_ENGINE
}

object PestDestroyerEngine {

    enum class State {
        IDLE,
        INITIALIZE_SPAWN,
        SCAN_TABLIST,
        ROUTING_PLOTS,
        TRANSIT_TO_PLOT,
        ROOFTOP_ASCENT,
        APPROACH_PLOT_CENTER,
        COMBAT_CLEANING,
        RETURN_GARDEN
    }

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var activeJob: Job? = null

    @Volatile
    var isRunning = false
        private set

    @Volatile
    var currentState = State.IDLE
        private set

    @Volatile
    var currentPlotIndex = 0
        private set

    @Volatile
    var totalPestsKilled = 0
        private set

    private var sessionStartTimeMs: Long = 0L
    val sessionUptimeMs: Long
        get() = if (isRunning && sessionStartTimeMs > 0) System.currentTimeMillis() - sessionStartTimeMs else 0L

    @Volatile
    var callerSource = PestCallerSource.MANUAL_USER
        private set

    @Volatile
    private var callingEngineInstance: IFarmEngine? = null

    fun stop() {
        activeJob?.cancel()
        activeJob = null
        isRunning = false
        currentState = State.IDLE
        callingEngineInstance = null
        CentralMovementCoordinator.isAbortRequested = true
        MacroInputController.releaseAllMovement()
        MacroInputController.releaseUseItem()
    }

    fun findVacuumSlot(client: Minecraft): Int? {
        return com.hypcro.util.SkyBlockItemHelper.findVacuumSlot(client)
    }

    fun startPestDestroyer(
        source: PestCallerSource = PestCallerSource.MANUAL_USER,
        callingEngine: IFarmEngine? = null
    ): Boolean {
        if (isRunning) return false
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        val level = client.level ?: return false

        if (!com.hypcro.util.GardenStateReader.isInGarden(client)) {
            HypCroMod.logWarn("Pest Destroyer halted: Player is not in Area: Garden.")
            return false
        }

        val vacuumSlot = com.hypcro.util.SkyBlockItemHelper.findVacuumSlot(client)
        if (vacuumSlot == null) {
            HypCroMod.logWarn("Cannot start Pest Destroyer: No Vacuum found on hotbar (0-8)!")
            return false
        }

        CentralMovementCoordinator.isAbortRequested = false
        CentralMovementCoordinator.lastAbortTimestamp = 0L

        callerSource = source
        callingEngineInstance = callingEngine
        isRunning = true
        sessionStartTimeMs = System.currentTimeMillis()
        currentState = State.INITIALIZE_SPAWN
        totalPestsKilled = 0

        activeJob?.cancel()
        activeJob = engineScope.launch {
            try {
                executePestCycle(client)
            } catch (e: CancellationException) {
                HypCroMod.log("Pest Destroyer cancelled.")
            } catch (e: Exception) {
                HypCroMod.logWarn("Pest Destroyer encountered an error: ${e.message}")
            } finally {
                stopPestDestroyer("Cycle Finished")
            }
        }
        return true
    }

    fun stopPestDestroyer(reason: String = "Manual") {
        isRunning = false
        currentState = State.IDLE
        activeJob?.cancel()
        activeJob = null
        CentralMovementCoordinator.isAbortRequested = true
        MacroInputController.releaseAll()
    }

    fun getVacuumTierInfo(client: Minecraft, slot: Int): com.hypcro.util.VacuumTierInfo {
        return com.hypcro.util.SkyBlockItemHelper.getVacuumTierInfo(client, slot)
    }

    private suspend fun executePestCycle(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        val cfg = ConfigManager.config.pestDestroyer
        val isAutoRun = (callingEngineInstance != null || callerSource != PestCallerSource.MANUAL_USER)

        HypCroMod.log("Starting Pest Destroyer cycle (${if (isAutoRun) "Automated Farm Run" else "Manual Run"})...")

        // =========================================================================
        // SECTION 1: PRE-FLIGHT VACUUM & LOCATION SAVE VALIDATION
        // =========================================================================
        val vacuumSlot = findVacuumSlot(client)
        if (vacuumSlot == null) {
            if (isAutoRun) {
                HypcroWatchdog.potentialStaffCheck("No Vacuum found in hotbar during auto pest cycle")
            } else {
                HypCroMod.logWarn("Cannot start Pest Destroyer: No Vacuum found in hotbar!")
            }
            return
        }

        // =========================================================================
        // SECTION 2: INITIAL SAFETY & PEST CONFIRMATION
        // =========================================================================
        currentState = State.SCAN_TABLIST

        if (!PestTabReader.isInGarden(client)) {
            HypCroMod.logWarn("Pest Destroyer halted: Player is not in Area: Garden.")
            return
        }

        var totalAlive = 0
        val initialInfestedPlots = mutableSetOf<Int>()

        for (attempt in 1..5) {
            if (!isRunning) return

            val scoreboardInfo = PestTabReader.scanScoreboardPests(client)
            val tabInfo = PestTabReader.scanPests(client)
            totalAlive = max(scoreboardInfo.aliveCount, tabInfo.aliveCount)

            val detectedPlots = if (tabInfo.infestedPlots.isNotEmpty()) {
                tabInfo.infestedPlots
            } else {
                scoreboardInfo.infestedPlots
            }

            if (detectedPlots.isNotEmpty()) {
                initialInfestedPlots.addAll(detectedPlots)
                break
            }

            if (totalAlive <= 0) {
                HypCroMod.log("No pests detected on scoreboard/tablist (Pest count: 0). Halting.")
                return
            }

            if (attempt < 5) {
                HypCroMod.log("Scoreboard reported $totalAlive pests, but infested plot list is not ready yet. Retrying in 2s (attempt $attempt/5)...")
                delay(2000L)
            }
        }

        if (initialInfestedPlots.isEmpty()) {
            HypCroMod.logWarn("Scoreboard reported pests, but no specific infested plot was found in Tablist/Scoreboard after 5 attempts (10s).")
            return
        }

        // Proactive Initial Check: If garden has 1 pest left and all remaining infested plots are preserved plots, halt immediately before moving
        if (cfg.keepPest && totalAlive <= 1 && initialInfestedPlots.all { cfg.leavePestPlots.contains(it) }) {
            HypCroMod.log("Garden has only 1 pest left in preserved plot(s) ${initialInfestedPlots.joinToString()}. Halting immediately.")
            return
        }

        HypCroMod.logSuccess("Scoreboard confirmed $totalAlive pests in Garden across plots: ${initialInfestedPlots.joinToString()}")

        if (isAutoRun) {
            HypCroMod.log("Saving farming location before departure (/setspawn)...")
            CommandHelper.sendCommand(client, "/setspawn")
            delay(450L) // 450ms wait for packet confirmation
        }

        // =========================================================================
        // SECTION 3: ROUTE INITIALIZATION & MAIN VISITING LOOP
        // =========================================================================
        currentState = State.ROUTING_PLOTS
        val remainingPlots = initialInfestedPlots.toMutableList()

        while (remainingPlots.isNotEmpty() && isRunning) {
            val livePlayer = client.player ?: break
            val livePlayerPos = livePlayer.position()

            // Step 1: Live Plot Re-Check from Tablist and Scoreboard
            val liveTab = PestTabReader.scanPests(client)
            val liveScoreboard = PestTabReader.scanScoreboardPests(client)
            val liveInfested = if (liveTab.infestedPlots.isNotEmpty()) liveTab.infestedPlots else liveScoreboard.infestedPlots
            val liveTotalPests = max(liveScoreboard.aliveCount, liveTab.aliveCount)

            // Keep only plots that are currently still infested
            remainingPlots.retainAll { liveInfested.contains(it) }
            if (remainingPlots.isEmpty()) {
                HypCroMod.log("All infested plots have been cleared!")
                break
            }

            // Proactive Preserved Plot Filter: if garden has only 1 pest left and remaining plots are preserved plots
            if (cfg.keepPest) {
                val nonPreservedRemaining = remainingPlots.filter { !cfg.leavePestPlots.contains(it) }
                if (nonPreservedRemaining.isEmpty()) {
                    if (liveTotalPests <= 1) {
                        HypCroMod.log("Garden has only 1 pest left in preserved plot(s). Halting sweep!")
                        break
                    }
                    val allSingle = remainingPlots.all { pId ->
                        val count = PestTabReader.getScoreboardPlotPestCount(client, pId)
                        count == 1
                    }
                    if (allSingle) {
                        HypCroMod.log("All remaining plots are preserved plots with only 1 pest. Halting sweep!")
                        break
                    }
                }
            }

            // Pick closest remaining infested plot from live position
            val targetPlotId = remainingPlots.minByOrNull { plotId ->
                val pData = PlotCoordinateData.getPlot(plotId) ?: return@minByOrNull Double.MAX_VALUE
                livePlayerPos.distanceTo(pData.centerPos)
            } ?: break

            val plotData = PlotCoordinateData.getPlot(targetPlotId)
            if (plotData == null) {
                remainingPlots.remove(targetPlotId)
                continue
            }

            val center = plotData.centerPos
            HypCroMod.log("Targeting Plot #$targetPlotId (Remaining plots: ${remainingPlots.size})")

            // =========================================================================
            // SECTION 4: DYNAMIC REAL-TIME TRANSIT TO TARGET PLOT
            // =========================================================================
            currentState = State.TRANSIT_TO_PLOT

            // Find best unlocked teleportable plot (Plot 0 is never teleported to)
            val bestTpPlot: Int? = if (targetPlotId == 0) {
                null
            } else if (cfg.teleportablePlots.contains(targetPlotId)) {
                targetPlotId
            } else {
                cfg.teleportablePlots.minByOrNull { tpId ->
                    val tpData = PlotCoordinateData.getPlot(tpId) ?: return@minByOrNull Double.MAX_VALUE
                    tpData.tpPos.distanceTo(center)
                }
            }

            val currentY = livePlayer.position().y
            val initialDistToCenter = livePlayer.position().distanceTo(center)
            val cruiseY = Random.nextDouble(81.0, 84.0)

            var alreadyTeleported = false
            var tpRetried = false

            suspend fun handleStuckRecovery(contextMsg: String): Boolean {
                if (CentralMovementCoordinator.consecutiveStuckCount >= 5) {
                    if (bestTpPlot != null && targetPlotId != 0 && !tpRetried) {
                        HypCroMod.logWarn("Anti-stuck triggered 5 times in $contextMsg! Retrying /plottp $bestTpPlot...")
                        tpRetried = true
                        alreadyTeleported = true
                        MacroInputController.releaseAllMovement()
                        CentralMovementCoordinator.resetStuckCount()
                        delay(1500L)
                        CommandHelper.sendCommand(client, "/plottp $bestTpPlot")
                        delay(1500L)
                        val ascentSuccess = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                        if (!ascentSuccess) {
                            if (callerSource == PestCallerSource.WS_FARM_ENGINE || callerSource == PestCallerSource.VERTICAL_FARM_ENGINE) {
                                com.hypcro.failsafe.HypcroWatchdog.potentialStaffCheck("Anti Stuck Active Too many times")
                                stopPestDestroyer("Anti Stuck Failsafe")
                            } else {
                                HypCroMod.logWarn("Anti Stuck Active Too many times")
                                stopPestDestroyer("Anti Stuck Limit")
                            }
                            return false
                        }
                        return true
                    } else {
                        if (callerSource == PestCallerSource.WS_FARM_ENGINE || callerSource == PestCallerSource.VERTICAL_FARM_ENGINE) {
                            com.hypcro.failsafe.HypcroWatchdog.potentialStaffCheck("Anti Stuck Active Too many times")
                            stopPestDestroyer("Anti Stuck Failsafe")
                        } else {
                            HypCroMod.logWarn("Anti Stuck Active Too many times")
                            stopPestDestroyer("Anti Stuck Limit")
                        }
                        return false
                    }
                }
                return true
            }

            // Only perform high-altitude ascent if crossing long distances (> 30 blocks) between different plots
            if (initialDistToCenter > 30.0) {
                if (currentY < 79.0) {
                    // Low altitude: Attempt ascent to randomized cruise altitude
                    currentState = State.ROOFTOP_ASCENT
                    HypCroMod.log("Player Y < 79 (${String.format("%.1f", currentY)}) and Plot #$targetPlotId is ${String.format("%.0f", initialDistToCenter)}b away. Ascending to Y = ${String.format("%.1f", cruiseY)}...")

                    val startPos = livePlayer.position()
                    val ascendTarget = Vec3(startPos.x, cruiseY, startPos.z)
                    val hasStraightClimb = ThetaStarPathfinder.hasLineOfSight(level, startPos, ascendTarget)

                    if (hasStraightClimb) {
                        val climbSuccess = CentralMovementCoordinator.flyTo(client, targetX = startPos.x, targetY = cruiseY, targetZ = startPos.z)
                        if (!climbSuccess && !handleStuckRecovery("Ascent Climb")) return
                    } else {
                        val pathfinder = CentralMovementCoordinator.getActivePathfinder()
                        val waypoints = withTimeoutOrNull(3000L) {
                            withContext(Dispatchers.Default) {
                                pathfinder.computePath(level, startPos, ascendTarget)
                            }
                        }

                        if (waypoints.isNullOrEmpty()) {
                            HypCroMod.logWarn("Pathfinding ascent calculation timed out (>3s) or failed. Waiting 3s in position before /plottp...")
                            if (bestTpPlot != null && targetPlotId != 0) {
                                delay(3000L)
                                CommandHelper.sendCommand(client, "/plottp $bestTpPlot")
                                alreadyTeleported = true
                                delay(1500L)
                                val postTpAscent = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                                if (!postTpAscent && !handleStuckRecovery("Post-TP Ascent")) return
                            }
                        } else {
                            val climbSuccess = CentralMovementCoordinator.flyTo(client, targetX = startPos.x, targetY = cruiseY, targetZ = startPos.z)
                            if (!climbSuccess && !handleStuckRecovery("Ascent Flight")) return
                        }
                    }
                } else if (currentY < cruiseY - 1.0) {
                    val ascendSuccess = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                    if (!ascendSuccess && !handleStuckRecovery("Altitude Adjust")) return
                }

                // High altitude or post-ascent: Evaluate direct flight vs teleport only if not already teleported
                if (!alreadyTeleported) {
                    val afterAscentPos = client.player?.position() ?: break
                    val distPlayerToCenter = afterAscentPos.distanceTo(center)
                    val distTpToCenter = if (bestTpPlot != null) {
                        val tpData = PlotCoordinateData.getPlot(bestTpPlot)
                        tpData?.tpPos?.distanceTo(center) ?: Double.MAX_VALUE
                    } else {
                        Double.MAX_VALUE
                    }

                    if (distPlayerToCenter > distTpToCenter && bestTpPlot != null && targetPlotId != 0) {
                        HypCroMod.log("Waiting 3s in position before teleporting to Plot #$bestTpPlot via /plottp $bestTpPlot...")
                        delay(3000L)
                        CommandHelper.sendCommand(client, "/plottp $bestTpPlot")
                        alreadyTeleported = true
                        delay(1500L)
                        // Ascend after teleport to clear plot structures
                        val postTpAscent = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                        if (!postTpAscent && !handleStuckRecovery("Post-TP Ascent")) return
                    }
                }

                // Ensure player is at cruise altitude before cross-island flight
                val curY = client.player?.position()?.y ?: cruiseY
                if (curY < cruiseY - 1.0) {
                    val ascendSuccess = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                    if (!ascendSuccess && !handleStuckRecovery("Cruise Altitude Alignment")) return
                }
            }

            // Real-Time Plot Center Navigation & Altitude Verification
            currentState = State.APPROACH_PLOT_CENTER
            var plotConfirmed = false

            for (attempt in 1..2) {
                if (!isRunning || CentralMovementCoordinator.isAbortTriggered(client)) break

                // Verify cruise altitude before center navigation
                val currentY = client.player?.position()?.y ?: cruiseY
                if (currentY < cruiseY - 1.0) {
                    HypCroMod.log("Ascending to cruise altitude (current Y=${currentY.toInt()}, target Y=${cruiseY.toInt()})...")
                    val ascendSuccess = CentralMovementCoordinator.flyTo(client, targetX = null, targetY = cruiseY, targetZ = null)
                    if (!ascendSuccess && !handleStuckRecovery("Center Pre-Ascent")) return
                }

                // Fly directly towards plot center X/Z at cruise altitude
                val flyCenterSuccess = CentralMovementCoordinator.flyTo(client, targetX = center.x, targetY = null, targetZ = center.z)
                MacroInputController.releaseAllMovement()
                delay(200L)

                if (!flyCenterSuccess && CentralMovementCoordinator.consecutiveStuckCount >= 5) {
                    if (!handleStuckRecovery("Plot Center Transit")) return
                }

                // Verify scoreboard at plot center
                val lines = PestTabReader.readScoreboardLines(client)
                val plotRegex = Regex("""(?i)Plot\s*-\s*$targetPlotId(?!\d)""")
                val altPlotRegex = Regex("""(?i)Plot\s+$targetPlotId(?!\d)""")
                val matched = lines.any { plotRegex.containsMatchIn(it) || altPlotRegex.containsMatchIn(it) }

                if (matched) {
                    plotConfirmed = true
                    HypCroMod.logSuccess("Plot #$targetPlotId confirmed via scoreboard at plot center!")
                    break
                } else {
                    HypCroMod.logWarn("Plot verification failed on attempt $attempt (Expected Plot #$targetPlotId).")
                    if (attempt == 2) {
                        if (callerSource == PestCallerSource.WS_FARM_ENGINE || callerSource == PestCallerSource.VERTICAL_FARM_ENGINE) {
                            com.hypcro.failsafe.HypcroWatchdog.potentialStaffCheck("Pest Destroyer: Failed to arrive at Plot #$targetPlotId after 2 attempts.")
                            stopPestDestroyer("Failed Arrival Failsafe")
                            return
                        } else {
                            HypCroMod.logWarn("Pest Destroyer: Failed to confirm Plot #$targetPlotId after 2 attempts. Continuing with caution.")
                        }
                    } else {
                        delay(500L)
                    }
                }
            }

            if (!isRunning || CentralMovementCoordinator.isAbortTriggered(client)) {
                break
            }

            delay(200L)

            // =========================================================================
            // SECTION 5: PEST COMBAT, AIMING & STRICT KILL CONFIRMATION
            // =========================================================================
            currentState = State.COMBAT_CLEANING
            val isPreservedPlot = cfg.keepPest && cfg.leavePestPlots.contains(targetPlotId)

            var combatAttempts = 0
            while (combatAttempts < 15 && isRunning && !CentralMovementCoordinator.isAbortTriggered(client)) {
                combatAttempts++
                val curPlayer = client.player ?: break
                val curPos = curPlayer.position()

                // If preserved plot, check live scoreboard count first
                if (isPreservedPlot) {
                    val scbInfo = PestTabReader.scanScoreboardPests(client)
                    val plotPestCount = PestTabReader.getScoreboardPlotPestCount(client, targetPlotId)
                    if (plotPestCount == 1 || (plotPestCount == null && scbInfo.aliveCount <= 1)) {
                        HypCroMod.log("Preserved plot #$targetPlotId has only 1 pest remaining. Preserving and moving on!")
                        val randomRestSlot = Random.nextInt(4, 7)
                        client.execute { client.player?.inventory?.selectedSlot = randomRestSlot }
                        break
                    }
                }

                // Fresh live entity scan strictly within 46-block plot bounds
                // Checks every 2s up to 5 times (10s total) and re-verifies Tablist/Scoreboard continuously
                var nearbyPests = emptyList<TrackedPest>()
                var plotClearedOnTablist = false

                for (checkAttempt in 1..5) {
                    if (!isRunning || CentralMovementCoordinator.isAbortTriggered(client)) break

                    val windowStartMs = System.currentTimeMillis()
                    while (System.currentTimeMillis() - windowStartMs < 2000L && isRunning && !CentralMovementCoordinator.isAbortTriggered(client)) {
                        nearbyPests = PestTargetTracker.findPestsInPlot(client, center, maxDistance = 46.0)
                        if (nearbyPests.isNotEmpty()) break
                        delay(200L)
                    }

                    if (nearbyPests.isNotEmpty()) {
                        break
                    }

                    // Check Tablist and Scoreboard every 2s
                    val tabCheck = PestTabReader.scanPests(client)
                    val scbCheck = PestTabReader.scanScoreboardPests(client)
                    val isStillInfested = tabCheck.infestedPlots.contains(targetPlotId) || scbCheck.infestedPlots.contains(targetPlotId)

                    if (!isStillInfested) {
                        plotClearedOnTablist = true
                        HypCroMod.log("Plot #$targetPlotId is no longer infested on Tablist/Scoreboard. Advancing.")
                        break
                    }
                }

                if (plotClearedOnTablist) {
                    break
                }

                if (nearbyPests.isEmpty()) {
                    // Reached 10s limit with 0 entities loaded and Tablist still claiming infested
                    if (isAutoRun) {
                        HypcroWatchdog.potentialStaffCheck("Plot #$targetPlotId is listed as infested on Tablist/Scoreboard, but 0 pest entities loaded after 10s")
                        return
                    } else {
                        HypCroMod.logWarn("Plot #$targetPlotId is listed as infested, but no pest entities loaded after 10s. Halting manual Pest Destroyer.")
                        return
                    }
                }

                // Target closest pest
                val targetPest = nearbyPests.minByOrNull { it.position.distanceTo(curPos) } ?: break
                val targetPos = targetPest.position

                // Switch to vacuum slot to begin attack if not already held
                val curVacSlot = findVacuumSlot(client)
                if (curVacSlot != null) {
                    val currentSelected = client.player?.inventory?.selectedSlot
                    if (currentSelected != curVacSlot) {
                        client.execute {
                            client.player?.inventory?.selectedSlot = curVacSlot
                        }
                        delay(60L)
                    }
                }

                val tierInfo = curVacSlot?.let { getVacuumTierInfo(client, it) } ?: VacuumTierInfo(1, "Basic Vacuum", 8000L, 3.0)
                val derpyMultiplier = if (cfg.derpy) 1.8 else 1.0
                val maxFireDurationMs = (tierInfo.baseDurationMs * derpyMultiplier).toLong()

                val pestName = targetPest.entity.name.string.uppercase()
                val skullName = targetPest.skullMarker?.name?.string?.uppercase() ?: ""
                val isLocustOrCricket = pestName.contains("LOCUST") || pestName.contains("CRICKET") || skullName.contains("LOCUST") || skullName.contains("CRICKET")

                // Calculate safe attack position with tier-specific range clamped to plot bounds
                val safeAttackPos = PestTargetTracker.findSafeAttackPosition(
                    client,
                    curPos,
                    targetPos,
                    nearbyPests,
                    keepPestActive = isPreservedPlot,
                    targetEntityId = targetPest.entity.id,
                    attackRange = tierInfo.attackRange,
                    plotCenter = center,
                    maxPlotOffset = 46.0
                )

                if (curPos.distanceTo(safeAttackPos) > 1.5) {
                    val flyCombatSuccess = CentralMovementCoordinator.flyTo(
                        client,
                        targetX = safeAttackPos.x,
                        targetY = safeAttackPos.y,
                        targetZ = safeAttackPos.z
                    )
                    MacroInputController.releaseAllMovement()
                    delay(60L)

                    if (!flyCombatSuccess && CentralMovementCoordinator.consecutiveStuckCount >= 5) {
                        if (!handleStuckRecovery("Combat Position Flight")) return
                    }
                }

                val shootingPlayer = client.player ?: break
                val eyePos = shootingPlayer.eyePosition

                // Calculate initial angle to target
                val dx = targetPos.x - eyePos.x
                val dy = (targetPos.y + 0.4) - eyePos.y
                val dz = targetPos.z - eyePos.z
                val horizDist = sqrt(dx * dx + dz * dz)

                val baseYaw = ((atan2(dz, dx) * 180.0 / Math.PI).toFloat() - 90.0f)
                val basePitch = (-(atan2(dy, horizDist) * 180.0 / Math.PI).toFloat())

                // Initial humanized jitter offset
                val offsetAngle = Random.nextDouble(0.0, 2.0 * Math.PI)
                val maxAllowedOffset = if (isPreservedPlot || nearbyPests.size > 1) 7.5 else 14.0
                val minAllowedOffset = if (isPreservedPlot || nearbyPests.size > 1) 2.0 else 4.0
                val offsetMag = Random.nextDouble(minAllowedOffset, maxAllowedOffset)
                var currentAimYaw = baseYaw + (offsetMag * cos(offsetAngle)).toFloat()
                var currentAimPitch = (basePitch + (offsetMag * sin(offsetAngle)).toFloat()).coerceIn(-89.0f, 89.0f)

                MouseMovementEngine.rotateTo(client, currentAimYaw, currentAimPitch)

                // Fire Vacuum with ultra-fast 10ms sub-tick loop and live continuous trajectory tracking
                MacroInputController.holdUseItem()

                val fireStartMs = System.currentTimeMillis()
                var lastAimStepMs = fireStartMs
                var lastFlightRepositionMs = fireStartMs
                var pestEliminated = false

                while (System.currentTimeMillis() - fireStartMs < maxFireDurationMs && isRunning && !CentralMovementCoordinator.isAbortTriggered(client)) {
                    delay(10L) // Sub-tick 10ms polling rate

                    // Instant Kill / Despawn Check or pest outside plot bounds: immediately halt any tracking/movement
                    val livePest = targetPest.entity.position()
                    val isDead = PestTargetTracker.isPestDeadOrRemoved(client, targetPest)
                    val isOutOfBounds = !PestTargetTracker.isInsidePlotBounds(livePest, center, 46.0)

                    if (isDead || isOutOfBounds) {
                        pestEliminated = isDead
                        PestTargetTracker.forgetPest(targetPest.entity.uuid)
                        CentralMovementCoordinator.stopNavigation()
                        MouseMovementEngine.resetFlightVelocity()
                        break
                    }

                    val now = System.currentTimeMillis()

                    // Continuous Smooth Camera Tracking at 30ms intervals
                    if (now - lastAimStepMs >= 30L) {
                        lastAimStepMs = now
                        val liveP = client.player ?: break
                        val liveEye = liveP.eyePosition
                        val cdx = livePest.x - liveEye.x
                        val cdy = (livePest.y + 0.4) - liveEye.y
                        val cdz = livePest.z - liveEye.z
                        val cHoriz = sqrt(cdx * cdx + cdz * cdz)
                        val cBaseYaw = ((atan2(cdz, cdx) * 180.0 / Math.PI).toFloat() - 90.0f)
                        val cBasePitch = (-(atan2(cdy, cHoriz) * 180.0 / Math.PI).toFloat()).coerceIn(-89.0f, 89.0f)

                        client.execute {
                            MouseMovementEngine.smoothStepFlight(client, cBaseYaw, cBasePitch, dt = 0.03f)
                        }
                    }

                    // Smooth repositioning if pest drifts out of vacuum range
                    if (now - lastFlightRepositionMs >= 300L) {
                        lastFlightRepositionMs = now
                        val liveP = client.player ?: break
                        val currentDist = liveP.position().distanceTo(livePest)
                        if (currentDist > tierInfo.attackRange + 1.2) {
                            val liveSafePos = PestTargetTracker.findSafeAttackPosition(
                                client,
                                liveP.position(),
                                livePest,
                                nearbyPests,
                                keepPestActive = isPreservedPlot,
                                targetEntityId = targetPest.entity.id,
                                attackRange = tierInfo.attackRange,
                                plotCenter = center,
                                maxPlotOffset = 46.0
                            )

                            // For Locusts & Crickets: if they jump high in the air, don't climb after them
                            val targetFlightY = if (isLocustOrCricket && livePest.y > safeAttackPos.y + 0.8) {
                                safeAttackPos.y
                            } else {
                                liveSafePos.y
                            }

                            CentralMovementCoordinator.flyTo(
                                client,
                                targetX = liveSafePos.x,
                                targetY = targetFlightY,
                                targetZ = liveSafePos.z
                            )
                        }
                    }
                }

                // If preserved plot, release firing immediately and switch to safe non-attack slot (5, 6, 7)
                if (isPreservedPlot) {
                    MacroInputController.releaseUseItem()
                    val randomRestSlot = Random.nextInt(4, 7)
                    if (client.player?.inventory?.selectedSlot != randomRestSlot) {
                        client.execute {
                            client.player?.inventory?.selectedSlot = randomRestSlot
                        }
                    }
                }

                if (pestEliminated) {
                    totalPestsKilled++
                    HypCroMod.logSuccess("Pest eliminated with ${tierInfo.name}! (Total killed this cycle: $totalPestsKilled)")
                    delay(300L)
                } else {
                    HypCroMod.logWarn("Pest kill unconfirmed after ${maxFireDurationMs}ms firing window with ${tierInfo.name}.")
                    delay(150L)
                }
            }

            // Finished all pests in this plot: release vacuum firing before moving to next plot
            MacroInputController.releaseUseItem()
            remainingPlots.remove(targetPlotId)
        }

        // =========================================================================
        // SECTION 6: COMPLETION & RETURN FLOW
        // =========================================================================
        currentState = State.RETURN_GARDEN

        if (!isAutoRun) {
            HypCroMod.logSuccess("Pest Destroyer cycle completed (Manual Run). Halting in place.")
            stop()
        } else {
            if (!isRunning || CentralMovementCoordinator.isAbortTriggered(client)) {
                stop()
                return
            }

            HypCroMod.log("All plots cleared. Warping back to Garden spawn (/warp garden)...")
            CommandHelper.sendCommand(client, "/warp garden")
            delay(1000L) // 1000ms delay for chunk reload

            if (!isRunning || CentralMovementCoordinator.isAbortTriggered(client)) {
                stop()
                return
            }

            val engineToResume = callingEngineInstance
            callingEngineInstance = null
            stop()

            if (engineToResume != null) {
                HypCroMod.log("Resuming ${engineToResume.engineName}...")
                engineToResume.startMacro()
            } else {
                HypCroMod.log("Resuming active farming engine...")
                MacroController.startMacro()
            }
        }
    }
}
