package com.saarthi.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.speech.Speaker
import com.saarthi.ui.components.CtaButton
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.components.StepHeader
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiDimens
import com.saarthi.ui.theme.SaarthiType

/** Step 2 of 6 — a voice choice. Sample playback wires up alongside the rest of the voice pipeline later. */
@Composable
fun VoiceScreen(
    speakers: List<Speaker>,
    selected: Speaker,
    onSelected: (Speaker) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 2, onBack = onBack)

        Text(
            text = stringResource(R.string.voice_screen_title),
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
            color = SaarthiColors.Text,
        )
        Text(
            text = stringResource(R.string.voice_screen_body),
            modifier = Modifier.padding(top = 10.dp, bottom = 24.dp),
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp, lineHeight = 27.sp),
            color = SaarthiColors.Neutral700,
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            speakers.forEach { speaker ->
                SpeakerRow(speaker = speaker, selected = speaker == selected, onClick = { onSelected(speaker) })
            }
        }

        Hairline(modifier = Modifier.padding(top = 22.dp))
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Kicker(
                    text = selected.displayName,
                    fontSize = 12.sp,
                    letterSpacing = 0.16.em,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Text(
                    text = "“${stringResource(R.string.onboarding_voice_sample_line)}”",
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 15.5.sp, lineHeight = 25.sp, fontStyle = FontStyle.Italic),
                    color = SaarthiColors.Neutral800,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        CtaButton(text = stringResource(R.string.use_this_voice), onClick = onContinue)
    }
}

@Composable
private fun SpeakerRow(speaker: Speaker, selected: Boolean, onClick: () -> Unit) {
    val ring = if (selected) SaarthiColors.Accent else SaarthiColors.Divider
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ring, RoundedCornerShape(SaarthiDimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(46.dp).border(1.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                speaker.waveformBars.forEach { barHeight ->
                    Box(
                        modifier = Modifier
                            .size(width = 2.dp, height = barHeight.dp)
                            .background(SaarthiColors.Accent700),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = speaker.displayName,
                style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 23.sp),
                color = SaarthiColors.Text,
            )
            Text(
                text = speaker.description,
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 14.sp, fontStyle = FontStyle.Italic),
                color = SaarthiColors.Neutral600,
            )
        }
        if (selected) {
            Text(text = "●", fontSize = 15.sp, color = SaarthiColors.Accent700)
        }
    }
}
