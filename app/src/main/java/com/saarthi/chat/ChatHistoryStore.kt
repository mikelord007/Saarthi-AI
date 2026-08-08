package com.saarthi.chat

import android.content.Context
import android.util.Log
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "SaarthiChatStore"

/**
 * Persists past task "chats" so they survive app restarts, as a single
 * SharedPreferences string holding the whole list as one JSON blob — no
 * schema version, and [loadAll] falling back to `emptyList()` on any
 * decode failure means a bad blob is silently invisible right up until
 * the next [upsert] makes the loss permanent. Every field added to
 * [ChatEntry]/[ChatTurn] since this was written has been additive with a
 * Kotlin default specifically to keep old JSON decodable under
 * [ignoreUnknownKeys] — that convention is load-bearing, not a style
 * preference; adding a required field or a new enum constant to
 * [ChatStatus]/[BlockCause] would make an old blob fail to decode.
 */
class ChatHistoryStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<ChatEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ChatEntry>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "History decode failed — history will read as empty until overwritten", e)
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
