package com.hypcro.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class ColorPickerModal(
    private val parent: Screen,
    private val initialHex: String,
    private val onSave: (String) -> Unit
) : Screen(Component.literal("Select ESP Color")) {

    private var currentHex: String = initialHex
    private var hue: Float = 0f
    private var saturation: Float = 1f
    private var value: Float = 1f

    private var isDraggingSV = false
    private var isDraggingHue = false

    private val modalW = 250
    private val modalH = 224
    private var modalX = 0
    private var modalY = 0

    // SV Box Bounds
    private val svW = 160
    private val svH = 96
    private var svX = 0
    private var svY = 0

    // Hue Bar Bounds
    private val hueW = 16
    private val hueH = 96
    private var hueX = 0
    private var hueY = 0

    private lateinit var hexField: EditBox

    private val presetColors = listOf(
        "#EF4444", // Red
        "#F97316", // Orange
        "#EAB308", // Yellow
        "#22C55E", // Green
        "#06B6D4", // Cyan
        "#3B82F6", // Blue
        "#A855F7", // Purple
        "#EC4899", // Pink
        "#FFFFFF"  // White
    )

    override fun init() {
        modalX = (width - modalW) / 2
        modalY = (height - modalH) / 2

        svX = modalX + 16
        svY = modalY + 34

        hueX = modalX + 16 + svW + 12
        hueY = modalY + 34

        val (r, g, b) = parseHexToRgb(initialHex)
        val hsv = rgbToHsv(r, g, b)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        currentHex = formatHex(r, g, b)

        // Hex Input Field
        hexField = EditBox(font, modalX + 88, modalY + 166, 68, 16, Component.literal("Hex"))
        hexField.value = currentHex
        hexField.setResponder { text ->
            val clean = text.removePrefix("#").trim()
            if (clean.length == 6) {
                try {
                    val num = clean.toInt(16)
                    val cr = (num shr 16) and 0xFF
                    val cg = (num shr 8) and 0xFF
                    val cb = num and 0xFF
                    val newHsv = rgbToHsv(cr, cg, cb)
                    hue = newHsv[0]
                    saturation = newHsv[1]
                    value = newHsv[2]
                    currentHex = "#" + clean.uppercase()
                } catch (_: Exception) {}
            }
        }
        addRenderableWidget(hexField)

        // Cancel Button
        val btnY = modalY + modalH - 24
        addRenderableWidget(Button.builder(Component.literal("Cancel")) {
            onClose()
        }.bounds(modalX + modalW - 132, btnY, 56, 18).build())

        // Apply Button
        addRenderableWidget(Button.builder(Component.literal("Apply")) {
            onSave(currentHex)
            onClose()
        }.bounds(modalX + modalW - 70, btnY, 56, 18).build())
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // Dim background
        graphics.fill(0, 0, width, height, 0x88000000.toInt())

        // Modal Box Frame
        graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF38BDF8.toInt())
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF0F172A.toInt())

        // Header
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xFF1E293B.toInt())
        graphics.text(font, "§b§lSelect ESP Color", modalX + 12, modalY + 7, 0xFF38BDF8.toInt())

        // 1. Render Saturation-Value 2D Field
        val svStep = 4
        for (x in 0 until svW step svStep) {
            val s = x.toFloat() / svW.toFloat()
            for (y in 0 until svH step svStep) {
                val v = 1.0f - (y.toFloat() / svH.toFloat())
                val (r, g, b) = hsvToRgb(hue, s, v)
                val col = ARGB.color(255, r, g, b)
                graphics.fill(svX + x, svY + y, svX + min(x + svStep, svW), svY + min(y + svStep, svH), col)
            }
        }
        // SV Border
        graphics.fill(svX - 1, svY - 1, svX + svW + 1, svY, 0xFF334155.toInt())
        graphics.fill(svX - 1, svY + svH, svX + svW + 1, svY + svH + 1, 0xFF334155.toInt())
        graphics.fill(svX - 1, svY, svX, svY + svH, 0xFF334155.toInt())
        graphics.fill(svX + svW, svY, svX + svW + 1, svY + svH, 0xFF334155.toInt())

        // SV Handle Cursor
        val handleX = svX + (saturation * svW).toInt().coerceIn(0, svW)
        val handleY = svY + ((1.0f - value) * svH).toInt().coerceIn(0, svH)
        graphics.fill(handleX - 3, handleY - 3, handleX + 3, handleY + 3, 0xFF000000.toInt())
        graphics.fill(handleX - 2, handleY - 2, handleX + 2, handleY + 2, 0xFFFFFFFF.toInt())

        // 2. Render Vertical Hue Slider Bar
        val hueStep = 2
        for (y in 0 until hueH step hueStep) {
            val h = (y.toFloat() / hueH.toFloat()) * 360.0f
            val (hr, hg, hb) = hsvToRgb(h, 1.0f, 1.0f)
            val hcol = ARGB.color(255, hr, hg, hb)
            graphics.fill(hueX, hueY + y, hueX + hueW, hueY + min(y + hueStep, hueH), hcol)
        }
        // Hue Border
        graphics.fill(hueX - 1, hueY - 1, hueX + hueW + 1, hueY, 0xFF334155.toInt())
        graphics.fill(hueX - 1, hueY + hueH, hueX + hueW + 1, hueY + hueH + 1, 0xFF334155.toInt())
        graphics.fill(hueX - 1, hueY, hueX, hueY + hueH, 0xFF334155.toInt())
        graphics.fill(hueX + hueW, hueY, hueX + hueW + 1, hueY + hueH, 0xFF334155.toInt())

        // Hue Handle Cursor
        val hueHandleY = hueY + ((hue / 360.0f) * hueH).toInt().coerceIn(0, hueH - 1)
        graphics.fill(hueX - 2, hueHandleY - 1, hueX + hueW + 2, hueHandleY + 2, 0xFFFFFFFF.toInt())
        graphics.fill(hueX - 1, hueHandleY, hueX + hueW + 1, hueHandleY + 1, 0xFF000000.toInt())

        // 3. Quick Preset Palette Chips
        val chipStartX = modalX + 16
        val chipY = modalY + 140
        val chipW = 20
        val chipH = 14
        val chipGap = 4

        for ((idx, pColor) in presetColors.withIndex()) {
            val cx = chipStartX + idx * (chipW + chipGap)
            val isHovered = mouseX in cx until (cx + chipW) && mouseY in chipY until (chipY + chipH)
            val (pr, pg, pb) = parseHexToRgb(pColor)
            val isSelected = currentHex.equals(pColor, ignoreCase = true)

            val borderCol = if (isSelected) 0xFFFFFFFF.toInt() else if (isHovered) 0xFF38BDF8.toInt() else 0xFF334155.toInt()
            graphics.fill(cx - 1, chipY - 1, cx + chipW + 1, chipY + chipH + 1, borderCol)
            graphics.fill(cx, chipY, cx + chipW, chipY + chipH, ARGB.color(255, pr, pg, pb))
        }

        // 4. Live Preview Box & Hex Label
        val (cr, cg, cb) = hsvToRgb(hue, saturation, value)
        val previewX = modalX + 16
        val previewY = modalY + 166
        val previewW = 34
        val previewH = 16

        graphics.fill(previewX - 1, previewY - 1, previewX + previewW + 1, previewY + previewH + 1, 0xFF475569.toInt())
        graphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, ARGB.color(255, cr, cg, cb))

        graphics.text(font, "HEX:", modalX + 58, modalY + 170, 0xFF94A3B8.toInt())

        super.extractRenderState(graphics, mouseX, mouseY, delta)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Click inside SV area
            if (mouseX in svX until (svX + svW) && mouseY in svY until (svY + svH)) {
                isDraggingSV = true
                updateSVFromMouse(mouseX, mouseY)
                return true
            }

            // Click inside Hue bar
            if (mouseX in hueX until (hueX + hueW) && mouseY in hueY until (hueY + hueH)) {
                isDraggingHue = true
                updateHueFromMouse(mouseY)
                return true
            }

            // Click on Preset Chips
            val chipStartX = modalX + 16
            val chipY = modalY + 140
            val chipW = 20
            val chipH = 14
            val chipGap = 4

            for ((idx, pColor) in presetColors.withIndex()) {
                val cx = chipStartX + idx * (chipW + chipGap)
                if (mouseX in cx until (cx + chipW) && mouseY in chipY until (chipY + chipH)) {
                    val (pr, pg, pb) = parseHexToRgb(pColor)
                    val hsv = rgbToHsv(pr, pg, pb)
                    hue = hsv[0]
                    saturation = hsv[1]
                    value = hsv[2]
                    currentHex = pColor
                    hexField.value = currentHex
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mouseX = event.x().toInt()
        val mouseY = event.y().toInt()

        if (isDraggingSV) {
            updateSVFromMouse(mouseX, mouseY)
            return true
        }
        if (isDraggingHue) {
            updateHueFromMouse(mouseY)
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            isDraggingSV = false
            isDraggingHue = false
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        return super.keyPressed(event)
    }

    private fun updateSVFromMouse(mouseX: Int, mouseY: Int) {
        val relX = (mouseX - svX).coerceIn(0, svW)
        val relY = (mouseY - svY).coerceIn(0, svH)
        saturation = relX.toFloat() / svW.toFloat()
        value = 1.0f - (relY.toFloat() / svH.toFloat())
        syncCurrentHex()
    }

    private fun updateHueFromMouse(mouseY: Int) {
        val relY = (mouseY - hueY).coerceIn(0, hueH)
        hue = (relY.toFloat() / hueH.toFloat()) * 360.0f
        syncCurrentHex()
    }

    private fun syncCurrentHex() {
        val (r, g, b) = hsvToRgb(hue, saturation, value)
        currentHex = formatHex(r, g, b)
        hexField.value = currentHex
    }

    companion object {
        fun formatHex(r: Int, g: Int, b: Int): String {
            return String.format("#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
        }

        fun parseHexToRgb(hex: String): Triple<Int, Int, Int> {
            val clean = hex.removePrefix("#").trim()
            return try {
                val num = clean.toInt(16)
                val r = (num shr 16) and 0xFF
                val g = (num shr 8) and 0xFF
                val b = num and 0xFF
                Triple(r, g, b)
            } catch (_: Exception) {
                Triple(239, 68, 68)
            }
        }

        fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
            val rf = r / 255f
            val gf = g / 255f
            val bf = b / 255f

            val maxVal = max(rf, max(gf, bf))
            val minVal = min(rf, min(gf, bf))
            val delta = maxVal - minVal

            var h = 0f
            if (delta > 0.00001f) {
                h = when (maxVal) {
                    rf -> ((gf - bf) / delta) % 6f
                    gf -> ((bf - rf) / delta) + 2f
                    else -> ((rf - gf) / delta) + 4f
                } * 60f
                if (h < 0f) h += 360f
            }

            val s = if (maxVal > 0.00001f) delta / maxVal else 0f
            val v = maxVal

            return floatArrayOf(h, s, v)
        }

        fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
            val c = v * s
            val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
            val m = v - c

            val (rPrime, gPrime, bPrime) = when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

            val r = ((rPrime + m) * 255f).toInt().coerceIn(0, 255)
            val g = ((gPrime + m) * 255f).toInt().coerceIn(0, 255)
            val b = ((bPrime + m) * 255f).toInt().coerceIn(0, 255)

            return Triple(r, g, b)
        }
    }
}
