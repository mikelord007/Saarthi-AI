package com.saarthi.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.saarthi.R
import com.saarthi.speech.Language
import com.saarthi.ui.Permissions
import com.saarthi.ui.components.Hairline
import com.saarthi.ui.components.SaarthiSwitch
import com.saarthi.ui.components.Kicker
import com.saarthi.ui.components.LanguageTile
import com.saarthi.ui.components.Pill
import com.saarthi.ui.icons.SaarthiIcons
import com.saarthi.ui.theme.SaarthiColors
import com.saarthi.ui.theme.SaarthiType

/**
 * The other half of the "quiet half": the language choice (speaker isn't
 * here — see Home/Voice onboarding), the three permissions re-checked live,
 * and the three behaviour toggles.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    languages: List<Language>,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    permissions: Permissions,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenMicrophoneSettings: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    narrateEveryStep: Boolean,
    onNarrateToggle: (Boolean) -> Unit,
    speakSlowly: Boolean,
    onSpeakSlowlyToggle: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(32.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                Icon(imageVector = SaarthiIcons.ArrowLeft, contentDescription = stringResource(R.string.back), tint = SaarthiColors.Neutral700, modifier = Modifier.size(20.dp))
            }
            Kicker(text = stringResource(R.string.settings_title), fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 26.dp),
        ) {
            Kicker(text = stringResource(R.string.language_section), fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 10.dp))
            LanguageGrid(languages, selectedLanguage, onLanguageSelected)
            Text(
                text = stringResource(R.string.settings_language_note, selectedLanguage.nativeName),
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp),
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 13.sp, fontStyle = FontStyle.Italic),
                color = SaarthiColors.Neutral600,
            )

            Kicker(text = stringResource(R.string.permissions_section), fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 6.dp))
            Hairline()
            PermissionRow(label = stringResource(R.string.permission_accessibility), on = permissions.accessibility, onClick = onOpenAccessibilitySettings)
            PermissionRow(label = stringResource(R.string.permission_microphone), on = permissions.microphone, onClick = onOpenMicrophoneSettings)
            PermissionRow(label = stringResource(R.string.permission_default_assistant), on = permissions.defaultAssistant, onClick = onOpenAssistantSettings)
            Spacer(Modifier.height(20.dp))

            Kicker(text = stringResource(R.string.behavior_section), fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 6.dp))
            Hairline()
            ToggleRow(
                title = stringResource(R.string.toggle_narrate_title),
                description = stringResource(R.string.toggle_narrate_desc),
                checked = narrateEveryStep,
                onCheckedChange = onNarrateToggle,
            )
            ToggleRow(
                title = stringResource(R.string.toggle_handback_title),
                description = stringResource(R.string.toggle_handback_desc),
                checked = true,
                onCheckedChange = null,
            )
            ToggleRow(
                title = stringResource(R.string.toggle_speak_slowly_title),
                description = stringResource(R.string.toggle_speak_slowly_desc),
                checked = speakSlowly,
                onCheckedChange = onSpeakSlowlyToggle,
            )
        }
    }
}

@Composable
private fun LanguageGrid(languages: List<Language>, selected: Language, onSelected: (Language) -> Unit) {
    val rows = languages.chunked(2)
    Column {
        rows.forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { language ->
                    LanguageTile(
                        language = language,
                        selected = language == selected,
                        onClick = { onSelected(language) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp), color = SaarthiColors.Text)
        Pill(text = stringResource(if (on) R.string.pill_on else R.string.pill_off), on = on)
    }
    Hairline()
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val rowModifier = if (onCheckedChange != null) {
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 16.sp), color = SaarthiColors.Text)
            Text(
                text = description,
                style = TextStyle(fontFamily = SaarthiType.Lora, fontSize = 13.5.sp, fontStyle = FontStyle.Italic),
                color = SaarthiColors.Neutral600,
            )
        }
        Box(modifier = if (onCheckedChange == null) Modifier.alpha(0.45f) else Modifier) {
            SaarthiSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
    Hairline()
}
