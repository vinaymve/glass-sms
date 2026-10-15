package com.glasssms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.glasssms.Screen
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
import androidx.compose.foundation.layout.width
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
import com.glasssms.data.SwipeAction
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
    var cleaning by remember { mutableStateOf(false) }

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
                    header = "Swipe Actions",
                    footer = "Swipe a conversation past a third of the way and let go to run the action.",
                ) {
                    SwipeSetting("Swipe Right", s.swipeRight) { a -> vm.updateSettings { it.copy(swipeRight = a) } }
                    Divider()
                    SwipeSetting("Swipe Left", s.swipeLeft) { a -> vm.updateSettings { it.copy(swipeLeft = a) } }
                    Divider()
                    ToggleRow("Confirm Before Deleting", s.confirmDelete) { v -> vm.updateSettings { it.copy(confirmDelete = v) } }
                    Divider()
                    ToggleRow("Explosion on Delete", s.deleteExplosion) { v -> vm.updateSettings { it.copy(deleteExplosion = v) } }
                }
            }
            item {
                GroupedSection(
                    header = "Notifications",
                    footer = "Offers are promotional texts (TRAI \u201c-P\u201d senders and similar). They still arrive, just silently. " +
                        "Mute any conversation from its long-press menu.",
                ) {
                    ToggleRow("Silence Offers", s.silenceOffers) { v -> vm.updateSettings { it.copy(silenceOffers = v) } }
                }
            }
            item {
                GroupedSection(
                    header = "Spam & Blocking",
                    footer = "Blocked messages skip your inbox and notifications and wait in Spam, where you can restore them.",
                ) {
                    ToggleRow("Block All Promotional (-P)", s.blockPromotional) { v -> vm.updateSettings { it.copy(blockPromotional = v) } }
                    Divider()
                    NavRow("Blocked Senders & Words", "${s.blockedSenders.size + s.blockedKeywords.size}") { vm.push(Screen.Blocked) }
                    Divider()
                    NavRow("Spam") { vm.push(Screen.Spam) }
                }
            }
            item {
                GroupedSection(header = "Storage") {
                    NavRow("Clean Up Inbox") { cleaning = true }
                    Divider()
                    NavRow("Recently Deleted") { vm.push(Screen.Trash) }
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

    if (cleaning) CleanerDialog(vm) { cleaning = false }
}

private val SWIPE_OPTIONS = listOf(
    SwipeAction.None to "None",
    SwipeAction.ToggleRead to "Read",
    SwipeAction.Archive to "Archive",
    SwipeAction.Delete to "Delete",
)

@Composable
private fun SwipeSetting(title: String, value: SwipeAction, onChange: (SwipeAction) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Text(title, style = IosType.body, color = p.label)
        Spacer(Modifier.height(8.dp))
        LiquidSegmentedControl(
            options = SWIPE_OPTIONS.map { it.second },
            selected = SWIPE_OPTIONS.indexOfFirst { it.first == value }.coerceAtLeast(0),
            onSelect = { i -> onChange(SWIPE_OPTIONS[i].first) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NavRow(title: String, detail: String? = null, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosType.body, color = p.label, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = IosType.body, color = p.secondaryLabel)
        Icon(Icons.Rounded.ChevronRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(20.dp))
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
