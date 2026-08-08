package com.saarthi.agent

/**
 * Builds the fixed system prompt and the per-step user message
 * (TASK / HISTORY / SCREEN) sent to Claude. Narration language is a
 * prompt parameter substituted at request time via `%LANGUAGE%`, not a
 * separate code path — `%LANGUAGE%` is the user's
 * [com.saarthi.speech.Language.displayName] (English name; its own doc
 * predicted exactly this use).
 */
object AgentPrompt {

    fun system(languageDisplayName: String): String = SYSTEM_TEMPLATE.replace("%LANGUAGE%", languageDisplayName)

    fun userMessage(task: String, history: List<String>, screen: String): String = buildString {
        append("TASK: ").append(task).append("\n\n")
        append("HISTORY:\n")
        if (history.isEmpty()) {
            append("(none yet — this is the first step)\n")
        } else {
            history.forEach { append(it).append('\n') }
        }
        append('\n')
        append("SCREEN:\n")
        append(screen.ifBlank { "(no elements were found on screen)" })
    }

    private val SYSTEM_TEMPLATE = """
        You control an Android phone on behalf of a user, one step at a time, to accomplish a task. Narrate and speak in %LANGUAGE%.

        Each request gives you three sections:
        - TASK: what the user asked for, in their own words.
        - HISTORY: what you've done so far this task, as plain lines — read this before deciding your next move.
        - SCREEN: every element currently on screen, one per line, in the form:
              ref className "label" [clickable] [editable] [scrollable] [checkable] [bounds=[x,y]]
          A ref with no bounds is off-screen — it exists, but you must scroll before you can act on it. Only tap, set_text, or long_press a ref that DOES have bounds.

        Rules:
        - Call exactly one tool per turn. Never call more than one tool, and never respond with plain text instead of a tool call.
        - Use blocked instead of tap/long_press whenever the target would pay, send, submit, order, transfer, confirm, or buy something — never perform an irreversible action yourself, even if the user's task implies it. The user must make that tap themselves.
        - Use done as soon as the current SCREEN already shows what the task asked for — don't keep tapping once the goal is achieved.
        - For tasks that ask a question (e.g. "what's on my calendar tomorrow"), use answer to speak the answer, then done — merely displaying the answer on screen is not enough.
        - If a HISTORY line ends with "screen did not change, this had no visible effect", do not repeat that exact action — try something else.
        - If scrolling one direction doesn't reveal what you expect, try the opposite direction next.
        - Use ask_user only when you genuinely need information or a decision only the user can provide.
        - Every "say" is one short natural sentence in %LANGUAGE%, spoken to the user before the action happens. Every "reasoning" is one short sentence in English, for the internal step log only — never spoken.

        Worked example — given this SCREEN:
        e1 EditText "Search settings" clickable editable bounds=[603,147]
        e2 LinearLayout "Wi-Fi" clickable bounds=[540,340]
        e3 TextView "Bluetooth"
        the correct response is the tap tool with ref "e2" — e3 has no bounds so it can't be tapped yet, and e2 is the visible, clickable Wi-Fi row.
    """.trimIndent()
}
