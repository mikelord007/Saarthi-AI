package com.saarthi.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.minDimension
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/** The five perceivable states of the voice/assist surface, shared by the assist overlay and (later) the home mic control. */
enum class VoiceState { Idle, Listening, Thinking, Speaking, Acting }

@Composable
private fun stateLabel(state: VoiceState): String = when (state) {
    VoiceState.Idle -> stringResource(R.string.state_ready)
    VoiceState.Listening -> stringResource(R.string.state_listening)
    VoiceState.Thinking -> stringResource(R.string.state_thinking)
    VoiceState.Speaking -> stringResource(R.string.state_speaking)
    VoiceState.Acting -> stringResource(R.string.state_acting)
}

/** CSS `ease-out`, i.e. cubic-bezier(0, 0, 0.58, 1) — used by the ring and breathe animations throughout the app. */
private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The mark + its word, together — a symbol alone isn't accessible to this
 * audience, so there is no variant that renders only the animated mark.
 * [onDark] selects the overlay/promise palette (gold at accent-300, idle
 * ring at 40% white) vs. the light-ground palette.
 */
@Composable
fun VoiceStateIndicator(
    state: VoiceState,
    modifier: Modifier = Modifier,
    markSize: Dp = 44.dp,
    onDark: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VoiceStateMark(state = state, size = markSize, onDark = onDark)
        Text(
            text = stateLabel(state).uppercase(),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 14.sp, letterSpacing = 0.2.em),
            color = if (onDark) SaarthiColors.Accent300 else SaarthiColors.Accent700,
        )
    }
}

@Composable
private fun VoiceStateMark(state: VoiceState, size: Dp, onDark: Boolean) {
    val context = LocalContext.current
    // Read once: the accessible channel is the word above, which never
    // depends on this flag, so a live ContentObserver isn't worth the cost.
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    val ringColor = if (onDark) SaarthiColors.Accent300 else SaarthiColors.Accent
    val idleColor = if (onDark) SaarthiColors.OnDarkInk40 else SaarthiColors.Neutral400
    val barColor = if (onDark) SaarthiColors.Accent300 else SaarthiColors.Accent700
    val thinkingBaseColor = if (onDark) SaarthiColors.OnDarkRuleStrong else SaarthiColors.Neutral300

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        when (state) {
            VoiceState.Idle -> IdleMark(size, idleColor)
            VoiceState.Listening -> ListeningMark(size, ringColor, reduceMotion)
            VoiceState.Thinking -> ThinkingMark(size, thinkingBaseColor, ringColor, reduceMotion)
            VoiceState.Speaking -> SpeakingMark(size, ringColor, barColor, reduceMotion)
            VoiceState.Acting -> ActingMark(size, ringColor, reduceMotion)
        }
    }
}

@Composable
private fun IdleMark(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        drawCircle(color = color, radius = (this.size.minDimension - strokeWidth) / 2, style = Stroke(strokeWidth))
    }
}

@Composable
private fun ListeningMark(size: Dp, ringColor: Color, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "listening")
    val progress1 = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = EaseOut)),
        label = "ring1",
    )
    val progress2 = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = EaseOut),
            initialStartOffset = StartOffset(1100, StartOffsetType.Delay),
        ),
        label = "ring2",
    )
    Canvas(Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        val baseRadius = (this.size.minDimension - strokeWidth) / 2
        // Static base ring
        drawCircle(color = ringColor, radius = baseRadius, style = Stroke(strokeWidth))
        if (reduceMotion) return@Canvas
        for (p in listOf(progress1.value, progress2.value)) {
            val scale = lerp(0.62f, 1.5f, p)
            val alpha = lerp(0.55f, 0f, p)
            drawCircle(
                color = ringColor.copy(alpha = alpha),
                radius = baseRadius * scale,
                style = Stroke(strokeWidth),
            )
        }
    }
}

@Composable
private fun ThinkingMark(size: Dp, baseColor: Color, accentColor: Color, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "thinking")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2
        drawCircle(color = baseColor, radius = radius, style = Stroke(strokeWidth))
        val sweep = 90f
        val start = if (reduceMotion) -90f else -90f + angle.value
        drawArc(
            color = accentColor,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(this.size.width / 2 - radius, this.size.height / 2 - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun SpeakingMark(size: Dp, ringColor: Color, barColor: Color, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "speaking")
    val delays = listOf(0, 180, 360, 540)
    // Each bar pulses 0.24 -> 1 -> 0.24 over 1s (ease-in-out), staggered by
    // its delay — a keyframe spec expresses the up-down shape directly.
    val barScales = delays.map { delayMs ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1000
                    0.24f at 0
                    1f at 500
                    0.24f at 1000
                },
                initialStartOffset = StartOffset(delayMs, StartOffsetType.Delay),
            ),
            label = "bar_scale",
        )
    }
    Canvas(Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        drawCircle(color = ringColor, radius = (this.size.minDimension - strokeWidth) / 2, style = Stroke(strokeWidth))

        val barWidth = 2.dp.toPx()
        val barHeight = 18.dp.toPx()
        val gap = 3.5.dp.toPx()
        val totalWidth = barWidth * 4 + gap * 3
        var x = this.size.width / 2 - totalWidth / 2
        val centerY = this.size.height / 2
        barScales.forEach { scale ->
            val h = barHeight * (if (reduceMotion) 0.62f else scale.value.coerceIn(0.24f, 1f))
            drawLine(
                color = barColor,
                start = Offset(x + barWidth / 2, centerY - h / 2),
                end = Offset(x + barWidth / 2, centerY + h / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
            x += barWidth + gap
        }
    }
}

@Composable
private fun ActingMark(size: Dp, color: Color, reduceMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "acting")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "spin",
    )
    Canvas(Modifier.size(size)) {
        val strokeWidth = 1.dp.toPx()
        rotate(if (reduceMotion) 0f else angle.value) {
            drawCircle(
                color = color,
                radius = (this.size.minDimension - strokeWidth) / 2,
                style = Stroke(
                    width = strokeWidth,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
        drawCircle(color = color, radius = 4.dp.toPx())
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
