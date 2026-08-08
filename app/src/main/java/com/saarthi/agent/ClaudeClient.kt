package com.saarthi.agent

import com.saarthi.BuildConfig
import com.saarthi.net.SaarthiHttp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

private const val ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages"
private const val ANTHROPIC_VERSION = "2023-06-01"

/**
 * The model used for the agent's per-step tap/scroll/done decisions.
 * Sonnet, not Opus or Haiku: this is a high-volume, latency-sensitive,
 * small-context "pick 1 of 11 tools" decision, called many times per task
 * (up to `AgentLoop.MAX_STEPS`) — Opus is too slow/expensive per step at
 * that volume, and Haiku risks missing irreversible-action semantics and
 * narration-text quality in the user's language. Kept behind this one
 * constant so it's a one-line swap if real-world cost/accuracy data says
 * otherwise later.
 */
private const val AGENT_MODEL = "claude-sonnet-5"
private const val MAX_TOKENS = 1024

/** A step decision from Claude, or a reason there isn't one — never a boolean; callers need to tell "malformed" from "refused" from "network failure". */
sealed interface ClaudeDecision {
    data class ToolCall(val tool: AgentTool) : ClaudeDecision

    /** No usable `tool_use` block in the response — caller retries with a hint, per AgentLoop's malformed-output policy. */
    data object Malformed : ClaudeDecision
    data class Refused(val message: String) : ClaudeDecision
    data class Failed(val message: String) : ClaudeDecision
}

object ClaudeClient {

    suspend fun decide(systemPrompt: String, userMessage: String): ClaudeDecision = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.ANTHROPIC_API_KEY
        if (apiKey.isBlank()) return@withContext ClaudeDecision.Failed("Anthropic API key not configured (local.properties: anthropic.apiKey)")

        val requestBody = buildJsonObject {
            put("model", AGENT_MODEL)
            put("max_tokens", MAX_TOKENS)
            // Adaptive thinking is ON by default on Sonnet 5 when `thinking`
            // is omitted — a silent default change from Sonnet 4.6/Opus
            // 4.8. Disabling it explicitly keeps this a fast, cheap
            // per-step decision instead of spending part of max_tokens on
            // unrequested reasoning. No temperature/top_p/top_k — Sonnet 5
            // rejects them at non-default values.
            put("thinking", buildJsonObject { put("type", "disabled") })
            put("output_config", buildJsonObject { put("effort", "low") })
            put("system", systemPrompt)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put("content", userMessage)
                        },
                    )
                },
            )
            put("tools", AgentTool.toolDefinitions())
            // The primary, code-level control for "exactly one decision per
            // turn" — the system prompt's "call exactly one tool" rule is
            // backup only, matching the irreversible-guard philosophy of
            // code-first/prompt-second. disable_parallel_tool_use rules out
            // Claude's default of allowing multiple tool_use blocks in one
            // response; the response parser below additionally takes only
            // the first tool_use block as a third layer.
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

        try {
            SaarthiHttp.client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string()
                if (!response.isSuccessful || rawBody == null) {
                    ClaudeDecision.Failed("Claude request failed: HTTP ${response.code} ${rawBody.orEmpty()}")
                } else {
                    parseResponse(rawBody)
                }
            }
        } catch (e: IOException) {
            ClaudeDecision.Failed("Claude request failed: ${e.message}")
        }
    }

    private fun parseResponse(rawBody: String): ClaudeDecision {
        val json = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull()
            ?: return ClaudeDecision.Failed("Claude response was not valid JSON")

        // Branch on stop_reason, never on stop_details — stop_details can
        // be null even on an actual refusal.
        when (json["stop_reason"]?.jsonPrimitive?.contentOrNull) {
            "refusal" -> return ClaudeDecision.Refused("Claude declined this step")
            "max_tokens" -> return ClaudeDecision.Failed("Claude response was truncated before completing a tool call (max_tokens)")
        }

        val content: JsonArray = json["content"]?.jsonArray ?: return ClaudeDecision.Malformed
        val toolUse = content
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "tool_use" }
            ?: return ClaudeDecision.Malformed

        val name = toolUse["name"]?.jsonPrimitive?.contentOrNull ?: return ClaudeDecision.Malformed
        val input = toolUse["input"]?.jsonObject ?: return ClaudeDecision.Malformed
        val tool = AgentToolParser.parse(name, input) ?: return ClaudeDecision.Malformed

        return ClaudeDecision.ToolCall(tool)
    }
}
