package com.hypcro.pest

import net.minecraft.client.Minecraft
import java.util.regex.Pattern

data class PestTabInfo(
    val aliveCount: Int,
    val infestedPlots: Set<Int>
)

object PestTabReader {

    private val STRIP_COLOR = Pattern.compile("(?i)§.")
    private val PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?")
    private val INFESTED_PLOTS_PATTERN = Pattern.compile("(?i)Plots?:\\s*(.+)")

    private val GARDEN_PEST_COUNT_PATTERN = Pattern.compile("(?i)The\\s*Garden.*?x(\\d+)")
    private val SCOREBOARD_PLOT_PATTERN = Pattern.compile("(?i)Plot\\s*-\\s*(\\d+).*?x(\\d+)")

    fun stripColor(input: String): String {
        return STRIP_COLOR.matcher(input).replaceAll("").replace("§", "").replace('\u00A0', ' ').trim()
    }

    fun readScoreboardLines(client: Minecraft): List<String> {
        val level = client.level ?: return emptyList()
        val scoreboard = level.scoreboard
        val objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) ?: return emptyList()
        val entries = scoreboard.listPlayerScores(objective).sortedByDescending { it.value() }
        val lines = mutableListOf<String>()
        for (entry in entries) {
            if (entry.isHidden) continue
            val team = scoreboard.getPlayersTeam(entry.owner())
            val fullComponent = if (team != null) {
                team.playerPrefix.copy().append(entry.display() ?: net.minecraft.network.chat.Component.empty()).append(team.playerSuffix)
            } else {
                entry.display() ?: entry.ownerName()
            }
            val cleanText = stripColor(fullComponent.string)
            if (cleanText.isNotBlank()) {
                lines.add(cleanText)
            }
        }
        return lines
    }

    fun getScoreboardPlotPestCount(client: Minecraft, targetPlotId: Int): Int? {
        val lines = readScoreboardLines(client)
        for (line in lines) {
            val plotMatcher = SCOREBOARD_PLOT_PATTERN.matcher(line)
            if (plotMatcher.find()) {
                val plotId = plotMatcher.group(1).toIntOrNull()
                val count = plotMatcher.group(2).toIntOrNull()
                if (plotId == targetPlotId && count != null) {
                    return count
                }
            }
        }
        return null
    }

    fun scanScoreboardPests(client: Minecraft): PestTabInfo {
        val lines = readScoreboardLines(client)
        var totalGardenPests = 0
        val plots = mutableSetOf<Int>()

        for (line in lines) {
            val gardenMatcher = GARDEN_PEST_COUNT_PATTERN.matcher(line)
            if (gardenMatcher.find()) {
                totalGardenPests = gardenMatcher.group(1).toIntOrNull() ?: 0
            }

            val plotMatcher = SCOREBOARD_PLOT_PATTERN.matcher(line)
            if (plotMatcher.find()) {
                val plotId = plotMatcher.group(1).toIntOrNull()
                if (plotId != null && plotId in 0..24) {
                    plots.add(plotId)
                }
            }
        }

        return PestTabInfo(totalGardenPests, plots)
    }

    fun readTabList(client: Minecraft): List<String> {
        val connection = client.connection ?: return emptyList()
        val lines = mutableListOf<String>()
        for (info in connection.listedOnlinePlayers) {
            val displayName = info.tabListDisplayName?.string ?: info.profile.name
            val clean = stripColor(displayName)
            if (clean.isNotBlank()) {
                lines.add(clean)
            }
        }
        return lines
    }

    fun isInGarden(client: Minecraft): Boolean {
        val lines = readTabList(client)
        return lines.any { it.contains("Area: Garden", ignoreCase = true) }
    }

    fun scanPests(client: Minecraft): PestTabInfo {
        val lines = readTabList(client)
        var aliveCount = 0
        val plots = mutableSetOf<Int>()

        for (line in lines) {
            val aliveMatcher = PESTS_ALIVE_PATTERN.matcher(line)
            if (aliveMatcher.find()) {
                aliveCount = aliveMatcher.group(1).toIntOrNull() ?: 0
            }

            val plotsMatcher = INFESTED_PLOTS_PATTERN.matcher(line)
            if (plotsMatcher.find()) {
                val rawPlots = plotsMatcher.group(1)
                for (digits in Regex("\\d+").findAll(rawPlots).map { it.value }) {
                    val plotId = digits.toIntOrNull()
                    if (plotId != null && plotId in 0..24) {
                        plots.add(plotId)
                    }
                }
            }
        }

        return PestTabInfo(aliveCount, plots)
    }
}
