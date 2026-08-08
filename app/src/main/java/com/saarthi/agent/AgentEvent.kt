package com.saarthi.agent

import com.saarthi.chat.BlockCause

/** The only thing UI code consumes from [AgentLoop] — one per step, plus exactly one terminal event per run. */
sealed interface AgentEvent {
    data class Step(val index: Int, val description: String) : AgentEvent
    data class Done(val summary: String) : AgentEvent

    /**
     * Resumable: [history] is the full step history so far, so a caller
     * can continue the SAME task once the user answers —
     * `AgentLoop.run(task, initialHistory = event.history)` — instead of
     * starting a brand-new task with no memory. Every other terminal
     * event is a genuine hard stop with no resume path.
     */
    data class AskUser(val question: String, val history: List<String>) : AgentEvent

    /**
     * [actionLabel] (the blocked button's own visible text, e.g. "Pay
     * ₹450") is only ever populated for [BlockCause.IRREVERSIBLE_GUARD] —
     * deliberately never auto-resumed on the next utterance, since the
     * whole point of the guard is that the user makes that tap
     * themselves.
     */
    data class Blocked(val reason: String, val cause: BlockCause, val actionLabel: String? = null) : AgentEvent
    data class Error(val message: String) : AgentEvent
}
