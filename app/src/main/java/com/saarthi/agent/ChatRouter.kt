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
 * own constant, not [ClaudeClient]'s `AGENT_MODEL`: this is a different
 * workload (one call, tiny context, a trivial binary decision) that can
 * run on a cheaper/faster model without touching the agent's own per-step
 * model choice. Haiku 4.5, not Sonnet — no `output_config`/`thinking`
 * blocks in [classify]'s request body: Haiku 4.5 rejects `effort` and
 * non-adaptive `thinking` the way Sonnet 5 accepts them.
 */
private const val ROUTER_MODEL = "claude-haiku-4-5-20251001"
private const val MAX_TOKENS = 512

/**
 * A message is either answered directly ([Chat]) or handed to
 * [AgentLoop] ([Task]) — the whole point of this type is that [Chat]
 * never touches [com.saarthi.perception.ScreenPerception] at all, so it
 * can never trigger the empty-screen HOME-press recovery or the
 * task-glow overlay, both of which only ever fire from inside
 * [AgentLoop.run].
 *
 * [Task.task] is a self-contained restatement of the instruction, not
 * necessarily the raw message — a bare "yes" confirming a suggestion
 * Saarthi just made in [Chat] (e.g. "Open Swiggy and order chicken
 * biryani") is meaningless on its own to [AgentLoop], which never sees
 * the [Chat] turn that gave it meaning. Callers must run this, not the
 * raw message, through [AgentLoop.run].
 */
sealed interface RouterDecision {
    data class Chat(val reply: String) : RouterDecision
    data class Task(val task: String) : RouterDecision
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
            return@withContext RouterDecision.Task(message)
        }

        val requestBody = buildJsonObject {
            put("model", ROUTER_MODEL)
            put("max_tokens", MAX_TOKENS)
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
                    RouterDecision.Task(message)
                } else {
                    parseResponse(rawBody, message)
                }
            }
        } catch (e: IOException) {
            log("✖ failed: ${e.message} — falling back to TASK")
            RouterDecision.Task(message)
        }

        when (decision) {
            is RouterDecision.Chat -> log("→ CHAT: \"${decision.reply}\"")
            is RouterDecision.Task -> log("→ TASK: \"${decision.task}\"")
        }
        decision
    }

    /** [originalMessage] is the fallback [RouterDecision.Task.task] on every error path, and when `start_task`'s own `task` field comes back blank. */
    private fun parseResponse(rawBody: String, originalMessage: String): RouterDecision {
        val json = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: run { log("✖ response was not valid JSON — falling back to TASK"); return RouterDecision.Task(originalMessage) }

        when (json["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> { log("✖ refused — falling back to TASK"); return RouterDecision.Task(originalMessage) }
            "max_tokens" -> { log("✖ truncated before completing a tool call — falling back to TASK"); return RouterDecision.Task(originalMessage) }
        }

        val content: JsonArray = json["content"]?.jsonArray
            ?: run { log("✖ malformed response (no content) — falling back to TASK"); return RouterDecision.Task(originalMessage) }
        val toolUse = content
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            ?: run { log("✖ malformed response (no tool_use) — falling back to TASK"); return RouterDecision.Task(originalMessage) }

        val name = toolUse["name"]?.jsonPrimitive?.contentOrNull
        val input = toolUse["input"]?.jsonObject

        return when (name) {
            "chat" -> {
                val reply = input?.get("reply")?.jsonPrimitive?.contentOrNull
                if (reply.isNullOrBlank()) {
                    log("✖ chat tool call had a blank reply — falling back to TASK")
                    RouterDecision.Task(originalMessage)
                } else {
                    RouterDecision.Chat(reply)
                }
            }
            "start_task" -> {
                val task = input?.get("task")?.jsonPrimitive?.contentOrNull
                RouterDecision.Task(if (task.isNullOrBlank()) originalMessage else task)
            }
            else -> { log("✖ unrecognized tool \"$name\" — falling back to TASK"); RouterDecision.Task(originalMessage) }
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
        You are the front door for Saarthi, a voice assistant that can either chat with the user directly, or take over their Android phone to perform a task on their behalf. Given the user's message, decide which this is.

        Call exactly one tool:
        - chat: the message is a greeting, small talk, a question about Saarthi itself, thanks, or a follow-up that needs no action on the phone. Reply naturally and briefly (one to three short sentences) in %LANGUAGE%.
        - start_task: the message asks you to open an app, find or search for something, change a setting, read the current screen, or do anything else that requires looking at or acting on the phone. Its `task` field is what actually reaches the device-control agent, which never sees RECENT — write it as a complete, self-contained instruction. If MESSAGE is already one (e.g. "open Chrome"), just use it as-is. If MESSAGE is short and only makes sense next to RECENT (e.g. "yes", "do it", "the second one" confirming or answering something Saarthi just said), resolve it into the actual instruction — e.g. if RECENT shows Saarthi asked "Want me to open Swiggy and order chicken biryani?" and MESSAGE is "yes", `task` should be "Open Swiggy and order chicken biryani", not "yes".

        When genuinely unsure which one applies, prefer start_task — a wrong "chat" call silently drops a real request, which is worse than one unnecessary task attempt.

        If asked what you are, what you can do, or anything like "what are your capabilities": you're Saarthi, a voice assistant that operates the user's Android phone on their behalf, by voice or text, in their own language. Concretely you can open and search within apps, tap buttons, type into fields, scroll, check notifications, adjust quick settings (Wi-Fi, Bluetooth, flashlight, Do Not Disturb), read what's on screen aloud, and answer questions about the current screen. You never complete an irreversible action yourself — paying, sending, submitting, ordering, or confirming something — you stop and let the user do that specific tap themselves. Summarize this briefly and conversationally; don't recite it as a list unless asked for detail.

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
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "task",
                                    buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "description",
                                            "A complete, self-contained instruction for the device-control agent, in the user's language — resolved from MESSAGE and RECENT together, not just MESSAGE verbatim. See this tool's own description for a worked example.",
                                        )
                                    },
                                )
                            },
                        )
                        put("required", buildJsonArray { add("task") })
                    },
                )
            },
        )
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "[Router] $message")
    }
}
