package com.saarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiDimens
import com.saarthi.ui.theme.SaarthiType

/**
 * The primary call-to-action across onboarding: 56dp tall, transparent with
 * a 1dp gold outline, filling with a faint gold tint on press. Every screen
 * from welcome through assistant uses exactly this button — per the design's
 * "gold is stroke-only" rule, this button never has a solid gold fill (the
 * one exception in the whole app is [com.saarthi.ui.components.HandbackConfirmButton]).
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = SaarthiColors.Accent,
    textColor: Color = SaarthiColors.Accent700,
    pressedTint: Color = SaarthiColors.Accent100,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(SaarthiDimens.RadiusMd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (pressed) pressedTint else Color.Transparent)
            .border(1.dp, accentColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(
                fontFamily = SaarthiType.Cormorant,
                fontWeight = FontWeight.W400,
                fontSize = 19.sp,
                letterSpacing = 0.08.em,
            ),
            color = textColor,
        )
    }
}
