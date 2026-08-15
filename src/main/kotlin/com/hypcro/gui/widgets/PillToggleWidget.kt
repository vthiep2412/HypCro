package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class PillToggleWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val options: List<String>,
    var selectedIndex: Int = 0,
    val onSelect: (Int) -> Unit
) : AbstractWidget(x, y, width, height, Component.empty()) {

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val segmentWidth = width / options.size

        // Background pill
        graphics.fill(x, y, x + width, y + height, 0xFF1E293B.toInt())

        // Selected pill highlight
        val selX = x + selectedIndex * segmentWidth
        graphics.fill(selX + 2, y + 2, selX + segmentWidth - 2, y + height - 2, 0xFF3B82F6.toInt())

        // Render option texts
        val font = Minecraft.getInstance().font
        for (i in options.indices) {
            val text = options[i]
            val optX = x + i * segmentWidth + segmentWidth / 2
            val optY = y + (height - 8) / 2
            val color = if (i == selectedIndex) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt()
            
            val strWidth = font.width(text)
            graphics.text(font, text, optX - strWidth / 2, optY, color)
        }
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        val mouseX = event.x()
        if (mouseX >= x && mouseX <= x + width) {
            val segmentWidth = width / options.size
            val clickedIdx = ((mouseX - x) / segmentWidth).toInt().coerceIn(0, options.size - 1)
            if (clickedIdx != selectedIndex) {
                selectedIndex = clickedIdx
                onSelect(selectedIndex)
            }
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}
