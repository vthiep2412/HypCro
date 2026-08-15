package com.hypcro.gui

import com.hypcro.farming.WSFarmEngine
import com.hypcro.gui.widgets.SectionBoxWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class MainFarmingScreen : Screen(Component.literal("Hypcro Deck")) {

    private val sidebarWidth = 110
    private var cardX = 0
    private var cardY = 0
    private var cardW = 0
    private var cardH = 0

    private val modeOptions = listOf("W/S", "Vertical")
    private var currentModeIndex = 0
    private var isModeDropdownOpen = false
    private lateinit var modeDropdownBtn: Button

    override fun init() {
        cardX = sidebarWidth + 16
        cardY = 24
        cardW = width - cardX - 24
        cardH = 85

        // 1. Background Section Box
        addRenderableWidget(SectionBoxWidget("CROP FARMING", cardX, cardY, cardW, cardH))

        // 2. Mode Selector Dropdown Button (Cleanly placed on right side of card)
        modeDropdownBtn = Button.builder(Component.literal("Mode: ${modeOptions[currentModeIndex]} ▼")) {
            isModeDropdownOpen = !isModeDropdownOpen
        }.bounds(cardX + cardW - 130, cardY + 22, 118, 20).build()
        addRenderableWidget(modeDropdownBtn)

        // 3. Settings Button (Cleanly placed below the dropdown)
        addRenderableWidget(
            Button.builder(Component.literal("⚙ Settings")) {
                minecraft.setScreen(CropSettingsModal(this))
            }.bounds(cardX + cardW - 130, cardY + 48, 118, 20).build()
        )
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Dark theme background
        graphics.fill(0, 0, width, height, 0xFF0B0F19.toInt())

        // Left sidebar
        graphics.fill(0, 0, sidebarWidth, height, 0xFF0F172A.toInt())
        graphics.fill(sidebarWidth, 0, sidebarWidth + 1, height, 0xFF1E293B.toInt())

        // App logo & category item
        graphics.text(font, "HYPCRO", 14, 16, 0xFF38BDF8.toInt())
        graphics.fill(4, 38, sidebarWidth - 4, 58, 0x3338BDF8)
        graphics.text(font, "> Farming", 12, 44, 0xFFFFFFFF.toInt())

        // Card status & details
        val statusColor = if (WSFarmEngine.isRunning) 0xFF4ADE80.toInt() else 0xFF94A3B8.toInt()
        val statusText = if (WSFarmEngine.isRunning) "Running (Click to Stop)" else "Idle (Click card to Start)"

        graphics.text(font, "Status: $statusText", cardX + 14, cardY + 32, statusColor)
        graphics.text(font, "Supported: Wheat, Carrot, Potato, Nether Wart, Mushroom", cardX + 14, cardY + 54, 0xFF64748B.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, delta)

        // Render Mode Dropdown Popup overlay if open
        if (isModeDropdownOpen) {
            val dropX = cardX + cardW - 130
            val dropY = cardY + 44
            val dropW = 118
            val itemH = 18
            val dropH = modeOptions.size * itemH

            graphics.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF334155.toInt())
            graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1E293B.toInt())

            for ((i, opt) in modeOptions.withIndex()) {
                val iy = dropY + (i * itemH)
                val isHovered = mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)
                if (isHovered || i == currentModeIndex) {
                    graphics.fill(dropX, iy, dropX + dropW, iy + itemH, if (i == currentModeIndex) 0xFF38BDF8.toInt() else 0xFF475569.toInt())
                }
                val textColor = if (i == currentModeIndex) 0xFF0F172A.toInt() else 0xFFF1F5F9.toInt()
                graphics.text(font, opt, dropX + 8, iy + 5, textColor)
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        if (isModeDropdownOpen && event.button() == 0) {
            val dropX = cardX + cardW - 130
            val dropY = cardY + 44
            val dropW = 118
            val itemH = 18

            for ((i, opt) in modeOptions.withIndex()) {
                val iy = dropY + (i * itemH)
                if (mouseX in dropX until (dropX + dropW) && mouseY in iy until (iy + itemH)) {
                    currentModeIndex = i
                    modeDropdownBtn.message = Component.literal("Mode: $opt ▼")
                    isModeDropdownOpen = false
                    return true
                }
            }
            isModeDropdownOpen = false
            return true
        }

        // Click on left/main area of the card toggles start/stop
        if (mouseX >= cardX && mouseX <= cardX + cardW - 136 && mouseY >= cardY && mouseY <= cardY + cardH) {
            if (event.button() == 0) {
                if (WSFarmEngine.isRunning) {
                    WSFarmEngine.stopMacro(reason = "GUI Toggle")
                } else {
                    WSFarmEngine.startMacro()
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
