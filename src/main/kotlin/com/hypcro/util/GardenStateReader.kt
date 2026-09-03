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

    private var cachedTabList: List<String> = emptyList()
    private var lastTabListMs: Long = 0L

    private var cachedScoreboardLines: List<String> = emptyList()
    private var lastScoreboardMs: Long = 0L

    fun reset() {
        cachedTabList = emptyList()
        lastTabListMs = 0L
        cachedScoreboardLines = emptyList()
        lastScoreboardMs = 0L
    }

    fun readTabList(client: Minecraft): List<String> {
        val now = System.currentTimeMillis()
        if (now - lastTabListMs < 400L && cachedTabList.isNotEmpty()) {
            return cachedTabList
        }
        val connection = client.connection ?: return emptyList()
        val lines = mutableListOf<String>()
        for (info in connection.listedOnlinePlayers) {
            val displayName = info.tabListDisplayName?.string ?: info.profile.name
            val clean = stripColor(displayName)
            if (clean.isNotBlank()) {
                lines.add(clean)
            }
        }
        cachedTabList = lines
        lastTabListMs = now
        return lines
    }

    fun readScoreboardLines(client: Minecraft): List<String> {
        val now = System.currentTimeMillis()
        if (now - lastScoreboardMs < 400L && cachedScoreboardLines.isNotEmpty()) {
            return cachedScoreboardLines
        }
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
        cachedScoreboardLines = lines
        lastScoreboardMs = now
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

    fun getDungeonFloor(client: Minecraft): String {
        val scoreLines = readScoreboardLines(client)
        for (line in scoreLines) {
            val idxOpen = line.indexOf("(")
            val idxClose = line.indexOf(")")
            if (idxOpen != -1 && idxClose > idxOpen) {
                val candidate = line.substring(idxOpen + 1, idxClose).trim().uppercase()
                if (candidate.startsWith("F") || candidate.startsWith("M") || candidate == "E") {
                    return candidate
                }
            }
        }
        return ""
    }

    fun isInBossRoom(client: Minecraft, floor: String = ""): Boolean {
        val player = client.player ?: return false
        val currentFloor = getDungeonFloor(client)
        if (floor.isNotEmpty() && !currentFloor.endsWith(floor, ignoreCase = true)) {
            return false
        }
        val pos = player.position()
        val rawFloor = if (floor.isNotEmpty()) floor else currentFloor
        val floorNum = rawFloor.trim().trimStart('F', 'M', 'f', 'm')
        return when (floorNum) {
            "1" -> pos.x in -72.0..-14.0 && pos.y in 55.0..146.0 && pos.z in -40.0..49.0
            "2" -> pos.x in -40.0..24.0 && pos.y in 54.0..99.0 && pos.z in -40.0..54.0
            "3" -> pos.x in -40.0..42.0 && pos.y in 64.0..118.0 && pos.z in -40.0..73.0
            "4" -> pos.x in -40.0..50.0 && pos.y in 53.0..112.0 && pos.z in -40.0..81.0
            "5" -> pos.x in -40.0..50.0 && pos.y in 53.0..112.0 && pos.z in -8.0..118.0
            "6" -> pos.x in -40.0..22.0 && pos.y in 51.0..110.0 && pos.z in -8.0..134.0
            "7" -> pos.x in -8.0..134.0 && pos.y in 0.0..254.0 && pos.z in -8.0..147.0
            else -> false
        }
    }

    fun isInJerryWorkshop(client: Minecraft): Boolean {
        val tabLines = readTabList(client)
        if (tabLines.any { it.contains("Jerry's Workshop", ignoreCase = true) || it.contains("Jerry’s Workshop", ignoreCase = true) }) {
            return true
        }
        val scoreLines = readScoreboardLines(client)
        return scoreLines.any { it.contains("Jerry's Workshop", ignoreCase = true) || it.contains("Jerry’s Workshop", ignoreCase = true) || (it.contains("Jerry", ignoreCase = true) && it.contains("Workshop", ignoreCase = true)) }
    }

    fun isInCrystalHollows(client: Minecraft): Boolean {
        val tabLines = readTabList(client)
        if (tabLines.any { it.contains("Crystal Hollows", ignoreCase = true) }) {
            return true
        }
        val scoreLines = readScoreboardLines(client)
        return scoreLines.any { it.contains("Crystal Hollows", ignoreCase = true) || (it.contains("Crystal", ignoreCase = true) && it.contains("Hollows", ignoreCase = true)) }
    }

    private val YEAR_REGEX = Regex("Year\\s+(\\d+)", RegexOption.IGNORE_CASE)

    fun readSkyBlockYear(client: Minecraft): Int {
        val scoreLines = readScoreboardLines(client)
        for (line in scoreLines) {
            val match = YEAR_REGEX.find(line)
            if (match != null) {
                return match.groupValues[1].toIntOrNull() ?: 0
            }
        }
        return 0
    }
}
