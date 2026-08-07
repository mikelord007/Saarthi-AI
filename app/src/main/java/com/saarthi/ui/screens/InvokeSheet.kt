package com.saarthi.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiDimens
import com.saarthi.ui.theme.SaarthiTheme
import com.saarthi.ui.theme.SaarthiType
import kotlinx.coroutines.delay

/**
 * The three states of the invoke sheet — the surface a long-press home (or
 * the gesture-nav assist swipe) opens. Driven entirely from outside: see
 * [InvokeSheet]. There is no fourth "asking a follow-up question" or "error"
 * state here — those are [com.saarthi.ui.SaarthiInteractionSession]'s own
 * concern (it falls back to re-entering [Listening]), because this surface
 * never grows a text field or a chat-style history to show them in.
 */
sealed interface InvokeState {
    data object Listening : InvokeState

    data class Heard(val transcript: String, val plan: String) : InvokeState

    /**
     * [step]/[of] drive the four-segment progress track. [of] is the number
     * of VISIBLE segments, not a real step budget — a caller driving this
     * from a larger step count should first collapse it down (e.g. 3 real
     * steps per segment) before constructing this state.
     */
    data class Working(val step: Int, val of: Int, val label: String) : InvokeState
}

/**
 * The invoke sheet: one composable, driven by [state]. Bottom-anchored;
 * hosted by [com.saarthi.ui.SaarthiInteractionSession] inside a
 * non-fullscreen, non-dimming window so the app underneath stays visible.
 *
 * Deliberately dumb — no capture or task logic happens in here, and there's
 * no text field: typing is the home-screen fallback, not this surface.
 * Every action the sheet can trigger is a callback; the caller decides what
 * actually happens.
 */
@Composable
fun InvokeSheet(
    state: InvokeState,
    languageLabel: String,
    onMicTap: () -> Unit,
    onReadScreenAloud: () -> Unit,
    onDoTaskForMe: () -> Unit,
    onConfirmPlan: () -> Unit,
    onRetryTranscript: () -> Unit,
    onStop: () -> Unit,
    onSwipeDownDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The entrance slide-up plays exactly once per session (Listening's first
    // appearance) — Listening -> Heard -> Working never re-triggers it;
    // Working's own collapse is the animateContentSize below, not this.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    // animateContentSize would otherwise ALSO animate the sheet's very first
    // layout pass (growing from zero), fighting the slide-in above. Delay
    // enabling it until the entrance animation's own duration has elapsed, so
    // only later state changes (Listening->Heard grows, Heard->Working
    // shrinks to a rail) animate their height.
    var animateSize by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(220)
        animateSize = true
    }

    var dragAccumulatorPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 96.dp.toPx() }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        enter = slideInVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { fullHeight -> fullHeight },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(SaarthiColors.Bg)
                .then(if (animateSize) Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing)) else Modifier)
                .pointerInput(onSwipeDownDismiss) {
                    detectVerticalDragGestures(
                        onDragStart = { dragAccumulatorPx = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            dragAccumulatorPx += dragAmount
                            if (dragAccumulatorPx > dismissThresholdPx) {
                                onSwipeDownDismiss()
                                change.consume()
                            }
                        },
                    )
                },
        ) {
            when (state) {
                InvokeState.Listening -> ListeningContent(
                    languageLabel = languageLabel,
                    onMicTap = onMicTap,
                    onReadScreenAloud = onReadScreenAloud,
                    onDoTaskForMe = onDoTaskForMe,
                )
                is InvokeState.Heard -> HeardContent(
                    state = state,
                    onConfirmPlan = onConfirmPlan,
                    onRetryTranscript = onRetryTranscript,
                )
                is InvokeState.Working -> WorkingContent(state = state, onStop = onStop)
            }
        }
    }
}

// --- State 1: Listening ---

@Composable
private fun ListeningContent(
    languageLabel: String,
    onMicTap: () -> Unit,
    onReadScreenAloud: () -> Unit,
    onDoTaskForMe: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .padding(top = 14.dp, bottom = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(22.dp))

        MicOrb(onClick = onMicTap, contentDescription = stringResource(R.string.state_listening))
        Spacer(Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.invoke_listening_title),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 34.sp, lineHeight = 40.sp),
            color = SaarthiColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.invoke_listening_prompt, languageLabel),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp, lineHeight = 21.sp),
            color = SaarthiColors.Neutral700,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))

        InvokeOutlinedButton(
            text = stringResource(R.string.invoke_read_screen_button),
            borderColor = SaarthiColors.Accent,
            textColor = SaarthiColors.Accent700,
            onClick = onReadScreenAloud,
        )
        Spacer(Modifier.height(10.dp))
        InvokeOutlinedButton(
            text = stringResource(R.string.invoke_do_task_button),
            borderColor = SaarthiColors.Divider,
            textColor = SaarthiColors.Text,
            onClick = onDoTaskForMe,
        )

        Spacer(Modifier.height(20.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = languageLabel,
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 12.5.sp),
                color = SaarthiColors.Neutral600,
            )
            Text(
                text = stringResource(R.string.invoke_hold_home_hint),
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 12.5.sp),
                color = SaarthiColors.Neutral600,
            )
        }
    }
}

/** CSS `ease-out`, cubic-bezier(0, 0, 0.58, 1) — matches the rest of the app's breathing-ring animations. */
private val RingEase = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The 96dp mic circle plus its two outward-animating gold rings. Sized to a
 *168dp canvas (96dp * the rings' 1.75 max scale) so the animation never
 * clips. Tapping it is the sheet's one piece of interactivity in this state —
 * barge-in during the spoken prompt, per the caller's wiring of [onClick].
 */
@Composable
private fun MicOrb(onClick: () -> Unit, contentDescription: String) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val transition = rememberInfiniteTransition(label = "mic_rings")
    val ring1 = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = RingEase)),
        label = "ring1",
    )
    val ring2 = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = RingEase),
            initialStartOffset = StartOffset(1200, StartOffsetType.Delay),
        ),
        label = "ring2",
    )

    Box(
        modifier = Modifier
            .size(168.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(168.dp)) {
            val baseRadius = 48.dp.toPx() // half of the 96dp orb
            if (!reduceMotion) {
                for (progress in listOf(ring1.value, ring2.value)) {
                    val scale = 1f + progress * 0.75f // 1 -> 1.75
                    val alpha = 0.85f * (1f - progress) // .85 -> 0
                    drawCircle(
                        color = SaarthiColors.Accent.copy(alpha = alpha),
                        radius = baseRadius * scale,
                        style = Stroke(1.dp.toPx()),
                    )
                }
            }
            drawCircle(color = SaarthiColors.Accent, radius = baseRadius, style = Stroke(1.5.dp.toPx()))
        }
        Icon(
            imageVector = SaarthiIcons.Mic,
            contentDescription = null,
            tint = SaarthiColors.Accent,
            modifier = Modifier.size(34.dp),
        )
    }
}

// --- State 2: Heard you ---

@Composable
private fun HeardContent(
    state: InvokeState.Heard,
    onConfirmPlan: () -> Unit,
    onRetryTranscript: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 26.dp)
            .padding(top = 14.dp, bottom = 26.dp),
    ) {
        RailHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckBadge(contentDescription = stringResource(R.string.invoke_heard_you))
            Spacer(Modifier.width(10.dp))
            Kicker(
                text = stringResource(R.string.invoke_heard_you),
                fontSize = 13.sp,
                letterSpacing = 0.18.em,
                color = SaarthiColors.Neutral600,
            )
        }
        Spacer(Modifier.height(18.dp))

        Column(modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }) {
            Text(
                text = "“${state.transcript}”",
                style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 32.sp, lineHeight = 38.sp),
                color = SaarthiColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.width(56.dp).height(1.dp).background(SaarthiColors.Accent))
            Spacer(Modifier.height(16.dp))

            Text(
                text = state.plan,
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp, lineHeight = 22.sp),
                color = SaarthiColors.Neutral700,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.invoke_heard_promise),
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic),
                color = SaarthiColors.Accent700,
            )
        }
        Spacer(Modifier.height(24.dp))

        InvokeOutlinedButton(
            text = stringResource(R.string.invoke_confirm_button),
            borderColor = SaarthiColors.Accent,
            textColor = SaarthiColors.Accent700,
            onClick = onConfirmPlan,
        )
        Spacer(Modifier.height(10.dp))
        InvokeOutlinedButton(
            text = stringResource(R.string.invoke_retry_button),
            borderColor = SaarthiColors.Divider,
            textColor = SaarthiColors.Text,
            onClick = onRetryTranscript,
        )
    }
}

@Composable
private fun CheckBadge(contentDescription: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(1.5.dp, SaarthiColors.Accent, CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = SaarthiIcons.Check, contentDescription = null, tint = SaarthiColors.Accent, modifier = Modifier.size(20.dp))
    }
}

// --- State 3: Working ---

@Composable
private fun WorkingContent(state: InvokeState.Working, onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        SegmentedTrack(done = state.step, total = state.of)
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val stepDescription = stringResource(R.string.step_of, state.step, state.of)
            WorkingSpinner(contentDescription = stepDescription)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            ) {
                Kicker(text = stepDescription, fontSize = 12.5.sp, letterSpacing = 0.16.em, color = SaarthiColors.Neutral600)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.label,
                    style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 22.sp, lineHeight = 27.sp),
                    color = SaarthiColors.Text,
                )
            }
            Spacer(Modifier.width(10.dp))
            StopButton(onClick = onStop)
        }
    }
}

@Composable
private fun SegmentedTrack(done: Int, total: Int) {
    val segments = total.coerceAtLeast(1)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(segments) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (index < done) SaarthiColors.Accent else SaarthiColors.Divider),
            )
        }
    }
}

@Composable
private fun WorkingSpinner(contentDescription: String) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val transition = rememberInfiniteTransition(label = "working_spinner")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(
        modifier = Modifier
            .size(44.dp)
            .semantics { this.contentDescription = contentDescription },
    ) {
        val strokeWidth = 1.5.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        drawCircle(color = SaarthiColors.Divider, radius = radius, style = Stroke(strokeWidth))
        val start = if (reduceMotion) -90f else -90f + angle.value
        drawArc(
            color = SaarthiColors.Accent,
            startAngle = start,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(this.size.width / 2 - radius, this.size.height / 2 - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    val label = stringResource(R.string.stop_label)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(SaarthiDimens.RadiusMd))
            .border(1.dp, SaarthiColors.Accent, RoundedCornerShape(SaarthiDimens.RadiusMd))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = label }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp), color = SaarthiColors.Accent700)
    }
}

// --- Shared pieces ---

/**
 * The 74x12dp rail handle: the app mark's horizontal rail-with-end-posts
 * silhouette, reused as the sheet's grab handle. Purely decorative (no
 * semantics) — dismissal is a real gesture/back/Stop elsewhere, this is
 * just the visual affordance a sighted user expects atop a bottom sheet.
 */
@Composable
private fun RailHandle(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 74.dp, height = 12.dp)) {
        val strokeWidth = 2.dp.toPx()
        val color = SaarthiColors.Neutral400
        val midY = size.height / 2
        drawLine(
            color = color,
            start = Offset(strokeWidth / 2, midY),
            end = Offset(size.width - strokeWidth / 2, midY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        val postHalfHeight = size.height / 2 - strokeWidth / 2
        drawLine(
            color = color,
            start = Offset(strokeWidth / 2, midY - postHalfHeight),
            end = Offset(strokeWidth / 2, midY + postHalfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - strokeWidth / 2, midY - postHalfHeight),
            end = Offset(size.width - strokeWidth / 2, midY + postHalfHeight),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * The gold-outline / hairline-outline button shared by all three button
 * pairs in this sheet. `heightIn(min = 60dp)` rather than a fixed height —
 * per the accessibility rules, the sheet grows at large font scales, it
 * never clips.
 */
@Composable
private fun InvokeOutlinedButton(
    text: String,
    borderColor: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(SaarthiDimens.RadiusMd)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(if (pressed) SaarthiColors.Accent100 else Color.Transparent)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick, role = Role.Button)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp, lineHeight = 22.sp),
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}

// --- Previews: one per state, over a light host and a dark host, plus 200% font scale. ---
// The SHEET itself never adopts a dark theme (see SaarthiTheme's own doc: it
// is a single, deliberately-lit system) — "light/dark host" here means the
// simulated app running underneath, which is genuinely visible through the
// non-dimming window in real use. These previews exist to check the sheet
// still reads clearly regardless of what's behind it.

private val previewHeard = InvokeState.Heard(
    transcript = "Book a cab home",
    plan = "I'll open Ola, set your destination to Home, and choose the fastest ride.",
)
private val previewWorking = InvokeState.Working(step = 2, of = 4, label = "Opening Ola")

@Composable
private fun PreviewHost(dark: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF17151A) else Color(0xFFDAD7D2)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        content()
    }
}

@Composable
private fun PreviewSheet(state: InvokeState) {
    InvokeSheet(
        state = state,
        languageLabel = "Hindi",
        onMicTap = {},
        onReadScreenAloud = {},
        onDoTaskForMe = {},
        onConfirmPlan = {},
        onRetryTranscript = {},
        onStop = {},
    )
}

@Preview(name = "1 Listening — light host", widthDp = 380, heightDp = 640)
@Composable
private fun PreviewListeningLight() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(InvokeState.Listening) } }
}

@Preview(name = "1 Listening — dark host", widthDp = 380, heightDp = 640)
@Composable
private fun PreviewListeningDark() {
    SaarthiTheme { PreviewHost(dark = true) { PreviewSheet(InvokeState.Listening) } }
}

@Preview(name = "1 Listening — 200% font", widthDp = 380, heightDp = 780, fontScale = 2.0f)
@Composable
private fun PreviewListeningLargeFont() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(InvokeState.Listening) } }
}

@Preview(name = "2 Heard you — light host", widthDp = 380, heightDp = 640)
@Composable
private fun PreviewHeardLight() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(previewHeard) } }
}

@Preview(name = "2 Heard you — dark host", widthDp = 380, heightDp = 640)
@Composable
private fun PreviewHeardDark() {
    SaarthiTheme { PreviewHost(dark = true) { PreviewSheet(previewHeard) } }
}

@Preview(name = "2 Heard you — 200% font", widthDp = 380, heightDp = 900, fontScale = 2.0f)
@Composable
private fun PreviewHeardLargeFont() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(previewHeard) } }
}

@Preview(name = "3 Working — light host", widthDp = 380, heightDp = 300)
@Composable
private fun PreviewWorkingLight() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(previewWorking) } }
}

@Preview(name = "3 Working — dark host", widthDp = 380, heightDp = 300)
@Composable
private fun PreviewWorkingDark() {
    SaarthiTheme { PreviewHost(dark = true) { PreviewSheet(previewWorking) } }
}

@Preview(name = "3 Working — 200% font", widthDp = 380, heightDp = 340, fontScale = 2.0f)
@Composable
private fun PreviewWorkingLargeFont() {
    SaarthiTheme { PreviewHost(dark = false) { PreviewSheet(previewWorking) } }
}
