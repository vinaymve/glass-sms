package com.glasssms.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.data.Message
import com.glasssms.data.StoredMessage
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.GlassIconButton
import com.glasssms.ui.glass.LiquidSegmentedControl
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Grey grouped page with a sign title, a glass back button and an optional glass action on the right. */
@Composable
private fun LibraryPage(
    title: String,
    onBack: () -> Unit,
    action: (@Composable (HazeState) -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val insets = WindowInsets.systemBars.asPaddingValues()
    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 56.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        ) {
            item { SignTitle(title) }
            content()
        }
        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 64.dp).edgeBlur(haze))
        Box(Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp, end = 16.dp)) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", haze, onClick = onBack, modifier = Modifier.align(Alignment.CenterStart))
            if (action != null) Box(Modifier.align(Alignment.CenterEnd)) { action(haze) }
        }
    }
}

/** One card per sender: avatar, name, count, preview and two text buttons. */
@Composable
private fun SenderCard(
    vm: AppViewModel,
    items: List<StoredMessage>,
    caption: String,
    primary: String,
    onPrimary: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val first = items.first()
    val name = remember(first.address) { vm.displayName(first.address) }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.secondaryGroupedBackground)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = IosType.headline, color = p.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(caption, style = IosType.footnote, color = p.secondaryLabel, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(first.body, style = IosType.subhead, color = p.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(primary, style = IosType.body, fontWeight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.clickable(onClick = onPrimary))
            Text("Delete", style = IosType.body, color = p.red, modifier = Modifier.clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = IosType.subhead,
        color = LocalPalette.current.secondaryLabel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = IosType.headline) },
        text = { Text(text, style = IosType.subhead) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = p.red, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = p.accent) } },
    )
}

private fun ago(millis: Long): String {
    val d = System.currentTimeMillis() - millis
    return when {
        d < TimeUnit.HOURS.toMillis(1) -> "just now"
        d < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(d)}h ago"
        else -> "${TimeUnit.MILLISECONDS.toDays(d)}d ago"
    }
}

private fun List<StoredMessage>.bySender() =
    groupBy { it.address }.values.sortedByDescending { group -> group.maxOf { it.storedAt } }

@Composable
fun TrashScreen(vm: AppViewModel) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<StoredMessage>?>(null) }
    val p = LocalPalette.current
    LibraryPage(
        "Recently Deleted",
        onBack = { vm.pop() },
        action = if (trash.isEmpty()) null else { haze ->
            GlassCapsule(haze, onClick = { confirmEmpty = true }) { Text("Empty", style = IosType.body, color = p.red) }
        },
    ) {
        item {
            Text(
                "Deleted messages stay here for 30 days, then they're removed for good.",
                style = IosType.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (trash.isEmpty()) item { EmptyNote("Nothing here.") }
        items(trash.bySender(), key = { "t:" + it.first().address }) { group ->
            val days = 30 - TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - group.minOf { it.storedAt })
            SenderCard(
                vm,
                group,
                caption = "${group.size} message${if (group.size == 1) "" else "s"} · deleted ${ago(group.maxOf { it.storedAt })} · $days days left",
                primary = "Restore",
                onPrimary = { vm.restoreTrash(group) },
                onDelete = { confirmDelete = group },
            )
        }
    }
    if (confirmEmpty) {
        ConfirmDialog("Empty Recently Deleted?", "${trash.size} messages will be deleted for good.", "Delete All", { vm.emptyTrash() }) { confirmEmpty = false }
    }
    confirmDelete?.let { g ->
        ConfirmDialog("Delete for good?", "These messages can't be recovered afterwards.", "Delete", { vm.deleteFromTrash(g) }) { confirmDelete = null }
    }
}

@Composable
fun SpamScreen(vm: AppViewModel) {
    val spam by vm.spam.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    val p = LocalPalette.current
    LibraryPage(
        "Spam",
        onBack = { vm.pop() },
        action = if (spam.isEmpty()) null else { haze ->
            GlassCapsule(haze, onClick = { confirmEmpty = true }) { Text("Empty", style = IosType.body, color = p.red) }
        },
    ) {
        item {
            Text(
                "Messages from blocked senders, blocked words and blocked promotions land here without notifying you.",
                style = IosType.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (spam.isEmpty()) item { EmptyNote("No spam. Nice.") }
        items(spam.bySender(), key = { "s:" + it.first().address }) { group ->
            SenderCard(
                vm,
                group,
                caption = "${group.size} message${if (group.size == 1) "" else "s"} · ${group.first().reason.ifBlank { "Spam" }}",
                primary = "Not Spam",
                onPrimary = { vm.notSpam(group) },
                onDelete = { vm.deleteSpam(group) },
            )
        }
    }
    if (confirmEmpty) {
        ConfirmDialog("Empty Spam?", "${spam.size} messages will be deleted for good.", "Delete All", { vm.deleteSpam(spam) }) { confirmEmpty = false }
    }
}

@Composable
fun BlockedScreen(vm: AppViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val p = LocalPalette.current
    LibraryPage("Blocked", onBack = { vm.pop() }) {
        item {
            GroupedSection(
                header = "Senders",
                footer = "Numbers match on the last 10 digits; sender IDs ignore the carrier prefix and TRAI suffix, " +
                    "so blocking HDFCBK covers JX-HDFCBK-S and AD-HDFCBK-T.",
            ) {
                s.blockedSenders.sorted().forEach { key ->
                    RemovableRow(key) { vm.unblock(key) }
                    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = p.separator)
                }
                AddRow("Add number or sender ID") { vm.blockSender(it) }
            }
        }
        item {
            GroupedSection(header = "Words", footer = "Any message containing one of these words goes to Spam. Not case-sensitive.") {
                s.blockedKeywords.sorted().forEach { w ->
                    RemovableRow(w) { vm.removeBlockedKeyword(w) }
                    HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = p.separator)
                }
                AddRow("Add a word, e.g. loan or lottery") { vm.addBlockedKeyword(it) }
            }
        }
    }
}

@Composable
private fun RemovableRow(text: String, onRemove: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = IosType.body, color = p.label, modifier = Modifier.weight(1f))
        Icon(
            Icons.Rounded.Close,
            "Remove $text",
            tint = p.secondaryLabel,
            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onRemove).padding(8.dp),
        )
    }
}

@Composable
private fun AddRow(placeholder: String, onAdd: (String) -> Unit) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = IosType.body.copy(color = p.label),
            cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(placeholder, style = IosType.body, color = p.tertiaryLabel)
                    inner()
                }
            },
        )
        if (text.isNotBlank()) {
            Text("Add", style = IosType.body, fontWeight = FontWeight.SemiBold, color = p.accent, modifier = Modifier.clickable { submit() }.padding(8.dp))
        }
    }
}

private val OTP_AGES = listOf(null to "Keep", 1 to "1 day", 7 to "7 days", 30 to "30 days")
private val OFFER_AGES = listOf(null to "Keep", 7 to "7 days", 30 to "30 days", 90 to "90 days")

/** Bulk-moves old codes and offers to Recently Deleted. */
@Composable
fun CleanerDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var otpIdx by remember { mutableIntStateOf(1) }
    var offerIdx by remember { mutableIntStateOf(2) }
    var found by remember { mutableStateOf<List<Message>?>(null) }
    LaunchedEffect(otpIdx, offerIdx) {
        found = null
        found = vm.findCleanable(OTP_AGES[otpIdx].first, OFFER_AGES[offerIdx].first)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(p.secondaryGroupedBackground)
                .padding(20.dp),
        ) {
            Text("Clean Up Inbox", style = IosType.title3, color = p.label)
            Spacer(Modifier.height(4.dp))
            Text("Moves old codes and offers to Recently Deleted, where you can restore them for 30 days.", style = IosType.footnote, color = p.secondaryLabel)
            Spacer(Modifier.height(16.dp))
            Text("OTPs older than", style = IosType.subhead, color = p.secondaryLabel)
            Spacer(Modifier.height(6.dp))
            LiquidSegmentedControl(OTP_AGES.map { it.second }, otpIdx, { otpIdx = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text("Offers older than", style = IosType.subhead, color = p.secondaryLabel)
            Spacer(Modifier.height(6.dp))
            LiquidSegmentedControl(OFFER_AGES.map { it.second }, offerIdx, { offerIdx = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            val n = found?.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (n) {
                        null -> "Counting…"
                        0 -> "Nothing to clean"
                        else -> "$n message${if (n == 1) "" else "s"}"
                    },
                    style = IosType.body,
                    color = p.label,
                    modifier = Modifier.weight(1f),
                )
                Text("Cancel", style = IosType.body, color = p.accent, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Clean",
                    style = IosType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if ((n ?: 0) > 0) p.red else p.tertiaryLabel,
                    modifier = Modifier
                        .clickable(enabled = (n ?: 0) > 0) {
                            found?.let { vm.clean(it) }
                            onDismiss()
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Glass "Deleted · Undo" bar that rises above the bottom controls for five seconds. */
@Composable
fun UndoBar(vm: AppViewModel, haze: HazeState, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val event by vm.undo.collectAsStateWithLifecycle()
    var shown by remember { mutableStateOf(event) }
    LaunchedEffect(event) {
        if (event != null) shown = event
        val e = event ?: return@LaunchedEffect
        delay(5_000)
        vm.dismissUndo(e.id)
    }
    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 500f)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val e = shown ?: return@AnimatedVisibility
        GlassCapsule(haze, Modifier.fillMaxWidth()) {
            Text(e.label, style = IosType.subhead, color = p.label, modifier = Modifier.weight(1f))
            Text(
                "Undo",
                style = IosType.body,
                fontWeight = FontWeight.SemiBold,
                color = p.accent,
                modifier = Modifier
                    .clickable {
                        e.undo()
                        vm.dismissUndo(e.id)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
