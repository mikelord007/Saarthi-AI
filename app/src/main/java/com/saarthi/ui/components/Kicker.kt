package com.saarthi.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/**
 * The small uppercase, wide-tracked Cormorant label used everywhere in this
 * design for section headers and step counters — "STEP 1 OF 7", "SAARTHI",
 * "EARLIER TODAY", "MY PROMISE". Compose has no CSS text-transform, so this
 * uppercases [text] itself; callers pass normal-case copy.
 */
@Composable
fun Kicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SaarthiColors.Neutral600,
    fontSize: TextUnit = 15.sp,
    letterSpacing: TextUnit = 0.15.em,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = SaarthiType.Cormorant,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
        ),
        color = color,
    )
}
