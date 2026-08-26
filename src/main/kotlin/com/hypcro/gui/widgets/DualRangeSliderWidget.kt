package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.roundToInt

class DualRangeSliderWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val minValue: Int = 1,
    val maxValue: Int = 8,
    var currentMin: Int = 3,
    var currentMax: Int = 4,
    val onRangeChanged: (min: Int, max: Int) -> Unit = { _, _ -> }
) : AbstractWidget(x, y, width, height, Component.empty()) {

    private var activeDraggingHandle = 0 // 0: None, 1: Min Handle, 2: Max Handle

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val font = Minecraft.getInstance().font

        // 1. Text display (Cleanly formatted on the right side)
        val text = if (currentMin == currentMax) "$currentMin Pests" else "$currentMin - $currentMax Pests"
        val textCol = if (active) 0xFF38BDF8.toInt() else 0xFF64748B.toInt()
        graphics.text(font, text, x + width + 8, y + 2, textCol)

        // 2. Track geometry
        val trackY = y + 4
        val trackH = 6

        // Background track
        val trackBg = if (active) 0xFF1E293B.toInt() else 0xFF0F172A.toInt()
        graphics.fill(x, trackY, x + width, trackY + trackH, trackBg)
        graphics.fill(x, trackY, x + width, trackY + 1, 0xFF334155.toInt())

        // Active highlighted range bar between min and max
        val totalRange = (maxValue - minValue).toFloat().coerceAtLeast(1f)
        val minProg = (currentMin - minValue).toFloat() / totalRange
        val maxProg = (currentMax - minValue).toFloat() / totalRange

        val minFillX = x + (minProg * width).roundToInt().coerceIn(0, width)
        val maxFillX = x + (maxProg * width).roundToInt().coerceIn(0, width)

        val rangeCol = if (active) 0xFF10B981.toInt() else 0xFF334155.toInt() // Emerald active range
        if (maxFillX >= minFillX) {
            graphics.fill(minFillX, trackY, maxFillX, trackY + trackH, rangeCol)
        }

        // 3. Render Modern Thumbs
        val thumbW = 6
        val thumbH = 12
        val thumbY = y + 1

        val minThumbX = (minFillX - thumbW / 2).coerceIn(x, x + width - thumbW)
        val maxThumbX = (maxFillX - thumbW / 2).coerceIn(x, x + width - thumbW)

        // Min Handle (Blue)
        val minCol = if (!active) 0xFF64748B.toInt() else if (activeDraggingHandle == 1) 0xFF60A5FA.toInt() else 0xFF3B82F6.toInt()
        graphics.fill(minThumbX, thumbY, minThumbX + thumbW, thumbY + thumbH, minCol)
        graphics.fill(minThumbX + 1, thumbY + 1, minThumbX + thumbW - 1, thumbY + thumbH - 1, 0xFF93C5FD.toInt())

        // Max Handle (Emerald)
        val maxCol = if (!active) 0xFF64748B.toInt() else if (activeDraggingHandle == 2) 0xFF34D399.toInt() else 0xFF10B981.toInt()
        graphics.fill(maxThumbX, thumbY, maxThumbX + thumbW, thumbY + thumbH, maxCol)
        graphics.fill(maxThumbX + 1, thumbY + 1, maxThumbX + thumbW - 1, thumbY + thumbH - 1, 0xFF6EE7B7.toInt())
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        if (!active) return
        val mouseX = event.x()
        val totalRange = (maxValue - minValue).toFloat().coerceAtLeast(1f)
        val minProg = (currentMin - minValue).toFloat() / totalRange
        val maxProg = (currentMax - minValue).toFloat() / totalRange

        val minThumbX = x + (minProg * width).roundToInt()
        val maxThumbX = x + (maxProg * width).roundToInt()

        val distToMin = abs(mouseX - minThumbX)
        val distToMax = abs(mouseX - maxThumbX)

        activeDraggingHandle = if (distToMin < distToMax) {
            1
        } else if (distToMax < distToMin) {
            2
        } else {
            if (mouseX <= minThumbX) 1 else 2
        }

        updateFromMouse(mouseX)
    }

    override fun onRelease(event: MouseButtonEvent) {
        activeDraggingHandle = 0
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        if (!active || activeDraggingHandle == 0) return
        updateFromMouse(event.x())
    }

    private fun updateFromMouse(mouseX: Double) {
        val relX = (mouseX - x).coerceIn(0.0, width.toDouble())
        val progress = (relX / width.toDouble()).toFloat()
        val targetVal = (minValue + progress * (maxValue - minValue)).roundToInt().coerceIn(minValue, maxValue)

        var changed = false
        if (activeDraggingHandle == 1) {
            if (targetVal > currentMax) {
                currentMin = currentMax
                currentMax = targetVal
                activeDraggingHandle = 2
                changed = true
            } else if (targetVal != currentMin) {
                currentMin = targetVal
                changed = true
            }
        } else if (activeDraggingHandle == 2) {
            if (targetVal < currentMin) {
                currentMax = currentMin
                currentMin = targetVal
                activeDraggingHandle = 1
                changed = true
            } else if (targetVal != currentMax) {
                currentMax = targetVal
                changed = true
            }
        }

        if (changed) {
            onRangeChanged(currentMin, currentMax)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}
