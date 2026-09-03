package com.hypcro.experiment

import com.hypcro.HypCroMod
import com.hypcro.config.ConfigManager
import com.hypcro.config.ExperimentSpeed
import com.hypcro.mixins.AccessorAbstractContainerScreen
import com.hypcro.movement.MouseMovementEngine
import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.sqrt

object AutoExperimentAddons {

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var currentStatusText: String = "Inactive"
        private set

    private var activeJob: Job? = null

    // Track state of completed add-ons in this run
    private var isChronomatronDone: Boolean = false
    private var isUltrasequencerDone: Boolean = false

    // Dynamic max rounds calculation from Stakes lore (accounting for Metaphysical Serum)
    private var chronoTargetRounds: Int = 12
    private var ultraTargetRounds: Int = 10
    private var isChronoTargetReached: Boolean = false
    private var isUltraTargetReached: Boolean = false
    private val roundsNeededRegex = Regex("""(?:Chain|Series|Sequence)\s*(?:of)?\s*(\d+):""", RegexOption.IGNORE_CASE)

    // Chronomatron sequence tracking
    private val chronoMasterSequence: MutableList<Int> = mutableListOf()
    private var chronoRoundShowIndex: Int = 0
    private var chronoCurrentFlashedSlot: Int = -1
    private var chronoHasClickedThisRound: Boolean = false

    // Ultrasequencer sequence tracking (Dye number -> slot index)
    private val ultrasequencerSequence: MutableMap<Int, Int> = mutableMapOf()
    private var ultraHasClickedThisRound: Boolean = false

    // 3-second watchdog for main menu
    private var mainMenuStartTimeMs: Long = 0L

    fun start() {
        if (isRunning) return

        val client = Minecraft.getInstance()
        if (client.player == null || client.level == null) {
            HypCroMod.logWarn("Auto Experiment Table: Player or level not loaded.")
            return
        }

        isRunning = true
        isChronomatronDone = false
        isUltrasequencerDone = false
        isChronoTargetReached = false
        isUltraTargetReached = false
        chronoMasterSequence.clear()
        chronoRoundShowIndex = 0
        chronoCurrentFlashedSlot = -1
        chronoHasClickedThisRound = false
        ultrasequencerSequence.clear()
        ultraHasClickedThisRound = false
        mainMenuStartTimeMs = 0L
        currentStatusText = "Scanning Table"

        HypCroMod.logSuccess("Started Auto Experiment Table Add-ons.")

        // Launch solver loop on background coroutine with client thread synchronization
        val job = CoroutineScope(Dispatchers.Default).launch {
            try {
                runLoop(client)
            } catch (e: CancellationException) {
                // Clean cancellation on user abort or GUI close
            } catch (e: Exception) {
                HypCroMod.logWarn("Auto Experiment Table error: ${e.message}")
            } finally {
                withContext(NonCancellable) {
                    // Guard against newer run overriding activeJob
                    if (activeJob == coroutineContext[Job]) {
                        isRunning = false
                        currentStatusText = "Inactive"
                        activeJob = null
                    }
                }
            }
        }
        activeJob = job
    }

    fun stop(reason: String = "Manual") {
        if (!isRunning) return
        isRunning = false
        activeJob?.cancel()
        activeJob = null
        mainMenuStartTimeMs = 0L
        currentStatusText = "Inactive"
        HypCroMod.logWarn("Stopped Auto Experiment Table Add-ons ($reason).")
    }

    private suspend fun CoroutineScope.runLoop(client: Minecraft) {
        // Step 1: Open table if not already open
        if (client.gui.screen() !is AbstractContainerScreen<*>) {
            val opened = locateAndOpenTable(client)
            if (!opened) {
                stop("No Table in Reach")
                return
            }
            // Wait for container to open
            var waitTicks = 0
            while (client.gui.screen() !is AbstractContainerScreen<*> && waitTicks < 25) {
                delay(50L)
                waitTicks++
            }
            if (client.gui.screen() !is AbstractContainerScreen<*>) {
                HypCroMod.logWarn("Auto Experiment Table: Container failed to open.")
                stop("Open Failed")
                return
            }
        }

        delay(randomDelay(250, 450))

        // Main execution state machine
        while (isRunning && isActive) {
            var screen = client.gui.screen() as? AbstractContainerScreen<*>
            if (screen == null) {
                // If we haven't finished both add-ons, try to reopen the Experimentation table!
                if (!isChronomatronDone || !isUltrasequencerDone) {
                    currentStatusText = "Reopening Table"
                    delay(350L)
                    val reopened = locateAndOpenTable(client)
                    if (reopened) {
                        var waitTicks = 0
                        while (client.gui.screen() !is AbstractContainerScreen<*> && waitTicks < 25 && isRunning) {
                            delay(50L)
                            waitTicks++
                        }
                        screen = client.gui.screen() as? AbstractContainerScreen<*>
                    }
                }

                if (screen == null) {
                    if (isChronomatronDone && isUltrasequencerDone) {
                        finishAllAddons(client)
                    } else {
                        stop("Container Closed")
                    }
                    return
                }
            }

            val title = screen.title.string.trim()

            when {
                // 1. Main Experimentation Table menu
                title.contains("Experimentation Table", ignoreCase = true) -> {
                    currentStatusText = "Main Menu"
                    handleMainMenu(client, screen)
                }

                // 2. Stakes selection menus (e.g. "Chronomatron ➜ Stakes" or "Ultrasequencer ➜ Stakes")
                title.contains("Stakes", ignoreCase = true) || title.contains("➜", ignoreCase = true) || title.contains("->", ignoreCase = true) -> {
                    currentStatusText = "Selecting Stakes"
                    handleStakesMenu(client, screen)
                }

                // 3. Rewards / Experiment Over screens
                title.contains("Experiment Over", ignoreCase = true) ||
                title.contains("Superpairs Rewards", ignoreCase = true) ||
                title.contains("Chronomatron Rewards", ignoreCase = true) ||
                title.contains("Ultrasequencer Rewards", ignoreCase = true) -> {
                    currentStatusText = "Claiming Rewards"
                    handleRewardsScreen(client, screen)
                }

                // 4. Chronomatron Minigame screen (e.g. "Chronomatron (Metaphysical)")
                title.startsWith("Chronomatron", ignoreCase = true) -> {
                    currentStatusText = "Playing Chronomatron"
                    handleChronomatron(client, screen)
                }

                // 5. Ultrasequencer Minigame screen (e.g. "Ultrasequencer (Metaphysical)")
                title.startsWith("Ultrasequencer", ignoreCase = true) -> {
                    currentStatusText = "Playing Ultrasequencer"
                    handleUltrasequencer(client, screen)
                }

                else -> {
                    delay(50L)
                }
            }

            delay(20L)
        }
    }

    private suspend fun locateAndOpenTable(client: Minecraft): Boolean {
        val player = client.player ?: return false
        val level = client.level ?: return false
        val eyePos = player.eyePosition

        var bestTargetPos: Vec3? = null
        var bestTargetEntity: ArmorStand? = null
        var bestTargetBlockPos: BlockPos? = null
        var minDistanceSq = Double.MAX_VALUE

        // 1. Search for ArmorStand wearing Experimentation Table skull within 4.5 blocks
        val nearbyEntities = level.getEntities(player, player.boundingBox.inflate(4.5))
        for (entity in nearbyEntities) {
            if (entity is ArmorStand && !entity.isRemoved) {
                val headItem = entity.getItemBySlot(EquipmentSlot.HEAD)
                val customName = entity.customName?.string ?: ""
                val isExperimentSkull = headItem.item == Items.PLAYER_HEAD ||
                        headItem.item == Items.ENCHANTING_TABLE ||
                        customName.contains("Experiment", ignoreCase = true) ||
                        customName.contains("Table", ignoreCase = true)

                if (isExperimentSkull) {
                    val entityCenter = entity.position().add(0.0, 1.2, 0.0)
                    val distSq = eyePos.distanceToSqr(entityCenter)
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq
                        bestTargetPos = entityCenter
                        bestTargetEntity = entity
                    }
                }
            }
        }

        // 2. Search for Enchanting Table block within 4.5 blocks if no ArmorStand matched
        if (bestTargetEntity == null) {
            val centerBlock = player.blockPosition()
            val radius = 4
            for (dx in -radius..radius) {
                for (dy in -2..2) {
                    for (dz in -radius..radius) {
                        val pos = centerBlock.offset(dx, dy, dz)
                        val state = level.getBlockState(pos)
                        if (state.`is`(Blocks.ENCHANTING_TABLE)) {
                            val blockCenter = Vec3(pos.x + 0.5, pos.y + 0.75, pos.z + 0.5)
                            val distSq = eyePos.distanceToSqr(blockCenter)
                            if (distSq < minDistanceSq) {
                                minDistanceSq = distSq
                                bestTargetPos = blockCenter
                                bestTargetBlockPos = pos
                            }
                        }
                    }
                }
            }
        }

        if (bestTargetPos == null) {
            HypCroMod.logWarn("Auto Experiment Table: No Experimentation Table found within 4.5 blocks.")
            return false
        }

        // 3. Calculate target Yaw and Pitch
        val diff = bestTargetPos.subtract(eyePos)
        val horizontalDist = sqrt(diff.x * diff.x + diff.z * diff.z)
        val targetYaw = Mth.wrapDegrees((atan2(-diff.x, diff.z) * (180.0 / Math.PI)).toFloat())
        val targetPitch = Mth.clamp((atan2(-diff.y, horizontalDist) * (180.0 / Math.PI)).toFloat(), -90.0f, 90.0f)

        // 4. Smooth camera rotation toward table
        MouseMovementEngine.rotateTo(client, targetYaw, targetPitch, customDurationMs = 280L)
        delay(80L)

        // 5. Interact using Minecraft 26.2 entity interact / useItemOn
        client.execute {
            val gameMode = client.gameMode
            if (gameMode == null) {
                HypCroMod.logWarn("Auto Experiment Table: GameMode is null during table interaction.")
                return@execute
            }
            if (bestTargetEntity != null) {
                val entityHit = net.minecraft.world.phys.EntityHitResult(bestTargetEntity)
                gameMode.interact(player, bestTargetEntity, entityHit, InteractionHand.MAIN_HAND)
            } else if (bestTargetBlockPos != null) {
                val hitResult = BlockHitResult(bestTargetPos, Direction.UP, bestTargetBlockPos, false)
                gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            }
        }

        return true
    }

    private suspend fun handleMainMenu(client: Minecraft, screen: AbstractContainerScreen<*>) {
        val now = System.currentTimeMillis()
        if (mainMenuStartTimeMs == 0L) {
            mainMenuStartTimeMs = now
        }

        delay(randomDelay(200, 350))

        val slot11 = getSlotItem(screen, 11)
        val slot15 = getSlotItem(screen, 15)

        // 1. Guard against empty slots while container contents are still arriving from the server
        if ((slot11.isEmpty || slot11.item == Items.AIR) && (slot15.isEmpty || slot15.item == Items.AIR)) {
            if (now - mainMenuStartTimeMs >= 3000L) {
                HypCroMod.logWarn("Auto Experiment Table Timeout (3s): Main menu slots 11 and 15 are empty/Air.")
                mainMenuStartTimeMs = 0L
                stop("Main Menu Timeout")
            }
            delay(50L)
            return
        }

        // 2. Chronomatron (Slot 11 or scan for Chronomatron)
        if (!isChronomatronDone) {
            val chronoSlot = if (com.hypcro.util.GardenStateReader.stripColor(slot11.hoverName.string).contains("Chronomatron", ignoreCase = true)) {
                11
            } else {
                (0 until screen.menu.slots.size).firstOrNull {
                    com.hypcro.util.GardenStateReader.stripColor(getSlotItem(screen, it).hoverName.string).contains("Chronomatron", ignoreCase = true)
                } ?: 11
            }

            val item = getSlotItem(screen, chronoSlot)
            if (!item.isEmpty && item.item != Items.AIR) {
                val loreLines = getLoreLines(item).map { com.hypcro.util.GardenStateReader.stripColor(it) }
                val canPlay = loreLines.any { it.contains("Click to play", ignoreCase = true) }

                if (canPlay) {
                    mainMenuStartTimeMs = 0L
                    clickSlot(screen, chronoSlot)
                    delay(randomDelay(350, 600))
                    return
                } else if (item.item == Items.BARRIER || isAddonFinishedOrOnCooldown(item, loreLines)) {
                    isChronomatronDone = true
                }
            }
        }

        // 3. Ultrasequencer (Slot 15 or scan for Ultrasequencer)
        if (!isUltrasequencerDone) {
            val ultraSlot = if (com.hypcro.util.GardenStateReader.stripColor(slot15.hoverName.string).contains("Ultrasequencer", ignoreCase = true)) {
                15
            } else {
                (0 until screen.menu.slots.size).firstOrNull {
                    com.hypcro.util.GardenStateReader.stripColor(getSlotItem(screen, it).hoverName.string).contains("Ultrasequencer", ignoreCase = true)
                } ?: 15
            }

            val item = getSlotItem(screen, ultraSlot)
            if (!item.isEmpty && item.item != Items.AIR) {
                val loreLines = getLoreLines(item).map { com.hypcro.util.GardenStateReader.stripColor(it) }
                val canPlay = loreLines.any { it.contains("Click to play", ignoreCase = true) }

                if (canPlay) {
                    mainMenuStartTimeMs = 0L
                    clickSlot(screen, ultraSlot)
                    delay(randomDelay(350, 600))
                    return
                } else if (item.item == Items.BARRIER || isAddonFinishedOrOnCooldown(item, loreLines)) {
                    isUltrasequencerDone = true
                }
            }
        }

        // 4. Both Add-ons Completed!
        if (isChronomatronDone && isUltrasequencerDone) {
            mainMenuStartTimeMs = 0L
            finishAllAddons(client)
            return
        }

        // 5. 3-Second Diagnostic Timeout: print exact slot contents and lore if stuck
        if (now - mainMenuStartTimeMs >= 3000L) {
            val item11 = getSlotItem(screen, 11)
            val item15 = getSlotItem(screen, 15)
            val lore11 = getLoreLines(item11).map { com.hypcro.util.GardenStateReader.stripColor(it) }
            val lore15 = getLoreLines(item15).map { com.hypcro.util.GardenStateReader.stripColor(it) }

            HypCroMod.logWarn("Auto Experiment Table Diagnostic (Stuck in Main Menu > 3s):")
            HypCroMod.logWarn("Slot 11: name='${item11.hoverName.string}', item=${item11.item}, lore=${lore11.joinToString(" | ")}")
            HypCroMod.logWarn("Slot 15: name='${item15.hoverName.string}', item=${item15.item}, lore=${lore15.joinToString(" | ")}")
            mainMenuStartTimeMs = 0L
            stop("Main Menu Stuck (Diagnostic Printed)")
        }
    }

    private suspend fun handleStakesMenu(client: Minecraft, screen: AbstractContainerScreen<*>) {
        delay(randomDelay(300, 500))

        // Hypixel Stakes Tiers in descending order of priority
        val tierOrder = listOf("Metaphysical", "Transcendent", "Supreme", "Grand", "High", "Beginner")

        val isUltraStakes = screen.title.string.contains("Ultrasequencer", ignoreCase = true)
        var bestSlot: Int? = null
        var bestTierIndex = Int.MAX_VALUE
        var detectedRounds: Int = if (isUltraStakes) 10 else 12

        // Scan all container slots for available stakes
        for (slotIdx in 0 until screen.menu.slots.size) {
            val item = getSlotItem(screen, slotIdx)
            if (item.isEmpty || item.item == Items.AIR || item.item == Items.STAINED_GLASS_PANE.gray() || item.item == Items.BARRIER) continue

            val cleanName = com.hypcro.util.GardenStateReader.stripColor(item.hoverName.string)
            val loreLines = getLoreLines(item).map { com.hypcro.util.GardenStateReader.stripColor(it) }

            // Skip practice mode
            if (cleanName.contains("Practice", ignoreCase = true) || loreLines.any { it.contains("Practice mode has no rewards", ignoreCase = true) }) {
                continue
            }

            // Skip if locked due to level or experience
            if (loreLines.any { it.contains("Enchanting level too low!", ignoreCase = true) || it.contains("Not enough experience!", ignoreCase = true) }) {
                continue
            }

            // Check tier rank
            for ((index, tierName) in tierOrder.withIndex()) {
                if (cleanName.contains(tierName, ignoreCase = true) || loreLines.any { it.contains(tierName, ignoreCase = true) }) {
                    if (index < bestTierIndex) {
                        bestTierIndex = index
                        bestSlot = slotIdx

                        // Extract rounds needed from lore
                        val match = loreLines.asReversed().firstNotNullOfOrNull { line ->
                            roundsNeededRegex.find(line)
                        }
                        if (match != null) {
                            val num = match.groups[1]?.value?.toIntOrNull()
                            if (num != null) detectedRounds = num
                        }
                    }
                    break
                }
            }
        }

        if (isUltraStakes) {
            ultraTargetRounds = detectedRounds
        } else {
            chronoTargetRounds = detectedRounds
        }

        if (bestSlot != null) {
            clickSlot(screen, bestSlot)
            // Wait for screen to transition away from Stakes menu to avoid missing the first note flash
            var waitCount = 0
            while (client.gui.screen() === screen && waitCount < 20 && isRunning) {
                delay(40L)
                waitCount++
            }
            delay(randomDelay(80, 160))
        } else {
            // Fallback: click any slot with "Click to play!"
            val fallbackSlot = (0 until screen.menu.slots.size).firstOrNull { slotIdx ->
                val item = getSlotItem(screen, slotIdx)
                val lore = getLoreLines(item).map { com.hypcro.util.GardenStateReader.stripColor(it) }
                lore.any { it.contains("Click to play!", ignoreCase = true) }
            }

            if (fallbackSlot != null) {
                clickSlot(screen, fallbackSlot)
                var waitCount = 0
                while (client.gui.screen() === screen && waitCount < 20 && isRunning) {
                    delay(40L)
                    waitCount++
                }
                delay(randomDelay(80, 160))
            } else {
                HypCroMod.logWarn("Auto Experiment Table: Not enough XP or bottles for available Stakes tiers!")
                client.execute {
                    client.player?.closeContainer()
                }
                stop("Not Enough XP")
            }
        }
    }

    private suspend fun handleChronomatron(client: Minecraft, screen: AbstractContainerScreen<*>) {
        val config = ConfigManager.config.experimentAddons

        val phaseItem = getSlotItem(screen, 49)
        val phaseName = com.hypcro.util.GardenStateReader.stripColor(phaseItem.hoverName.string)

        val isReadPhase = phaseName.contains("Remember the pattern", ignoreCase = true)
        val isReplicatePhase = phaseName.contains("Timer:", ignoreCase = true) || phaseName.contains("Repeat", ignoreCase = true)

        if (isReadPhase) {
            chronoHasClickedThisRound = false

            // Scan slots 10..43 on the board for enchantment glint (active flashing note)
            var activeFoilSlot: Int? = null
            for (slotIdx in 10..43) {
                val item = getSlotItem(screen, slotIdx)
                if (!item.isEmpty && item.item != Items.AIR && item.hasFoil()) {
                    activeFoilSlot = slotIdx
                    break
                }
            }

            if (activeFoilSlot != null) {
                // If this is a new flash that just turned on
                if (chronoCurrentFlashedSlot == -1) {
                    if (chronoMasterSequence.size <= chronoRoundShowIndex) {
                        // New note at the end of the chain
                        chronoMasterSequence.add(activeFoilSlot)
                    } else {
                        // Replayed note in the chain
                        chronoRoundShowIndex++
                    }
                    chronoCurrentFlashedSlot = activeFoilSlot
                }
            } else {
                // No slot currently has foil (transition frame between notes)
                if (chronoCurrentFlashedSlot != -1) {
                    val prevItem = getSlotItem(screen, chronoCurrentFlashedSlot)
                    if (prevItem.isEmpty || !prevItem.hasFoil()) {
                        chronoCurrentFlashedSlot = -1
                    }
                }
            }

            delay(20L)
            return
        }

        if (isReplicatePhase) {
            if (chronoHasClickedThisRound) {
                delay(30L)
                return
            }

            chronoHasClickedThisRound = true
            chronoRoundShowIndex = 0
            chronoCurrentFlashedSlot = -1

            // Initial humanized hesitation before first click
            delay(randomDelay(220, 320))

            // Click recorded slot sequence
            if (chronoMasterSequence.isNotEmpty()) {
                val speedDelay = getClickSpeedDelay(config.speed)
                for (slotIdx in chronoMasterSequence) {
                    if (!isRunning || client.gui.screen() !== screen) break

                    clickSlot(screen, slotIdx)
                    // Guarantee humanized delay after every step
                    delay(speedDelay + randomDelay(20, 40))
                }

                val completedRound = chronoMasterSequence.size

                // Track whether target rounds were achieved
                if (completedRound >= chronoTargetRounds) {
                    isChronoTargetReached = true
                    if (!config.maximizeXp) {
                        delay(randomDelay(350, 450))
                        client.execute {
                            client.player?.closeContainer()
                        }
                        isChronomatronDone = true
                        delay(randomDelay(300, 500))
                        return
                    }
                }

                delay(randomDelay(150, 250))
            }
        }
    }

    private fun isUltrasequencerDye(stack: ItemStack): Boolean {
        if (stack.isEmpty || stack.item == Items.AIR) return false
        val item = stack.item
        if (item is net.minecraft.world.item.DyeItem) return true
        if (item == Items.INK_SAC || item == Items.BONE_MEAL || item == Items.LAPIS_LAZULI || item == Items.COCOA_BEANS) return true
        val itemId = item.toString().lowercase()
        return itemId.contains("dye")
    }

    private fun getUltrasequencerOrderNumber(stack: ItemStack): Int? {
        if (!isUltrasequencerDye(stack)) return null
        val cleanName = com.hypcro.util.GardenStateReader.stripColor(stack.hoverName.string).trim()
        val numFromName = cleanName.toIntOrNull()
        if (numFromName != null && numFromName > 0) return numFromName
        if (stack.count > 0) return stack.count
        return null
    }

    private suspend fun handleUltrasequencer(client: Minecraft, screen: AbstractContainerScreen<*>) {
        val config = ConfigManager.config.experimentAddons

        val phaseItem = getSlotItem(screen, 49)
        val phaseName = com.hypcro.util.GardenStateReader.stripColor(phaseItem.hoverName.string)

        val isReadPhase = phaseName.contains("Remember the pattern", ignoreCase = true)
        val isReplicatePhase = phaseName.contains("Timer:", ignoreCase = true) || phaseName.contains("Repeat", ignoreCase = true)

        if (isReadPhase) {
            // When transitioning back to read phase from replicate, reset sequence for new round
            if (ultraHasClickedThisRound) {
                ultrasequencerSequence.clear()
                ultraHasClickedThisRound = false
            }

            // Display phase: scan dye items with numbers across grid slots 9..44
            for (slotIdx in 9..44) {
                val item = getSlotItem(screen, slotIdx)
                val orderNum = getUltrasequencerOrderNumber(item)
                if (orderNum != null) {
                    ultrasequencerSequence[orderNum] = slotIdx
                }
            }
            delay(20L)
            return
        }

        if (isReplicatePhase) {
            if (ultraHasClickedThisRound) {
                delay(30L)
                return
            }

            ultraHasClickedThisRound = true

            // Initial hesitation before first click of replicate phase
            delay(randomDelay(250, 350))

            // Click slots in ascending order 1, 2, 3...
            if (ultrasequencerSequence.isNotEmpty()) {
                val sortedSlots = ultrasequencerSequence.toSortedMap().values.toList()
                val completedRound = sortedSlots.size
                val speedDelay = getClickSpeedDelay(config.speed)

                for (slotIdx in sortedSlots) {
                    if (!isRunning || client.gui.screen() !== screen) break
                    clickSlot(screen, slotIdx)
                    delay(speedDelay + randomDelay(20, 40))
                }

                // Track whether target rounds were achieved
                if (completedRound >= ultraTargetRounds) {
                    isUltraTargetReached = true
                    if (!config.maximizeXp) {
                        delay(randomDelay(350, 450))
                        client.execute {
                            client.player?.closeContainer()
                        }
                        isUltrasequencerDone = true
                        delay(randomDelay(300, 500))
                        return
                    }
                }

                delay(randomDelay(150, 250))
            }
        }
    }

    private suspend fun handleRewardsScreen(client: Minecraft, screen: AbstractContainerScreen<*>) {
        delay(randomDelay(350, 600))

        val title = screen.title.string.trim()
        val isChrono = title.contains("Chronomatron", ignoreCase = true)
        val isUltra = title.contains("Ultrasequencer", ignoreCase = true)

        // Safety Guard: Stop macro only if the active minigame ended prematurely before target rounds was reached
        if (isChrono && !isChronomatronDone && !isChronoTargetReached) {
            HypCroMod.logWarn("Auto Experiment Table: Chronomatron ended early before target rounds. Auto-claim halted to prevent lost clicks!")
            stop("Early End Protection")
            return
        }
        if (isUltra && !isUltrasequencerDone && !isUltraTargetReached) {
            HypCroMod.logWarn("Auto Experiment Table: Ultrasequencer ended early before target rounds. Auto-claim halted to prevent lost clicks!")
            stop("Early End Protection")
            return
        }

        // Claim rewards (typically at slot 11, slot 13, or slot labeled Claim)
        var claimed = false
        val claimItem11 = getSlotItem(screen, 11)
        val claimItem13 = getSlotItem(screen, 13)

        if (!claimItem11.isEmpty && (claimItem11.hoverName.string.contains("Claim", ignoreCase = true) || claimItem11.hoverName.string.contains("Chronomatron", ignoreCase = true))) {
            clickSlot(screen, 11)
            claimed = true
        } else if (!claimItem13.isEmpty && (claimItem13.hoverName.string.contains("Claim", ignoreCase = true) || claimItem13.hoverName.string.contains("Ultrasequencer", ignoreCase = true))) {
            clickSlot(screen, 13)
            claimed = true
        } else {
            // Fallback: click any slot labeled Claim
            for (i in 0 until screen.menu.slots.size) {
                val item = getSlotItem(screen, i)
                val cleanName = com.hypcro.util.GardenStateReader.stripColor(item.hoverName.string)
                val loreLines = getLoreLines(item).map { com.hypcro.util.GardenStateReader.stripColor(it) }
                if (cleanName.contains("Claim", ignoreCase = true) || loreLines.any { it.contains("Click to claim", ignoreCase = true) }) {
                    clickSlot(screen, i)
                    claimed = true
                    break
                }
            }
        }

        if (claimed) {
            if (isChrono) {
                isChronomatronDone = true
            } else if (isUltra) {
                isUltrasequencerDone = true
            }
        }

        // Humanized pause between minigames (350ms to 700ms)
        delay(randomDelay(350, 700))

        if (isChronomatronDone && isUltrasequencerDone) {
            finishAllAddons(client)
        }
    }

    private suspend fun finishAllAddons(client: Minecraft) {
        currentStatusText = "Finished"
        isRunning = false

        // 1. Close all container screens
        client.execute {
            client.player?.closeContainer()
        }

        HypCroMod.logSuccess("Completed both Chronomatron and Ultrasequencer!")

        // 2. Play 3 loud bell chimes (100ms apart at 1.5 volume)
        repeat(3) {
            client.execute {
                client.soundManager.play(
                    SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.5f)
                )
            }
            delay(100L)
        }
    }

    private fun getSlotItem(screen: AbstractContainerScreen<*>, slotIndex: Int): ItemStack {
        val slots = screen.menu.slots
        if (slotIndex < 0 || slotIndex >= slots.size) return ItemStack.EMPTY
        return slots[slotIndex].item
    }

    private fun clickSlot(screen: AbstractContainerScreen<*>, slotIndex: Int, button: Int = 0) {
        val menu = screen.menu
        if (slotIndex < 0 || slotIndex >= menu.slots.size) return
        val slot = menu.getSlot(slotIndex)
        val accessor = screen as? AccessorAbstractContainerScreen ?: return
        Minecraft.getInstance().execute {
            accessor.invokeSlotClicked(slot, slotIndex, button, ContainerInput.PICKUP)
        }
    }



    private fun getLoreLines(stack: ItemStack): List<String> {
        val list = mutableListOf<String>()
        val loreComponent = stack.get(DataComponents.LORE)
        if (loreComponent != null) {
            for (line in loreComponent.lines()) {
                list.add(line.string)
            }
        }
        val customData = stack.get(DataComponents.CUSTOM_DATA)
        if (customData != null) {
            val nbt = customData.copyTag()
            val display = nbt.getCompound("display").orElse(null)
            if (display != null) {
                val loreList = display.getList("Lore").orElse(null)
                if (loreList != null) {
                    for (i in 0 until loreList.size) {
                        val str = loreList.getString(i).orElse(null)
                        if (str != null && !list.contains(str)) {
                            list.add(str)
                        }
                    }
                }
            }
        }
        return list
    }

    private fun isAddonFinishedOrOnCooldown(item: ItemStack, loreLines: List<String>): Boolean {
        if (item.item == Items.BARRIER) return true
        return loreLines.any { line ->
            line.contains("Claimed", ignoreCase = true) ||
            line.contains("Already", ignoreCase = true) ||
            line.contains("cooldown", ignoreCase = true) ||
            line.contains("Resets in", ignoreCase = true) ||
            line.contains("Come back tomorrow", ignoreCase = true) ||
            line.contains("Right-Click to practice", ignoreCase = true)
        }
    }

    private fun getClickSpeedDelay(speed: ExperimentSpeed): Long = when (speed) {
        ExperimentSpeed.SLOW -> ThreadLocalRandom.current().nextLong(350, 480)
        ExperimentSpeed.MEDIUM -> ThreadLocalRandom.current().nextLong(220, 300)
        ExperimentSpeed.FAST -> ThreadLocalRandom.current().nextLong(130, 180)
    }

    private fun randomDelay(min: Int, max: Int): Long {
        return ThreadLocalRandom.current().nextLong(min.toLong(), (max + 1).toLong())
    }
}
