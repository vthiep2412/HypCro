package com.hypcro.gui

import com.hypcro.config.ConfigManager
import com.hypcro.farming.MacroController
import com.hypcro.gui.widgets.InfoIconWidget
import com.hypcro.gui.widgets.PillToggleWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

import net.minecraft.client.renderer.RenderPipelines
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
    }

    private val sidebarWidth = 110
    private var selectedTab = "Farming" // "Farming" or "Settings"
    private var farmingSubTab = 0 // 0 = "Macro", 1 = "General Config"

    // Header division line Y position
    private val headerLineY = 32

    // Main Card Geometry
    private var cardX = 0
    private var cardY = 0
    private var cardW = 0
    private var cardH = 0

    // Farming Mode Dropdown
    private val modeOptions = listOf("W/S", "Vertical")
    private var currentModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0
    private var isModeDropdownOpen = false
    private lateinit var modeDropdownBtn: Button
    private lateinit var settingsBtn: Button
    private lateinit var farmingSubTabPill: PillToggleWidget

    // Settings - Free Look Widgets
    private lateinit var freeLookModePill: PillToggleWidget
    private lateinit var invertZoomPill: PillToggleWidget
    private lateinit var rememberZoomPill: PillToggleWidget
    private lateinit var respectInvertPill: PillToggleWidget
    private lateinit var respectInvertInfo: InfoIconWidget

    // Settings - Key and Mouse Lock Widgets
    private lateinit var keyMouseLockHeaderInfo: InfoIconWidget
    private lateinit var lockHotbarPill: PillToggleWidget
    private lateinit var lockMovementPill: PillToggleWidget
    private lateinit var lockAllOtherKeybindsPill: PillToggleWidget
    private lateinit var lockAllOtherKeybindsInfo: InfoIconWidget
    private lateinit var lockMousePill: PillToggleWidget
    private lateinit var blockChatAndCommandsPill: PillToggleWidget
    private lateinit var blockChatAndCommandsInfo: InfoIconWidget

    // General Config - Anti-Stuck Widgets
    private lateinit var checkFlyingPill: PillToggleWidget

    // General Config - WatchDog Widgets
    private lateinit var checkRotationPill: PillToggleWidget
    private lateinit var debounceRotationPill: PillToggleWidget
    private lateinit var debounceRotationInfo: InfoIconWidget
    private lateinit var checkTeleportPill: PillToggleWidget
    private lateinit var checkHotbarSlotPill: PillToggleWidget
    private lateinit var checkFarmingInterruptionPill: PillToggleWidget
    private lateinit var checkFarmingInterruptionInfo: InfoIconWidget
    private lateinit var checkUnfamiliarGuiPill: PillToggleWidget
    private lateinit var checkUnfamiliarGuiInfo: InfoIconWidget

    override fun init() {
        cardX = sidebarWidth + 16
        cardY = headerLineY + 14 // Pushed cleanly below full-width line
        cardW = width - cardX - 24
        cardH = 88

        currentModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0

        // 0. Top Farming Sub-Tab Navigation Pill (Macro | General Config)
        farmingSubTabPill = PillToggleWidget(
            cardX, 5, 170, 18,
            listOf("Macro", "General Config"), farmingSubTab
        ) { idx ->
            farmingSubTab = idx
            updateWidgetVisibility()
        }
        addRenderableWidget(farmingSubTabPill)

        // 1. Mode Selector Dropdown Button
        modeDropdownBtn = Button.builder(Component.literal("Mode: ${modeOptions[currentModeIndex]} ▼")) {
            isModeDropdownOpen = !isModeDropdownOpen
        }.bounds(cardX + cardW - 130, cardY + 24, 118, 20).build()
        addRenderableWidget(modeDropdownBtn)

        // 2. Crop Settings Button
        settingsBtn = Button.builder(Component.literal("⚙ Settings")) {
            minecraft.setScreen(CropSettingsModal(this))
        }.bounds(cardX + cardW - 130, cardY + 50, 118, 20).build()
        addRenderableWidget(settingsBtn)

        // 3. Settings View - Free Look Widgets
        val curMode = ConfigManager.config.qolConfig.freeLookMode
        val curModeIdx = if (curMode.equals("TOGGLE", ignoreCase = true)) 1 else 0
        freeLookModePill = PillToggleWidget(
            cardX + 220, cardY + 22, 100, 16,
            listOf("Hold", "Toggle"), curModeIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookMode = if (idx == 1) "TOGGLE" else "HOLD"
            ConfigManager.save()
        }
        addRenderableWidget(freeLookModePill)

        val curInvertIdx = if (ConfigManager.config.qolConfig.freeLookInvertZoom) 1 else 0
        invertZoomPill = PillToggleWidget(
            cardX + 220, cardY + 42, 100, 16,
            listOf("OFF", "ON"), curInvertIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookInvertZoom = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(invertZoomPill)

        val curRememberIdx = if (ConfigManager.config.qolConfig.freeLookRememberZoom) 1 else 0
        rememberZoomPill = PillToggleWidget(
            cardX + 220, cardY + 62, 100, 16,
            listOf("OFF", "ON"), curRememberIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookRememberZoom = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(rememberZoomPill)

        val curRespectMode = ConfigManager.config.qolConfig.freeLookRespectInvertMouse.uppercase()
        val curRespectIdx = when (curRespectMode) {
            "ALWAYS" -> 2
            "ON" -> 1
            else -> 0 // "OFF"
        }
        respectInvertPill = PillToggleWidget(
            cardX + 190, cardY + 82, 130, 16,
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

        val respectTooltipMsg = "§eRespect Minecraft Invert Mouse Settings\n" +
                "§7Options -> Controls -> Mouse Settings -> Invert Mouse X/Y\n" +
                "§7• §cOFF§7: Standard free look controls §6(Recommended)\n" +
                "§7• §aON§7: Free look inverts X/Y if your Minecraft settings are ON.\n" +
                "§7• §dAlways§7: Always invert X/Y axis."
        respectInvertInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Respect Invert Mouse:")) + 6, cardY + 84, respectTooltipMsg)
        addRenderableWidget(respectInvertInfo)

        // Settings View - Key and Mouse Lock Widgets
        val genCfg = ConfigManager.config.generalConfig
        val lockCardY = cardY + 112

        val keyMouseLockHeaderTooltip = "§eInput Lock\n§7Lock player actions and inputs while the macro is running."
        keyMouseLockHeaderInfo = InfoIconWidget(cardX + 10 + font.width(Component.literal("§b§lKey and Mouse Lock")) + 6, lockCardY + 5, keyMouseLockHeaderTooltip)
        addRenderableWidget(keyMouseLockHeaderInfo)

        lockHotbarPill = PillToggleWidget(
            cardX + 220, lockCardY + 22, 100, 16,
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
            cardX + 220, lockCardY + 42, 100, 16,
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

        val lockAllOtherTooltip = "§eLock All Other Keybinds\n" +
                "§7Lock every other key except F3, Tab, and your GUI open key.\n" +
                "§7For example, it will block SkyHanni Quick GUI Editor keybinds.\n" +
                "§cWarning: §7Requires both Hotbar and Movement Lock §lON§r§7 to take full effect."
        lockAllOtherKeybindsInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Lock All Other Keybinds:")) + 6, lockCardY + 64, lockAllOtherTooltip)
        addRenderableWidget(lockAllOtherKeybindsInfo)

        val canLockOtherInit = genCfg.inputLock.lockHotbar && genCfg.inputLock.lockMovement
        lockAllOtherKeybindsPill = PillToggleWidget(
            cardX + 220, lockCardY + 62, 100, 16,
            listOf("OFF", "ON"), if (canLockOtherInit && genCfg.inputLock.lockAllOtherKeybinds) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockAllOtherKeybinds = (idx == 1)
            ConfigManager.save()
        }
        lockAllOtherKeybindsPill.active = canLockOtherInit
        addRenderableWidget(lockAllOtherKeybindsPill)

        lockMousePill = PillToggleWidget(
            cardX + 220, lockCardY + 82, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.lockMouse) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.lockMouse = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(lockMousePill)

        val blockChatTooltip = "§eBlock Chat & Commands\n" +
                "§7Blocks outgoing chat messages and commands from sending to the server while macroing.\n" +
                "§7• There are many background QOL mods send commands without triggering anti-cheats.\n" +
                "§7• However, typing or executing commands during macroing can easily flag staff checks."
        blockChatAndCommandsInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Block Chat and Command:")) + 6, lockCardY + 104, blockChatTooltip)
        addRenderableWidget(blockChatAndCommandsInfo)

        blockChatAndCommandsPill = PillToggleWidget(
            cardX + 220, lockCardY + 102, 100, 16,
            listOf("OFF", "ON"), if (genCfg.inputLock.blockChatAndCommands) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.inputLock.blockChatAndCommands = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(blockChatAndCommandsPill)

        // 4. Farming -> General Config Widgets
        // Section 1: Anti-Stuck
        val sec1Y = cardY
        checkFlyingPill = PillToggleWidget(
            cardX + 220, sec1Y + 22, 100, 16,
            listOf("OFF", "ON"), if (genCfg.antiStuck.checkFlying) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.antiStuck.checkFlying = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkFlyingPill)

        // Section 2: WatchDog
        val sec2Y = sec1Y + 46
        checkRotationPill = PillToggleWidget(
            cardX + 220, sec2Y + 22, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkRotation) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkRotation = (idx == 1)
            ConfigManager.save()
            updateWidgetVisibility()
        }
        addRenderableWidget(checkRotationPill)

        val snapbackTooltip = "§eAdmin Snapback Protection\n" +
                "§7When staff abruptly rotate you and immediately rotate you back to test if you react like a bot, this adds a brief delay so you do NOT react and get falsely flagged."
        debounceRotationInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("  └ Admin Snapback Grace:")) + 6, sec2Y + 44, snapbackTooltip)
        addRenderableWidget(debounceRotationInfo)

        debounceRotationPill = PillToggleWidget(
            cardX + 220, sec2Y + 42, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkRotation && genCfg.watchdog.debounceRotation) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.debounceRotation = (idx == 1)
            ConfigManager.save()
        }
        debounceRotationPill.active = genCfg.watchdog.checkRotation
        addRenderableWidget(debounceRotationPill)

        checkTeleportPill = PillToggleWidget(
            cardX + 220, sec2Y + 62, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkTeleport) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkTeleport = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkTeleportPill)

        checkHotbarSlotPill = PillToggleWidget(
            cardX + 220, sec2Y + 82, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkHotbarSlot) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkHotbarSlot = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkHotbarSlotPill)

        val farmingInterruptionTooltip = "§eFarming Interruption Check\n" +
                "§7If you stop moving suspiciously, this will trigger."
        checkFarmingInterruptionInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Farming Interruption failsafe:")) + 6, sec2Y + 104, farmingInterruptionTooltip)
        addRenderableWidget(checkFarmingInterruptionInfo)

        checkFarmingInterruptionPill = PillToggleWidget(
            cardX + 220, sec2Y + 102, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkFarmingInterruption) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkFarmingInterruption = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkFarmingInterruptionPill)

        val unfamiliarGuiTooltip = "§eUnfamiliar GUI Check\n" +
                "§7If an unexpected GUI or captcha opens, this triggers the failsafe alarm."
        checkUnfamiliarGuiInfo = InfoIconWidget(cardX + 12 + font.width(Component.literal("Unfamiliar GUI failsafe:")) + 6, sec2Y + 124, unfamiliarGuiTooltip)
        addRenderableWidget(checkUnfamiliarGuiInfo)

        checkUnfamiliarGuiPill = PillToggleWidget(
            cardX + 220, sec2Y + 122, 100, 16,
            listOf("OFF", "ON"), if (genCfg.watchdog.checkUnfamiliarGui) 1 else 0
        ) { idx ->
            ConfigManager.config.generalConfig.watchdog.checkUnfamiliarGui = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(checkUnfamiliarGuiPill)

        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        val isFarming = selectedTab == "Farming"
        val isMacroView = isFarming && farmingSubTab == 0
        val isGeneralConfigView = isFarming && farmingSubTab == 1
        val isSettingsView = !isFarming

        farmingSubTabPill.visible = isFarming
        modeDropdownBtn.visible = isMacroView
        settingsBtn.visible = isMacroView
        if (!isMacroView) isModeDropdownOpen = false

        // Settings - Free Look Widgets
        freeLookModePill.visible = isSettingsView
        invertZoomPill.visible = isSettingsView
        rememberZoomPill.visible = isSettingsView
        respectInvertPill.visible = isSettingsView
        respectInvertInfo.visible = isSettingsView

        // Settings - Key and Mouse Lock Widgets
        val genCfg = ConfigManager.config.generalConfig
        val canLockOther = genCfg.inputLock.lockHotbar && genCfg.inputLock.lockMovement
        lockAllOtherKeybindsPill.active = canLockOther
        if (!canLockOther && genCfg.inputLock.lockAllOtherKeybinds) {
            genCfg.inputLock.lockAllOtherKeybinds = false
            lockAllOtherKeybindsPill.selectedIndex = 0
            ConfigManager.save()
        }

        keyMouseLockHeaderInfo.visible = isSettingsView
        lockHotbarPill.visible = isSettingsView
        lockMovementPill.visible = isSettingsView
        lockAllOtherKeybindsPill.visible = isSettingsView
        lockAllOtherKeybindsInfo.visible = isSettingsView
        lockMousePill.visible = isSettingsView
        blockChatAndCommandsPill.visible = isSettingsView
        blockChatAndCommandsInfo.visible = isSettingsView

        // General Config Widgets
        checkFlyingPill.visible = isGeneralConfigView
        checkRotationPill.visible = isGeneralConfigView
        val canDebounce = genCfg.watchdog.checkRotation
        debounceRotationPill.active = canDebounce
        if (!canDebounce && genCfg.watchdog.debounceRotation) {
            genCfg.watchdog.debounceRotation = false
            debounceRotationPill.selectedIndex = 0
            ConfigManager.save()
        }
        debounceRotationPill.visible = isGeneralConfigView
        debounceRotationInfo.visible = isGeneralConfigView
        checkTeleportPill.visible = isGeneralConfigView
        checkHotbarSlotPill.visible = isGeneralConfigView
        checkFarmingInterruptionPill.visible = isGeneralConfigView
        checkFarmingInterruptionInfo.visible = isGeneralConfigView
        checkUnfamiliarGuiPill.visible = isGeneralConfigView
        checkUnfamiliarGuiInfo.visible = isGeneralConfigView
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // 1. Dark theme background
        graphics.fill(0, 0, width, height, 0xFF0B0F19.toInt())

        // 2. Full-width top boundary line (stretches across the entire screen from 0 to width)
        graphics.fill(0, headerLineY, width, headerLineY + 1, 0xFF1E293B.toInt())

        // 3. Left sidebar background & vertical separator
        graphics.fill(0, 0, sidebarWidth, height, 0xFF0F172A.toInt())
        graphics.fill(sidebarWidth, 0, sidebarWidth + 1, height, 0xFF1E293B.toInt())

        // 4. Top Header Logo: Icon + HypCro (Centered vertically above header line)
        val titleY = (headerLineY - 9) / 2
        val iconSize = 20
        val iconY = (headerLineY - iconSize) / 2
        // Render 20x20 icon on screen with normalized 0.0f -> 1.0f UV coordinates
        graphics.blit(HYPCRO_ICON, 10, iconY, 10 + iconSize, iconY + iconSize, 0.0f, 1.0f, 0.0f, 1.0f)

        // Text: HypCro (Shifted right for the larger icon)
        graphics.text(font, "§b§lHypCro", 36, titleY, 0xFF38BDF8.toInt())

        // --- TOP SIDEBAR ITEM: Farming ---
        val farmingBoxY = headerLineY + 14
        val farmingBoxH = 24
        val farmingHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in farmingBoxY until (farmingBoxY + farmingBoxH)
        val isFarmingActive = selectedTab == "Farming"

        // Sidebar Item Border (top & bottom)
        val farmingBorderColor = if (isFarmingActive) 0xFF38BDF8.toInt() else if (farmingHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, farmingBoxY - 1, sidebarWidth - 6, farmingBoxY, farmingBorderColor) // top border
        graphics.fill(6, farmingBoxY + farmingBoxH, sidebarWidth - 6, farmingBoxY + farmingBoxH + 1, farmingBorderColor) // bottom border

        if (isFarmingActive) {
            graphics.fill(6, farmingBoxY, sidebarWidth - 6, farmingBoxY + farmingBoxH, 0x3338BDF8)
            graphics.text(font, "> Farming", 14, farmingBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (farmingHovered) graphics.fill(6, farmingBoxY, sidebarWidth - 6, farmingBoxY + farmingBoxH, 0x1A38BDF8)
            graphics.text(font, "  Farming", 14, farmingBoxY + 8, if (farmingHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // --- BOTTOM SIDEBAR ITEM: Settings ---
        val settingsBoxH = 24
        val settingsBoxY = height - settingsBoxH - 12
        val settingsHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in settingsBoxY until (settingsBoxY + settingsBoxH)
        val isSettingsActive = selectedTab == "Settings"

        val settingsBorderColor = if (isSettingsActive) 0xFF38BDF8.toInt() else if (settingsHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, settingsBoxY - 1, sidebarWidth - 6, settingsBoxY, settingsBorderColor) // top border
        graphics.fill(6, settingsBoxY + settingsBoxH, sidebarWidth - 6, settingsBoxY + settingsBoxH + 1, settingsBorderColor) // bottom border

        if (isSettingsActive) {
            graphics.fill(6, settingsBoxY, sidebarWidth - 6, settingsBoxY + settingsBoxH, 0x3338BDF8)
            graphics.text(font, "> Settings", 14, settingsBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (settingsHovered) graphics.fill(6, settingsBoxY, sidebarWidth - 6, settingsBoxY + settingsBoxH, 0x1A38BDF8)
            graphics.text(font, "  Settings", 14, settingsBoxY + 8, if (settingsHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // --- RIGHT-HAND VIEW RENDERING ---
        if (isFarmingActive) {
            if (farmingSubTab == 0) {
                val isClickableAreaHovered = mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= cardY && mouseY <= cardY + cardH

                val cardBg = if (isClickableAreaHovered) {
                    0xFF1E293B.toInt() // Elevated hover tone
                } else {
                    0xFF141D2D.toInt() // Subtle darker idle tone
                }

                val cardBorder = if (isClickableAreaHovered) 0xFF38BDF8.toInt() else 0xFF334155.toInt()

                // Outer container
                graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, cardBorder)
                graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, cardBg)

                // Header Bar without brackets, bold title
                val headerBg = if (isClickableAreaHovered) 0xFF334155.toInt() else 0xFF1E293B.toInt()
                graphics.fill(cardX, cardY, cardX + cardW, cardY + 20, headerBg)
                graphics.text(font, "§b§lCrop Farming", cardX + 12, cardY + 6, 0xFF38BDF8.toInt())

                // Main Action Text
                val actionColor = if (isClickableAreaHovered) 0xFF4ADE80.toInt() else 0xFFFFFFFF.toInt()
                graphics.text(font, "§lClick to Start Farming", cardX + 14, cardY + 36, actionColor)
                graphics.text(font, "Supported:", cardX + 14, cardY + 60, 0xFF64748B.toInt())
                val labelWidth = font.width("Supported: ")
                var cropIconX = cardX + 14 + labelWidth
                val cropIconY = cardY + 56
                for (cropItem in CROP_ITEMS) {
                    graphics.item(cropItem, cropIconX, cropIconY)
                    cropIconX += 18
                }
            } else {
                // General Config View (2 Clean Sections: Anti-Stuck & WatchDog)
                // Section 1: Anti-Stuck
                val sec1Y = cardY
                val sec1H = 42
                graphics.fill(cardX - 1, sec1Y - 1, cardX + cardW + 1, sec1Y + sec1H + 1, 0xFF334155.toInt())
                graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + sec1H, 0xFF1E293B.toInt())
                graphics.fill(cardX, sec1Y, cardX + cardW, sec1Y + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lAnti-Stuck", cardX + 10, sec1Y + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Check Flying:", cardX + 12, sec1Y + 25, 0xFF94A3B8.toInt())

                // Section 2: WatchDog
                val sec2Y = sec1Y + 46
                val sec2H = 142
                graphics.fill(cardX - 1, sec2Y - 1, cardX + cardW + 1, sec2Y + sec2H + 1, 0xFF334155.toInt())
                graphics.fill(cardX, sec2Y, cardX + cardW, sec2Y + sec2H, 0xFF1E293B.toInt())
                graphics.fill(cardX, sec2Y, cardX + cardW, sec2Y + 18, 0xFF334155.toInt())
                graphics.text(font, "§b§lWatchDog", cardX + 10, sec2Y + 5, 0xFF38BDF8.toInt())
                graphics.text(font, "Rotation failsafe:", cardX + 12, sec2Y + 25, 0xFF94A3B8.toInt())
                val snapbackColor = if (debounceRotationPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
                graphics.text(font, "  └ Admin Snapback Grace:", cardX + 12, sec2Y + 45, snapbackColor)
                graphics.text(font, "Teleport failsafe:", cardX + 12, sec2Y + 65, 0xFF94A3B8.toInt())
                graphics.text(font, "Hotbar-Slot Switch failsafe:", cardX + 12, sec2Y + 85, 0xFF94A3B8.toInt())
                graphics.text(font, "Farming Interruption failsafe:", cardX + 12, sec2Y + 105, 0xFF94A3B8.toInt())
                graphics.text(font, "Unfamiliar GUI failsafe:", cardX + 12, sec2Y + 125, 0xFF94A3B8.toInt())
            }
        } else {
            // Settings Tab View (Card 1: Free Look, Card 2: Key and Mouse Lock)
            // Card 1: Free Look
            val card1H = 104
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + card1H + 1, 0xFF334155.toInt())
            graphics.fill(cardX, cardY, cardX + cardW, cardY + card1H, 0xFF1E293B.toInt())
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 18, 0xFF334155.toInt())
            graphics.text(font, "§b§lFree Look", cardX + 10, cardY + 5, 0xFF38BDF8.toInt())

            graphics.text(font, "Activation Mode:", cardX + 12, cardY + 25, 0xFF94A3B8.toInt())
            graphics.text(font, "Invert Zoom:", cardX + 12, cardY + 45, 0xFF94A3B8.toInt())
            graphics.text(font, "Remember Zoom (Max 25b):", cardX + 12, cardY + 65, 0xFF94A3B8.toInt())
            graphics.text(font, "Respect Invert Mouse:", cardX + 12, cardY + 85, 0xFF94A3B8.toInt())

            // Card 2: Key and Mouse Lock
            val card2Y = cardY + 112
            val card2H = 124
            graphics.fill(cardX - 1, card2Y - 1, cardX + cardW + 1, card2Y + card2H + 1, 0xFF334155.toInt())
            graphics.fill(cardX, card2Y, cardX + cardW, card2Y + card2H, 0xFF1E293B.toInt())
            graphics.fill(cardX, card2Y, cardX + cardW, card2Y + 18, 0xFF334155.toInt())
            graphics.text(font, "§b§lKey and Mouse Lock", cardX + 10, card2Y + 5, 0xFF38BDF8.toInt())

            graphics.text(font, "Lock Hotbar Keys:", cardX + 12, card2Y + 25, 0xFF94A3B8.toInt())
            graphics.text(font, "Lock Movement Keys:", cardX + 12, card2Y + 45, 0xFF94A3B8.toInt())
            val lockOtherLabelColor = if (lockAllOtherKeybindsPill.active) 0xFF94A3B8.toInt() else 0xFF475569.toInt()
            graphics.text(font, "Lock All Other Keybinds:", cardX + 12, card2Y + 65, lockOtherLabelColor)
            graphics.text(font, "Lock Mouse Movement:", cardX + 12, card2Y + 85, 0xFF94A3B8.toInt())
            graphics.text(font, "Block Chat and Command:", cardX + 12, card2Y + 105, 0xFF94A3B8.toInt())
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        // Render Mode Dropdown Popup overlay if open (only during farming tab)
        if (isFarmingActive && isModeDropdownOpen) {
            val dropX = cardX + cardW - 130
            val dropY = cardY + 46
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

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        if (event.button() == 0) {
            // Check Sidebar clicks
            if (mouseX in 6 until (sidebarWidth - 6)) {
                val farmingBoxY = headerLineY + 14
                val farmingBoxH = 24
                if (mouseY in farmingBoxY until (farmingBoxY + farmingBoxH)) {
                    selectedTab = "Farming"
                    isModeDropdownOpen = false
                    updateWidgetVisibility()
                    return true
                }

                val settingsBoxH = 24
                val settingsBoxY = height - settingsBoxH - 12
                if (mouseY in settingsBoxY until (settingsBoxY + settingsBoxH)) {
                    selectedTab = "Settings"
                    isModeDropdownOpen = false
                    updateWidgetVisibility()
                    return true
                }
            }

            // Farming Mode Dropdown selection
            if (selectedTab == "Farming" && isModeDropdownOpen) {
                val dropX = cardX + cardW - 130
                val dropY = cardY + 46
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

            // Farming Card start/stop toggle (Macro tab only)
            if (selectedTab == "Farming" && farmingSubTab == 0 && mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= cardY && mouseY <= cardY + cardH) {
                if (MacroController.isRunning) {
                    MacroController.stopMacro(reason = "GUI Toggle")
                } else {
                    MacroController.startMacro()
                }
                onClose()
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
