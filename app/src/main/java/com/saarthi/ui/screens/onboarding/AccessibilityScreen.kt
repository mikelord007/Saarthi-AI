package com.saarthi.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.ui.components.CtaButton
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.StepHeader
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/** Step 4 of 6 — the second required permission. Two things happen when this is granted: reading labels, and typing/tapping on the user's behalf. */
@Composable
fun AccessibilityScreen(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 4, onBack = onBack)

        Text(
            text = stringResource(R.string.a11y_screen_title),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
            color = SaarthiColors.Text,
        )
        Text(
            text = stringResource(R.string.a11y_screen_body),
            modifier = Modifier.padding(top = 12.dp, bottom = 22.dp),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.5.sp, lineHeight = 28.sp),
            color = SaarthiColors.Neutral800,
        )

        Hairline()
        TableRow(text = stringResource(R.string.a11y_row_1))
        TableRow(text = stringResource(R.string.a11y_row_2))
        TableRow(text = stringResource(R.string.a11y_row_3), muted = true)

        Spacer(Modifier.weight(1f))
        CtaButton(text = stringResource(R.string.open_android_settings), onClick = onOpenSettings)
        Text(
            text = stringResource(R.string.a11y_settings_path),
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            style = TextStyle(
                fontFamily = SaarthiType.Lora,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            ),
            color = SaarthiColors.Neutral600,
        )
    }
}

@Composable
private fun TableRow(text: String, muted: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "—", color = SaarthiColors.Accent700, style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp))
        Text(
            text = text,
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
            color = if (muted) SaarthiColors.Neutral700 else SaarthiColors.Text,
        )
    }
    Hairline()
}
