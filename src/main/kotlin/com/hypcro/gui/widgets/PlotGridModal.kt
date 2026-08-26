package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class PlotGridModal(
    private val modalTitle: String,
    private val selectedPlots: MutableSet<Int>,
    private val parent: Screen,
    private val onSave: (Set<Int>) -> Unit
) : Screen(Component.literal(modalTitle)) {

    companion object {
        // 5x5 Garden Plot Layout (0 represents the Center Barn)
        val PLOT_GRID = arrayOf(
            intArrayOf(21, 13,  9, 14, 22),
            intArrayOf(15,  5,  1,  6, 17),
            intArrayOf(10,  2,  0,  3, 11),
            intArrayOf(16,  7,  4,  8, 18),
            intArrayOf(23, 19, 12, 20, 24)
        )
    }

    private var modalX = 0
    private var modalY = 0
    private val modalW = 280
    private val modalH = 300

    private val cellSize = 42
    private val cellGap = 4

    override fun init() {
        modalX = (width - modalW) / 2
        modalY = (height - modalH) / 2

        val btnY = modalY + modalH - 28

        // Select All Button
        addRenderableWidget(Button.builder(Component.literal("Select All")) {
            for (r in PLOT_GRID) {
                for (plot in r) {
                    if (plot > 0) selectedPlots.add(plot)
                }
            }
            onSave(selectedPlots.toSet())
        }.bounds(modalX + 16, btnY, 70, 20).build())

        // Clear All Button
        addRenderableWidget(Button.builder(Component.literal("Clear All")) {
            selectedPlots.clear()
            onSave(selectedPlots.toSet())
        }.bounds(modalX + 92, btnY, 70, 20).build())

        // Done Button
        addRenderableWidget(Button.builder(Component.literal("Done")) {
            onClose()
        }.bounds(modalX + modalW - 86, btnY, 70, 20).build())
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x88000000.toInt())

        // Modal Box
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0F172A.toInt())
        graphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + modalH - 1, 0xFF1E293B.toInt())

        // Header Title
        val font = Minecraft.getInstance().font
        graphics.text(font, modalTitle, modalX + 16, modalY + 12, 0xFF38BDF8.toInt())

        // Divider
        graphics.fill(modalX + 16, modalY + 26, modalX + modalW - 16, modalY + 27, 0xFF334155.toInt())

        // Render 5x5 Grid
        val gridStartX = modalX + (modalW - (5 * cellSize + 4 * cellGap)) / 2
        val gridStartY = modalY + 36

        for (row in 0 until 5) {
            for (col in 0 until 5) {
                val plotId = PLOT_GRID[row][col]
                val cellX = gridStartX + col * (cellSize + cellGap)
                val cellY = gridStartY + row * (cellSize + cellGap)

                val isHovered = mouseX in cellX until (cellX + cellSize) && mouseY in cellY until (cellY + cellSize)

                if (plotId == 0) {
                    // Center Barn
                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, 0xFF0F172A.toInt())
                    val text = "Barn"
                    val tw = font.width(text)
                    graphics.text(font, text, cellX + (cellSize - tw) / 2, cellY + (cellSize - 8) / 2, 0xFF64748B.toInt())
                } else {
                    val isSelected = selectedPlots.contains(plotId)
                    val bgCol = when {
                        isSelected && isHovered -> 0xFF22C55E.toInt()
                        isSelected -> 0xFF16A34A.toInt()
                        isHovered -> 0xFF334155.toInt()
                        else -> 0xFF1E293B.toInt()
                    }
                    val borderCol = if (isSelected) 0xFF4ADE80.toInt() else 0xFF334155.toInt()

                    graphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, borderCol)
                    graphics.fill(cellX + 1, cellY + 1, cellX + cellSize - 1, cellY + cellSize - 1, bgCol)

                    val text = "Plot $plotId"
                    val tw = font.width(text)
                    val textCol = if (isSelected) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt()
                    graphics.text(font, text, cellX + (cellSize - tw) / 2, cellY + (cellSize - 8) / 2, textCol)
                }
            }
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        val gridStartX = modalX + (modalW - (5 * cellSize + 4 * cellGap)) / 2
        val gridStartY = modalY + 36

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (row in 0 until 5) {
                for (col in 0 until 5) {
                    val plotId = PLOT_GRID[row][col]
                    if (plotId == 0) continue

                    val cellX = gridStartX + col * (cellSize + cellGap)
                    val cellY = gridStartY + row * (cellSize + cellGap)

                    if (mouseX in cellX until (cellX + cellSize) && mouseY in cellY until (cellY + cellSize)) {
                        if (selectedPlots.contains(plotId)) {
                            selectedPlots.remove(plotId)
                        } else {
                            selectedPlots.add(plotId)
                        }
                        onSave(selectedPlots.toSet())
                        return true
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun onClose() {
        onSave(selectedPlots.toSet())
        Minecraft.getInstance().setScreen(parent)
    }
}
