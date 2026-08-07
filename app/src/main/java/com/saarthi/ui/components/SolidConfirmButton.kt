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
 * The hand-back confirm button — "Yes — I'll do it myself" in the design.
 * This is THE SINGLE SOLID-GOLD FILL IN THE ENTIRE APP; every other button
 * is a gold outline on transparent. That contrast is the safety affordance:
 * a user learns "gold filled = my finger, not an automated one."
 *
 * Tapping THIS button must never itself complete the guarded action —
 * callers wire [onClick] to dismiss the overlay and hand the real screen
 * back, not to perform the tap that was refused.
 */
@Composable
fun HandbackConfirmButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (pressed) SaarthiColors.Accent800 else SaarthiColors.Accent700
    val shape = RoundedCornerShape(SaarthiDimens.RadiusMd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(shape)
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = TextStyle(
                fontFamily = SaarthiType.Cormorant,
                fontWeight = FontWeight.W400,
                fontSize = 20.sp,
                letterSpacing = 0.08.em,
            ),
            color = SaarthiColors.Accent100,
        )
    }
}

/** The hand-back screen's "No, stop" — cancels, same as the overlay's Stop/×. */
@Composable
fun HandbackSecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (pressed) SaarthiColors.Accent200 else Color.Transparent
    val shape = RoundedCornerShape(SaarthiDimens.RadiusMd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, SaarthiColors.Accent300, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp),
            color = SaarthiColors.Accent800,
        )
    }
}
