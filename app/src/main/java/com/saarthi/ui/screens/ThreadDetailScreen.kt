package com.saarthi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.chat.ChatEntry
import com.saarthi.chat.ChatStatus
import com.saarthi.chat.ChatTurn
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.SaarthiTextField
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.components.Pill
import com.saarthi.ui.components.SendButton
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/**
 * A History row's destination. Turns are drawn as bubbles — right/gold-
 * stroked for the user, left/paper-filled for the app, built from the same
 * tokens as the rest of the design (Cormorant kicker labels, Lora body
 * copy, hairline-weight strokes, gold used only as a stroke, never a solid
 * fill). Carries its own message row so a thread can keep going here — chat
 * for a while, then ask for a task, all in the same conversation — instead
 * of being read-only. A thread that starts as chat and then runs a task
 * gets a "Task" tag with its outcome (steps/done/handed back/etc, same
 * note text [HistoryScreen] shows) right above the turn where it finished.
 */
@Composable
fun ThreadDetailScreen(
    entry: ChatEntry?,
    onBack: () -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
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
            Kicker(text = stringResource(R.string.conversation_title), fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.conversation_gone), style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp), color = SaarthiColors.Neutral600)
            }
            return
        }

        Text(
            text = entry.task,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 23.sp, lineHeight = 27.sp),
            color = SaarthiColors.Text,
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
            itemsIndexed(entry.turns) { index, turn ->
                val taskStartsHere = turn.isTaskStep && (index == 0 || !entry.turns[index - 1].isTaskStep)
                if (taskStartsHere) TaskStartTag(turn)
                TurnRow(turn)
            }
            if (entry.status == ChatStatus.RUNNING) {
                item { ThinkingRow() }
            }
        }

        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val busy = entry.status == ChatStatus.RUNNING
            SaarthiTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.thread_reply_placeholder),
                enabled = !busy,
                onSend = onSend,
            )
            SendButton(onClick = onSend)
        }
    }
}

/** Bubble corner radius — larger than [com.saarthi.ui.theme.SaarthiDimens.RadiusLg] on purpose, the one place in this design that reads as "chat." */
private val BubbleRadius = 16.dp
private val BubbleTailRadius = 4.dp

private fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = BubbleRadius,
    topEnd = BubbleRadius,
    bottomStart = if (isUser) BubbleRadius else BubbleTailRadius,
    bottomEnd = if (isUser) BubbleTailRadius else BubbleRadius,
)

@Composable
private fun Bubble(isUser: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fill = if (isUser) SaarthiColors.Neutral100 else SaarthiColors.Surface
    val stroke = if (isUser) SaarthiColors.Accent else SaarthiColors.Divider
    Box(
        modifier = modifier
            .background(fill, bubbleShape(isUser))
            .border(1.dp, stroke, bubbleShape(isUser))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun ThinkingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Start) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth(0.82f)) {
            Kicker(text = stringResource(R.string.app_name), fontSize = 11.sp, letterSpacing = 0.16.em, color = SaarthiColors.Accent700)
            Spacer(Modifier.height(4.dp))
            Bubble(isUser = false) {
                Text(
                    text = stringResource(R.string.home_caption_thinking),
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic),
                    color = SaarthiColors.Neutral600,
                )
            }
        }
    }
}

/** The marker showing a task ran at this point in the thread — [turn] is always the terminal outcome turn (see [ChatTurn.isTaskStep]'s doc), so its own fields carry the outcome to render, same note text [HistoryScreen] shows per-entry. */
@Composable
private fun TaskStartTag(turn: ChatTurn) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Pill(text = stringResource(R.string.history_task_tag), on = true)
        Spacer(Modifier.width(8.dp))
        Text(
            text = taskOutcomeNote(turn.taskStatus ?: ChatStatus.DONE, turn.blockCause, turn.handbackLabel, turn.taskStepCount, turn.text),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 14.sp, fontStyle = FontStyle.Italic),
            color = SaarthiColors.Neutral600,
        )
    }
}

@Composable
private fun TurnRow(turn: ChatTurn) {
    val isUser = turn.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Kicker(
                text = if (isUser) stringResource(R.string.you_label) else stringResource(R.string.app_name),
                fontSize = 11.sp,
                letterSpacing = 0.16.em,
                color = if (isUser) SaarthiColors.Neutral600 else SaarthiColors.Accent700,
            )
            Spacer(Modifier.height(4.dp))
            Bubble(isUser = isUser) {
                Text(
                    text = turn.text,
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
                    color = SaarthiColors.Text,
                )
            }
        }
    }
}
