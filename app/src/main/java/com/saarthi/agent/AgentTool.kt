package com.saarthi.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The agent's 13-action vocabulary. Nothing in this file (or anywhere
 * under `com.saarthi.agent`) imports `android.view.accessibility.*` — the
 * agent reasons purely over the ref strings [com.saarthi.perception.ScreenPerception]
 * hands out; only `com.saarthi.perception` ever touches a real node.
 *
 * Each non-terminal action carries `say` (one short sentence, in the
 * user's language, narrated aloud before the action runs) and `reasoning`
 * (one short sentence in English, for the internal step-history log only
 * — not spoken). `reasoning` is how this design gets the self-critique
 * signal AutoDroid's `Analyses`/`Next step` fields provide, without
 * paying for a second LLM call the way M3A's separate step-summary call
 * does — see AgentLoop's doc for the no-progress circuit breaker this
 * still doesn't replace.
 */
sealed interface AgentTool {
    data class Tap(val ref: String, val say: String, val reasoning: String) : AgentTool
    data class SetText(val ref: String, val text: String, val clear: Boolean, val say: String, val reasoning: String) : AgentTool
    data class Scroll(val direction: String, val say: String, val reasoning: String) : AgentTool
    data class LongPress(val ref: String, val say: String, val reasoning: String) : AgentTool
    data class KeyboardEnter(val say: String, val reasoning: String) : AgentTool
    data class Back(val say: String, val reasoning: String) : AgentTool
    data class Home(val say: String, val reasoning: String) : AgentTool
    data class Notifications(val say: String, val reasoning: String) : AgentTool
    data class QuickSettings(val say: String, val reasoning: String) : AgentTool

    /** Speaks [text] but does NOT end the task — `done` must still follow. See the `answer` tool description below for why this exists separately from `done`. */
    data class Answer(val text: String) : AgentTool
    data class Done(val summary: String) : AgentTool
    data class AskUser(val question: String) : AgentTool
    data class Blocked(val reason: String) : AgentTool

    companion object {
        /** The `tools` array for the Claude request — see [AgentToolSchema]. */
        fun toolDefinitions(): JsonArray = AgentToolSchema.definitions()
    }
}

/** Parses a Claude `tool_use` block's `(name, input)` into an [AgentTool]. Null on any unrecognized name or missing required field — treated as malformed by the caller, never a crash. */
object AgentToolParser {

    fun parse(name: String, input: JsonObject): AgentTool? = runCatching {
        when (name) {
            "tap" -> AgentTool.Tap(
                ref = input.string("ref") ?: return null,
                say = input.string("say").orEmpty(),
                reasoning = input.string("reasoning").orEmpty(),
            )
            "set_text" -> AgentTool.SetText(
                ref = input.string("ref") ?: return null,
                text = input.string("text") ?: return null,
                clear = input["clear"]?.jsonPrimitive?.booleanOrNull ?: false,
                say = input.string("say").orEmpty(),
                reasoning = input.string("reasoning").orEmpty(),
            )
            "scroll" -> AgentTool.Scroll(
                direction = input.string("direction") ?: return null,
                say = input.string("say").orEmpty(),
                reasoning = input.string("reasoning").orEmpty(),
            )
            "long_press" -> AgentTool.LongPress(
                ref = input.string("ref") ?: return null,
                say = input.string("say").orEmpty(),
                reasoning = input.string("reasoning").orEmpty(),
            )
            "keyboard_enter" -> AgentTool.KeyboardEnter(
                say = input.string("say").orEmpty(),
                reasoning = input.string("reasoning").orEmpty(),
            )
            "back" -> AgentTool.Back(say = input.string("say").orEmpty(), reasoning = input.string("reasoning").orEmpty())
            "home" -> AgentTool.Home(say = input.string("say").orEmpty(), reasoning = input.string("reasoning").orEmpty())
            "notifications" -> AgentTool.Notifications(say = input.string("say").orEmpty(), reasoning = input.string("reasoning").orEmpty())
            "quick_settings" -> AgentTool.QuickSettings(say = input.string("say").orEmpty(), reasoning = input.string("reasoning").orEmpty())
            "answer" -> AgentTool.Answer(text = input.string("text") ?: return null)
            "done" -> AgentTool.Done(summary = input.string("summary") ?: return null)
            "ask_user" -> AgentTool.AskUser(question = input.string("question") ?: return null)
            "blocked" -> AgentTool.Blocked(reason = input.string("reason") ?: return null)
            else -> null
        }
    }.getOrNull()

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/** The Claude tool-schema (`name`/`description`/`input_schema`) for each of the 13 [AgentTool] variants. */
private object AgentToolSchema {

    private const val SAY_DESCRIPTION =
        "One short natural sentence, in the user's language, narrating this action out loud before it happens — e.g. \"Wi-Fi खोल रहा हूँ\"."
    private const val REASONING_DESCRIPTION =
        "One short sentence in English explaining why this action moves the task forward. Not spoken to the user — goes into the internal step history only."
    private const val REF_DESCRIPTION =
        "The ref of the target element from the SCREEN block, e.g. \"e4\". Must be an element that has bounds in the current SCREEN — a ref with no bounds is off-screen; scroll first."

    fun definitions(): JsonArray = buildJsonArray {
        add(
            toolDef(
                "tap",
                "Tap a clickable element identified by its ref from the SCREEN block — buttons, links, list rows, icons.",
                mapOf("ref" to stringProp(REF_DESCRIPTION), "say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("ref", "say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "set_text",
                "Type text into an editable field identified by its ref. Sets the field's full text — it does not press Enter/Search afterward; call keyboard_enter separately if the field needs submitting.",
                mapOf(
                    "ref" to stringProp(REF_DESCRIPTION),
                    "text" to stringProp("The text to type into the field."),
                    "clear" to buildJsonObject {
                        put("type", "boolean")
                        put("description", "True to erase any existing text in the field before typing — set this whenever the field may already have default or placeholder text in it.")
                    },
                    "say" to stringProp(SAY_DESCRIPTION),
                    "reasoning" to stringProp(REASONING_DESCRIPTION),
                ),
                listOf("ref", "text", "say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "scroll",
                "Scroll the main scrollable list or container on screen. If scrolling one direction doesn't reveal what you expect, try the opposite direction next.",
                mapOf(
                    "direction" to buildJsonObject {
                        put("type", "string")
                        put("enum", buildJsonArray { add("up"); add("down") })
                        put("description", "\"down\" reveals content further down the screen / toward the bottom of the list. \"up\" reveals content further up / toward the top.")
                    },
                    "say" to stringProp(SAY_DESCRIPTION),
                    "reasoning" to stringProp(REASONING_DESCRIPTION),
                ),
                listOf("direction", "say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "long_press",
                "Long-press (press and hold) an element identified by its ref — used to enter text-selection mode or open a context menu.",
                mapOf("ref" to stringProp(REF_DESCRIPTION), "say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("ref", "say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "keyboard_enter",
                "Press the keyboard's Enter/Search/Go key on the currently focused text field — use this after set_text to submit a search or form field.",
                mapOf("say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "back",
                "Press the device Back button/gesture.",
                mapOf("say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "home",
                "Press the device Home button — leaves the current app entirely. Rarely needed mid-task.",
                mapOf("say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "notifications",
                "Open the notification shade, exactly like swiping down from the top of the screen — use this instead of trying to scroll or swipe to see notifications.",
                mapOf("say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "quick_settings",
                "Open Quick Settings (Wi-Fi, Bluetooth, flashlight, and other toggles), exactly like swiping down twice from the top of the screen — use this instead of trying to scroll or swipe to reach these toggles.",
                mapOf("say" to stringProp(SAY_DESCRIPTION), "reasoning" to stringProp(REASONING_DESCRIPTION)),
                listOf("say", "reasoning"),
            ),
        )
        add(
            toolDef(
                "answer",
                "Speak an answer to the user's question out loud, for tasks that ask for information (e.g. \"what's on my calendar tomorrow\"). This does NOT end the task — you must still call done afterward. Merely showing the answer on screen is not enough; the user needs to hear it.",
                mapOf("text" to stringProp("The answer to speak to the user, in their language.")),
                listOf("text"),
            ),
        )
        add(
            toolDef(
                "done",
                "Declare the task complete. Use this as soon as the current SCREEN already shows what the task asked for — don't keep tapping once the goal is achieved.",
                mapOf("summary" to stringProp("One short sentence, in the user's language, summarizing what was accomplished.")),
                listOf("summary"),
            ),
        )
        add(
            toolDef(
                "ask_user",
                "Stop and ask the user a question because you need information or a decision only they can provide — e.g. which of several matching results to choose. The user's next reply resumes this same task.",
                mapOf("question" to stringProp("The question to ask the user, in their language.")),
                listOf("question"),
            ),
        )
        add(
            toolDef(
                "blocked",
                "Stop the task because it cannot or should not be completed automatically — the target app/feature doesn't exist, or the next step would be an irreversible action (pay, send, submit, order, transfer, confirm, buy) that only the user should perform. Never tap such a button yourself — call blocked instead.",
                mapOf("reason" to stringProp("One short sentence, in the user's language, explaining why the task was stopped.")),
                listOf("reason"),
            ),
        )
    }

    private fun stringProp(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun toolDef(name: String, description: String, properties: Map<String, JsonElement>, required: List<String>): JsonObject =
        buildJsonObject {
            put("name", name)
            put("description", description)
            put(
                "input_schema",
                buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject { properties.forEach { (key, value) -> put(key, value) } })
                    put("required", buildJsonArray { required.forEach { add(it) } })
                },
            )
        }
}
