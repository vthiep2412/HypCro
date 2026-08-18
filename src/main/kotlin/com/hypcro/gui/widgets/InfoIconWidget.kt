package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence

class InfoIconWidget(
    x: Int,
    y: Int,
    val tooltipText: String,
    val wrapWidth: Int = 260
) : AbstractWidget(x, y, 14, 10, Component.empty()) {

    private val cachedLines: List<FormattedCharSequence> by lazy {
        Minecraft.getInstance().font.split(Component.literal(tooltipText), wrapWidth)
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val isHovered = mouseX in x until (x + width) && mouseY in y until (y + height)
        val font = Minecraft.getInstance().font

        val iconText = if (isHovered) "§8[§b?§8]" else "§8[§7?§8]"
        graphics.text(font, iconText, x, y, 0xFFFFFFFF.toInt())

        if (isHovered) {
            graphics.setTooltipForNextFrame(font, cachedLines, mouseX, mouseY)
        }
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        // Pure informational widget, no click action
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}
}

