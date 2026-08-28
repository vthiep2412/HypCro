package com.hypcro.gui

import com.hypcro.bouncy.AutoBouncyBall
import com.hypcro.config.ConfigManager
import com.hypcro.failsafe.HypcroWatchdog
import com.hypcro.farming.MacroController
import com.hypcro.farming.WSFarmEngine
import com.hypcro.pest.PestCallerSource
import com.hypcro.pest.PestDestroyerEngine
import com.hypcro.pest.PestTabReader
import com.hypcro.util.CropBpsTracker
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.ARGB
import java.util.Locale

object HudOverlayRenderer {

    private var lastPestScanTimeMs: Long = 0L
    private var cachedPestCount: Int = 0

    fun render(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val client = Minecraft.getInstance()
        val hudCfg = ConfigManager.config.hud
        if (!hudCfg.enabled) return
        if (client.options.hideGui) return
        if (client.screen is HudEditScreen) return

        val window = client.window
        val screenWidth = window.guiScaledWidth
        val screenHeight = window.guiScaledHeight

        val lines = mutableListOf<String>()
        val isAlarm = HypcroWatchdog.isAlarmActive
        val isWarn = HypcroWatchdog.hasRecentWarning()
        val isAnyActive = MacroController.isAnyMacroActive()

        val activeStatusColor = when {
            isAlarm -> 0xFFEF4444.toInt() // Red (Staff / Alarm)
            isWarn -> 0xFFEAB308.toInt()  // Yellow (3s Warning)
            isAnyActive -> 0xFF22C55E.toInt() // Green (Active Macro)
            else -> 0xFF64748B.toInt()    // Neutral Gray (Idle)
        }

        if (isAnyActive) {
            if (PestDestroyerEngine.isRunning) {
                val source = PestDestroyerEngine.callerSource
                val now = System.currentTimeMillis()
                if (now - lastPestScanTimeMs >= 500L) {
                    val scbInfo = PestTabReader.scanScoreboardPests(client)
                    val tabInfo = PestTabReader.scanPests(client)
                    cachedPestCount = kotlin.math.max(scbInfo.aliveCount, tabInfo.aliveCount)
                    lastPestScanTimeMs = now
                }
                val pestCount = cachedPestCount
                val pestWord = if (pestCount == 1) "pest" else "pests"

                if (source == PestCallerSource.WS_FARM_ENGINE || source == PestCallerSource.VERTICAL_FARM_ENGINE) {
                    val cropName = (WSFarmEngine.currentFarmedCrop ?: CropBpsTracker.currentFarmedCrop)?.displayName ?: "Crop"
                    val farmUptime = formatElapsed(CropBpsTracker.getSessionUptimeMs())
                    lines.add("§fW/S Farm $cropName §7($farmUptime)")
                    lines.add("§eIn pester ($pestCount $pestWord left)")
                } else {
                    val pesterUptime = formatElapsed(PestDestroyerEngine.sessionUptimeMs)
                    lines.add("§fPester §7($pesterUptime)")
                    lines.add("§e$pestCount $pestWord left")
                }
            } else if (AutoBouncyBall.isRunning) {
                val bouncyUptime = formatElapsed(AutoBouncyBall.sessionUptimeMs)
                lines.add("§fBouncy ball §7($bouncyUptime)")
                lines.add("§7Ball count: §f${AutoBouncyBall.ballCount}")
                lines.add("§7Current Bounce: §a${AutoBouncyBall.bounceCount}")
            } else if (MacroController.isRunning) {
                val cropName = (WSFarmEngine.currentFarmedCrop ?: CropBpsTracker.currentFarmedCrop)?.displayName ?: "Crop"
                val uptimeStr = formatElapsed(CropBpsTracker.getSessionUptimeMs())
                val currentBps = CropBpsTracker.getCurrentBps()
                val avgBps = CropBpsTracker.getAverageBps()
                val currentKey = WSFarmEngine.currentActiveKey.toString()

                lines.add("§fW/S Farm $cropName §7($uptimeStr)")
                lines.add("§a${String.format(Locale.US, "%.1f", currentBps)} §7BPS / §a${String.format(Locale.US, "%.1f", avgBps)} §7avgr")
                lines.add("§7Direction: §b$currentKey")
            } else {
                lines.add("§aMacro Running")
            }
        } else {
            lines.add("§7No Macro Active")
        }

        val font = client.font
        var maxTextWidth = 70
        for (line in lines) {
            val w = font.width(line)
            if (w > maxTextWidth) maxTextWidth = w
        }

        val paddingX = 8
        val paddingY = 6
        val lineHeight = 10
        val cardWidth = maxTextWidth + paddingX * 2 + 4
        val cardHeight = lines.size * lineHeight + paddingY * 2

        val scale = hudCfg.scale.coerceIn(0.5f, 2.5f)
        val scaledCardW = cardWidth * scale
        val scaledCardH = cardHeight * scale

        val defaultX = screenWidth - scaledCardW - 10f
        val defaultY = screenHeight - scaledCardH - 10f
        val maxX = (screenWidth - scaledCardW).coerceAtLeast(0f)
        val maxY = (screenHeight - scaledCardH).coerceAtLeast(0f)
        val targetX = if (hudCfg.posX < 0f) defaultX.coerceAtLeast(0f) else hudCfg.posX.coerceIn(0f, maxX)
        val targetY = if (hudCfg.posY < 0f) defaultY.coerceAtLeast(0f) else hudCfg.posY.coerceIn(0f, maxY)

        drawCard(
            graphics = graphics,
            font = font,
            x = targetX,
            y = targetY,
            scale = scale,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            lines = lines,
            opacity = hudCfg.opacity,
            leftBorderColor = activeStatusColor,
            rightBorderColor = activeStatusColor
        )
    }

    fun drawCard(
        graphics: GuiGraphicsExtractor,
        font: Font,
        x: Float,
        y: Float,
        scale: Float,
        cardWidth: Int,
        cardHeight: Int,
        lines: List<String>,
        opacity: Float,
        leftBorderColor: Int,
        rightBorderColor: Int
    ) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(x, y)
        if (scale != 1.0f) {
            graphics.pose().scale(scale, scale)
        }

        val alpha = (opacity.coerceIn(0.10f, 1.00f) * 255).toInt()
        val bgColor = ARGB.color(alpha, 16, 18, 22)

        // Background sharp rectangular box
        graphics.fill(0, 0, cardWidth, cardHeight, bgColor)

        // Left vertical border (2px)
        graphics.fill(0, 0, 2, cardHeight, leftBorderColor)

        // Right vertical border (2px)
        graphics.fill(cardWidth - 2, 0, cardWidth, cardHeight, rightBorderColor)

        // Text lines rendering
        var textY = 6
        for (line in lines) {
            graphics.text(font, line, 8, textY, 0xFFFFFFFF.toInt())
            textY += 10
        }

        graphics.pose().popMatrix()
    }

    fun formatElapsed(millis: Long): String {
        val totalSec = (millis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L

        return when {
            h > 0 -> "${h}h ${m}m ${s}s"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }
}
