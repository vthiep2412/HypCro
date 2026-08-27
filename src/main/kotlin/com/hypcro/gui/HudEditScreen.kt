package com.hypcro.gui

import com.hypcro.config.ConfigManager
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class HudEditScreen(private val parentScreen: Screen?) : Screen(Component.literal("HUD Editor")) {

    private var isDragging: Boolean = false
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f

    private var currentX: Float = 0f
    private var currentY: Float = 0f
    private var currentScale: Float = 1.0f

    private val previewLines = listOf(
        "§fW/S Farm Wheat §7(14m 20s)",
        "§a19.8 §7BPS / §a19.5 §7avgr",
        "§7Direction: §bS"
    )

    override fun init() {
        val hudCfg = ConfigManager.config.hud
        currentScale = hudCfg.scale.coerceIn(0.5f, 2.5f)

        val cardDim = calculateCardDimensions()
        val scaledW = cardDim.first * currentScale
        val scaledH = cardDim.second * currentScale

        val defaultX = width - scaledW - 10f
        val defaultY = height - scaledH - 10f

        currentX = if (hudCfg.posX < 0f) defaultX else hudCfg.posX.coerceIn(0f, width - scaledW)
        currentY = if (hudCfg.posY < 0f) defaultY else hudCfg.posY.coerceIn(0f, height - scaledH)

        addRenderableWidget(
            Button.builder(Component.literal("Reset Position")) {
                currentX = width - scaledW - 10f
                currentY = height - scaledH - 10f
                currentScale = 1.0f
                saveConfig()
            }.bounds(width / 2 - 110, height - 32, 100, 20).build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                saveConfig()
                minecraft.setScreen(parentScreen)
            }.bounds(width / 2 + 10, height - 32, 100, 20).build()
        )
    }

    private fun calculateCardDimensions(): Pair<Int, Int> {
        var maxW = 70
        for (line in previewLines) {
            val w = font.width(line)
            if (w > maxW) maxW = w
        }
        val paddingX = 8
        val paddingY = 6
        val lineHeight = 10
        val cardW = maxW + paddingX * 2 + 4
        val cardH = previewLines.size * lineHeight + paddingY * 2
        return Pair(cardW, cardH)
    }

    private fun saveConfig() {
        ConfigManager.config.hud.posX = currentX
        ConfigManager.config.hud.posY = currentY
        ConfigManager.config.hud.scale = currentScale
        ConfigManager.save()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val mouseX = event.x()
            val mouseY = event.y()
            val cardDim = calculateCardDimensions()
            val scaledW = cardDim.first * currentScale
            val scaledH = cardDim.second * currentScale

            if (mouseX >= currentX && mouseX <= currentX + scaledW &&
                mouseY >= currentY && mouseY <= currentY + scaledH
            ) {
                isDragging = true
                dragOffsetX = (mouseX - currentX).toFloat()
                dragOffsetY = (mouseY - currentY).toFloat()
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (isDragging) {
            val mouseX = event.x()
            val mouseY = event.y()
            val cardDim = calculateCardDimensions()
            val scaledW = cardDim.first * currentScale
            val scaledH = cardDim.second * currentScale

            currentX = (mouseX.toFloat() - dragOffsetX).coerceIn(0f, width - scaledW)
            currentY = (mouseY.toFloat() - dragOffsetY).coerceIn(0f, height - scaledH)
            saveConfig()
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (isDragging) {
            isDragging = false
            saveConfig()
            return true
        }
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (verticalAmount != 0.0) {
            currentScale = (currentScale + (verticalAmount * 0.05f).toFloat()).coerceIn(0.5f, 2.5f)
            
            val cardDim = calculateCardDimensions()
            val scaledW = cardDim.first * currentScale
            val scaledH = cardDim.second * currentScale

            // Keep box clamped on resize
            currentX = currentX.coerceIn(0f, width - scaledW)
            currentY = currentY.coerceIn(0f, height - scaledH)

            saveConfig()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            saveConfig()
            minecraft.setScreen(parentScreen)
            return true
        }
        return super.keyPressed(event)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Dark translucent overlay
        graphics.fill(0, 0, width, height, 0x66000000)

        // Header instructions
        val scalePercent = (currentScale * 100).toInt()
        val titleText = "§b§lHUD Editor §7— Drag to move | Scroll to scale (§f${scalePercent}%§7)"
        val titleW = font.width(titleText)
        graphics.text(font, titleText, (width - titleW) / 2, 14, 0xFFFFFFFF.toInt())

        val cardDim = calculateCardDimensions()
        val scaledW = cardDim.first * currentScale
        val scaledH = cardDim.second * currentScale

        // Selection highlight box
        val outlineColor = if (isDragging) 0xFF38BDF8.toInt() else 0x8838BDF8.toInt()
        graphics.fill((currentX - 2).toInt(), (currentY - 2).toInt(), (currentX + scaledW + 2).toInt(), (currentY - 1).toInt(), outlineColor)
        graphics.fill((currentX - 2).toInt(), (currentY + scaledH + 1).toInt(), (currentX + scaledW + 2).toInt(), (currentY + scaledH + 2).toInt(), outlineColor)
        graphics.fill((currentX - 2).toInt(), (currentY - 2).toInt(), (currentX - 1).toInt(), (currentY + scaledH + 2).toInt(), outlineColor)
        graphics.fill((currentX + scaledW + 1).toInt(), (currentY - 2).toInt(), (currentX + scaledW + 2).toInt(), (currentY + scaledH + 2).toInt(), outlineColor)

        // Render card preview with current opacity and green borders
        val hudCfg = ConfigManager.config.hud
        HudOverlayRenderer.drawCard(
            graphics = graphics,
            font = font,
            x = currentX,
            y = currentY,
            scale = currentScale,
            cardWidth = cardDim.first,
            cardHeight = cardDim.second,
            lines = previewLines,
            opacity = hudCfg.opacity,
            leftBorderColor = 0xFF22C55E.toInt(),
            rightBorderColor = 0xFF22C55E.toInt()
        )

        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }
}
