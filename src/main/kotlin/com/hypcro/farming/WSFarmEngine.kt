package com.hypcro.farming

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.config.CropType
import com.hypcro.failsafe.HypcroWatchdog
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.FluidTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.*
import net.minecraft.world.phys.Vec3
import kotlinx.coroutines.*
import com.hypcro.util.CropBpsTracker
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object WSFarmEngine : IFarmEngine {
    override val engineName: String = "W/S Crop Farming"

    @Volatile
    override var isRunning: Boolean = false
        private set

    @Volatile
    var currentActiveKey: Char = 'S'
        private set

    @Volatile
    var currentFarmedCrop: CropType? = null
        private set

    @Volatile
    override var currentTargetAngles: Pair<Float, Float>? = null
        private set

    @Volatile
    private var farmJob: Job? = null
    private var lastToggleTime: Long = 0L

    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    override var isFarmingActive: Boolean = false
        private set

    override fun startMacro(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < 500) return false // 500ms debounce
        lastToggleTime = now

        if (isRunning) return true
        val client = Minecraft.getInstance()
        val player = client.player ?: return false

        // 1. Raycast horizontal forward blocks based on player yaw
        val detectedCrop = detectCrop(client)
        if (detectedCrop == null) {
            HypCroMod.logWarn("No valid crop detected in front of player!")
            return false
        }

        // 2. Validate and identify farming tool upfront (without switching yet)
        val toolSlot = com.hypcro.util.SkyBlockItemHelper.findToolSlot(client, detectedCrop)
        if (toolSlot == null) {
            HypCroMod.logWarn("Missing farming tool for ${detectedCrop.displayName} on hotbar!")
            return false
        }
        val selectedToolName = player.inventory.getItem(toolSlot).hoverName.string
        HypCroMod.log("Selected tool: §f$selectedToolName §7for §f${detectedCrop.displayName}")

        val targetAngles = getTargetAngles(detectedCrop)
        currentTargetAngles = targetAngles

        isRunning = true
        isFarmingActive = false

        farmJob = engineScope.launch {
            try {
                if (!isRunning) return@launch

                // 2.5 Anti-Stuck: Check Flying (if player is airborne or flying, sneak down to ground)
                val grounded = com.hypcro.failsafe.AntiStuckEngine.resolveFlyingState(client)
                if (!isRunning) return@launch
                if (!grounded) {
                    abortScript("Anti-Stuck: Player could not reach ground safely.")
                    return@launch
                }

                // 3. Check and Align Angles via Mousemat FIRST (before holding tool)
                var currentYaw = 0f
                var currentPitch = 0f
                client.execute {
                    val curPlayer = client.player
                    currentYaw = curPlayer?.yRot ?: 0f
                    currentPitch = curPlayer?.xRot ?: 0f
                }
                // Allow client tick to capture orientation if needed
                delay(50)
                if (!isRunning) return@launch

                val anglesMatched = com.hypcro.util.AngleUtils.areAnglesClose(
                    currentYaw, currentPitch, targetAngles.first, targetAngles.second, tolerance = 0.1f
                )

                if (!anglesMatched) {
                    HypCroMod.log("Aligning angles to Yaw: ${targetAngles.first}, Pitch: ${targetAngles.second} via Squeaky Mousemat...")
                    val aligned = MousematHelper.alignAngles(client, targetAngles.first, targetAngles.second)
                    if (!isRunning) return@launch
                    if (!aligned) {
                        abortScript("Failed to align angles via Squeaky Mousemat")
                        return@launch
                    }
                }

                if (!isRunning) return@launch

                // 4. Now switch directly to the verified farming tool
                var toolName = "Farming Tool"
                client.execute {
                    if (!isRunning) return@execute
                    client.player?.inventory?.selectedSlot = toolSlot
                    toolName = client.player?.inventory?.getItem(toolSlot)?.hoverName?.string ?: "Farming Tool"
                }
                delay(200)
                if (!isRunning) return@launch

                // 5. Start Watchdog with expected tool slot on client thread
                client.execute {
                    if (!isRunning) return@execute
                    HypcroWatchdog.start(toolSlot)
                }

                // 6. Initialize active key & activate main-thread tick loop
                lastStatusLogTime = 0L
                lastPosCheckTime = System.currentTimeMillis()
                macroStartTime = System.currentTimeMillis()
                
                client.execute {
                    if (!isRunning) return@execute
                    lastCheckPos = client.player?.position()
                    val inWater = isPlayerFeetInWater(client)
                    currentActiveKey = if (inWater) 'W' else 'S'
                    currentFarmedCrop = detectedCrop
                    CropBpsTracker.startOrResumeSession(detectedCrop)
                    applyMovementKeys(inWater)
                    
                    HypCroMod.logStartBanner(engineName, detectedCrop.displayName, targetAngles.first, targetAngles.second, toolName)
                    isFarmingActive = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (isRunning) {
                    HypCroMod.log("§c[CRASH] startMacro failed: ${t.javaClass.simpleName} - ${t.message}")
                    abortScript("Startup crashed: ${t.message}")
                }
            }
        }
        return true
    }

    private var lastStatusLogTime: Long = 0L
    private var lastPosCheckTime: Long = 0L
    private var lastPestCheckTime: Long = 0L
    private var lastCheckPos: Vec3? = null
    private var macroStartTime: Long = 0L
    @Volatile
    private var lastTurnTime: Long = 0L

    fun isTurningOrRecovering(): Boolean = (System.currentTimeMillis() - lastTurnTime < 800L)

    override fun onClientTick(client: Minecraft) {
        try {
            if (!isRunning || !isFarmingActive) return
            val player = client.player ?: return
            val level = client.level ?: return
            // Run Watchdog failsafes on client tick
            HypcroWatchdog.onClientTick(client)
            if (!isRunning || !isFarmingActive) return

            val now = System.currentTimeMillis()

            // Automatic Pest Destroyer activation check (evaluated every 1000ms / 1s)
            if (ConfigManager.config.autoActivePest && now - lastPestCheckTime >= 1000L) {
                lastPestCheckTime = now
                val scbInfo = com.hypcro.pest.PestTabReader.scanScoreboardPests(client)
                val tabInfo = com.hypcro.pest.PestTabReader.scanPests(client)
                val pestCount = kotlin.math.max(scbInfo.aliveCount, tabInfo.aliveCount)
                val threshold = ConfigManager.config.pestTriggerCount

                if (pestCount >= threshold && !com.hypcro.pest.PestDestroyerEngine.isRunning) {
                    HypCroMod.log("Auto Pest Trigger activated ($pestCount pests >= $threshold threshold). Pausing farm and starting Pest Destroyer...")
                    stopMacro(reason = "Auto Pest Sweep")
                    com.hypcro.pest.PestDestroyerEngine.startPestDestroyer(
                        source = com.hypcro.pest.PestCallerSource.WS_FARM_ENGINE,
                        callingEngine = this
                    )
                    return
                }
            }

            // Maintain attack and movement via MacroInputController
            MacroInputController.attack = true

            // 200ms positional check loop
            if (now - lastPosCheckTime >= 200L) {
                val currentPos = player.position()
                val previousPos = lastCheckPos
                
                if (previousPos != null && now - macroStartTime > 1000L) { // Give 1 second to accelerate
                    val dist = previousPos.distanceTo(currentPos)
                    // If the player moved less than 0.05 blocks in 200ms, they have hit a wall and stopped moving
                    if (dist < 0.05) {
                        val inWater = isPlayerFeetInWater(client)
                        val neededKey = if (inWater) 'W' else 'S'
                        
                        if (neededKey != currentActiveKey) {
                            // Scenario A: Water state dictates a different key -> Switch!
                            currentActiveKey = neededKey
                            lastTurnTime = System.currentTimeMillis()
                            // HypCroMod.log(">> Water transition: inWater=$inWater -> Key=$currentActiveKey at (${String.format("%.1f", player.x)}, ${String.format("%.1f", player.y)}, ${String.format("%.1f", player.z)})")
                        } else {
                            // Scenario B: Stopped moving, but water state is exactly the same
                            // Intended: Only trigger failsafe if configured; full anti-stuck handles broader recovery flows
                            // Skip while turning/re-accelerating to avoid false alarm right after a W/S key switch
                            if (ConfigManager.config.generalConfig.watchdog.checkFarmingInterruption && !isTurningOrRecovering()) {
                                HypcroWatchdog.potentialStaffCheck("Farming Interruption")
                                return
                            }
                        }
                    }
                }
                
                lastCheckPos = currentPos
                lastPosCheckTime = now
            }

            // Apply movement keys to central MacroInputController
            if (currentActiveKey == 'W') {
                MacroInputController.holdW()
                client.options.keyUp.setDown(true)
                client.options.keyDown.setDown(false)
            } else {
                MacroInputController.holdS()
                client.options.keyUp.setDown(false)
                client.options.keyDown.setDown(true)
            }

            if (now - lastStatusLogTime >= 3000L) { // Every 3 seconds real time
                lastStatusLogTime = now
                // val inWater = isPlayerFeetInWater(client)
                // HypCroMod.log("[Farming] key=$currentActiveKey | inWater=$inWater | pos=(${String.format("%.1f", player.x)}, ${String.format("%.1f", player.y)}, ${String.format("%.1f", player.z)})")
            }
        } catch (t: Throwable) {
            HypCroMod.log("§c[CRASH] onClientTick failed: ${t.javaClass.simpleName} - ${t.message}")
            abortScript("Tick crashed due to internal error.")
        }
    }

    override fun stopMacro(reason: String) {
        if (!isRunning) return
        isRunning = false
        isFarmingActive = false
        currentFarmedCrop = null
        CropBpsTracker.pauseSession()
        farmJob?.cancel()
        farmJob = null
        currentTargetAngles = null

        HypcroWatchdog.stop()
        MacroInputController.releaseAll()
        HypCroMod.logStopBanner(reason)
    }

    override fun abortScript(message: String) {
        isRunning = false
        isFarmingActive = false
        currentFarmedCrop = null
        CropBpsTracker.pauseSession()
        farmJob?.cancel()
        farmJob = null
        currentTargetAngles = null

        HypcroWatchdog.stop()
        MacroInputController.releaseAll()
        HypCroMod.logStopBanner(message)
    }

    fun isPlayerFeetInWater(client: Minecraft): Boolean {
        return FarmEngineHelper.isPlayerFeetInWater(client)
    }

    override fun detectCrop(client: Minecraft): CropType? {
        return FarmEngineHelper.detectFrontCrop(client)
    }



    private fun getTargetAngles(crop: CropType): Pair<Float, Float> {
        val ws = ConfigManager.config.wsConfig
        val setting = ws.crops[crop.name]
        return if (setting != null && setting.useCustomAngles) {
            Pair(setting.yaw, setting.pitch)
        } else {
            Pair(ws.globalAngles.yaw, ws.globalAngles.pitch)
        }
    }

    private fun applyMovementKeys(inWater: Boolean) {
        MacroInputController.holdAttack()
        if (inWater) {
            MacroInputController.holdW()
        } else {
            MacroInputController.holdS()
        }
    }
}
