package com.saarthi.chat

import kotlinx.serialization.Serializable

/**
 * [isTaskStep] marks the terminal turn of a completed task run — not
 * "where the task began" but where it ended, carrying its outcome. Set
 * only on that one turn; every other turn (both sides of a plain chat
 * exchange, and the user turn that triggered the task) leaves it false.
 * [ThreadDetailScreen][com.saarthi.ui.screens.ThreadDetailScreen]'s
 * `TaskStartTag` renders right above the first turn where this flips
 * false -> true, so a thread that's chat for a while and then runs a
 * task gets a marker exactly at that point.
 */
@Serializable
data class ChatTurn(
    val role: String,
    val text: String,
    val isTaskStep: Boolean = false,
    /** These four are set only when [isTaskStep] is true — same meaning as the matching [ChatEntry] fields, but scoped to this one run instead of the whole thread. Defaulted so turns persisted before these fields existed still decode. */
    val taskStatus: ChatStatus? = null,
    val blockCause: BlockCause? = null,
    val handbackLabel: String? = null,
    val taskStepCount: Int = 0,
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
