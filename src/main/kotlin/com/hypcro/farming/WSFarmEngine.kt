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
        val detectedCrop = detectFrontCrop(client)
        if (detectedCrop == null) {
            HypCroMod.logWarn("No valid crop detected in front of player!")
            return false
        }

        // 2. Validate and identify farming tool upfront (without switching yet)
        val toolSlot = findBestToolSlot(client, detectedCrop)
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
                // 2.5 Anti-Stuck: Check Flying (if player is airborne or flying, sneak down to ground)
                val grounded = com.hypcro.failsafe.AntiStuckEngine.resolveFlyingState(client)
                if (!grounded) {
                    abortScript("Anti-Stuck: Player could not reach ground safely.")
                    return@launch
                }

                // 3. Check and Align Angles via Mousemat FIRST (before holding tool)
                val curPlayer = client.player
                val currentYaw = curPlayer?.yRot ?: 0f
                val currentPitch = curPlayer?.xRot ?: 0f

                val yawDelta = abs((((currentYaw - targetAngles.first + 180f) % 360f + 360f) % 360f) - 180f)
                val pitchDelta = abs(currentPitch - targetAngles.second)
                // Intended: Strict 0.1 degree precision check before allowing farming loop to proceed
                val anglesMatched = yawDelta < 0.1f && pitchDelta < 0.1f

                if (!anglesMatched) {
                    HypCroMod.log("Aligning angles to Yaw: ${targetAngles.first}, Pitch: ${targetAngles.second} via Squeaky Mousemat...")
                    val aligned = MousematHelper.alignAngles(client, targetAngles.first, targetAngles.second)
                    if (!aligned) {
                        abortScript("Failed to align angles via Squeaky Mousemat")
                        return@launch
                    }
                }

                // 4. Now switch directly to the verified farming tool
                client.execute { client.player?.inventory?.selectedSlot = toolSlot }
                val toolName = client.player?.inventory?.getItem(toolSlot)?.hoverName?.string ?: "Farming Tool"
                delay(200)

                // 5. Start Watchdog with expected tool slot
                HypcroWatchdog.start(toolSlot)

                // 6. Initialize active key & activate main-thread tick loop
                lastStatusLogTime = 0L
                lastPosCheckTime = System.currentTimeMillis()
                macroStartTime = System.currentTimeMillis()
                lastCheckPos = client.player?.position()
                
                client.execute {
                    val inWater = isPlayerFeetInWater(client)
                    currentActiveKey = if (inWater) 'W' else 'S'
                    applyMovementKeys(inWater)
                    
                    HypCroMod.logStartBanner(engineName, detectedCrop.displayName, targetAngles.first, targetAngles.second, toolName)
                    isFarmingActive = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                HypCroMod.log("§c[CRASH] startMacro failed: ${t.javaClass.simpleName} - ${t.message}")
                abortScript("Startup crashed: ${t.message}")
            }
        }
        return true
    }

    private var lastStatusLogTime: Long = 0L
    private var lastPosCheckTime: Long = 0L
    private var lastCheckPos: Vec3? = null
    private var macroStartTime: Long = 0L

    override fun onClientTick(client: Minecraft) {
        try {
            if (!isRunning || !isFarmingActive) return
            val player = client.player ?: return
            val level = client.level ?: return

            // Run Watchdog failsafes on client tick
            HypcroWatchdog.onClientTick(client)
            if (!isRunning || !isFarmingActive) return

            // Maintain attack and movement via MacroInputController
            MacroInputController.attack = true

            val now = System.currentTimeMillis()

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
                            // HypCroMod.log(">> Water transition: inWater=$inWater -> Key=$currentActiveKey at (${String.format("%.1f", player.x)}, ${String.format("%.1f", player.y)}, ${String.format("%.1f", player.z)})")
                        } else {
                            // Scenario B: Stopped moving, but water state is exactly the same
                            // Intended: Only trigger failsafe if configured; full anti-stuck handles broader recovery flows
                            if (ConfigManager.config.generalConfig.watchdog.checkFarmingInterruption) {
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
        farmJob?.cancel()
        farmJob = null
        currentTargetAngles = null

        HypcroWatchdog.stop()
        MacroInputController.releaseAll()
        HypCroMod.logStopBanner(message)
    }

    fun isPlayerFeetInWater(client: Minecraft): Boolean {
        val player = client.player ?: return false
        val level = client.level ?: return false

        if (player.isInWater || player.isUnderWater) return true
        if (player.isEyeInFluid(FluidTags.WATER)) return true

        // Check entity bounding box lower half (feet/legs) for water fluid or water block
        val bb = player.boundingBox
        val minX = kotlin.math.floor(bb.minX).toInt()
        val maxX = kotlin.math.floor(bb.maxX).toInt()
        val minY = kotlin.math.floor(bb.minY).toInt()
        val maxY = kotlin.math.floor(bb.minY + 0.6).toInt()
        val minZ = kotlin.math.floor(bb.minZ).toInt()
        val maxZ = kotlin.math.floor(bb.maxZ).toInt()

        val mutablePos = BlockPos.MutableBlockPos()
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    mutablePos.set(x, y, z)
                    val fluid = level.getFluidState(mutablePos)
                    if (fluid.`is`(FluidTags.WATER) && !fluid.isEmpty) {
                        return true
                    }
                    val blockState = level.getBlockState(mutablePos)
                    if (blockState.block == Blocks.WATER || blockState.block == Blocks.BUBBLE_COLUMN) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun detectFrontCrop(client: Minecraft): CropType? {
        val player = client.player ?: return null
        val level = client.level ?: return null

        val yawRad = Math.toRadians(player.yRot.toDouble())
        val forwardDir = Vec3(-sin(yawRad), 0.0, cos(yawRad)).normalize()
        val footPos = player.position()

        val mutablePos = BlockPos.MutableBlockPos()
        // Scan 1 to 3 blocks forward in horizontal yaw direction
        for (i in 1..3) {
            val checkX = footPos.x + forwardDir.x * i
            val checkY = footPos.y
            val checkZ = footPos.z + forwardDir.z * i

            val blockX = kotlin.math.floor(checkX).toInt()
            val blockY = kotlin.math.floor(checkY).toInt()
            val blockZ = kotlin.math.floor(checkZ).toInt()

            for (dy in -1..1) {
                mutablePos.set(blockX, blockY + dy, blockZ)
                val block = level.getBlockState(mutablePos).block

                when (block) {
                    is CropBlock -> {
                        if (block == Blocks.WHEAT) return CropType.WHEAT
                        if (block == Blocks.CARROTS) return CropType.CARROT
                        if (block == Blocks.POTATOES) return CropType.POTATO
                    }
                    is NetherWartBlock -> return CropType.NETHER_WART
                    is MushroomBlock -> return CropType.MUSHROOM
                }
            }
        }
        return null
    }

    private fun findBestToolSlot(client: Minecraft, crop: CropType): Int? {
        val player = client.player ?: return null
        val prefix = when (crop) {
            CropType.WHEAT -> "THEORETICAL_HOE_WHEAT"
            CropType.CARROT -> "THEORETICAL_HOE_CARROT"
            CropType.POTATO -> "THEORETICAL_HOE_POTATO"
            CropType.NETHER_WART -> "THEORETICAL_HOE_WARTS"
            CropType.MUSHROOM -> "FUNGI_CUTTER"
        }

        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (hasExtraAttrId(stack, prefix)) return slot
        }

        // Fallback: any hoe
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.item.toString().contains("hoe", ignoreCase = true)) return slot
        }
        return null
    }

    private fun hasExtraAttrId(stack: ItemStack, prefix: String): Boolean {
        val customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
        if (customData != null) {
            val nbt = customData.copyTag()
            if (nbt.contains("ExtraAttributes")) {
                val ea = nbt.getCompound("ExtraAttributes").orElse(null)
                val id = ea?.getString("id")?.orElse("") ?: ""
                if (id.startsWith(prefix, ignoreCase = true)) return true
            }
        }
        return false
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
