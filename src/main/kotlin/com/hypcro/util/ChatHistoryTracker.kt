package com.hypcro.util

import java.util.concurrent.ConcurrentLinkedDeque

object ChatHistoryTracker {
    data class ChatEntry(val message: String, val timestamp: Long)

    private val history = ConcurrentLinkedDeque<ChatEntry>()
    private const val MAX_AGE_MS = 10_000L

    fun onMessageReceived(message: String) {
        val now = System.currentTimeMillis()
        val clean = message.replace("§[0-9a-fk-or]".toRegex(), "").trim()
        history.add(ChatEntry(clean, now))
        cleanup(now)
    }

    private fun cleanup(now: Long) {
        while (true) {
            val first = history.peekFirst() ?: break
            if (now - first.timestamp > MAX_AGE_MS) {
                history.pollFirst()
            } else {
                break
            }
        }
    }

    /**
     * Gets all messages received strictly since [sinceTimestamp] and up to [maxTimestamp].
     */
    fun getMessagesSince(sinceTimestamp: Long, maxTimestamp: Long = Long.MAX_VALUE): List<ChatEntry> {
        val now = System.currentTimeMillis()
        cleanup(now)
        return history.filter {
            it.timestamp > sinceTimestamp && it.timestamp <= maxTimestamp
        }
    }
}
