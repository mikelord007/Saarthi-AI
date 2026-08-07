package com.saarthi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.speech.Language
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiDimens
import com.saarthi.ui.theme.SaarthiType

/** One cell of the Settings 2-column language grid — same data as [LanguageRow], different (compact, bordered) presentation. */
@Composable
fun LanguageTile(language: Language, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring = if (selected) SaarthiColors.Accent else SaarthiColors.Divider
    val ink = if (selected) SaarthiColors.Accent700 else SaarthiColors.Text

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, ring, RoundedCornerShape(SaarthiDimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = language.nativeName,
            style = TextStyle(fontFamily = SaarthiType.Cormorant, fontSize = 19.sp, lineHeight = 21.sp),
            color = ink,
        )
        Text(
            text = language.latinName,
            style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 11.5.sp),
            color = SaarthiColors.Neutral600,
        )
    }
}
