package com.saarthi.agent

import android.util.Log
import com.saarthi.BuildConfig
import com.saarthi.chat.BlockCause
import com.saarthi.perception.ActionExecutor
import com.saarthi.perception.ActionResult
import com.saarthi.perception.PerceptionResult
import com.saarthi.perception.ScreenPerception
import com.saarthi.perception.ScrollDirection
import com.saarthi.perception.SaarthiAccessibilityService
import kotlinx.coroutines.launch

private const val TAG = "SaarthiAgent"
private const val MAX_STEPS = 20
private const val MAX_NO_PROGRESS_STREAK = 3
private const val MAX_MALFORMED_RETRIES = 2
private const val EMPTY_SCREEN_RETRIES = 3
private const val EMPTY_SCREEN_RETRY_WAIT_MS = 800L
private const val MALFORMED_HINT =
    "Your previous response was not a single valid tool call. Call exactly one tool from the list, with every required field filled in."

/**
 * The ReAct-style takeover loop: per step, capture -> empty-screen retry
 * -> no-progress circuit breaker -> login-wall check -> ask Claude ->
 * dispatch/narrate/execute -> append a history line -> emit an
 * [AgentEvent]. Falling through [MAX_STEPS] without a terminal tool call
 * ends the run as [BlockCause.STEP_BUDGET].
 *
 * Deliberately has no reflexion/self-critique LLM call — neither of the
 * reference agents this design surveyed (M3A, AutoDroid) actually
 * implements "compare before/after state, detect no-change, retry
 * differently" as a second model call; M3A's second call is an
 * unconditional per-step *summarizer*, and AutoDroid folds critique into
 * the single decision JSON. This loop does the latter (see
 * [AgentTool]'s `reasoning` field) plus the no-progress circuit breaker
 * below, which is a genuine addition neither reference has at all.
 */
object AgentLoop {

    /**
     * @param languageDisplayName English display name of the user's chosen
     *   language (e.g. "Hindi") — substituted into the system prompt so
     *   the model narrates and speaks in it.
     * @param initialHistory Resumes a task after [AgentEvent.AskUser] —
     *   pass the history from that event to continue rather than restart.
     * @param narrateEveryStep Gates only per-step "say" narration; terminal
     *   outcomes (done/ask_user/model-declared blocked/answer/error)
     *   always speak regardless of this flag.
     * @param speak Wired to [com.saarthi.speech.SarvamTts] by every real
     *   caller; defaults to a no-op so this loop is fully testable without
     *   TTS.
     * @param onEvent Called once per step, plus exactly once more with a
     *   terminal event, before this function returns.
     */
    suspend fun run(
        service: SaarthiAccessibilityService,
        task: String,
        languageDisplayName: String,
        initialHistory: List<String> = emptyList(),
        narrateEveryStep: Boolean = true,
        speak: suspend (String) -> Unit = {},
        onEvent: suspend (AgentEvent) -> Unit,
    ) {
        // Glow spans the whole run, not just the dispatch/execute branch
        // below — it's the "Saarthi is working" signal, which is just as
        // true while waiting on Claude's decision as while tapping. In a
        // finally so every return path (all the early returns below, a
        // thrown exception, or falling through the step budget) turns it
        // back off.
        service.showTaskGlow()
        try {
            runSteps(service, task, languageDisplayName, initialHistory, narrateEveryStep, speak, onEvent)
        } finally {
            service.hideTaskGlow()
        }
    }

    private suspend fun runSteps(
        service: SaarthiAccessibilityService,
        task: String,
        languageDisplayName: String,
        initialHistory: List<String>,
        narrateEveryStep: Boolean,
        speak: suspend (String) -> Unit,
        onEvent: suspend (AgentEvent) -> Unit,
    ) {
        val history = initialHistory.toMutableList()
        val systemPrompt = AgentPrompt.system(languageDisplayName)
        var previousSerialized: String? = null
        var noProgressStreak = 0
        var stepIndex = 1

        log("▶ TASK: \"$task\" (lang=$languageDisplayName${if (initialHistory.isNotEmpty()) ", resuming ${initialHistory.size} history lines" else ""})")

        while (stepIndex <= MAX_STEPS) {
            log("[Step $stepIndex] Capturing screen…")
            var perception = ScreenPerception.capture(service, goHomeIfEmpty = stepIndex == 1)

            // Empty/non-actionable-screen retry for steps after the first —
            // a blank read, or a read with nothing actionable on it at
            // all, is treated as "app mid-transition," not a dead end. The
            // non-actionable case specifically catches a transitional
            // frame with a few decorative nodes but no real content (e.g.
            // only the status bar's own icons) slipping through despite
            // ActionExecutor's own settle wait.
            fun needsRetry(p: PerceptionResult) = p.isEmpty || !p.hasActionableElement
            if (needsRetry(perception) && stepIndex > 1) {
                var attempt = 0
                while (needsRetry(perception) && attempt < EMPTY_SCREEN_RETRIES) {
                    log("[Step $stepIndex] Screen has nothing actionable yet (attempt ${attempt + 1}/$EMPTY_SCREEN_RETRIES) — waiting for it to settle…")
                    service.awaitContentChanged(EMPTY_SCREEN_RETRY_WAIT_MS)
                    perception = ScreenPerception.capture(service, goHomeIfEmpty = false)
                    attempt++
                }
            }

            if (perception.isEmpty) {
                val message = "I couldn't read the screen — the app may not have finished loading."
                log("[Step $stepIndex] ✖ Screen empty after retries — $message")
                speak(message)
                onEvent(AgentEvent.Error(message))
                return
            }

            log("[Step $stepIndex] Screen (stale=${perception.stale}):\n${perception.serialized}")

            // No-progress circuit breaker: a weak model will happily keep
            // scrolling/tapping a dead end. Compared byte-for-byte against
            // the previous step's serialized screen, not inferred from any
            // ActionResult — a scroll at the end of a list or a tap that
            // lands on the wrong node both report Success despite doing
            // nothing visible.
            if (previousSerialized != null && perception.serialized == previousSerialized) {
                noProgressStreak++
                if (history.isNotEmpty()) {
                    history[history.lastIndex] = "${history.last()} — screen did not change, this had no visible effect"
                }
                if (noProgressStreak >= MAX_NO_PROGRESS_STREAK) {
                    val reason = "The screen stopped responding to my actions."
                    log("[Step $stepIndex] ✖ No-progress circuit breaker tripped ($noProgressStreak identical screens in a row)")
                    speak(reason)
                    onEvent(AgentEvent.Blocked(reason = reason, cause = BlockCause.NO_PROGRESS))
                    return
                }
            } else {
                noProgressStreak = 0
            }
            previousSerialized = perception.serialized

            if (LoginWallDetector.isLoginWall(perception)) {
                val question = "This looks like a login, password, or verification screen — please handle it yourself, then tell me to continue."
                log("[Step $stepIndex] ✖ Login-wall detected — stopping before asking Claude")
                speak(question)
                onEvent(AgentEvent.AskUser(question = question, history = history.toList()))
                return
            }

            // ---- ask Claude, with malformed-output retry ----
            var hint: String? = null
            var tool: AgentTool? = null
            var malformedAttempts = 0

            while (tool == null) {
                val userMessage = AgentPrompt.userMessage(task, history, perception.serialized).let { base ->
                    hint?.let { "$base\n\n$it" } ?: base
                }

                log("[Step $stepIndex] Asking Claude…")
                when (val decision = ClaudeClient.decide(systemPrompt, userMessage)) {
                    is ClaudeDecision.ToolCall -> {
                        log("[Step $stepIndex] Claude → ${decision.tool}")
                        tool = decision.tool
                    }
                    is ClaudeDecision.Malformed -> {
                        malformedAttempts++
                        log("[Step $stepIndex] Claude → malformed response (attempt $malformedAttempts/$MAX_MALFORMED_RETRIES)")
                        if (malformedAttempts > MAX_MALFORMED_RETRIES) {
                            val reason = "I couldn't understand how to proceed."
                            speak(reason)
                            onEvent(AgentEvent.Blocked(reason = reason, cause = BlockCause.MALFORMED_OUTPUT))
                            return
                        }
                        hint = MALFORMED_HINT
                    }
                    is ClaudeDecision.Refused -> {
                        log("[Step $stepIndex] Claude → refused: ${decision.message}")
                        val reason = "I'm not able to help with that step."
                        speak(reason)
                        onEvent(AgentEvent.Error(reason))
                        return
                    }
                    is ClaudeDecision.Failed -> {
                        log("[Step $stepIndex] Claude → failed: ${decision.message}")
                        val message = "Something went wrong talking to the assistant."
                        speak(message)
                        onEvent(AgentEvent.Error(message))
                        return
                    }
                }
            }

            // ---- dispatch ----
            when (val decided = tool) {
                is AgentTool.Done -> {
                    log("✔ TASK DONE: ${decided.summary}")
                    history += "Step $stepIndex: done -> ${decided.summary}"
                    speak(decided.summary)
                    onEvent(AgentEvent.Done(decided.summary))
                    return
                }
                is AgentTool.AskUser -> {
                    log("? TASK PAUSED (ask_user): ${decided.question}")
                    history += "Step $stepIndex: ask_user -> ${decided.question}"
                    speak(decided.question)
                    onEvent(AgentEvent.AskUser(question = decided.question, history = history.toList()))
                    return
                }
                is AgentTool.Blocked -> {
                    log("✖ TASK BLOCKED (model-declared): ${decided.reason}")
                    history += "Step $stepIndex: blocked -> ${decided.reason}"
                    speak(decided.reason)
                    onEvent(AgentEvent.Blocked(reason = decided.reason, cause = BlockCause.MODEL_DECLARED))
                    return
                }
                is AgentTool.Answer -> {
                    // Always spoken directly, never gated by narrateEveryStep
                    // — this IS the information the task asked for, not
                    // routine step narration. The loop continues: answer
                    // does not end the task, done must still follow.
                    history += "Step $stepIndex: answer -> ${decided.text}"
                    speak(decided.text)
                    onEvent(AgentEvent.Step(stepIndex, decided.text))
                    stepIndex++
                }
                else -> {
                    val say = sayOf(decided)
                    onEvent(AgentEvent.Step(stepIndex, say ?: historyDescriptor(decided)))
                    if (narrateEveryStep && say != null) {
                        service.agentScope.launch { speak(say) }
                    }

                    val result = execute(service, perception, decided)
                    log("[Step $stepIndex] Action: ${historyDescriptor(decided)} -> ${describe(result)}")
                    history += "Step $stepIndex: ${historyDescriptor(decided)} -> ${describe(result)}"

                    if (result is ActionResult.Blocked) {
                        log("✖ TASK BLOCKED (irreversible-action guard): ${result.label} (matched \"${result.matchedKeyword}\")")
                        // The irreversible-action guard fired in code,
                        // independent of what the model said it was doing.
                        // Deliberately does not speak here — the caller's
                        // hand-back UI narrates from its own localized copy
                        // using actionLabel, and this stop has no resume
                        // path: the whole point of the guard is that the
                        // user makes this exact tap themselves.
                        onEvent(
                            AgentEvent.Blocked(
                                reason = "This step would ${result.matchedKeyword} something and needs your confirmation.",
                                cause = BlockCause.IRREVERSIBLE_GUARD,
                                actionLabel = result.label,
                            ),
                        )
                        return
                    }
                    // A Failed result is not terminal — the loop continues
                    // so the model sees the failure in history and tries
                    // something else next step.
                    stepIndex++
                }
            }
        }

        val reason = "I reached my step limit without finishing this task."
        log("✖ TASK BLOCKED: step budget ($MAX_STEPS) exhausted")
        speak(reason)
        onEvent(AgentEvent.Blocked(reason = reason, cause = BlockCause.STEP_BUDGET))
    }

    /** Debug-build-only step tracing — see the class doc. Never compiled into a release build's active log output, consistent with [com.saarthi.perception.DebugActionReceiver] being debug-only. */
    private fun log(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private suspend fun execute(
        service: SaarthiAccessibilityService,
        perception: PerceptionResult,
        tool: AgentTool,
    ): ActionResult = when (tool) {
        is AgentTool.Tap -> ActionExecutor.tap(service, perception, tool.ref)
        is AgentTool.SetText -> ActionExecutor.setText(service, perception, tool.ref, tool.text, tool.clear)
        is AgentTool.Scroll -> ActionExecutor.scroll(
            service,
            perception,
            if (tool.direction.equals("up", ignoreCase = true)) ScrollDirection.UP else ScrollDirection.DOWN,
        )
        is AgentTool.LongPress -> ActionExecutor.longPress(service, perception, tool.ref)
        is AgentTool.KeyboardEnter -> ActionExecutor.keyboardEnter(service, perception)
        is AgentTool.Back -> ActionExecutor.back(service)
        is AgentTool.Home -> ActionExecutor.home(service)
        is AgentTool.Notifications -> ActionExecutor.openNotifications(service)
        is AgentTool.QuickSettings -> ActionExecutor.openQuickSettings(service)
        else -> error("execute() called with a terminal/answer tool: $tool")
    }

    private fun sayOf(tool: AgentTool): String? = when (tool) {
        is AgentTool.Tap -> tool.say
        is AgentTool.SetText -> tool.say
        is AgentTool.Scroll -> tool.say
        is AgentTool.LongPress -> tool.say
        is AgentTool.KeyboardEnter -> tool.say
        is AgentTool.Back -> tool.say
        is AgentTool.Home -> tool.say
        is AgentTool.Notifications -> tool.say
        is AgentTool.QuickSettings -> tool.say
        else -> null
    }

    private fun historyDescriptor(tool: AgentTool): String = when (tool) {
        is AgentTool.Tap -> "tap(${tool.ref})"
        is AgentTool.SetText -> "set_text(${tool.ref}, \"${tool.text}\", clear=${tool.clear})"
        is AgentTool.Scroll -> "scroll(${tool.direction})"
        is AgentTool.LongPress -> "long_press(${tool.ref})"
        is AgentTool.KeyboardEnter -> "keyboard_enter"
        is AgentTool.Back -> "back"
        is AgentTool.Home -> "home"
        is AgentTool.Notifications -> "notifications"
        is AgentTool.QuickSettings -> "quick_settings"
        else -> tool.toString()
    }

    private fun describe(result: ActionResult): String = when (result) {
        is ActionResult.Success -> "Success"
        is ActionResult.Blocked -> "Blocked(${result.label}, keyword=${result.matchedKeyword})"
        is ActionResult.Failed -> "Failed(${result.reason})"
    }
}
