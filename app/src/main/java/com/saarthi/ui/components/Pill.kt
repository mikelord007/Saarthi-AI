package com.saarthi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/** The ON/OFF permission-status pill used in Settings. */
@Composable
fun Pill(text: String, on: Boolean, modifier: Modifier = Modifier) {
    val ink = if (on) SaarthiColors.Accent700 else SaarthiColors.Neutral600
    val border = if (on) SaarthiColors.Accent else SaarthiColors.Neutral400
    Text(
        text = text.uppercase(),
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(100))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(
            fontFamily = SaarthiType.Cormorant,
            fontSize = 13.sp,
            letterSpacing = 0.14.em,
        ),
        color = ink,
    )
}
