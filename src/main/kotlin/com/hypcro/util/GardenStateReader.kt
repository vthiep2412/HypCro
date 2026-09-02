package com.hypcro.util

import net.minecraft.client.Minecraft

object GardenStateReader {

    fun stripColor(input: String): String {
        val len = input.length
        if (len == 0) return ""

        // 1. Scan until first dirty character
        var i = 0
        while (i < len) {
            val c = input[i]
            if (c == '§' || c == '\u00A0' || (i == 0 && c <= ' ')) {
                break
            }
            i++
        }

        // 2. Fast-path: 100% clean string with zero leading/trailing spaces -> 0 heap allocations
        if (i == len) {
            return if (input[len - 1] > ' ') input else input.trim()
        }

        // 3. Lazy allocation: allocate primitive buffer only when modifications are needed
        val chars = CharArray(len)
        var outLen = 0
        var lastNonSpace = -1

        // 4. Copy clean prefix and safely locate last non-space character
        if (i > 0) {
            input.toCharArray(chars, 0, 0, i)
            outLen = i
            var p = i - 1
            while (p >= 0 && chars[p] <= ' ') {
                p--
            }
            lastNonSpace = p
        }

        // 5. Finish remainder of the string in the same single pass
        while (i < len) {
            val c = input[i]
            if (c == '§') {
                i += 2 // Skip section sign and color code
                continue
            }

            val outChar = if (c == '\u00A0') ' ' else c

            // Skip leading whitespace on the fly
            if (outLen == 0 && outChar <= ' ') {
                i++
                continue
            }

            chars[outLen] = outChar
            if (outChar > ' ') {
                lastNonSpace = outLen
            }
            outLen++
            i++
        }

        if (lastNonSpace < 0) return ""
        return String(chars, 0, lastNonSpace + 1)
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

    fun isInDungeons(client: Minecraft): Boolean {
        val tabLines = readTabList(client)
        if (tabLines.any { it.contains("Area: Catacombs", ignoreCase = true) || it.contains("Dungeon: Catacombs", ignoreCase = true) }) {
            return true
        }
        val scoreLines = readScoreboardLines(client)
        return scoreLines.any { it.contains("The Catacombs", ignoreCase = true) || it.contains("Catacombs (", ignoreCase = true) }
    }
}
