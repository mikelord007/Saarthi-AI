package com.saarthi.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.saarthi.R
import com.saarthi.chat.BlockCause
import com.saarthi.chat.ChatStatus

/**
 * The status/step-count/hand-back note text shared by [HistoryScreen]'s
 * per-entry row (one task run's worth of state, at the [com.saarthi.chat.ChatEntry]
 * level) and [ThreadDetailScreen]'s per-turn "Task" tag (one run's outcome,
 * at the [com.saarthi.chat.ChatTurn] level) — same shape, different
 * granularity, so this takes the raw values rather than either type
 * directly. [detail] is the text shown for the BLOCKED-without-hand-back
 * and ERROR cases (History passes the thread's last assistant turn;
 * ThreadDetailScreen passes this turn's own text, which is the same
 * string in practice since the terminal turn IS the last assistant turn).
 */
@Composable
internal fun taskOutcomeNote(status: ChatStatus, blockCause: BlockCause?, handbackLabel: String?, stepCount: Int, detail: String): String =
    when (status) {
        ChatStatus.RUNNING -> stringResource(R.string.note_in_progress)
        ChatStatus.ASK_USER -> stringResource(R.string.note_waiting_answer)
        ChatStatus.DONE -> if (stepCount > 0) pluralStringResource(R.plurals.steps_count, stepCount, stepCount) else stringResource(R.string.note_done)
        ChatStatus.BLOCKED -> when (blockCause) {
            BlockCause.IRREVERSIBLE_GUARD -> {
                val stepsPart = if (stepCount > 0) pluralStringResource(R.plurals.steps_count, stepCount, stepCount) + " · " else ""
                stepsPart + stringResource(R.string.note_handback_at, handbackLabel ?: "")
            }
            else -> stringResource(R.string.note_stopped, detail)
        }
        ChatStatus.ERROR -> stringResource(R.string.note_couldnt_finish, detail)
    }
