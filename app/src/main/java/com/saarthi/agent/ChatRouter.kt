package com.saarthi.agent

import android.util.Log
import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "SaarthiAgent"
private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"

/**
 * Model for the chat-vs-task classification call only — deliberately its
 * own constant, not [ClaudeClient]'s `AGENT_MODEL`. Same family for now,
 * but this is a different workload (one call, tiny context, a trivial
 * binary decision) and may want a cheaper/faster model later without
 * touching the agent's own per-step model choice. If this is ever swapped
 * to a Haiku model, the `output_config`/`thinking` blocks in [classify]
 * must be removed first — Haiku 4.5 rejects `effort` and non-adaptive
 * `thinking` the way Sonnet 5 accepts them.
 */
private const val ROUTER_MODEL = "claude-sonnet-5"
private const val MAX_TOKENS = 512

/**
 * A message is either answered directly ([Chat]) or handed to
 * [AgentLoop] ([Task]) — the whole point of this type is that [Chat]
 * never touches [com.saarthi.perception.ScreenPerception] at all, so it
 * can never trigger the empty-screen HOME-press recovery or the
 * task-glow overlay, both of which only ever fire from inside
 * [AgentLoop.run].
 */
sealed interface RouterDecision {
    data class Chat(val reply: String) : RouterDecision
    data object Task : RouterDecision
}

/**
 * Classifies a fresh user message as plain conversation or a device task,
 * before [AgentLoop] ever runs — every caller must skip this entirely
 * when resuming a thread paused on `ChatStatus.ASK_USER` (the reply there
 * is an answer to the agent's own question, not a new instruction, and
 * classifying it as chat-or-task would be nonsense).
 *
 * Self-contained rather than reusing [ClaudeClient.decide]: the tool
 * schema, return type, and retry policy are all different enough that
 * threading them through the agent's own hot path (up to
 * `AgentLoop.MAX_STEPS` calls per task) to serve this one cold call isn't
 * worth it — matches this codebase's existing convention of small
 * single-purpose files ([AgentTool], [AgentPrompt], [LoginWallDetector]).
 *
 * Fails open to [RouterDecision.Task] on every error path (missing key,
 * network failure, refusal, malformed response, blank reply) — a wrong
 * "chat" classification silently drops a real task, which is worse than
 * one avoidable [AgentLoop] run. This also means a genuine failure to
 * reach Claude here doesn't need its own error UX: [AgentLoop]'s own
 * first decision call will hit the same failure and surface the
 * already-established [AgentEvent.Error] path.
 */
object ChatRouter {

    suspend fun classify(
        message: String,
        recentContext: List<String> = emptyList(),
        languageDisplayName: String,
    ): RouterDecision = withContext(Dispatchers.IO) {
        log("▶ \"$message\"")
        val apiKey = BuildConfig.ANTHROPIC_API_KEY
        if (apiKey.isBlank()) {
            log("✖ no API key — falling back to TASK")
            return@withContext RouterDecision.Task
        }

        val requestBody = buildJsonObject {
            put("model", ROUTER_MODEL)
            put("max_tokens", MAX_TOKENS)
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("output_config", buildJsonObject { put("effort", "low") })
            put("system", systemPrompt(languageDisplayName))
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userMessage(message, recentContext))
                        },
                    )
                },
            )
            put("tools", toolDefinitions())
            put(
                "tool_choice",
                buildJsonObject {
                    put("type", "any")
                    put("disable_parallel_tool_use", true)
                },
            )
        }

        val request = Request.Builder()
            .url(ANTHROPIC_API_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val decision = try {
            SaarthiHttp.client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string()
                if (!response.isSuccessful || rawBody == null) {
                    log("✖ HTTP ${response.code} — falling back to TASK")
                    RouterDecision.Task
                } else {
                    parseResponse(rawBody)
                }
            }
        } catch (e: IOException) {
            log("✖ failed: ${e.message} — falling back to TASK")
            RouterDecision.Task
        }

        when (decision) {
            is RouterDecision.Chat -> log("→ CHAT: \"${decision.reply}\"")
            RouterDecision.Task -> log("→ TASK")
        }
        decision
    }

    private fun parseResponse(rawBody: String): RouterDecision {
        val json = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: run { log("✖ response was not valid JSON — falling back to TASK"); return RouterDecision.Task }

        when (json["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> { log("✖ refused — falling back to TASK"); return RouterDecision.Task }
            "max_tokens" -> { log("✖ truncated before completing a tool call — falling back to TASK"); return RouterDecision.Task }
        }

        val content: JsonArray = json["content"]?.jsonArray
            ?: run { log("✖ malformed response (no content) — falling back to TASK"); return RouterDecision.Task }
        val toolUse = content
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            ?: run { log("✖ malformed response (no tool_use) — falling back to TASK"); return RouterDecision.Task }

        val name = toolUse["name"]?.jsonPrimitive?.contentOrNull
        val input = toolUse["input"]?.jsonObject

        return when (name) {
            "chat" -> {
                val reply = input?.get("reply")?.jsonPrimitive?.contentOrNull
                if (reply.isNullOrBlank()) {
                    log("✖ chat tool call had a blank reply — falling back to TASK")
                    RouterDecision.Task
                } else {
                    RouterDecision.Chat(reply)
                }
            }
            "start_task" -> RouterDecision.Task
            else -> { log("✖ unrecognized tool \"$name\" — falling back to TASK"); RouterDecision.Task }
        }
    }

    private fun userMessage(message: String, recentContext: List<String>): String = buildString {
        if (recentContext.isNotEmpty()) {
            append("RECENT:\n")
            recentContext.forEach { append(it).append('\n') }
            append('\n')
        }
        append("MESSAGE: ").append(message)
    }

    private fun systemPrompt(languageDisplayName: String): String =
        SYSTEM_TEMPLATE.replace("%LANGUAGE%", languageDisplayName)

    private val SYSTEM_TEMPLATE = """
        You are the front door for Saarthi, a voice assistant that can either chat with the user directly, or take over their Android phone to perform a task on their behalf (open apps, tap, type, read the screen). Given the user's message, decide which this is.

        Call exactly one tool:
        - chat: the message is a greeting, small talk, a question about Saarthi itself, thanks, or a follow-up that needs no action on the phone. Reply naturally and briefly (one to three short sentences) in %LANGUAGE%.
        - start_task: the message asks you to open an app, find or search for something, change a setting, read the current screen, or do anything else that requires looking at or acting on the phone.

        When genuinely unsure which one applies, prefer start_task — a wrong "chat" call silently drops a real request, which is worse than one unnecessary task attempt.

        RECENT, if present, is a short excerpt of the recent conversation for context. MESSAGE is the new thing the user just said.
    """.trimIndent()

    private fun toolDefinitions(): JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put("name", "chat")
                put("description", "Reply directly to a conversational message — no phone/screen interaction needed.")
                put(
                    "input_schema",
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "reply",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "The natural-language reply to speak back to the user, in their language.")
                                    },
                                )
                            },
                        )
                        put("required", buildJsonArray { add("reply") })
                    },
                )
            },
        )
        add(
            buildJsonObject {
                put("name", "start_task")
                put("description", "The message requires performing an action on the user's phone — hand off to the task agent.")
                put(
                    "input_schema",
                    buildJsonObject {
                        put("type", "object")
                        put("properties", buildJsonObject {})
                        put("required", buildJsonArray {})
                    },
                )
            },
        )
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[Router] $message")
    }
}
