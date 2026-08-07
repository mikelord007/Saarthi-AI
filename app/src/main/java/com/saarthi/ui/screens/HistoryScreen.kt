package com.saarthi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.chat.BlockCause
import com.saarthi.chat.ChatEntry
import com.saarthi.chat.ChatStatus
import com.saarthi.chat.EntryKind
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.components.Pill
import com.saarthi.ui.formatRelativeTime
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/**
 * The "quiet half" of the app — a flat receipt list of what ran, what it
 * cost (in steps), and what it stopped short of. One list, newest first;
 * rows where a task actually ran (as opposed to a plain chat reply) carry a
 * small "Task" tag rather than living on a separate tab — a thread can be
 * chat for a while and then run a task partway through, so a hard split
 * into two tabs would have to put it in both or neither. Tapping any row
 * opens [ThreadDetailScreen].
 */
@Composable
fun HistoryScreen(entries: List<ChatEntry>, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val sorted = entries.sortedByDescending { it.timestamp }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(imageVector = SaarthiIcons.ArrowLeft, contentDescription = stringResource(R.string.back), tint = SaarthiColors.Neutral700, modifier = Modifier.size(20.dp))
            }
            Kicker(text = stringResource(R.string.history_title), fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
            items(sorted, key = { it.id }) { entry ->
                val isTask = isTaskEntry(entry)
                HistoryRow(
                    title = entry.task,
                    time = formatRelativeTime(entry.timestamp),
                    note = if (entry.kind == EntryKind.NARRATION || isTask) taskNote(entry) else conversationNote(entry),
                    isTask = isTask,
                    onClick = { onOpenThread(entry.id) },
                )
            }
        }
    }
}

/** A real task run happened somewhere in this thread — as opposed to a plain spoken/typed reply. */
private fun isTaskEntry(entry: ChatEntry): Boolean = entry.turns.any { it.isTaskStep }

@Composable
private fun HistoryRow(title: String, time: String, note: String, isTask: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                if (isTask) {
                    Pill(text = stringResource(R.string.history_task_tag), on = true)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.5.sp),
                    color = SaarthiColors.Text,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = time,
                style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 13.sp).merge(SaarthiType.TabularFigures),
                color = SaarthiColors.Neutral600,
            )
        }
        Text(
            text = note,
            modifier = Modifier.padding(top = 5.dp),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 14.sp, fontStyle = FontStyle.Italic),
            color = SaarthiColors.Neutral600,
        )
        Spacer(Modifier.height(16.dp))
        Hairline()
    }
}

@Composable
private fun taskNote(entry: ChatEntry): String {
    if (entry.kind == EntryKind.NARRATION) return stringResource(R.string.note_narrated)
    return when (entry.status) {
        ChatStatus.RUNNING -> stringResource(R.string.note_in_progress)
        ChatStatus.ASK_USER -> stringResource(R.string.note_waiting_answer)
        ChatStatus.DONE -> if (entry.stepCount > 0) pluralStringResource(R.plurals.steps_count, entry.stepCount, entry.stepCount) else stringResource(R.string.note_done)
        ChatStatus.BLOCKED -> when (entry.blockCause) {
            BlockCause.IRREVERSIBLE_GUARD -> {
                val stepsPart = if (entry.stepCount > 0) pluralStringResource(R.plurals.steps_count, entry.stepCount, entry.stepCount) + " · " else ""
                stepsPart + stringResource(R.string.note_handback_at, entry.handbackLabel ?: "")
            }
            else -> stringResource(R.string.note_stopped, lastAssistantText(entry))
        }
        ChatStatus.ERROR -> stringResource(R.string.note_couldnt_finish, lastAssistantText(entry))
    }
}

@Composable
private fun conversationNote(entry: ChatEntry): String {
    val replies = entry.turns.count { it.role == "assistant" }
    return stringResource(R.string.conversation_note, entry.task, pluralStringResource(R.plurals.replies_count, replies, replies))
}

@Composable
private fun lastAssistantText(entry: ChatEntry): String =
    entry.turns.lastOrNull { it.role == "assistant" }?.text?.take(60) ?: stringResource(R.string.unknown_reason)
