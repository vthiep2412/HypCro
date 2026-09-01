package com.hypcro.gui

// =========================================================================================
// NOTE: DO NOT ADD RUNTIME STATUS LABELS OR COUNTERS TO ANY MACRO CARD IN THIS SCREEN.
// When any macro or bot is started, the screen closes immediately. 
// Pressing the menu key while running acts as an emergency stop before the screen opens.
// The user can NEVER view this screen while a bot is actively running. 
// Keep cards minimal with title and start toggle only (with some hover effect).
// =========================================================================================

import com.hypcro.config.ConfigManager
import com.hypcro.farming.MacroController
import com.hypcro.gui.widgets.DualRangeSliderWidget
import com.hypcro.gui.widgets.InfoIconWidget
import com.hypcro.gui.widgets.PillToggleWidget
import com.hypcro.gui.widgets.PlotGridModal
import com.hypcro.gui.widgets.SingleSliderWidget
import com.hypcro.dungeon.DungeonESP
import com.hypcro.pest.PestDestroyerEngine
import com.hypcro.pest.PestESP
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.lwjgl.glfw.GLFW

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class MainFarmingScreen : Screen(Component.literal("HypCro Deck")) {

    companion object {
        private val HYPCRO_ICON = Identifier.fromNamespaceAndPath("hypcro", "textures/gui/icon.png")
        private val CROP_ITEMS = listOf(
            ItemStack(Items.WHEAT),
            ItemStack(Items.CARROT),
            ItemStack(Items.POTATO),
            ItemStack(Items.NETHER_WART)
        )

        private const val SEC_MOUSE_H = 104
        private const val SEC_MOUSE_GAP = 110
        private const val SEC_PATHFINDING_H = 104
        private const val SEC_PATHFINDING_GAP = 110
        private const val SEC_FREELOOK_H = 104
        private const val SEC_FREELOOK_GAP = 110
        private const val SEC_FREECAM_H = 68
        private const val SEC_FREECAM_GAP = 74
        private const val SEC_AUTOSPRINT_H = 46
        private const val SEC_WATCHDOG_H = 164
        private const val SEC_WATCHDOG_GAP = 170
        private const val SEC_LOCK_H = 124

        private const val SEC_FARM_FLY_GAP = 44
        private const val SEC_FARM_PEST_GAP = 64
        private const val SEC_PEST_CONF_GAP = 156
        private const val SEC_MISC_CONF_GAP = 88
        private const val SEC_HUD_H = 88
        private const val SEC_DUNGEON_ESP_H = 130
        private const val SEC_DUNGEON_ESP_GAP = 136
        private const val SEC_OTHER_ESP_H = 46
    }

    private val sidebarWidth = 110
    private var selectedTab = "Farming" // "Farming", "Pester", "Misc", "ESP", "HUD", or "Settings"
    private var farmingSubTab = 0 // 0 = "Macro", 1 = "General Config"
    private var pesterSubTab = 0  // 0 = "Macro", 1 = "General Config"
    private var miscSubTab = 0    // 0 = "Macro", 1 = "General Config"
    private var settingsSubTab = 0 // 0 = "Movement", 1 = "Failsafe", 2 = "QOL"

    private val headerLineY = 32
    private var scrollOffset = 0

    // Main Card Geometry
    private var cardX = 0
    private var cardY = 0
    private var cardW = 0
    private var cardH = 88

    // Farming Mode Dropdown
    private val modeOptions = listOf("W/S", "Vertical")
    private var currentModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0
    private var isModeDropdownOpen = false
    private lateinit var modeDropdownBtn: Button
    private lateinit var settingsBtn: Button

    // Top Sub-Tab Navigation Pills
    private lateinit var farmingSubTabPill: PillToggleWidget
    private lateinit var pesterSubTabPill: PillToggleWidget
    private lateinit var miscSubTabPill: PillToggleWidget
    private lateinit var settingsSubTabPill: PillToggleWidget

    // ESP View Widgets
    private lateinit var batEspPill: PillToggleWidget
    private lateinit var starMobsEspPill: PillToggleWidget
    private lateinit var lostAdventurerEspPill: PillToggleWidget
    private lateinit var shadowAssassinEspPill: PillToggleWidget
    private lateinit var diamondGuyEspPill: PillToggleWidget
    private lateinit var espTabPestPill: PillToggleWidget

    // Settings - Mouse Movement
    private lateinit var mouseMovementTypePill: PillToggleWidget
    private lateinit var mouseHumanizePill: PillToggleWidget
    private lateinit var mouseOvershootPill: PillToggleWidget
    private lateinit var mouseDpiSlider: SingleSliderWidget

    // Settings - Pathfinding & Visuals
    private lateinit var pathfindingAlgoPill: PillToggleWidget
    private lateinit var stopAfterDestPill: PillToggleWidget
    private lateinit var stopAfterDestInfo: InfoIconWidget
    private lateinit var pestEspPill: PillToggleWidget
    private lateinit var pathfindingVisualizerPill: PillToggleWidget
    private lateinit var verboseVisualizerPill: PillToggleWidget

    // Settings - Free Look Widgets
    private lateinit var freeLookModePill: PillToggleWidget
    private lateinit var invertZoomPill: PillToggleWidget
    private lateinit var rememberZoomPill: PillToggleWidget
    private lateinit var respectInvertPill: PillToggleWidget
    private lateinit var respectInvertInfo: InfoIconWidget
    private lateinit var freecamSpeedSlider: SingleSliderWidget
    private lateinit var freecamSpeedInfo: InfoIconWidget
    private lateinit var freecamHideGuiPill: PillToggleWidget
    private lateinit var freecamHideGuiInfo: InfoIconWidget
    private lateinit var autoSprintPill: PillToggleWidget
    private lateinit var autoSprintInfo: InfoIconWidget

    // Settings - Key and Mouse Lock Widgets
    private lateinit var keyMouseLockHeaderInfo: InfoIconWidget
    private lateinit var lockHotbarPill: PillToggleWidget
    private lateinit var lockMovementPill: PillToggleWidget
    private lateinit var lockAllOtherKeybindsPill: PillToggleWidget
    private lateinit var lockAllOtherKeybindsInfo: InfoIconWidget
    private lateinit var lockMousePill: PillToggleWidget
    private lateinit var blockChatAndCommandsPill: PillToggleWidget
    private lateinit var blockChatAndCommandsInfo: InfoIconWidget

    // Farming General Config Widgets
    private lateinit var checkFlyingPill: PillToggleWidget
    private lateinit var checkRotationPill: PillToggleWidget
    private lateinit var debounceRotationPill: PillToggleWidget
    private lateinit var debounceRotationInfo: InfoIconWidget
    private lateinit var checkTeleportPill: PillToggleWidget
    private lateinit var checkHotbarSlotPill: PillToggleWidget
    private lateinit var checkFarmingInterruptionPill: PillToggleWidget
    private lateinit var checkFarmingInterruptionInfo: InfoIconWidget
    private lateinit var checkBpsDropPill: PillToggleWidget
    private lateinit var checkBpsDropInfo: InfoIconWidget
    private lateinit var checkUnfamiliarGuiPill: PillToggleWidget
    private lateinit var checkUnfamiliarGuiInfo: InfoIconWidget
    private lateinit var autoActivePestPill: PillToggleWidget
    private lateinit var pestCountSlider: SingleSliderWidget

    // Pester General Config Widgets
    private lateinit var pestRooftopPill: PillToggleWidget
    private lateinit var teleportablePlotsBtn: Button
    private lateinit var keepPestPill: PillToggleWidget
    private lateinit var keepPestInfo: InfoIconWidget
    private lateinit var leavePlotsBtn: Button
    private lateinit var derpyPill: PillToggleWidget

    // Misc General Config Widgets
    private lateinit var bouncyModePill: PillToggleWidget
    private lateinit var bouncyModeInfo: InfoIconWidget
    private lateinit var targetBouncesSlider: SingleSliderWidget
    private lateinit var targetBouncesInfo: InfoIconWidget
    private lateinit var goBackToStartPill: PillToggleWidget
    private lateinit var goBackToStartInfo: InfoIconWidget

    // HUD Config Widgets
    private lateinit var hudStatusPill: PillToggleWidget
    private lateinit var hudOpacitySlider: SingleSliderWidget
    private lateinit var hudEditBtn: Button

    override fun init() {
        cardX = sidebarWidth + 16
        cardY = headerLineY + 14
        cardW = width - cardX - 24
        cardH = 88

        currentModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0

        // 0. Top Sub-Tab Navigation Pills
        farmingSubTabPill = PillToggleWidget(
            cardX, 5, 170, 18,
            listOf("Macro", "General Config"), farmingSubTab
        ) { idx ->
            farmingSubTab = idx
            scrollOffset = 0
            updateWidgetVisibility()
        }
        addRenderableWidget(farmingSubTabPill)

        pesterSubTabPill = PillToggleWidget(
            cardX, 5, 170, 18,
            listOf("Macro", "General Config"), pesterSubTab
        ) { idx ->
            pesterSubTab = idx
            scrollOffset = 0
            updateWidgetVisibility()
        }
        addRenderableWidget(pesterSubTabPill)

        miscSubTabPill = PillToggleWidget(
            cardX, 5, 170, 18,
            listOf("Macro", "General Config"), miscSubTab
        ) { idx ->
            miscSubTab = idx
            scrollOffset = 0
            updateWidgetVisibility()
        }
        addRenderableWidget(miscSubTabPill)

        settingsSubTabPill = PillToggleWidget(
            cardX, 5, 230, 18,
            listOf("Movement", "Failsafe", "QOL"), settingsSubTab
        ) { idx ->
            settingsSubTab = idx
            scrollOffset = 0
            updateWidgetVisibility()
        }
        addRenderableWidget(settingsSubTabPill)

        // 1. Farming Mode Selector Dropdown Button
        modeDropdownBtn = Button.builder(Component.literal("Mode: ${modeOptions[currentModeIndex]} ▼")) {
            isModeDropdownOpen = !isModeDropdownOpen
        }.bounds(cardX + cardW - 130, cardY + 24, 118, 20).build()
        addRenderableWidget(modeDropdownBtn)

        // 2. Crop Settings Button
        settingsBtn = Button.builder(Component.literal("⚙ Settings")) {
            minecraft.gui.setScreen(CropSettingsModal(this))
        }.bounds(cardX + cardW - 130, cardY + 50, 118, 20).build()
        addRenderableWidget(settingsBtn)

        // 3. Settings View - Movement Sub-Tab: Mouse Movement
        val mouseCfg = ConfigManager.config.generalConfig.mouseMovement
        val mmY = cardY
        val mmTypeIdx = when (mouseCfg.movementType.uppercase()) {
            "SIMPLE" -> 0
            "BEZIER" -> 1
            else -> 2 // "GCD"
        }
        mouseMovementTypePill = PillToggleWidget(
            cardX + 220, mmY + 20, 160, 16,
            listOf("Simple", "Bezier", "GCD"), mmTypeIdx
        ) { idx ->
            ConfigManager.config.generalConfig.mouseMovement.movementType = when (idx) {
                0 -> "Simple"
                1 -> "Bezier"
                else -> "GCD"
            }
            ConfigManager.save()
        }
        addRenderableWidget(mouseMovementTypePill)

        mouseHumanizePill = PillToggleWidget(
            cardX + 220, mmY + 40, 100, 16,
            listOf("OFF", "ON"), if (mouseCfg.humanize) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.mouseMovement.humanize = (idx == 1)
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(mouseHumanizePill)

        mouseOvershootPill = PillToggleWidget(
            cardX + 220, mmY + 60, 100, 16,
            listOf("OFF", "ON"), if (mouseCfg.overshoot) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.mouseMovement.overshoot = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(mouseOvershootPill)

        val clampedDpi = mouseCfg.dpiSpeed.coerceIn(1, 20)
        if (clampedDpi != mouseCfg.dpiSpeed) {
            ConfigManager.config.generalConfig.mouseMovement.dpiSpeed = clampedDpi
            ConfigManager.save()
        }

        mouseDpiSlider = SingleSliderWidget(
            cardX + 220, mmY + 80, 140, 16,
            1, 20, clampedDpi, labelPrefix = "DPI Speed: "
        ) { value ->
            ConfigManager.config.generalConfig.mouseMovement.dpiSpeed = value
            ConfigManager.save()
        }
        addRenderableWidget(mouseDpiSlider)

        // 4. Settings View - Movement Sub-Tab: Pathfinding & Flying (with integrated Visuals)
        val pestDestroyerCfg = ConfigManager.config.pestDestroyer
        val pfY = mmY + SEC_MOUSE_GAP
        val algoIdx = when (pestDestroyerCfg.pathfindingAlgorithm.uppercase()) {
            "3D A* WITH SMOOTHING", "3D A*" -> 1
            "BIT*", "BIT", "RRT*" -> 2
            else -> 0 // "Theta*"
        }
        pathfindingAlgoPill = PillToggleWidget(
            cardX + 220, pfY + 20, 160, 16,
            listOf("Theta*", "3D A*", "BIT*"), algoIdx
        ) { idx ->
            ConfigManager.config.pestDestroyer.pathfindingAlgorithm = when (idx) {
                1 -> "3D A* with Smoothing"
                2 -> "BIT*"
                else -> "Theta*"
            }
            ConfigManager.save()
        }
        addRenderableWidget(pathfindingAlgoPill)

        stopAfterDestPill = PillToggleWidget(
            cardX + 220, pfY + 40, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.stopAfterDestination) 1 else 0
        ) { idx ->
            ConfigManager.config.pestDestroyer.stopAfterDestination = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(stopAfterDestPill)

        stopAfterDestInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Brake after reaching destination:")) + 6, pfY + 42, "§eUses S to brake and cancel player glide forward")
        addRenderableWidget(stopAfterDestInfo)

        val visCfg = ConfigManager.config.generalConfig.visuals
        val isVisActive = visCfg.pathfindingVisualizer
        val isVerboseSaved = visCfg.verbosePathfindingVisual
        pathfindingVisualizerPill = PillToggleWidget(
            cardX + 220, pfY + 60, 100, 16,
            listOf("OFF", "ON"), if (isVisActive) 1 else 0
        ) { idx ->
            val active = (idx == 1)
            ConfigManager.config.generalConfig.visuals.pathfindingVisualizer = active
            verboseVisualizerPill.active = active
            if (!active) {
                ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual = false
                verboseVisualizerPill.selectedIndex = 0
            }
            ConfigManager.save()
        }
        addRenderableWidget(pathfindingVisualizerPill)

        verboseVisualizerPill = PillToggleWidget(
            cardX + 220, pfY + 80, 100, 16,
            listOf("OFF", "ON"), if (isVisActive && isVerboseSaved) 1 else 0
        ) { idx ->
            val active = (idx == 1)
            ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual = active
            ConfigManager.save()
        }
        verboseVisualizerPill.active = isVisActive
        addRenderableWidget(verboseVisualizerPill)

        // 5. Settings View - QOL Sub-Tab: Free Look
        val curMode = ConfigManager.config.qolConfig.freeLookMode
        val curModeIdx = if (curMode.equals("TOGGLE", ignoreCase = true)) 1 else 0
        val flY = cardY

        freeLookModePill = PillToggleWidget(
            cardX + 220, flY + 20, 100, 16,
            listOf("Hold", "Toggle"), curModeIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookMode = if (idx == 1) "TOGGLE" else "HOLD"
            ConfigManager.save()
        }
        addRenderableWidget(freeLookModePill)

        invertZoomPill = PillToggleWidget(
            cardX + 220, flY + 40, 100, 16,
            listOf("OFF", "ON"), if (ConfigManager.config.qolConfig.freeLookInvertZoom) 1 else 0
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookInvertZoom = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(invertZoomPill)

        rememberZoomPill = PillToggleWidget(
            cardX + 220, flY + 60, 100, 16,
            listOf("OFF", "ON"), if (ConfigManager.config.qolConfig.freeLookRememberZoom) 1 else 0
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookRememberZoom = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(rememberZoomPill)

        val curRespectMode = ConfigManager.config.qolConfig.freeLookRespectInvertMouse.uppercase()
        val curRespectIdx = when (curRespectMode) {
            "ALWAYS" -> 2
            "ON" -> 1
            else -> 0
        }
        respectInvertPill = PillToggleWidget(
            cardX + 220, flY + 80, 140, 16,
            listOf("OFF", "ON", "Always"), curRespectIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookRespectInvertMouse = when (idx) {
                2 -> "ALWAYS"
                1 -> "ON"
                else -> "OFF"
            }
            ConfigManager.save()
        }
        addRenderableWidget(respectInvertPill)

        respectInvertInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Respect Invert Mouse:")) + 6, flY + 82, "§eRespect Minecraft Invert Mouse Settings")
        addRenderableWidget(respectInvertInfo)

        val fcY = flY + SEC_FREELOOK_GAP
        val initialSpeedInt = (ConfigManager.config.qolConfig.freecamSpeed * 10).toInt().coerceIn(1, 50)
        freecamSpeedSlider = SingleSliderWidget(
            cardX + 220, fcY + 20, 110, 16,
            minValue = 1, maxValue = 50, currentValue = initialSpeedInt,
            customFormatter = { v -> String.format(java.util.Locale.ROOT, "%.1fx", v / 10.0) }
        ) { intVal ->
            ConfigManager.config.qolConfig.freecamSpeed = intVal / 10.0
            ConfigManager.save()
        }
        addRenderableWidget(freecamSpeedSlider)

        freecamSpeedInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Flight Speed:")) + 6, fcY + 22, "§eFreecam Flight Speed\n\nHold Sprint (Ctrl) while flying for 1.2x boost.\nDefault keybind: [U]")
        addRenderableWidget(freecamSpeedInfo)

        freecamHideGuiPill = PillToggleWidget(
            cardX + 220, fcY + 40, 100, 16,
            listOf("OFF", "ON"), if (ConfigManager.config.qolConfig.freecamHideGui) 1 else 0
        ) { idx ->
            ConfigManager.config.qolConfig.freecamHideGui = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(freecamHideGuiPill)

        freecamHideGuiInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Hide GUI:")) + 6, fcY + 42, "§eHide GUI in Freecam\n\nHides first-person hand, hotbar, and health/hunger bars while Freecam is active.")
        addRenderableWidget(freecamHideGuiInfo)

        val asY = fcY + SEC_FREECAM_GAP
        autoSprintPill = PillToggleWidget(
            cardX + 220, asY + 20, 100, 16,
            listOf("OFF", "ON"), if (ConfigManager.config.qolConfig.autoSprint) 1 else 0
        ) { idx ->
            ConfigManager.config.qolConfig.autoSprint = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(autoSprintPill)

        autoSprintInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Auto Sprint:")) + 6, asY + 22, "§eAuto Sprint QOL\n\nAutomatically sprints when moving forward with W.\nPauses while farming macros are active.")
        addRenderableWidget(autoSprintInfo)

        // 6. Settings View - Failsafe Sub-Tab: WatchDog
        val genCfg = ConfigManager.config.generalConfig
        val wdY = cardY
        checkRotationPill = PillToggleWidget(
            cardX + 220, wdY + 20, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkRotation) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkRotation = (idx == 1)
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(checkRotationPill)

        debounceRotationInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("  └ Admin Snapback Grace:")) + 6, wdY + 42, "§eAdmin Snapback Protection")
        addRenderableWidget(debounceRotationInfo)

        debounceRotationPill = PillToggleWidget(
            cardX + 220, wdY + 40, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkRotation && genCfg.watchdog.debounceRotation) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.debounceRotation = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(debounceRotationPill)

        checkTeleportPill = PillToggleWidget(
            cardX + 220, wdY + 60, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkTeleport) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkTeleport = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkTeleportPill)

        checkHotbarSlotPill = PillToggleWidget(
            cardX + 220, wdY + 80, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkHotbarSlot) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkHotbarSlot = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkHotbarSlotPill)

        checkFarmingInterruptionInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Farming Interruption failsafe:")) + 6, wdY + 102, "§eFarming Interruption Check")
        addRenderableWidget(checkFarmingInterruptionInfo)

        checkFarmingInterruptionPill = PillToggleWidget(
            cardX + 220, wdY + 100, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkFarmingInterruption) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkFarmingInterruption = (idx == 1)
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(checkFarmingInterruptionPill)

        checkBpsDropInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("  └ BPS Drop Protection:")) + 6, wdY + 122, "§eSuddenly Low BPS Check\n\nArms when breaking crops at >=17 BPS.\nTriggers an alarm failsafe if BPS drops below 17 for >1.6s.\nResets during W/S turn recovery (800ms).")
        addRenderableWidget(checkBpsDropInfo)

        checkBpsDropPill = PillToggleWidget(
            cardX + 220, wdY + 120, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkFarmingInterruption && genCfg.watchdog.checkBpsDrop) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkBpsDrop = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkBpsDropPill)

        checkUnfamiliarGuiInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Unfamiliar GUI failsafe:")) + 6, wdY + 142, "§eUnfamiliar GUI Check")
        addRenderableWidget(checkUnfamiliarGuiInfo)

        checkUnfamiliarGuiPill = PillToggleWidget(
            cardX + 220, wdY + 140, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkUnfamiliarGui) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkUnfamiliarGui = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkUnfamiliarGuiPill)

        // 7. Settings View - Failsafe Sub-Tab: Key and Mouse Lock
        val lockCardY = wdY + SEC_WATCHDOG_GAP

        keyMouseLockHeaderInfo = InfoIconWidget(cardX + 10 + font.width(Component.literal("§b§lKey and Mouse Lock")) + 6, lockCardY + 5, "§eInput Lock")
        addRenderableWidget(keyMouseLockHeaderInfo)

        lockHotbarPill = PillToggleWidget(
            cardX + 220, lockCardY + 20, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.lockHotbar) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockHotbar = (idx == 1)
            if (idx == 0) {
                ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds = false
                lockAllOtherKeybindsPill.selectedIndex = 0
            }
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(lockHotbarPill)

        lockMovementPill = PillToggleWidget(
            cardX + 220, lockCardY + 40, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.lockMovement) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockMovement = (idx == 1)
            if (idx == 0) {
                ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds = false
                lockAllOtherKeybindsPill.selectedIndex = 0
            }
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(lockMovementPill)

        lockAllOtherKeybindsInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Lock All Other Keybinds:")) + 6, lockCardY + 62, "§eLock All Other Keybinds")
        addRenderableWidget(lockAllOtherKeybindsInfo)

        val canLockOtherInit = genCfg.inputLock.lockHotbar && genCfg.inputLock.lockMovement
        lockAllOtherKeybindsPill = PillToggleWidget(
            cardX + 220, lockCardY + 60, 100, 16,
            listOf("OFF", "ON"), if (canLockOtherInit && genCfg.inputLock.lockAllOtherKeybinds) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds = (idx == 1)
            ConfigManager.save()
        }
        lockAllOtherKeybindsPill.active = canLockOtherInit
        addRenderableWidget(lockAllOtherKeybindsPill)

        lockMousePill = PillToggleWidget(
            cardX + 220, lockCardY + 80, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.lockMouse) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockMouse = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(lockMousePill)

        blockChatAndCommandsInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Block Chat and Command:")) + 6, lockCardY + 102, "§eBlock Chat & Commands")
        addRenderableWidget(blockChatAndCommandsInfo)

        blockChatAndCommandsPill = PillToggleWidget(
            cardX + 220, lockCardY + 100, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.blockChatAndCommands) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.blockChatAndCommands = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(blockChatAndCommandsPill)

        // 8. Farming -> General Config Widgets
        val farmSec1Y = cardY
        checkFlyingPill = PillToggleWidget(
            cardX + 220, farmSec1Y + 20, 100, 16,
            listOf("OFF", "ON"), if (genCfg.antiStuck.checkFlying) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.antiStuck.checkFlying = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkFlyingPill)

        val farmSec2Y = farmSec1Y + 44
        autoActivePestPill = PillToggleWidget(
            cardX + 220, farmSec2Y + 20, 100, 16,
            listOf("OFF", "ON"), if (ConfigManager.config.autoActivePest) 1 else 0
        ) { idx ->
            ConfigManager.config.autoActivePest = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(autoActivePestPill)

        val initialMinRequired = if (pestDestroyerCfg.keepPest) (pestDestroyerCfg.leavePestPlots.size + 1).coerceAtMost(30) else 1
        if (ConfigManager.config.pestTriggerCount < initialMinRequired) {
            ConfigManager.config.pestTriggerCount = initialMinRequired
            ConfigManager.save()
        }

        pestCountSlider = SingleSliderWidget(
            cardX + 220, farmSec2Y + 40, 110, 16,
            minValue = 1, maxValue = 30, currentValue = ConfigManager.config.pestTriggerCount,
            labelPrefix = ">= "
        ) { valVal ->
            val minRequired = if (ConfigManager.config.pestDestroyer.keepPest) {
                (ConfigManager.config.pestDestroyer.leavePestPlots.size + 1).coerceAtMost(30)
            } else {
                1
            }
            val effectiveVal = valVal.coerceAtLeast(minRequired)
            pestCountSlider.currentValue = effectiveVal
            ConfigManager.config.pestTriggerCount = effectiveVal
            ConfigManager.save()
        }
        addRenderableWidget(pestCountSlider)

        // 8. Pester -> General Config Widgets
        val pdY = cardY
        pestEspPill = PillToggleWidget(
            cardX + 220, pdY + 20, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.pestEsp) 1 else 0
        ) { idx ->
            val active = (idx == 1)
            ConfigManager.config.pestDestroyer.pestEsp = active
            if (::espTabPestPill.isInitialized) {
                espTabPestPill.selectedIndex = idx
            }
            ConfigManager.save()
        }
        addRenderableWidget(pestEspPill)

        // 8b. ESP Tab Widgets (Dungeon ESP & Other ESP)
        val dngCfg = ConfigManager.config.dungeon
        batEspPill = PillToggleWidget(
            cardX + 220, cardY + 20, 100, 16,
            listOf("OFF", "ON"), if (dngCfg.batEsp) 1 else 0
        ) { idx ->
            ConfigManager.config.dungeon.batEsp = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(batEspPill)

        starMobsEspPill = PillToggleWidget(
            cardX + 220, cardY + 42, 100, 16,
            listOf("OFF", "ON"), if (dngCfg.starMobsEsp) 1 else 0
        ) { idx ->
            ConfigManager.config.dungeon.starMobsEsp = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(starMobsEspPill)

        lostAdventurerEspPill = PillToggleWidget(
            cardX + 220, cardY + 64, 100, 16,
            listOf("OFF", "ON"), if (dngCfg.lostAdventurerEsp) 1 else 0
        ) { idx ->
            ConfigManager.config.dungeon.lostAdventurerEsp = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(lostAdventurerEspPill)

        shadowAssassinEspPill = PillToggleWidget(
            cardX + 220, cardY + 86, 100, 16,
            listOf("OFF", "ON"), if (dngCfg.shadowAssassinEsp) 1 else 0
        ) { idx ->
            ConfigManager.config.dungeon.shadowAssassinEsp = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(shadowAssassinEspPill)

        diamondGuyEspPill = PillToggleWidget(
            cardX + 220, cardY + 108, 100, 16,
            listOf("OFF", "ON"), if (dngCfg.diamondGuyEsp) 1 else 0
        ) { idx ->
            ConfigManager.config.dungeon.diamondGuyEsp = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(diamondGuyEspPill)

        espTabPestPill = PillToggleWidget(
            cardX + 220, cardY + SEC_DUNGEON_ESP_GAP + 20, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.pestEsp) 1 else 0
        ) { idx ->
            val active = (idx == 1)
            ConfigManager.config.pestDestroyer.pestEsp = active
            if (::pestEspPill.isInitialized) {
                pestEspPill.selectedIndex = idx
            }
            ConfigManager.save()
        }
        addRenderableWidget(espTabPestPill)

        pestRooftopPill = PillToggleWidget(
            cardX + 220, pdY + 42, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.getRooftop) 1 else 0
        ) { idx ->
            ConfigManager.config.pestDestroyer.getRooftop = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(pestRooftopPill)

        teleportablePlotsBtn = Button.builder(Component.literal("Edit Teleportable Plots...")) {
            minecraft.gui.setScreen(PlotGridModal("Teleportable Plots", ConfigManager.config.pestDestroyer.teleportablePlots, this) { savedPlots ->
                ConfigManager.config.pestDestroyer.teleportablePlots = savedPlots.toMutableSet()
                ConfigManager.save()
            })
        }.bounds(cardX + 220, pdY + 64, 160, 18).build()
        addRenderableWidget(teleportablePlotsBtn)

        keepPestInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Keep Pest:")) + 6, pdY + 90, "Keep 1 pest for Termite Shard Farming Fortune")
        addRenderableWidget(keepPestInfo)

        keepPestPill = PillToggleWidget(
            cardX + 220, pdY + 88, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.keepPest) 1 else 0
        ) { idx ->
            val keepOn = (idx == 1)
            ConfigManager.config.pestDestroyer.keepPest = keepOn
            if (keepOn) {
                val minRequired = (ConfigManager.config.pestDestroyer.leavePestPlots.size + 1).coerceAtMost(30)
                if (ConfigManager.config.pestTriggerCount < minRequired) {
                    ConfigManager.config.pestTriggerCount = minRequired
                    pestCountSlider.currentValue = minRequired
                }
            }
            ConfigManager.save()
            leavePlotsBtn.active = keepOn
        }
        addRenderableWidget(keepPestPill)

        leavePlotsBtn = Button.builder(Component.literal("Select Plots to Leave Pest...")) {
            minecraft.gui.setScreen(PlotGridModal("Plots to Leave Pest", ConfigManager.config.pestDestroyer.leavePestPlots, this) { savedPlots ->
                ConfigManager.config.pestDestroyer.leavePestPlots = savedPlots.toMutableSet()
                if (ConfigManager.config.pestDestroyer.keepPest) {
                    val minRequired = (savedPlots.size + 1).coerceAtMost(30)
                    if (ConfigManager.config.pestTriggerCount < minRequired) {
                        ConfigManager.config.pestTriggerCount = minRequired
                        pestCountSlider.currentValue = minRequired
                    }
                }
                ConfigManager.save()
            })
        }.bounds(cardX + 220, pdY + 110, 160, 18).build()
        addRenderableWidget(leavePlotsBtn)

        derpyPill = PillToggleWidget(
            cardX + 220, pdY + 132, 100, 16,
            listOf("OFF", "ON"), if (pestDestroyerCfg.derpy) 1 else 0
        ) { idx ->
            ConfigManager.config.pestDestroyer.derpy = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(derpyPill)

        // Misc Config Widgets
        val bouncyCfg = ConfigManager.config.bouncyBall

        bouncyModeInfo = InfoIconWidget(
            cardX + 12 + font.width(Component.literal("Movement Mode:")) + 6,
            cardY + 22,
            "§eBouncy Ball Mode\n§bCalm§7: Relaxed humanized strafe with debounce.\n§cAggressive§7: Fast 50Hz instant strafe.\n§aSmart§7: Overshoots behind the ball to steer it back to center."
        )
        addRenderableWidget(bouncyModeInfo)

        val bouncyModeIdx = when (bouncyCfg.mode) {
            com.hypcro.config.BouncyBallMode.CALM -> 0
            com.hypcro.config.BouncyBallMode.AGGRESSIVE -> 1
            com.hypcro.config.BouncyBallMode.SMART -> 2
        }
        bouncyModePill = PillToggleWidget(
            cardX + 140, cardY + 20, 180, 16,
            listOf("CALM", "AGGRESSIVE", "SMART"), bouncyModeIdx
        ) { idx ->
            ConfigManager.config.bouncyBall.mode = when (idx) {
                0 -> com.hypcro.config.BouncyBallMode.CALM
                1 -> com.hypcro.config.BouncyBallMode.AGGRESSIVE
                else -> com.hypcro.config.BouncyBallMode.SMART
            }
            ConfigManager.save()
        }
        addRenderableWidget(bouncyModePill)

        targetBouncesInfo = InfoIconWidget(
            cardX + 12 + font.width(Component.literal("Target Bounces:")) + 6,
            cardY + 42,
            "§eTarget Bounces\n§7Bot will stop catching the ball once target bounces are reached and place a new one to maximize Fishy Treats."
        )
        addRenderableWidget(targetBouncesInfo)

        targetBouncesSlider = SingleSliderWidget(
            cardX + 220, cardY + 40, 100, 16,
            minValue = 10, maxValue = 46, currentValue = bouncyCfg.targetBounces,
            customFormatter = { if (it >= 46) "Forever" else it.toString() }
        ) { value ->
            ConfigManager.config.bouncyBall.targetBounces = value
            ConfigManager.save()
        }
        addRenderableWidget(targetBouncesSlider)

        goBackToStartInfo = InfoIconWidget(
            cardX + 12 + font.width(Component.literal("Go back to Start:")) + 6,
            cardY + 64,
            "§eGo back to Start\n§7Returns to the initial starting position after a ball pops to prevent drifting toward edges."
        )
        addRenderableWidget(goBackToStartInfo)

        goBackToStartPill = PillToggleWidget(
            cardX + 220, cardY + 62, 100, 16,
            listOf("OFF", "ON"), if (bouncyCfg.goBackToStart) 1 else 0
        ) { idx ->
            ConfigManager.config.bouncyBall.goBackToStart = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(goBackToStartPill)

        // 9. HUD Config Widgets
        val hudCfg = ConfigManager.config.hud
        hudStatusPill = PillToggleWidget(
            cardX + 220, cardY + 20, 100, 16,
            listOf("OFF", "ON"), if (hudCfg.enabled) 1 else 0
        ) { idx ->
            val on = (idx == 1)
            ConfigManager.config.hud.enabled = on
            hudOpacitySlider.active = on
            ConfigManager.save()
        }
        addRenderableWidget(hudStatusPill)

        val clampedOpacity = (hudCfg.opacity * 100).toInt().coerceIn(10, 100)
        val normalizedOpacity = clampedOpacity / 100.0f
        if (hudCfg.opacity != normalizedOpacity) {
            hudCfg.opacity = normalizedOpacity
            ConfigManager.save()
        }
        hudOpacitySlider = SingleSliderWidget(
            cardX + 220, cardY + 42, 140, 16,
            10, 100, clampedOpacity, labelPrefix = "Opacity: ", labelSuffix = "%"
        ) { value ->
            ConfigManager.config.hud.opacity = value / 100.0f
            ConfigManager.save()
        }
        hudOpacitySlider.active = hudCfg.enabled
        addRenderableWidget(hudOpacitySlider)

        hudEditBtn = Button.builder(Component.literal("Edit")) {
            minecraft.gui.setScreen(HudEditScreen(this))
        }.bounds(cardX + 220, cardY + 64, 80, 18).build()
        addRenderableWidget(hudEditBtn)

        updateWidgetVisibility()
    }

    private fun calculateMaxScroll(): Int {
        val viewH = height - headerLineY - 14
        val contentH = when (selectedTab) {
            "Settings" -> when (settingsSubTab) {
                0 -> SEC_MOUSE_GAP + SEC_PATHFINDING_H + 20
                1 -> SEC_WATCHDOG_GAP + SEC_LOCK_H + 20
                2 -> SEC_FREELOOK_GAP + SEC_FREECAM_GAP + SEC_AUTOSPRINT_H + 20
                else -> 100
            }
            "Farming" -> if (farmingSubTab == 1) SEC_FARM_FLY_GAP + SEC_FARM_PEST_GAP + 20 else cardH + 20
            "Pester" -> if (pesterSubTab == 1) SEC_PEST_CONF_GAP + 20 else cardH + 20
            "Misc" -> if (miscSubTab == 1) SEC_MISC_CONF_GAP + 20 else cardH + 20
            "ESP" -> SEC_DUNGEON_ESP_GAP + SEC_OTHER_ESP_H + 20
            "HUD" -> SEC_HUD_H + 20
            else -> 100
        }
        return (contentH - viewH).coerceAtLeast(0)
    }

    private fun updateWidgetPositions() {
        val effectiveCardY = cardY - scrollOffset

        // Settings View - Sub-Tab 0: Movement
        val mmY = effectiveCardY
        mouseMovementTypePill.y = mmY + 20
        mouseHumanizePill.y = mmY + 40
        mouseOvershootPill.y = mmY + 60
        mouseDpiSlider.y = mmY + 80

        val pfY = mmY + SEC_MOUSE_GAP
        pathfindingAlgoPill.y = pfY + 20
        stopAfterDestPill.y = pfY + 40
        stopAfterDestInfo.y = pfY + 42
        pathfindingVisualizerPill.y = pfY + 60
        verboseVisualizerPill.y = pfY + 80

        // Settings View - Sub-Tab 1: Failsafe
        val wdY = effectiveCardY
        checkRotationPill.y = wdY + 20
        debounceRotationInfo.y = wdY + 42
        debounceRotationPill.y = wdY + 40
        checkTeleportPill.y = wdY + 60
        checkHotbarSlotPill.y = wdY + 80
        checkFarmingInterruptionInfo.y = wdY + 102
        checkFarmingInterruptionPill.y = wdY + 100
        checkBpsDropInfo.y = wdY + 122
        checkBpsDropPill.y = wdY + 120
        checkUnfamiliarGuiInfo.y = wdY + 142
        checkUnfamiliarGuiPill.y = wdY + 140

        val lockCardY = wdY + SEC_WATCHDOG_GAP
        keyMouseLockHeaderInfo.y = lockCardY + 5
        lockHotbarPill.y = lockCardY + 20
        lockMovementPill.y = lockCardY + 40
        lockAllOtherKeybindsPill.y = lockCardY + 60
        lockAllOtherKeybindsInfo.y = lockCardY + 62
        lockMousePill.y = lockCardY + 80
        blockChatAndCommandsPill.y = lockCardY + 100
        blockChatAndCommandsInfo.y = lockCardY + 102

        // Settings View - Sub-Tab 2: QOL
        val flY = effectiveCardY
        freeLookModePill.y = flY + 20
        invertZoomPill.y = flY + 40
        rememberZoomPill.y = flY + 60
        respectInvertPill.y = flY + 80
        respectInvertInfo.y = flY + 82

        val fcY = flY + SEC_FREELOOK_GAP
        freecamSpeedSlider.y = fcY + 20
        freecamSpeedInfo.y = fcY + 22
        freecamHideGuiPill.y = fcY + 40
        freecamHideGuiInfo.y = fcY + 42

        val asY = fcY + SEC_FREECAM_GAP
        autoSprintPill.y = asY + 20
        autoSprintInfo.y = asY + 22

        // ESP Tab Widgets
        val dngY = effectiveCardY
        batEspPill.y = dngY + 20
        starMobsEspPill.y = dngY + 42
        lostAdventurerEspPill.y = dngY + 64
        shadowAssassinEspPill.y = dngY + 86
        diamondGuyEspPill.y = dngY + 108

        val otherEspY = dngY + SEC_DUNGEON_ESP_GAP
        espTabPestPill.y = otherEspY + 20

        // Farming Config Widgets
        val farmSec1Y = effectiveCardY
        checkFlyingPill.y = farmSec1Y + 20

        val farmSec2Y = farmSec1Y + 44
        autoActivePestPill.y = farmSec2Y + 20
        pestCountSlider.y = farmSec2Y + 40

        // Pester Config Widgets
        val pdY = effectiveCardY
        pestEspPill.y = pdY + 20
        pestRooftopPill.y = pdY + 42
        teleportablePlotsBtn.y = pdY + 64
        keepPestPill.y = pdY + 88
        keepPestInfo.y = pdY + 90
        leavePlotsBtn.y = pdY + 110
        derpyPill.y = pdY + 132

        // Misc Config Widgets
        bouncyModeInfo.y = effectiveCardY + 22
        bouncyModePill.y = effectiveCardY + 20
        targetBouncesInfo.y = effectiveCardY + 42
        targetBouncesSlider.y = effectiveCardY + 40
        goBackToStartInfo.y = effectiveCardY + 64
        goBackToStartPill.y = effectiveCardY + 62

        // HUD Config Widgets
        val hudSecY = effectiveCardY
        hudStatusPill.y = hudSecY + 20
        hudOpacitySlider.y = hudSecY + 42
        hudEditBtn.y = hudSecY + 64
    }

    private fun updateWidgetVisibility() {
        val isFarming = selectedTab == "Farming"
        val isPester = selectedTab == "Pester"
        val isMisc = selectedTab == "Misc"
        val isEsp = selectedTab == "ESP"
        val isSettings = selectedTab == "Settings"

        val isFarmingMacro = isFarming && farmingSubTab == 0
        val isFarmingConfig = isFarming && farmingSubTab == 1

        val isPesterMacro = isPester && pesterSubTab == 0
        val isPesterConfig = isPester && pesterSubTab == 1

        val isMiscMacro = isMisc && miscSubTab == 0
        val isMiscConfig = isMisc && miscSubTab == 1

        val isSettingsMovement = isSettings && settingsSubTab == 0
        val isSettingsFailsafe = isSettings && settingsSubTab == 1
        val isSettingsQol = isSettings && settingsSubTab == 2

        farmingSubTabPill.visible = isFarming
        pesterSubTabPill.visible = isPester
        miscSubTabPill.visible = isMisc
        settingsSubTabPill.visible = isSettings

        modeDropdownBtn.visible = isFarmingMacro
        settingsBtn.visible = isFarmingMacro
        if (!isFarmingMacro) isModeDropdownOpen = false

        // Settings - Movement
        mouseMovementTypePill.visible = isSettingsMovement
        mouseHumanizePill.visible = isSettingsMovement
        mouseOvershootPill.visible = isSettingsMovement
        mouseDpiSlider.visible = isSettingsMovement

        pathfindingAlgoPill.visible = isSettingsMovement
        stopAfterDestPill.visible = isSettingsMovement
        stopAfterDestInfo.visible = isSettingsMovement
        pathfindingVisualizerPill.visible = isSettingsMovement
        val isVisOn = ConfigManager.config.generalConfig.visuals.pathfindingVisualizer
        verboseVisualizerPill.active = isVisOn
        if (!isVisOn && ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual) {
            ConfigManager.config.generalConfig.visuals.verbosePathfindingVisual = false
            verboseVisualizerPill.selectedIndex = 0
            ConfigManager.save()
        } else if (!isVisOn) {
            verboseVisualizerPill.selectedIndex = 0
        }
        verboseVisualizerPill.visible = isSettingsMovement

        // Settings - Failsafe: WatchDog
        checkRotationPill.visible = isSettingsFailsafe
        val canDebounce = ConfigManager.config.generalConfig.watchdog.checkRotation
        debounceRotationPill.active = canDebounce
        if (!canDebounce && ConfigManager.config.generalConfig.watchdog.debounceRotation) {
            ConfigManager.config.generalConfig.watchdog.debounceRotation = false
            debounceRotationPill.selectedIndex = 0
            ConfigManager.save()
        }
        debounceRotationPill.visible = isSettingsFailsafe
        debounceRotationInfo.visible = isSettingsFailsafe
        checkTeleportPill.visible = isSettingsFailsafe
        checkHotbarSlotPill.visible = isSettingsFailsafe
        checkFarmingInterruptionPill.visible = isSettingsFailsafe
        checkFarmingInterruptionInfo.visible = isSettingsFailsafe
        val canBpsDrop = ConfigManager.config.generalConfig.watchdog.checkFarmingInterruption
        checkBpsDropPill.active = canBpsDrop
        if (!canBpsDrop && ConfigManager.config.generalConfig.watchdog.checkBpsDrop) {
            ConfigManager.config.generalConfig.watchdog.checkBpsDrop = false
            checkBpsDropPill.selectedIndex = 0
            ConfigManager.save()
        }
        checkBpsDropPill.visible = isSettingsFailsafe
        checkBpsDropInfo.visible = isSettingsFailsafe
        checkUnfamiliarGuiPill.visible = isSettingsFailsafe
        checkUnfamiliarGuiInfo.visible = isSettingsFailsafe

        // Settings - Failsafe: Key and Mouse Lock
        keyMouseLockHeaderInfo.visible = isSettingsFailsafe
        lockHotbarPill.visible = isSettingsFailsafe
        lockMovementPill.visible = isSettingsFailsafe
        val canLockOther = ConfigManager.config.generalConfig.inputLock.lockHotbar && ConfigManager.config.generalConfig.inputLock.lockMovement
        lockAllOtherKeybindsPill.active = canLockOther
        if (!canLockOther && ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds) {
            ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds = false
            lockAllOtherKeybindsPill.selectedIndex = 0
            ConfigManager.save()
        }
        lockAllOtherKeybindsPill.visible = isSettingsFailsafe
        lockAllOtherKeybindsInfo.visible = isSettingsFailsafe
        lockMousePill.visible = isSettingsFailsafe
        blockChatAndCommandsPill.visible = isSettingsFailsafe
        blockChatAndCommandsInfo.visible = isSettingsFailsafe

        // Settings - QOL: Free Look & Freecam
        freeLookModePill.visible = isSettingsQol
        invertZoomPill.visible = isSettingsQol
        rememberZoomPill.visible = isSettingsQol
        respectInvertPill.visible = isSettingsQol
        respectInvertInfo.visible = isSettingsQol
        freecamSpeedSlider.visible = isSettingsQol
        freecamSpeedInfo.visible = isSettingsQol
        freecamHideGuiPill.visible = isSettingsQol
        freecamHideGuiInfo.visible = isSettingsQol
        autoSprintPill.visible = isSettingsQol
        autoSprintInfo.visible = isSettingsQol

        // ESP Tab
        batEspPill.visible = isEsp
        starMobsEspPill.visible = isEsp
        lostAdventurerEspPill.visible = isEsp
        shadowAssassinEspPill.visible = isEsp
        diamondGuyEspPill.visible = isEsp
        espTabPestPill.visible = isEsp

        // Farming - General Config
        checkFlyingPill.visible = isFarmingConfig
        autoActivePestPill.visible = isFarmingConfig
        pestCountSlider.visible = isFarmingConfig

        // Pester - General Config
        pestEspPill.visible = isPesterConfig
        pestRooftopPill.visible = isPesterConfig
        teleportablePlotsBtn.visible = isPesterConfig
        keepPestPill.visible = isPesterConfig
        keepPestInfo.visible = isPesterConfig
        leavePlotsBtn.active = ConfigManager.config.pestDestroyer.keepPest
        leavePlotsBtn.visible = isPesterConfig
        derpyPill.visible = isPesterConfig

        // Misc - General Config
        bouncyModeInfo.visible = isMiscConfig
        bouncyModePill.visible = isMiscConfig
        targetBouncesSlider.visible = isMiscConfig
        targetBouncesInfo.visible = isMiscConfig
        goBackToStartPill.visible = isMiscConfig
        goBackToStartInfo.visible = isMiscConfig

        // HUD Config
        val isHud = selectedTab == "HUD"
        hudStatusPill.visible = isHud
        hudOpacitySlider.visible = isHud
        hudOpacitySlider.active = ConfigManager.config.hud.enabled
        hudEditBtn.visible = isHud

        updateWidgetPositions()
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (mouseX >= cardX) {
            val maxScroll = calculateMaxScroll()
            if (maxScroll > 0) {
                scrollOffset = (scrollOffset - (verticalAmount * 24).toInt()).coerceIn(0, maxScroll)
                updateWidgetPositions()
                return true
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, width, height, 0xFF0B0F19.toInt())
        graphics.fill(sidebarWidth, headerLineY, width, headerLineY + 1, 0xFF1E293B.toInt())

        graphics.fill(0, 0, sidebarWidth, height, 0xFF0F172A.toInt())
        graphics.fill(sidebarWidth, 0, sidebarWidth + 1, height, 0xFF1E293B.toInt())

        val titleY = (headerLineY - 9) / 2
        val iconSize = 20
        val iconY = (headerLineY - iconSize) / 2
        graphics.blit(HYPCRO_ICON, 10, iconY, 10 + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f)
        graphics.text(font, "§b§lHypCro", 36, titleY, 0xFF38BDF8.toInt())

        // Top Sub-Tab Navigation Pills
        when (selectedTab) {
            "Farming" -> farmingSubTabPill.x = (width / 2) - 85
            "Pester" -> pesterSubTabPill.x = (width / 2) - 85
            "Misc" -> miscSubTabPill.x = (width / 2) - 85
            "Settings" -> settingsSubTabPill.x = (width / 2) - 115
        }

        // 1. Sidebar Item: Farming (Top)
        val farmingBoxY = headerLineY + 14
        val farmingBoxH = 24
        val farmingHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in farmingBoxY until (farmingBoxY + farmingBoxH)
        val isFarmingActive = selectedTab == "Farming"

        val farmingBorderColor = if (isFarmingActive) 0xFF38BDF8.toInt() else if (farmingHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, farmingBoxY - 1, sidebarWidth - 6, farmingBoxY, farmingBorderColor)
        graphics.fill(6, farmingBoxY + farmingBoxH, sidebarWidth - 6, farmingBoxY + farmingBoxH + 1, farmingBorderColor)

        if (isFarmingActive) {
            graphics.fill(6, farmingBoxY, sidebarWidth - 6, farmingBoxY + farmingBoxH, 0x3338BDF8)
            graphics.text(font, "> Farming", 14, farmingBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (farmingHovered) graphics.fill(6, farmingBoxY, sidebarWidth - 6, farmingBoxY + farmingBoxH, 0x1A38BDF8)
            graphics.text(font, "  Farming", 14, farmingBoxY + 8, if (farmingHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // 2. Sidebar Item: Pester (Right under Farming)
        val pesterBoxY = farmingBoxY + farmingBoxH + 4
        val pesterBoxH = 24
        val pesterHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in pesterBoxY until (pesterBoxY + pesterBoxH)
        val isPesterActive = selectedTab == "Pester"

        val pesterBorderColor = if (isPesterActive) 0xFF38BDF8.toInt() else if (pesterHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, pesterBoxY - 1, sidebarWidth - 6, pesterBoxY, pesterBorderColor)
        graphics.fill(6, pesterBoxY + pesterBoxH, sidebarWidth - 6, pesterBoxY + pesterBoxH + 1, pesterBorderColor)

        if (isPesterActive) {
            graphics.fill(6, pesterBoxY, sidebarWidth - 6, pesterBoxY + pesterBoxH, 0x3338BDF8)
            graphics.text(font, "> Pester", 14, pesterBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (pesterHovered) graphics.fill(6, pesterBoxY, sidebarWidth - 6, pesterBoxY + pesterBoxH, 0x1A38BDF8)
            graphics.text(font, "  Pester", 14, pesterBoxY + 8, if (pesterHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // 3. Sidebar Item: Misc (Right under Pester)
        val miscBoxY = pesterBoxY + pesterBoxH + 4
        val miscBoxH = 24
        val miscHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in miscBoxY until (miscBoxY + miscBoxH)
        val isMiscActive = selectedTab == "Misc"

        val miscBorderColor = if (isMiscActive) 0xFF38BDF8.toInt() else if (miscHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, miscBoxY - 1, sidebarWidth - 6, miscBoxY, miscBorderColor)
        graphics.fill(6, miscBoxY + miscBoxH, sidebarWidth - 6, miscBoxY + miscBoxH + 1, miscBorderColor)

        if (isMiscActive) {
            graphics.fill(6, miscBoxY, sidebarWidth - 6, miscBoxY + miscBoxH, 0x3338BDF8)
            graphics.text(font, "> Misc", 14, miscBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (miscHovered) graphics.fill(6, miscBoxY, sidebarWidth - 6, miscBoxY + miscBoxH, 0x1A38BDF8)
            graphics.text(font, "  Misc", 14, miscBoxY + 8, if (miscHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // 4. Sidebar Item: ESP (Above HUD)
        val settingsBoxH = 24
        val settingsBoxY = height - settingsBoxH - 12
        val hudBoxH = 24
        val hudBoxY = settingsBoxY - hudBoxH - 4
        val espBoxH = 24
        val espBoxY = hudBoxY - espBoxH - 4
        val espHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in espBoxY until (espBoxY + espBoxH)
        val isEspActive = selectedTab == "ESP"

        val espBorderColor = if (isEspActive) 0xFF38BDF8.toInt() else if (espHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, espBoxY - 1, sidebarWidth - 6, espBoxY, espBorderColor)
        graphics.fill(6, espBoxY + espBoxH, sidebarWidth - 6, espBoxY + espBoxH + 1, espBorderColor)

        if (isEspActive) {
            graphics.fill(6, espBoxY, sidebarWidth - 6, espBoxY + espBoxH, 0x3338BDF8)
            graphics.text(font, "> ESP", 14, espBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (espHovered) graphics.fill(6, espBoxY, sidebarWidth - 6, espBoxY + espBoxH, 0x1A38BDF8)
            graphics.text(font, "  ESP", 14, espBoxY + 8, if (espHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // 5. Sidebar Item: HUD (Above Settings)
        val hudHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in hudBoxY until (hudBoxY + hudBoxH)
        val isHudActive = selectedTab == "HUD"

        val hudBorderColor = if (isHudActive) 0xFF38BDF8.toInt() else if (hudHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, hudBoxY - 1, sidebarWidth - 6, hudBoxY, hudBorderColor)
        graphics.fill(6, hudBoxY + hudBoxH, sidebarWidth - 6, hudBoxY + hudBoxH + 1, hudBorderColor)

        if (isHudActive) {
            graphics.fill(6, hudBoxY, sidebarWidth - 6, hudBoxY + hudBoxH, 0x3338BDF8)
            graphics.text(font, "> HUD", 14, hudBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (hudHovered) graphics.fill(6, hudBoxY, sidebarWidth - 6, hudBoxY + hudBoxH, 0x1A38BDF8)
            graphics.text(font, "  HUD", 14, hudBoxY + 8, if (hudHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // 6. Sidebar Item: Settings (Bottom)
        val settingsHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in settingsBoxY until (settingsBoxY + settingsBoxH)
        val isSettingsActive = selectedTab == "Settings"

        val settingsBorderColor = if (isSettingsActive) 0xFF38BDF8.toInt() else if (settingsHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, settingsBoxY - 1, sidebarWidth - 6, settingsBoxY, settingsBorderColor)
        graphics.fill(6, settingsBoxY + settingsBoxH, sidebarWidth - 6, settingsBoxY + settingsBoxH + 1, settingsBorderColor)

        if (isSettingsActive) {
            graphics.fill(6, settingsBoxY, sidebarWidth - 6, settingsBoxY + settingsBoxH, 0x3338BDF8)
            graphics.text(font, "> Settings", 14, settingsBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (settingsHovered) graphics.fill(6, settingsBoxY, sidebarWidth - 6, settingsBoxY + settingsBoxH, 0x1A38BDF8)
            graphics.text(font, "  Settings", 14, settingsBoxY + 8, if (settingsHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // Right-Hand Views
        when (selectedTab) {
            "Farming" -> {
                if (farmingSubTab == 0) {
                    renderFarmingMacroCard(graphics, mouseX, mouseY)
                } else {
                    renderFarmingConfig(graphics, mouseX, mouseY)
                }
            }
            "Pester" -> {
                if (pesterSubTab == 0) {
                    renderPesterMacroCard(graphics, mouseX, mouseY)
                } else {
                    renderPesterConfig(graphics, mouseX, mouseY)
                }
            }
            "Misc" -> {
                if (miscSubTab == 0) {
                    renderMiscMacroCard(graphics, mouseX, mouseY)
                } else {
                    renderMiscConfig(graphics, mouseX, mouseY)
                }
            }
            "ESP" -> {
                renderEspView(graphics, mouseX, mouseY)
            }
            "HUD" -> {
                renderHudView(graphics, mouseX, mouseY)
            }
            "Settings" -> {
                renderSettingsView(graphics, mouseX, mouseY)
            }
        }

        // Render sleek scrollbar if overflowing
        val maxScroll = calculateMaxScroll()
        if (maxScroll > 0) {
            val viewH = height - headerLineY - 14
            val barH = ((viewH.toFloat() / (viewH + maxScroll).toFloat()) * viewH).toInt().coerceAtLeast(20)
            val barY = headerLineY + 14 + ((scrollOffset.toFloat() / maxScroll.toFloat()) * (viewH - barH)).toInt()

            graphics.fill(width - 6, headerLineY + 14, width - 2, height - 6, 0x1A334155)
            graphics.fill(width - 6, barY, width - 2, barY + barH, 0xFF38BDF8.toInt())
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        // Dropdown Overlay for Farming Mode
        if (isFarmingActive && farmingSubTab == 0 && isModeDropdownOpen) {
            val dropX = cardX + cardW - 130
            val dropY = cardY + 46 - scrollOffset
            val dropW = 118
            val itemH = 18
            val dropH = modeOptions.size * itemH

            graphics.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF334155.toInt())
            graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF0F172A.toInt())

            for ((i, opt) in modeOptions.withIndex()) {
                val iy = dropY + (i * itemH)
                val isHovered = mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)
                val isSelected = i == currentModeIndex

                if (isSelected) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, 0xFF0284C7.toInt())
                } else if (isHovered) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, 0xFF1E293B.toInt())
                }

                val textColor = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF38BDF8.toInt() else 0xFFCBD5E1.toInt()
                graphics.text(font, opt, dropX + 8, iy + 5, textColor)
            }
        }
    }

    private fun renderFarmingMacroCard(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val effY = cardY - scrollOffset
        val isClickableAreaHovered = mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= effY && mouseY <= effY + cardH

        val cardBg = if (isClickableAreaHovered) 0xFF1E293B.toInt() else 0xFF141D2D.toInt()
        val cardBorder = if (isClickableAreaHovered) 0xFF38BDF8.toInt() else 0xFF334155.toInt()

        graphics.fill(cardX - 1, effY - 1, cardX + cardW + 1, effY + cardH + 1, cardBorder)
        graphics.fill(cardX, effY, cardX + cardW, effY + cardH, cardBg)

        val headerBg = if (isClickableAreaHovered) 0xFF334155.toInt() else 0xFF1E293B.toInt()
        graphics.fill(cardX, effY, cardX + cardW, effY + 20, headerBg)
        graphics.text(font, "§b§lCrop Farming", cardX + 12, effY + 6, 0xFF38BDF8.toInt())

        val actionText = if (MacroController.isRunning) "§a§lClick to Stop Farming" else "§lClick to Start Farming"
        val actionColor = if (isClickableAreaHovered) 0xFF4ADE80.toInt() else 0xFFFFFFFF.toInt()
        graphics.text(font, actionText, cardX + 14, effY + 36, actionColor)

        graphics.text(font, "Supported:", cardX + 14, effY + 60, 0xFF64748B.toInt())
        val labelWidth = font.width("Supported: ")
        var cropIconX = cardX + 14 + labelWidth
        val cropIconY = effY + 56
        for (cropItem in CROP_ITEMS) {
            graphics.item(cropItem, cropIconX, cropIconY)
            cropIconX += 18
        }
    }

    private fun renderPesterMacroCard(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val effY = cardY - scrollOffset
        val isHovered = mouseX in cardX until (cardX + cardW) && mouseY in effY until (effY + cardH)
        val cardBg = if (isHovered) 0xFF1E293B.toInt() else 0xFF141D2D.toInt()
        val cardBorder = if (isHovered) 0xFF38BDF8.toInt() else 0xFF334155.toInt()

        graphics.fill(cardX - 1, effY - 1, cardX + cardW + 1, effY + cardH + 1, cardBorder)
        graphics.fill(cardX, effY, cardX + cardW, effY + cardH, cardBg)

        val headerBg = if (isHovered) 0xFF334155.toInt() else 0xFF1E293B.toInt()
        graphics.fill(cardX, effY, cardX + cardW, effY + 20, headerBg)
        graphics.text(font, "§b§lPest Destroyer", cardX + 12, effY + 6, 0xFF38BDF8.toInt())

        val actionText = "§lClick to Start Pest Destroyer"
        val actionColor = if (isHovered) 0xFF4ADE80.toInt() else 0xFFFFFFFF.toInt()
        graphics.text(font, actionText, cardX + 14, effY + 44, actionColor)
    }

    private fun renderMiscMacroCard(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val effY = cardY - scrollOffset
        val isHovered = mouseX in cardX until (cardX + cardW) && mouseY in effY until (effY + cardH)

        val cardBg = if (isHovered) 0xFF1E293B.toInt() else 0xFF141D2D.toInt()
        val cardBorder = if (isHovered) 0xFF38BDF8.toInt() else 0xFF334155.toInt()

        graphics.fill(cardX - 1, effY - 1, cardX + cardW + 1, effY + cardH + 1, cardBorder)
        graphics.fill(cardX, effY, cardX + cardW, effY + cardH, cardBg)

        val headerBg = if (isHovered) 0xFF334155.toInt() else 0xFF1E293B.toInt()
        graphics.fill(cardX, effY, cardX + cardW, effY + 20, headerBg)
        graphics.text(font, "§b§lAuto Bouncy Ball", cardX + 12, effY + 6, 0xFF38BDF8.toInt())

        val actionText = "§lClick to Start Auto Bouncy Ball"
        val actionColor = if (isHovered) 0xFF4ADE80.toInt() else 0xFFFFFFFF.toInt()
        graphics.text(font, actionText, cardX + 14, effY + 44, actionColor)
    }

    private fun renderMiscConfig(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sec1Y = cardY - scrollOffset
        val sec1H = 88
        graphics.fill(cardX - 1, sec1Y - 1, cardX + cardW + 1, sec1Y + sec1H + 1, 0xFF334155.toInt())
        graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + sec1H, 0xFF1E293B.toInt())
        graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lBouncy Beach Ball", cardX + 10, sec1Y + 5, 0xFF38BDF8.toInt())
        graphics.text(font, "Movement Mode:", cardX + 12, sec1Y + 25, 0xFF94A3B8.toInt())
        graphics.text(font, "Target Bounces:", cardX + 12, sec1Y + 45, 0xFF94A3B8.toInt())
        graphics.text(font, "Go back to Start:", cardX + 12, sec1Y + 67, 0xFF94A3B8.toInt())
    }

    private fun renderHudView(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val secY = cardY - scrollOffset
        val secH = SEC_HUD_H
        graphics.fill(cardX - 1, secY - 1, cardX + cardW + 1, secY + secH + 1, 0xFF334155.toInt())
        graphics.fill(cardX, secY, cardX + cardW, secY + secH, 0xFF1E293B.toInt())
        graphics.fill(cardX, secY, cardX + cardW, secY + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lHUD Config", cardX + 10, secY + 5, 0xFF38BDF8.toInt())

        graphics.text(font, "Macro Status:", cardX + 12, secY + 25, 0xFF94A3B8.toInt())
        val opacityColor = if (ConfigManager.config.hud.enabled) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
        graphics.text(font, "Background Opacity:", cardX + 12, secY + 47, opacityColor)
        graphics.text(font, "HUD Edit:", cardX + 12, secY + 69, 0xFF94A3B8.toInt())
    }

    private fun renderColorSwatch(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, swatchX: Int, swatchY: Int, hexColor: String) {
        val swatchW = 20
        val swatchH = 16
        val (r, g, b) = DungeonESP.parseRgb(hexColor)
        val isSwatchHovered = mouseX in swatchX until (swatchX + swatchW) && mouseY in swatchY until (swatchY + swatchH)
        val swatchBorder = if (isSwatchHovered) 0xFF38BDF8.toInt() else 0xFF475569.toInt()
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchW + 1, swatchY + swatchH + 1, swatchBorder)
        graphics.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, ARGB.color(255, r, g, b))
    }

    private fun renderEspView(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val dngY = cardY - scrollOffset
        val dngH = SEC_DUNGEON_ESP_H
        val dngCfg = ConfigManager.config.dungeon

        // 1. Dungeon Card (Top)
        graphics.fill(cardX - 1, dngY - 1, cardX + cardW + 1, dngY + dngH + 1, 0xFF334155.toInt())
        graphics.fill(cardX, dngY, cardX + cardW, dngY + dngH, 0xFF1E293B.toInt())
        graphics.fill(cardX, dngY, cardX + cardW, dngY + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lDungeon", cardX + 10, dngY + 5, 0xFF38BDF8.toInt())

        graphics.text(font, "Bat ESP:", cardX + 12, dngY + 25, 0xFF94A3B8.toInt())
        graphics.text(font, "StarMobs ESP:", cardX + 12, dngY + 47, 0xFF94A3B8.toInt())
        graphics.text(font, "Lost Adventurer:", cardX + 12, dngY + 69, 0xFF94A3B8.toInt())
        graphics.text(font, "Shadow Assassin:", cardX + 12, dngY + 91, 0xFF94A3B8.toInt())
        graphics.text(font, "Diamond Guy:", cardX + 12, dngY + 113, 0xFF94A3B8.toInt())

        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, dngY + 20, dngCfg.batEspColor)
        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, dngY + 42, dngCfg.starMobsEspColor)
        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, dngY + 64, dngCfg.lostAdventurerColor)
        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, dngY + 86, dngCfg.shadowAssassinColor)
        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, dngY + 108, dngCfg.diamondGuyColor)

        // 2. Other ESP Card (Below Dungeon)
        val otherY = dngY + SEC_DUNGEON_ESP_GAP
        val otherH = SEC_OTHER_ESP_H
        graphics.fill(cardX - 1, otherY - 1, cardX + cardW + 1, otherY + otherH + 1, 0xFF334155.toInt())
        graphics.fill(cardX, otherY, cardX + cardW, otherY + otherH, 0xFF1E293B.toInt())
        graphics.fill(cardX, otherY, cardX + cardW, otherY + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lOther ESP", cardX + 10, otherY + 5, 0xFF38BDF8.toInt())

        graphics.text(font, "Pest ESP:", cardX + 12, otherY + 25, 0xFF94A3B8.toInt())
        renderColorSwatch(graphics, mouseX, mouseY, cardX + 326, otherY + 20, ConfigManager.config.pestDestroyer.pestEspColor)
    }

    private fun renderFarmingConfig(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sec1Y = cardY - scrollOffset
        val sec1H = 42
        graphics.fill(cardX - 1, sec1Y - 1, cardX + cardW + 1, sec1Y + sec1H + 1, 0xFF334155.toInt())
        graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + sec1H, 0xFF1E293B.toInt())
        graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lAnti-Stuck", cardX + 10, sec1Y + 5, 0xFF38BDF8.toInt())
        graphics.text(font, "Check Flying:", cardX + 12, sec1Y + 25, 0xFF94A3B8.toInt())

        val sec2Y = sec1Y + 44
        val sec2H = 64
        graphics.fill(cardX - 1, sec2Y - 1, cardX + cardW + 1, sec2Y + sec2H + 1, 0xFF334155.toInt())
        graphics.fill(cardX, sec2Y, cardX + cardW, sec2Y + sec2H, 0xFF1E293B.toInt())
        graphics.fill(cardX, sec2Y, cardX + cardW, sec2Y + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lAuto Pest Trigger", cardX + 10, sec2Y + 5, 0xFF38BDF8.toInt())
        graphics.text(font, "Auto Active Pest:", cardX + 12, sec2Y + 25, 0xFF94A3B8.toInt())
        graphics.text(font, "Pest Count Trigger:", cardX + 12, sec2Y + 45, 0xFF94A3B8.toInt())
    }

    private fun renderPesterConfig(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val pdY = cardY - scrollOffset
        val pdH = 156
        graphics.fill(cardX - 1, pdY - 1, cardX + cardW + 1, pdY + pdH + 1, 0xFF334155.toInt())
        graphics.fill(cardX, pdY, cardX + cardW, pdY + pdH, 0xFF1E293B.toInt())
        graphics.fill(cardX, pdY, cardX + cardW, pdY + 18, 0xFF334155.toInt())
        graphics.text(font, "§b§lPest Destroyer Config", cardX + 10, pdY + 5, 0xFF38BDF8.toInt())

        graphics.text(font, "Pest ESP:", cardX + 12, pdY + 25, 0xFF94A3B8.toInt())

        // Pest ESP Color Swatch (on the right of toggle pill)
        val swatchX = cardX + 326
        val swatchY = pdY + 20
        val swatchW = 20
        val swatchH = 16
        val (r, g, b) = PestESP.parseRgb(ConfigManager.config.pestDestroyer.pestEspColor)
        val isSwatchHovered = mouseX in swatchX until (swatchX + swatchW) && mouseY in swatchY until (swatchY + swatchH)
        val swatchBorder = if (isSwatchHovered) 0xFF38BDF8.toInt() else 0xFF475569.toInt()
        graphics.fill(swatchX - 1, swatchY - 1, swatchX + swatchW + 1, swatchY + swatchH + 1, swatchBorder)
        graphics.fill(swatchX, swatchY, swatchX + swatchW, swatchY + swatchH, ARGB.color(255, r, g, b))

        graphics.text(font, "Get On Rooftop:", cardX + 12, pdY + 47, 0xFF94A3B8.toInt())
        graphics.text(font, "Teleportable plots:", cardX + 12, pdY + 69, 0xFF94A3B8.toInt())
        graphics.text(font, "Keep pest:", cardX + 12, pdY + 93, 0xFF94A3B8.toInt())
        graphics.text(font, "Plots to leave pest:", cardX + 12, pdY + 115, 0xFF94A3B8.toInt())
        graphics.text(font, "Derpy Mayor Active:", cardX + 12, pdY + 137, 0xFF94A3B8.toInt())
    }

    private fun renderSettingsView(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        when (settingsSubTab) {
            0 -> {
                // Movement Sub-Tab: Mouse Movement + Pathfinding & Flying
                val mmY = cardY - scrollOffset
                val mmH = SEC_MOUSE_H
                graphics.fill(cardX - 1, mmY - 1, cardX + cardW + 1, mmY + mmH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, mmY, cardX + cardW, mmY + mmH, 0xFF1E293B.toInt())
                graphics.fill(cardX, mmY, cardX + cardW, mmY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lMouse Movement", cardX + 10, mmY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Movement Type:", cardX + 12, mmY + 25, 0xFF94A3B8.toInt())
                graphics.text(font, "Humanize:", cardX + 12, mmY + 45, 0xFF94A3B8.toInt())
                graphics.text(font, "Overshoot:", cardX + 12, mmY + 65, 0xFF94A3B8.toInt())
                graphics.text(font, "DPI Speed:", cardX + 12, mmY + 85, 0xFF94A3B8.toInt())

                val pfY = mmY + SEC_MOUSE_GAP
                val pfH = SEC_PATHFINDING_H
                graphics.fill(cardX - 1, pfY - 1, cardX + cardW + 1, pfY + pfH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, pfY, cardX + cardW, pfY + pfH, 0xFF1E293B.toInt())
                graphics.fill(cardX, pfY, cardX + cardW, pfY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lPathfinding & Flying", cardX + 10, pfY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Pathfinding Algorithm:", cardX + 12, pfY + 25, 0xFF94A3B8.toInt())
                graphics.text(font, "Brake after reaching destination:", cardX + 12, pfY + 45, 0xFF94A3B8.toInt())
                graphics.text(font, "Pathfinding Visualizer:", cardX + 12, pfY + 65, 0xFF94A3B8.toInt())
                val verboseColor = if (verboseVisualizerPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                graphics.text(font, "§8└─ §rVerbose Visualizer:", cardX + 18, pfY + 85, verboseColor)
            }
            1 -> {
                // Failsafe Sub-Tab: WatchDog + Key and Mouse Lock
                val wdY = cardY - scrollOffset
                val wdH = SEC_WATCHDOG_H
                graphics.fill(cardX - 1, wdY - 1, cardX + cardW + 1, wdY + wdH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, wdY, cardX + cardW, wdY + wdH, 0xFF1E293B.toInt())
                graphics.fill(cardX, wdY, cardX + cardW, wdY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lWatchDog", cardX + 10, wdY + 5, 0xFF38BDF8.toInt())

                graphics.text(font, "Check Rotation:", cardX + 12, wdY + 25, 0xFF94A3B8.toInt())
                val debounceColor = if (debounceRotationPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                graphics.text(font, "  └ Admin Snapback Grace:", cardX + 12, wdY + 45, debounceColor)
                graphics.text(font, "Check Teleport:", cardX + 12, wdY + 65, 0xFF94A3B8.toInt())
                graphics.text(font, "Check Hotbar Slot:", cardX + 12, wdY + 85, 0xFF94A3B8.toInt())
                graphics.text(font, "Farming Interruption failsafe:", cardX + 12, wdY + 105, 0xFF94A3B8.toInt())
                val bpsDropColor = if (checkBpsDropPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                graphics.text(font, "  └ BPS Drop Protection:", cardX + 12, wdY + 125, bpsDropColor)
                graphics.text(font, "Unfamiliar GUI failsafe:", cardX + 12, wdY + 145, 0xFF94A3B8.toInt())

                val lockY = wdY + SEC_WATCHDOG_GAP
                val lockH = SEC_LOCK_H
                graphics.fill(cardX - 1, lockY - 1, cardX + cardW + 1, lockY + lockH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, lockY, cardX + cardW, lockY + lockH, 0xFF1E293B.toInt())
                graphics.fill(cardX, lockY, cardX + cardW, lockY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lKey and Mouse Lock", cardX + 10, lockY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Lock Hotbar Keys:", cardX + 12, lockY + 25, 0xFF94A3B8.toInt())
                graphics.text(font, "Lock Movement Keys:", cardX + 12, lockY + 45, 0xFF94A3B8.toInt())
                val lockOtherLabelColor = if (lockAllOtherKeybindsPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                graphics.text(font, "Lock All Other Keybinds:", cardX + 12, lockY + 65, lockOtherLabelColor)
                graphics.text(font, "Lock Mouse Movement:", cardX + 12, lockY + 85, 0xFF94A3B8.toInt())
                graphics.text(font, "Block Chat and Command:", cardX + 12, lockY + 105, 0xFF94A3B8.toInt())
            }
            2 -> {
                // QOL Sub-Tab: Free Look & Freecam
                val flY = cardY - scrollOffset
                val flH = SEC_FREELOOK_H
                graphics.fill(cardX - 1, flY - 1, cardX + cardW + 1, flY + flH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, flY, cardX + cardW, flY + flH, 0xFF1E293B.toInt())
                graphics.fill(cardX, flY, cardX + cardW, flY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lFree Look", cardX + 10, flY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Activation Mode:", cardX + 12, flY + 25, 0xFF94A3B8.toInt())
                graphics.text(font, "Invert Zoom:", cardX + 12, flY + 45, 0xFF94A3B8.toInt())
                graphics.text(font, "Remember Zoom (Max 25b):", cardX + 12, flY + 65, 0xFF94A3B8.toInt())
                graphics.text(font, "Respect Invert Mouse:", cardX + 12, flY + 85, 0xFF94A3B8.toInt())

                val fcY = flY + SEC_FREELOOK_GAP
                val fcH = SEC_FREECAM_H
                graphics.fill(cardX - 1, fcY - 1, cardX + cardW + 1, fcY + fcH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, fcY, cardX + cardW, fcY + fcH, 0xFF1E293B.toInt())
                graphics.fill(cardX, fcY, cardX + cardW, fcY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lFreecam", cardX + 10, fcY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Flight Speed:", cardX + 12, fcY + 25, 0xFF94A3B8.toInt())
                graphics.text(font, "Hide GUI:", cardX + 12, fcY + 45, 0xFF94A3B8.toInt())

                val asY = fcY + SEC_FREECAM_GAP
                val asH = SEC_AUTOSPRINT_H
                graphics.fill(cardX - 1, asY - 1, cardX + cardW + 1, asY + asH + 1, 0xFF334155.toInt())
                graphics.fill(cardX, asY, cardX + cardW, asY + asH, 0xFF1E293B.toInt())
                graphics.fill(cardX, asY, cardX + cardW, asY + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lAuto Sprint", cardX + 10, asY + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Auto Sprint:", cardX + 12, asY + 25, 0xFF94A3B8.toInt())
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()
        val effY = cardY - scrollOffset

        if (event.button() == 0) {
            // Sidebar item clicks
            if (mouseX in 6 until (sidebarWidth - 6)) {
                val farmingBoxY = headerLineY + 14
                val farmingBoxH = 24
                if (mouseY in farmingBoxY until (farmingBoxY + farmingBoxH)) {
                    selectedTab = "Farming"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }

                val pesterBoxY = farmingBoxY + farmingBoxH + 4
                val pesterBoxH = 24
                if (mouseY in pesterBoxY until (pesterBoxY + pesterBoxH)) {
                    selectedTab = "Pester"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }

                val miscBoxY = pesterBoxY + pesterBoxH + 4
                val miscBoxH = 24
                if (mouseY in miscBoxY until (miscBoxY + miscBoxH)) {
                    selectedTab = "Misc"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }

                val settingsBoxH = 24
                val settingsBoxY = height - settingsBoxH - 12
                val hudBoxH = 24
                val hudBoxY = settingsBoxY - hudBoxH - 4
                val espBoxH = 24
                val espBoxY = hudBoxY - espBoxH - 4

                if (mouseY in espBoxY until (espBoxY + espBoxH)) {
                    selectedTab = "ESP"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }

                if (mouseY in hudBoxY until (hudBoxY + hudBoxH)) {
                    selectedTab = "HUD"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }

                if (mouseY in settingsBoxY until (settingsBoxY + settingsBoxH)) {
                    selectedTab = "Settings"
                    isModeDropdownOpen = false
                    scrollOffset = 0
                    updateWidgetVisibility()
                    return true
                }
            }

            // Farming Macro Card Click
            if (selectedTab == "Farming" && farmingSubTab == 0 && mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= effY && mouseY <= effY + cardH) {
                if (MacroController.isRunning) {
                    MacroController.stopMacro(reason = "GUI Toggle")
                } else {
                    MacroController.startMacro()
                }
                onClose()
                return true
            }

            // Pester Macro Card Click
            if (selectedTab == "Pester" && pesterSubTab == 0 && mouseX in cardX until (cardX + cardW) && mouseY in effY until (effY + cardH)) {
                if (PestDestroyerEngine.isRunning) {
                    PestDestroyerEngine.stopPestDestroyer(reason = "GUI Toggle")
                } else {
                    PestDestroyerEngine.startPestDestroyer()
                }
                onClose()
                return true
            }

            // Misc Macro Card Click
            if (selectedTab == "Misc" && miscSubTab == 0 && mouseX in cardX until (cardX + cardW) && mouseY in effY until (effY + cardH)) {
                if (com.hypcro.bouncy.AutoBouncyBall.isRunning) {
                    com.hypcro.bouncy.AutoBouncyBall.stop()
                } else {
                    com.hypcro.bouncy.AutoBouncyBall.start()
                }
                onClose()
                return true
            }

            // ESP Tab Color Swatch Clicks
            if (selectedTab == "ESP" && mouseX in (cardX + 326) until (cardX + 346)) {
                // Dungeon Card Swatches
                if (mouseY in (effY + 20) until (effY + 36)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.dungeon.batEspColor) { newHex ->
                        ConfigManager.config.dungeon.batEspColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }
                if (mouseY in (effY + 42) until (effY + 58)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.dungeon.starMobsEspColor) { newHex ->
                        ConfigManager.config.dungeon.starMobsEspColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }
                if (mouseY in (effY + 64) until (effY + 80)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.dungeon.lostAdventurerColor) { newHex ->
                        ConfigManager.config.dungeon.lostAdventurerColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }
                if (mouseY in (effY + 86) until (effY + 102)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.dungeon.shadowAssassinColor) { newHex ->
                        ConfigManager.config.dungeon.shadowAssassinColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }
                if (mouseY in (effY + 108) until (effY + 124)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.dungeon.diamondGuyColor) { newHex ->
                        ConfigManager.config.dungeon.diamondGuyColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }

                // Other ESP Card (Pest ESP) Swatch
                val otherY = effY + SEC_DUNGEON_ESP_GAP
                if (mouseY in (otherY + 20) until (otherY + 36)) {
                    minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.pestDestroyer.pestEspColor) { newHex ->
                        ConfigManager.config.pestDestroyer.pestEspColor = newHex
                        ConfigManager.save()
                    })
                    return true
                }
            }

            // Pester Config Color Swatch Click
            if (selectedTab == "Pester" && pesterSubTab == 1 && mouseX in (cardX + 326) until (cardX + 346) && mouseY in (effY + 20) until (effY + 36)) {
                minecraft.gui.setScreen(ColorPickerModal(this, ConfigManager.config.pestDestroyer.pestEspColor) { newHex ->
                    ConfigManager.config.pestDestroyer.pestEspColor = newHex
                    ConfigManager.save()
                })
                return true
            }

            // Farming Mode Dropdown Menu
            if (selectedTab == "Farming" && farmingSubTab == 0 && isModeDropdownOpen) {
                val dropX = cardX + cardW - 130
                val dropY = effY + 46
                val dropW = 118
                val itemH = 18

                for ((i, opt) in modeOptions.withIndex()) {
                    val iy = dropY + (i * itemH)
                    if (mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)) {
                        currentModeIndex = i
                        modeDropdownBtn.message = Component.literal("Mode: $opt ▼")
                        isModeDropdownOpen = false
                        ConfigManager.config.activeMethod = if (i == 1) "VERTICAL" else "WS"
                        ConfigManager.save()
                        return true
                    }
                }
                isModeDropdownOpen = false
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || event.key() == GLFW.GLFW_KEY_END) {
            if (isModeDropdownOpen) {
                isModeDropdownOpen = false
                return true
            }
            onClose()
            return true
        }
        return super.keyPressed(event)
    }
}
