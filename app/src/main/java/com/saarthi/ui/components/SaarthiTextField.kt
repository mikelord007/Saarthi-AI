package com.saarthi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiDimens
import com.saarthi.ui.theme.SaarthiType

/**
 * The plain outlined text field used for "or type it instead" on Home — a
 * hairline border that turns gold on focus, gold caret, no Material fill
 * or floating label.
 */
@Composable
fun SaarthiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: Dp = 48.dp,
    enabled: Boolean = true,
    onSend: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) SaarthiColors.Accent else SaarthiColors.Divider

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp, color = SaarthiColors.Text),
        cursorBrush = SolidColor(SaarthiColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = if (onSend != null) ImeAction.Send else ImeAction.Default),
        keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .onFocusChanged { focused = it.isFocused }
            .background(Color.Transparent, RoundedCornerShape(SaarthiDimens.RadiusMd))
            .border(1.dp, borderColor, RoundedCornerShape(SaarthiDimens.RadiusMd))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp),
                        color = SaarthiColors.Neutral600,
                    )
                }
                innerTextField()
            }
        },
    )
}
