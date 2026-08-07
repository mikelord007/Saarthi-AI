package com.saarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.speech.Language
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/** A single row in the 11-language picker list — shared by onboarding and Settings so the two can't drift apart. */
@Composable
fun LanguageRow(language: Language, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring = if (selected) SaarthiColors.Accent else SaarthiColors.Divider
    val ink = if (selected) SaarthiColors.Accent700 else SaarthiColors.Text

    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = language.nativeName,
                    style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 23.sp),
                    color = ink,
                )
                Text(
                    text = language.latinName,
                    style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 13.5.sp),
                    color = SaarthiColors.Neutral600,
                )
            }
            RadioRing(selected = selected, ringColor = ring)
        }
        Hairline()
    }
}

@Composable
private fun RadioRing(selected: Boolean, ringColor: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .border(1.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(SaarthiColors.Accent, CircleShape),
            )
        }
    }
}
