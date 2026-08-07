package com.saarthi.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saarthi.R
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors

/** The gold-outlined arrow button next to a [SaarthiTextField] — shared by Home and Thread Detail's message rows. */
@Composable
fun SendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, SaarthiColors.Accent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = SaarthiIcons.ArrowRight,
            contentDescription = stringResource(R.string.cd_send),
            tint = SaarthiColors.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}
