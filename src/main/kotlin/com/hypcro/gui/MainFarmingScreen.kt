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
import org.lwjgl.glfw.GLFW

class MainFarmingScreen : Screen(Component.literal("HypCro Deck")) {

    private val sidebarWidth = 110
    private var selectedTab = "Farming" // "Farming" or "QOL Settings"

    // Header division line Y position
    private val headerLineY = 28

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

    // QOL Widgets
    private lateinit var freeLookModePill: PillToggleWidget
    private lateinit var invertZoomPill: PillToggleWidget
    private lateinit var rememberZoomPill: PillToggleWidget
    private lateinit var respectInvertPill: PillToggleWidget
    private lateinit var respectInvertInfo: InfoIconWidget

    // Click pulse feedback timer
    private var clickPulseStartTime: Long = 0L

    override fun init() {
        cardX = sidebarWidth + 16
        cardY = headerLineY + 14 // Pushed cleanly below full-width line
        cardW = width - cardX - 24
        cardH = 88

        currentModeIndex = if (ConfigManager.config.activeMethod.equals("VERTICAL", ignoreCase = true)) 1 else 0

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

        // 3. QOL Widgets
        val curMode = ConfigManager.config.qolConfig.freeLookMode
        val curModeIdx = if (curMode.equals("TOGGLE", ignoreCase = true)) 1 else 0
        freeLookModePill = PillToggleWidget(
            cardX + 175, cardY + 28, 140, 18,
            listOf("Hold", "Toggle"), curModeIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookMode = if (idx == 1) "TOGGLE" else "HOLD"
            ConfigManager.save()
        }
        addRenderableWidget(freeLookModePill)

        val curInvertIdx = if (ConfigManager.config.qolConfig.freeLookInvertZoom) 1 else 0
        invertZoomPill = PillToggleWidget(
            cardX + 175, cardY + 52, 140, 18,
            listOf("OFF", "ON"), curInvertIdx
        ) { idx ->
            ConfigManager.config.qolConfig.freeLookInvertZoom = (idx == 1)
            ConfigManager.save()
        }
        addRenderableWidget(invertZoomPill)

        val curRememberIdx = if (ConfigManager.config.qolConfig.freeLookRememberZoom) 1 else 0
        rememberZoomPill = PillToggleWidget(
            cardX + 175, cardY + 76, 140, 18,
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
            cardX + 175, cardY + 100, 140, 18,
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

        // Info Icon next to "Respect Invert Mouse" label
        val tooltipMsg = "§eRespect Minecraft Invert Mouse Settings\n" +
                "§7Options -> Controls -> Mouse Settings -> Invert Mouse X/Y\n" +
                "§7• §cOFF§7: Bruh, who use invert mouse? §6(Recommended)\n" +
                "§7• §aON§7: Free look will invert X/Y if your Minecraft settings are ON.\n" +
                "§7• §dAlways§7: Always invert X/Y §5(u weirdo)"
        respectInvertInfo = InfoIconWidget(cardX + 140, cardY + 104, tooltipMsg)
        addRenderableWidget(respectInvertInfo)

        updateWidgetVisibility()
    }

    private fun updateWidgetVisibility() {
        val isFarming = selectedTab == "Farming"
        modeDropdownBtn.visible = isFarming
        settingsBtn.visible = isFarming
        freeLookModePill.visible = !isFarming
        invertZoomPill.visible = !isFarming
        rememberZoomPill.visible = !isFarming
        respectInvertPill.visible = !isFarming
        respectInvertInfo.visible = !isFarming
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // 1. Dark theme background
        graphics.fill(0, 0, width, height, 0xFF0B0F19.toInt())

        // 2. Full-width top boundary line (stretches across the entire screen from 0 to width)
        graphics.fill(0, headerLineY, width, headerLineY + 1, 0xFF1E293B.toInt())

        // 3. Left sidebar background & vertical separator
        graphics.fill(0, 0, sidebarWidth, height, 0xFF0F172A.toInt())
        graphics.fill(sidebarWidth, 0, sidebarWidth + 1, height, 0xFF1E293B.toInt())

        // 4. Top Header Logo: Icon emblem + HypCro (Centered vertically above header line)
        val titleY = (headerLineY - 9) / 2
        // Crisp pixel-art emblem prefix
        graphics.fill(12, titleY - 1, 15, titleY + 9, 0xFF38BDF8.toInt())
        graphics.fill(16, titleY + 1, 19, titleY + 7, 0xFF0284C7.toInt())
        graphics.fill(13, titleY + 10, 18, titleY + 11, 0xFF38BDF8.toInt())

        // Text: HypCro (Case Sensitive & Bold)
        graphics.text(font, "§b§lHypCro", 24, titleY, 0xFF38BDF8.toInt())

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

        // --- BOTTOM SIDEBAR ITEM: QOL Settings ---
        val qolBoxH = 24
        val qolBoxY = height - qolBoxH - 12
        val qolHovered = mouseX in 6 until (sidebarWidth - 6) && mouseY in qolBoxY until (qolBoxY + qolBoxH)
        val isQolActive = selectedTab == "QOL Settings"

        val qolBorderColor = if (isQolActive) 0xFF38BDF8.toInt() else if (qolHovered) 0xFF475569.toInt() else 0xFF1E293B.toInt()
        graphics.fill(6, qolBoxY - 1, sidebarWidth - 6, qolBoxY, qolBorderColor) // top border
        graphics.fill(6, qolBoxY + qolBoxH, sidebarWidth - 6, qolBoxY + qolBoxH + 1, qolBorderColor) // bottom border

        if (isQolActive) {
            graphics.fill(6, qolBoxY, sidebarWidth - 6, qolBoxY + qolBoxH, 0x3338BDF8)
            graphics.text(font, "> QOL Settings", 10, qolBoxY + 8, 0xFFFFFFFF.toInt())
        } else {
            if (qolHovered) graphics.fill(6, qolBoxY, sidebarWidth - 6, qolBoxY + qolBoxH, 0x1A38BDF8)
            graphics.text(font, "  QOL Settings", 10, qolBoxY + 8, if (qolHovered) 0xFF38BDF8.toInt() else 0xFF94A3B8.toInt())
        }

        // --- RIGHT-HAND VIEW RENDERING ---
        if (isFarmingActive) {
            val isClickableAreaHovered = mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= cardY && mouseY <= cardY + cardH

            // Click pulse feedback
            val now = System.currentTimeMillis()
            val isPulsing = (now - clickPulseStartTime) in 0..120

            val cardBg = if (isPulsing) {
                0xFF0F172A.toInt() // Pulse dark on click
            } else if (isClickableAreaHovered) {
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

            // Status & details
            val statusColor = if (MacroController.isRunning) 0xFF4ADE80.toInt() else 0xFF94A3B8.toInt()
            val statusText = if (MacroController.isRunning) "Running (Click to Stop)" else "Idle (Click to Start)"

            graphics.text(font, "Status: $statusText", cardX + 14, cardY + 36, statusColor)
            graphics.text(font, "Supported: Wheat, Carrot, Potato, Nether Wart, Mushroom", cardX + 14, cardY + 60, 0xFF64748B.toInt())
        } else {
            // QOL Settings Card View (Height adjusted for 4 options)
            val qolCardH = 128
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + qolCardH + 1, 0xFF334155.toInt())
            graphics.fill(cardX, cardY, cardX + cardW, cardY + qolCardH, 0xFF1E293B.toInt())

            // Header Bar without brackets, bold title
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 20, 0xFF334155.toInt())
            graphics.text(font, "§b§lFree Look", cardX + 12, cardY + 6, 0xFF38BDF8.toInt())

            graphics.text(font, "Activation Mode:", cardX + 14, cardY + 32, 0xFF94A3B8.toInt())
            graphics.text(font, "Invert Zoom:", cardX + 14, cardY + 56, 0xFF94A3B8.toInt())
            graphics.text(font, "Remember Zoom (Max 25b):", cardX + 14, cardY + 80, 0xFF94A3B8.toInt())
            graphics.text(font, "Respect Invert Mouse:", cardX + 14, cardY + 104, 0xFF94A3B8.toInt())
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

                val qolBoxH = 24
                val qolBoxY = height - qolBoxH - 12
                if (mouseY in qolBoxY until (qolBoxY + qolBoxH)) {
                    selectedTab = "QOL Settings"
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

            // Farming Card start/stop toggle with click pulse feedback
            if (selectedTab == "Farming" && mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= cardY && mouseY <= cardY + cardH) {
                clickPulseStartTime = System.currentTimeMillis()

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
