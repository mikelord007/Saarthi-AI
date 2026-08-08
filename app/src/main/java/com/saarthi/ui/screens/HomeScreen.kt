package com.saarthi.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.chat.ChatEntry
import com.saarthi.speech.Language
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.SaarthiTextField
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.components.SendButton
import com.saarthi.ui.components.SuggestionChip
import com.saarthi.ui.formatRelativeTime
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/**
 * The everyday entry point: speak a task, tap a suggestion, or type one.
 * For a voice-first app, this screen's job is mostly to get out of the way —
 * one large mic control, three examples, and the last two tasks as a receipt.
 */
@Composable
fun HomeScreen(
    language: Language,
    recentEntries: List<ChatEntry>,
    draft: String,
    onDraftChange: (String) -> Unit,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isThinking: Boolean,
    onMicTap: () -> Unit,
    onSuggestionTap: (String) -> Unit,
    onSend: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(icon = SaarthiIcons.Menu, contentDescription = stringResource(R.string.cd_history), onClick = onOpenHistory)
            Kicker(text = stringResource(R.string.app_name), fontSize = 15.sp, letterSpacing = 0.2.em)
            HeaderIconButton(icon = SaarthiIcons.SlidersHorizontal, contentDescription = stringResource(R.string.cd_settings), onClick = onOpenSettings)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = TextStyle(
                    fontFamily = SaarthiType.Cormorant,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                ),
                color = SaarthiColors.Text,
            )
            Spacer(Modifier.height(26.dp))

            HomeMicControl(
                isRecording = isRecording,
                isTranscribing = isTranscribing,
                onClick = onMicTap,
            )
            Spacer(Modifier.height(26.dp))

            Kicker(
                text = homeCaption(isRecording, isTranscribing, isThinking, language),
                fontSize = 14.sp,
                letterSpacing = 0.2.em,
            )
            Spacer(Modifier.height(26.dp))

            // Stacked here to avoid pulling in the experimental FlowRow API for three fixed strings.
            val searchWeather = stringResource(R.string.suggestion_search_weather)
            val readScreen = stringResource(R.string.suggestion_read_screen)
            val searchCricket = stringResource(R.string.suggestion_search_cricket)
            SuggestionChip(text = searchWeather, onClick = { onSuggestionTap(searchWeather) })
            Spacer(Modifier.height(8.dp))
            SuggestionChip(text = readScreen, onClick = { onSuggestionTap(readScreen) })
            Spacer(Modifier.height(8.dp))
            SuggestionChip(text = searchCricket, onClick = { onSuggestionTap(searchCricket) })
        }

        if (recentEntries.isNotEmpty()) {
            EarlierTodaySection(entries = recentEntries.take(2), onOpenHistory = onOpenHistory, onOpenThread = onOpenThread)
        }

        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SaarthiTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.home_text_placeholder),
                onSend = onSend,
            )
            SendButton(onClick = onSend)
        }
    }
}

@Composable
private fun homeCaption(isRecording: Boolean, isTranscribing: Boolean, isThinking: Boolean, language: Language): String = when {
    isRecording -> stringResource(R.string.home_caption_listening)
    isTranscribing -> stringResource(R.string.home_caption_transcribing)
    isThinking -> stringResource(R.string.home_caption_thinking)
    else -> stringResource(R.string.home_caption_tap_and_speak, language.nativeName)
}

@Composable
private fun HeaderIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = SaarthiColors.Neutral700, modifier = Modifier.size(22.dp))
    }
}

/**
 * The 150dp mic control — its own component, not [com.saarthi.ui.components.VoiceStateIndicator]:
 * the outer ring pulses continuously at 3.2s (slower than the voice-state
 * mark's 2.2s) purely as an invitation to tap, independent of any real state.
 */
@Composable
private fun HomeMicControl(isRecording: Boolean, isTranscribing: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val transition = rememberInfiniteTransition(label = "home_mic_ring")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = CubicBezierEasing(0f, 0f, 0.58f, 1f))),
        label = "ring",
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(if (pressed) SaarthiColors.Accent100 else Color.Transparent)
            .border(1.dp, SaarthiColors.Accent, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(150.dp)) {
            val scale = 0.62f + (1.5f - 0.62f) * progress
            val alpha = 0.55f * (1f - progress)
            drawCircle(
                color = SaarthiColors.Accent300.copy(alpha = alpha),
                radius = (size.minDimension / 2) * scale,
                style = Stroke(1.dp.toPx()),
            )
        }
        val label = when {
            isRecording -> "●"
            isTranscribing -> "…"
            else -> null
        }
        if (label != null) {
            Text(text = label, color = SaarthiColors.Accent700, fontSize = 32.sp)
        } else {
            Icon(
                imageVector = SaarthiIcons.Mic,
                contentDescription = stringResource(R.string.cd_speak),
                tint = SaarthiColors.Accent700,
                modifier = Modifier.size(46.dp),
            )
        }
    }
}

@Composable
private fun EarlierTodaySection(entries: List<ChatEntry>, onOpenHistory: () -> Unit, onOpenThread: (String) -> Unit) {
    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            Kicker(text = stringResource(R.string.earlier_today), fontSize = 12.sp, letterSpacing = 0.18.em)
            Spacer(Modifier.weight(1f).height(1.dp).background(SaarthiColors.Divider))
            Text(
                text = stringResource(R.string.all),
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable(onClick = onOpenHistory),
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 13.sp),
                color = SaarthiColors.Accent700,
            )
        }
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onOpenThread(entry.id) })
                    .padding(vertical = 11.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = entry.task,
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp),
                    color = SaarthiColors.Text,
                )
                Text(
                    text = formatRelativeTime(entry.timestamp),
                    style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 13.sp).merge(SaarthiType.TabularFigures),
                    color = SaarthiColors.Neutral600,
                )
            }
            if (index == 0 && entries.size > 1) {
                Hairline()
            }
        }
    }
}
