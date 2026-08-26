package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

class SingleSliderWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val minValue: Int = 1,
    val maxValue: Int = 100,
    var currentValue: Int = 50,
    val labelPrefix: String = "",
    val labelSuffix: String = "",
    val customFormatter: ((Int) -> String)? = null,
    val onValueChanged: (Int) -> Unit = {}
) : AbstractWidget(x, y, width, height, Component.empty()) {

    private var isDragging = false

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val trackY = y + (height - 6) / 2
        val trackH = 6

        // 1. Background track
        val trackBg = if (active) 0xFF1E293B.toInt() else 0xFF0F172A.toInt()
        graphics.fill(x, trackY, x + width, trackY + trackH, trackBg)

        // 2. Active filled portion
        val progress = (currentValue - minValue).toFloat() / (maxValue - minValue).toFloat()
        val fillWidth = (progress * width).roundToInt().coerceIn(0, width)
        val fillCol = if (active) 0xFF38BDF8.toInt() else 0xFF334155.toInt()
        graphics.fill(x, trackY, x + fillWidth, trackY + trackH, fillCol)

        // 3. Draggable handle/thumb
        val thumbW = 8
        val thumbH = height - 2
        val thumbX = (x + fillWidth - thumbW / 2).coerceIn(x, x + width - thumbW)
        val thumbY = y + 1
        val thumbCol = if (!active) 0xFF64748B.toInt() else if (isDragging || isHovered) 0xFFFFFFFF.toInt() else 0xFFE2E8F0.toInt()
        graphics.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, thumbCol)

        // 4. Value text overlay centered or next to thumb
        val font = Minecraft.getInstance().font
        val text = customFormatter?.invoke(currentValue) ?: "$labelPrefix$currentValue$labelSuffix"
        val textCol = if (active) 0xFFFFFFFF.toInt() else 0xFF64748B.toInt()
        val textW = font.width(text)
        val textX = x + width / 2 - textW / 2
        val textY = y + (height - 8) / 2
        
        // Draw text with shadow / clear readability
        graphics.text(font, text, textX, textY, textCol)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        if (!active) return
        isDragging = true
        updateFromMouse(event.x())
    }

    override fun onRelease(event: MouseButtonEvent) {
        isDragging = false
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        if (!active || !isDragging) return
        updateFromMouse(event.x())
    }

    private fun updateFromMouse(mouseX: Double) {
        val relX = (mouseX - x).coerceIn(0.0, width.toDouble())
        val progress = (relX / width.toDouble()).toFloat()
        val newValue = (minValue + progress * (maxValue - minValue)).roundToInt().coerceIn(minValue, maxValue)
        if (newValue != currentValue) {
            currentValue = newValue
            onValueChanged(currentValue)
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}
