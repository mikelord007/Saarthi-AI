package com.saarthi.chat

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists past task "chats" so they survive app restarts. */
class ChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<ChatEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ChatEntry>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun find(id: String): ChatEntry? = loadAll().firstOrNull { it.id == id }

    fun upsert(entry: ChatEntry) {
        val entries = loadAll().toMutableList()
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) entries[index] = entry else entries.add(entry)
        prefs.edit().putString(KEY_ENTRIES, json.encodeToString(entries)).apply()
    }

    private companion object {
        const val PREFS_NAME = "saarthi_chat_history"
        const val KEY_ENTRIES = "entries"
    }
}
