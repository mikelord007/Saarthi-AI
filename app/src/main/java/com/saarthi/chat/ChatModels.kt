package com.saarthi.chat

import kotlinx.serialization.Serializable

@Serializable
data class ChatTurn(
    val role: String,
    val text: String,
    /** True for a turn produced while running a task (as opposed to a plain reply) — lets a thread that starts as chat and later runs a task mark where the task began. */
    val isTaskStep: Boolean = false,
)

enum class ChatStatus { RUNNING, DONE, ASK_USER, BLOCKED, ERROR }

/** Distinguishes a completed task run from a "read the screen" entry — History's two note styles differ, and only TASK entries have step counts. */
enum class EntryKind { TASK, NARRATION }

/**
 * Why a task stopped without finishing — [IRREVERSIBLE_GUARD] gets
 * History's "handed back at" note and the hand-back card; everything else
 * is a plain stop/failure note. [MODEL_DECLARED] is the model itself
 * calling the `blocked` tool (task infeasible, app/feature missing);
 * [STEP_BUDGET] is AgentLoop.MAX_STEPS being reached with no terminal
 * tool call; [MALFORMED_OUTPUT] is two consecutive un-parseable Claude
 * responses; [NO_PROGRESS] is the no-progress circuit breaker firing on
 * an unchanging screen.
 */
enum class BlockCause { IRREVERSIBLE_GUARD, MODEL_DECLARED, STEP_BUDGET, MALFORMED_OUTPUT, NO_PROGRESS, OTHER }

@Serializable
data class ChatEntry(
    val id: String,
    val task: String,
    val timestamp: Long,
    val turns: List<ChatTurn>,
    val status: ChatStatus = ChatStatus.RUNNING,
    /** Highest step index reached this run — retained here for History's "N steps" note. */
    val stepCount: Int = 0,
    /** Set only when [status] is BLOCKED — which stop reason, so History can tell a real hand-back from a failure. */
    val blockCause: BlockCause? = null,
    /** The blocked button's own visible text (e.g. "Pay ₹450") — set only for [BlockCause.IRREVERSIBLE_GUARD]. */
    val handbackLabel: String? = null,
    val kind: EntryKind = EntryKind.TASK,
    /** Set only when [status] is ASK_USER — AgentLoop's step history so far, so the next user reply can resume this same task instead of starting over. Defaulted so entries persisted before this field existed still decode. */
    val agentHistory: List<String> = emptyList(),
)
