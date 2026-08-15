package com.hypcro.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.gui.narration.NarrationElementOutput

class SectionBoxWidget(
    val title: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) : Renderable, GuiEventListener, NarratableEntry {

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Section outline & background
        graphics.fill(x, y, x + width, y + height, 0x1A334155)
        
        // Header title
        val font = Minecraft.getInstance().font
        graphics.text(font, title, x + 8, y + 6, 0xFF38BDF8.toInt())

        // Divider
        graphics.fill(x + 8, y + 18, x + width - 8, y + 19, 0xFF334155.toInt())
    }

    override fun setFocused(focused: Boolean) {}
    override fun isFocused(): Boolean = false
    override fun narrationPriority(): NarratableEntry.NarrationPriority = NarratableEntry.NarrationPriority.NONE
    override fun updateNarration(output: NarrationElementOutput) {}
}
