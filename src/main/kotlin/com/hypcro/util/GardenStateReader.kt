package com.hypcro.util

import net.minecraft.client.Minecraft
import java.util.regex.Pattern

object GardenStateReader {

    private val STRIP_COLOR = Pattern.compile("(?i)§.")

    fun stripColor(input: String): String {
        return STRIP_COLOR.matcher(input).replaceAll("").replace("§", "").replace('\u00A0', ' ').trim()
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

    fun isInGarden(client: Minecraft): Boolean {
        val tabLines = readTabList(client)
        if (tabLines.any { it.contains("Area: Garden", ignoreCase = true) }) {
            return true
        }
        val scoreLines = readScoreboardLines(client)
        return scoreLines.any { it.contains("The Garden", ignoreCase = true) || it.contains("Plot -", ignoreCase = true) }
    }
}
