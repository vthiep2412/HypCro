package com.hypcro.party

import com.hypcro.util.GardenStateReader
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object PartyApi {

    private val youJoinedPartyPattern = Pattern.compile("§eYou have joined (?<name>.*?)'s? §eparty!")
    private val othersJoinedPartyPattern = Pattern.compile("(?<name>.*?) §ejoined the party\\.")
    private val othersInThePartyPattern = Pattern.compile("§eYou'll be partying with: (?<names>.*)")
    private val otherLeftPattern = Pattern.compile("(?<name>.*?) §ehas left the party\\.")
    private val otherKickedPattern = Pattern.compile("(?<name>.*?) §ehas been removed from the party\\.")
    private val otherOfflineKickedPattern = Pattern.compile("§eKicked (?<name>.*?) because they were offline\\.")
    private val otherDisconnectedPattern = Pattern.compile("(?<name>.*?) §ewas removed from your party because they disconnected\\.")
    private val transferOnLeavePattern = Pattern.compile("The party was transferred to (?<newowner>.*?) because (?<name>.*?) left")
    private val transferVoluntaryPattern = Pattern.compile("The party was transferred to (?<newowner>.*?) by (?<name>.*)")
    private val disbandedPattern = Pattern.compile(".* §ehas disbanded the party!")
    private val kickedPattern = Pattern.compile("§eYou have been kicked from the party by .*")
    private val partyMembersStartPattern = Pattern.compile("§6Party Members \\(\\d+\\)")
    private val partyMemberListPattern = Pattern.compile("Party (?<kind>Leader|Moderators|Members): (?<names>.*)")
    private val kuudraFinderJoinPattern = Pattern.compile("§dParty Finder §f> (?<name>.*?) §ejoined the group! \\(.*\\)")
    private val dungeonFinderJoinPattern = Pattern.compile("§dParty Finder §f> (?<name>.*?) §ejoined the dungeon group! \\(.*\\)")

    private val members = ConcurrentHashMap.newKeySet<String>()

    var partyLeader: String? = null
        private set
    var prevPartyLeader: String? = null
        private set

    fun isInParty(): Boolean = members.isNotEmpty()

    fun isPartyMember(name: String): Boolean {
        val clean = cleanPlayerName(name)
        return members.contains(clean)
    }

    fun getPartyMembers(): Set<String> = members.toSet()

    fun reset() {
        members.clear()
        partyLeader = null
        prevPartyLeader = null
    }

    fun onChatMessage(rawMessage: String) {
        val message = rawMessage.trim()
        val stripped = GardenStateReader.stripColor(message)

        // 1. New member joins
        val youJoinedMatcher = youJoinedPartyPattern.matcher(message)
        if (youJoinedMatcher.find()) {
            val name = cleanPlayerName(youJoinedMatcher.group("name"))
            partyLeader = name
            addPlayer(name)
            return
        }

        val othersJoinedMatcher = othersJoinedPartyPattern.matcher(message)
        if (othersJoinedMatcher.find()) {
            val name = cleanPlayerName(othersJoinedMatcher.group("name"))
            if (members.isEmpty()) {
                val localName = getLocalPlayerName()
                if (localName.isNotEmpty()) {
                    partyLeader = localName
                }
            }
            addPlayer(name)
            return
        }

        val othersInPartyMatcher = othersInThePartyPattern.matcher(message)
        if (othersInPartyMatcher.find()) {
            val namesStr = othersInPartyMatcher.group("names")
            for (part in namesStr.split(",")) {
                addPlayer(cleanPlayerName(part))
            }
            return
        }

        val kuudraMatcher = kuudraFinderJoinPattern.matcher(message)
        if (kuudraMatcher.find()) {
            addPlayer(cleanPlayerName(kuudraMatcher.group("name")))
            return
        }

        val dungeonMatcher = dungeonFinderJoinPattern.matcher(message)
        if (dungeonMatcher.find()) {
            addPlayer(cleanPlayerName(dungeonMatcher.group("name")))
            return
        }

        // 2. Member leaves or kicked
        val otherLeftMatcher = otherLeftPattern.matcher(message)
        if (otherLeftMatcher.find()) {
            removePlayer(cleanPlayerName(otherLeftMatcher.group("name")))
            return
        }

        val otherKickedMatcher = otherKickedPattern.matcher(message)
        if (otherKickedMatcher.find()) {
            removePlayer(cleanPlayerName(otherKickedMatcher.group("name")))
            return
        }

        val otherOfflineMatcher = otherOfflineKickedPattern.matcher(message)
        if (otherOfflineMatcher.find()) {
            removePlayer(cleanPlayerName(otherOfflineMatcher.group("name")))
            return
        }

        val otherDisconnectMatcher = otherDisconnectedPattern.matcher(message)
        if (otherDisconnectMatcher.find()) {
            removePlayer(cleanPlayerName(otherDisconnectMatcher.group("name")))
            return
        }

        // 3. Transfers
        val transferLeaveMatcher = transferOnLeavePattern.matcher(stripped)
        if (transferLeaveMatcher.find()) {
            val name = cleanPlayerName(transferLeaveMatcher.group("name"))
            partyLeader = cleanPlayerName(transferLeaveMatcher.group("newowner"))
            removePlayer(name)
            return
        }

        val transferVoluntaryMatcher = transferVoluntaryPattern.matcher(stripped)
        if (transferVoluntaryMatcher.find()) {
            partyLeader = cleanPlayerName(transferVoluntaryMatcher.group("newowner"))
            prevPartyLeader = cleanPlayerName(transferVoluntaryMatcher.group("name"))
            return
        }

        // 4. Disbands & Self-leave
        if (disbandedPattern.matcher(message).find() ||
            kickedPattern.matcher(message).find() ||
            stripped == "You left the party." ||
            stripped == "The party was disbanded because all invites expired and the party was empty." ||
            stripped == "You are not currently in a party." ||
            stripped == "You are not in a party." ||
            stripped == "The party was disbanded because the party leader disconnected."
        ) {
            reset()
            return
        }

        // 5. Party list parsing
        if (partyMembersStartPattern.matcher(message).find()) {
            members.clear()
            return
        }

        val partyListMatcher = partyMemberListPattern.matcher(stripped)
        if (partyListMatcher.find()) {
            val kind = partyListMatcher.group("kind")
            val isLeader = kind.equals("Leader", ignoreCase = true)
            val names = partyListMatcher.group("names")
            for (entry in names.split("●", ",")) {
                val clean = cleanPlayerName(entry)
                if (clean.isNotEmpty()) {
                    addPlayer(clean)
                    if (isLeader) {
                        partyLeader = clean
                    }
                }
            }
        }
    }

    private fun addPlayer(name: String) {
        val clean = cleanPlayerName(name)
        if (clean.isEmpty() || clean.equals(getLocalPlayerName(), ignoreCase = true)) return
        members.add(clean)
    }

    private fun removePlayer(name: String) {
        val clean = cleanPlayerName(name)
        members.remove(clean)
        if (clean.equals(prevPartyLeader, ignoreCase = true)) {
            prevPartyLeader = null
        }
    }

    private fun getLocalPlayerName(): String {
        return Minecraft.getInstance().player?.gameProfile?.name ?: ""
    }

    fun cleanPlayerName(raw: String): String {
        var clean = GardenStateReader.stripColor(raw).trim()
        if (clean.startsWith("[")) {
            val closeIndex = clean.indexOf(']')
            if (closeIndex != -1 && closeIndex + 1 < clean.length) {
                clean = clean.substring(closeIndex + 1).trim()
            }
        }
        val firstSpace = clean.indexOf(' ')
        if (firstSpace != -1) {
            clean = clean.substring(0, firstSpace).trim()
        }
        if (clean.endsWith("'s")) {
            clean = clean.substring(0, clean.length - 2)
        }
        return clean.trim()
    }
}
