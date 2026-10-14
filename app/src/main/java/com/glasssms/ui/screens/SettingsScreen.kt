package com.glasssms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.data.Settings
import com.glasssms.ui.glass.GlassIconButton
import com.glasssms.ui.glass.LiquidSegmentedControl
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val DELETE_DELAYS = listOf(1, 5, 15, 60)

private val AUTO_DELETE = listOf(
    0 to "Off",
    Settings.AUTO_DELETE_AT_EXPIRY to "Expired",
    60 to "1 hr",
    24 * 60 to "24 hr",
)

@Composable
fun SettingsScreen(vm: AppViewModel, isDefaultApp: Boolean, onRequestDefault: () -> Unit) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val s by vm.settings.collectAsStateWithLifecycle()
    val insets = WindowInsets.systemBars.asPaddingValues()

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 56.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        ) {
            item { SignTitle("Settings") }
            item {
                GroupedSection(
                    footer = if (isDefaultApp) null else "Android only lets the default SMS app receive, send and delete messages.",
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(enabled = !isDefaultApp, onClick = onRequestDefault)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Default SMS App", style = IosType.body, color = p.label, modifier = Modifier.weight(1f))
                        Text(
                            if (isDefaultApp) "On" else "Set as Default",
                            style = IosType.body,
                            color = if (isDefaultApp) p.secondaryLabel else p.accent,
                        )
                    }
                }
            }
            item {
                GroupedSection(
                    header = "Verification Codes",
                    footer = "Codes are found on this phone by matching the message text. Nothing is sent anywhere.",
                ) {
                    ToggleRow("Detect Codes", s.detectCodes) { v -> vm.updateSettings { it.copy(detectCodes = v) } }
                    Divider()
                    ToggleRow("Code Notifications", s.codeNotifications, enabled = s.detectCodes) { v ->
                        vm.updateSettings { it.copy(codeNotifications = v) }
                    }
                    Divider()
                    ToggleRow("Copy Codes Automatically", s.autoCopy, enabled = s.detectCodes) { v ->
                        vm.updateSettings { it.copy(autoCopy = v) }
                    }
                    Divider()
                    ToggleRow("Delete After Copying", s.deleteAfterCopy, enabled = s.detectCodes) { v ->
                        vm.updateSettings { it.copy(deleteAfterCopy = v) }
                    }
                }
            }
            if (s.deleteAfterCopy) {
                item {
                    GroupedSection(header = "Delete After", footer = "Copied code messages are removed after this delay.") {
                        LiquidSegmentedControl(
                            options = DELETE_DELAYS.map { if (it < 60) "$it min" else "1 hr" },
                            selected = DELETE_DELAYS.indexOf(s.deleteDelayMinutes).coerceAtLeast(0),
                            onSelect = { i -> vm.updateSettings { it.copy(deleteDelayMinutes = DELETE_DELAYS[i]) } },
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                        )
                    }
                }
            }
            item {
                GroupedSection(
                    header = "Auto-Delete Codes",
                    footer = "Removes code messages automatically, copied or not. Applies to codes that arrive " +
                        "from now on; clear older ones from the Codes screen.",
                ) {
                    LiquidSegmentedControl(
                        options = AUTO_DELETE.map { it.second },
                        selected = AUTO_DELETE.indexOfFirst { it.first == s.autoDeleteCodesMinutes }.coerceAtLeast(0),
                        onSelect = { i -> vm.updateSettings { it.copy(autoDeleteCodesMinutes = AUTO_DELETE[i].first) } },
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                    )
                }
            }
            item {
                GroupedSection(
                    header = "Conversations",
                    footer = if (s.confirmDelete) "You'll be asked before a conversation is deleted."
                    else "Swipe left all the way, or tap Delete, to delete a conversation immediately.",
                ) {
                    ToggleRow("Confirm Before Deleting", s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 64.dp).edgeBlur(haze))
        GlassIconButton(
            Icons.AutoMirrored.Rounded.ArrowBackIos,
            "Back",
            haze,
            onClick = { vm.pop() },
            modifier = Modifier.padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp),
        )
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosType.body, color = if (enabled) p.label else p.tertiaryLabel, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = p.green,
                checkedThumbColor = Color.White,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = p.fill,
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = LocalPalette.current.separator)
}
