package com.saarthi.ui.screens.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.ui.components.CtaButton
import com.saarthi.ui.components.GhostButton
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.StepHeader
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/** Step 3 of 6 — the first of two required permissions, stated in plain words before Android asks. */
@Composable
fun MicScreen(onAllow: () -> Unit, onNotNow: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 3, onBack = onBack, dividerBottomMargin = 34.dp)

        Box(
            modifier = Modifier
                .size(74.dp)
                .border(1.dp, SaarthiColors.Accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = SaarthiIcons.Mic, contentDescription = null, tint = SaarthiColors.Accent700, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(26.dp))
        Text(
            text = stringResource(R.string.mic_screen_title),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
            color = SaarthiColors.Text,
        )
        Text(
            text = stringResource(R.string.mic_screen_body),
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.5.sp, lineHeight = 28.sp),
            color = SaarthiColors.Neutral800,
        )

        Hairline()
        val notePrefix = stringResource(R.string.mic_screen_note_prefix)
        val noteHighlight = stringResource(R.string.mic_screen_note_highlight)
        Text(
            text = buildAnnotatedString {
                append(notePrefix)
                withStyle(SpanStyle(fontStyle = FontStyle.Normal, color = SaarthiColors.Text)) {
                    append(noteHighlight)
                }
            },
            modifier = Modifier.padding(vertical = 16.dp),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic),
            color = SaarthiColors.Neutral700,
        )
        Hairline()

        Spacer(Modifier.weight(1f))
        CtaButton(text = stringResource(R.string.allow_microphone), onClick = onAllow)
        GhostButton(text = stringResource(R.string.not_now), onClick = onNotNow)
    }
}
