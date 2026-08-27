package com.hypcro.pest

import com.hypcro.util.GardenStateReader
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

data class PestTabInfo(
    val aliveCount: Int,
    val infestedPlots: Set<Int>
)

object PestTabReader {

    private val PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?")
    private val INFESTED_PLOTS_PATTERN = Pattern.compile("(?i)Plots?:\\s*(.+)")

    private val GARDEN_PEST_COUNT_PATTERN = Pattern.compile("(?i)The\\s*Garden.*?x(\\d+)")
    private val SCOREBOARD_PLOT_PATTERN = Pattern.compile("(?i)Plot\\s*-\\s*(\\d+).*?x(\\d+)")

    fun stripColor(input: String): String = GardenStateReader.stripColor(input)

    fun readScoreboardLines(client: Minecraft): List<String> = GardenStateReader.readScoreboardLines(client)

    fun readTabList(client: Minecraft): List<String> = GardenStateReader.readTabList(client)

    fun isInGarden(client: Minecraft): Boolean = GardenStateReader.isInGarden(client)

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
