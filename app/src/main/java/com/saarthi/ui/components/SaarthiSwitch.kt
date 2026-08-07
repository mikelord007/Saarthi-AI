package com.saarthi.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saarthi.ui.theme.SaarthiColors

/**
 * The Settings "How I behave" toggle track — a hand-drawn switch (44x26dp,
 * 18dp knob sliding 3dp -> 21dp over 180ms) rather than Material's Switch,
 * to match the design's outlined, non-Material component language.
 *
 * [onCheckedChange] is nullable so a permanently-on row can render this at
 * reduced opacity and non-interactive.
 */
@Composable
fun SaarthiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val ring = if (checked) SaarthiColors.Accent else SaarthiColors.Neutral400
    val knobOffset by animateDpAsState(targetValue = if (checked) 21.dp else 3.dp, animationSpec = tween(180), label = "saarthi_switch_knob")

    val base = modifier
        .size(width = 44.dp, height = 26.dp)
        .border(1.dp, ring, RoundedCornerShape(100))
    Box(
        modifier = if (onCheckedChange != null) {
            base.clickable { onCheckedChange(!checked) }
        } else {
            base
        },
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(18.dp)
                .background(ring, CircleShape),
        )
    }
}
