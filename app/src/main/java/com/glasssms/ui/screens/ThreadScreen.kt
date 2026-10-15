package com.glasssms.ui.screens

import androidx.compose.foundation.background
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import com.glasssms.otp.CodeActions
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import com.glasssms.ui.glass.GlassMenu
import com.glasssms.ui.glass.MenuItem
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppState
import com.glasssms.AppViewModel
import com.glasssms.data.Message
import com.glasssms.otp.Notifications
import com.glasssms.otp.Otp
import com.glasssms.otp.OtpParser
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.GlassIconButton
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThreadScreen(vm: AppViewModel, threadId: Long, address: String) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val haze = rememberHazeState()
    val messages by remember(threadId) { vm.messages(threadId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    // Notifications may arrive without an address; fall back to whatever the thread says.
    val peer = address.ifBlank { messages.lastOrNull()?.address.orEmpty() }
    val name by produceState(initialValue = peer, peer) {
        value = withContext(Dispatchers.IO) { vm.displayName(peer) }
    }
    var draft by rememberSaveable(threadId) { mutableStateOf("") }
    val settings by vm.settings.collectAsStateWithLifecycle()
    var selecting by remember { mutableStateOf<Message?>(null) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }
    var openingUrl by remember { mutableStateOf<String?>(null) }

    fun onLink(target: LinkTarget) {
        when (target) {
            is LinkTarget.Url -> openingUrl = target.url
            is LinkTarget.Phone -> context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + target.number.filter { it.isDigit() || it == '+' })))
            is LinkTarget.Email -> context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + target.address)))
            is LinkTarget.Upi -> {
                // Never open payment apps straight from an SMS; copying lets the user check first.
                CodeActions.copy(context, target.id)
                Toast.makeText(context, "UPI ID copied", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val listState = rememberLazyListState()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val newestFirst = remember(messages) { messages.asReversed() }

    LifecycleResumeEffect(threadId) {
        AppState.visibleThreadId = threadId
        Notifications.cancel(context, threadId)
        onPauseOrDispose { if (AppState.visibleThreadId == threadId) AppState.visibleThreadId = null }
    }
    LaunchedEffect(messages.size) {
        if (messages.any { !it.read && !it.isOutgoing }) vm.markRead(threadId)
        if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 96.dp,
                bottom = insets.calculateBottomPadding() + 72.dp,
                start = 12.dp,
                end = 12.dp,
            ),
        ) {
            itemsIndexed(newestFirst, key = { _, m -> m.id }) { i, m ->
                val newer = newestFirst.getOrNull(i - 1)
                val older = newestFirst.getOrNull(i + 1)
                val tail = newer == null || newer.isOutgoing != m.isOutgoing || newer.date - m.date > 60_000
                val header = older == null || m.date - older.date > 60 * 60 * 1000
                Column(Modifier.fillMaxWidth()) {
                    if (header) {
                        Text(
                            Times.headerLabel(m.date),
                            style = IosType.timestamp.copy(fontSize = 11.sp),
                            color = p.secondaryLabel,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 8.dp),
                        )
                    }
                    MessageBubble(
                        m = m,
                        tail = tail,
                        onCopyCode = { code -> vm.copyCode(code, m.id) },
                        onSelectText = { selecting = m },
                        onLink = ::onLink,
                        onForward = { vm.openNewMessage(body = m.body) },
                        onDelete = { if (settings.confirmDelete) pendingDelete = m else vm.deleteMessages(listOf(m.id)) },
                    )
                    Spacer(Modifier.height(if (tail) 8.dp else 2.dp))
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 110.dp).edgeBlur(haze))

        // Top bar: back button, then route-bullet avatar over a glass name pill.
        Box(Modifier.fillMaxWidth().padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp, end = 16.dp)) {
            GlassIconButton(
                Icons.AutoMirrored.Rounded.ArrowBackIos,
                "Back",
                haze,
                onClick = { vm.pop() },
                modifier = Modifier.align(Alignment.TopStart),
            )
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name, size = 52.dp)
                Spacer(Modifier.height(4.dp))
                GlassCapsule(haze, Modifier.height(30.dp)) {
                    Text(name, style = IosType.footnote, fontWeight = FontWeight.SemiBold, color = p.label, maxLines = 1)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            UndoBar(vm, haze, Modifier.padding(horizontal = 12.dp))
            GlassInputBar(
                text = draft,
                onTextChange = { draft = it },
                onSend = {
                    vm.send(peer, draft)
                    draft = ""
                },
                haze = haze,
            )
        }
    }

    selecting?.let { m -> SelectTextDialog(m.body) { selecting = null } }

    openingUrl?.let { url -> LinkSheet(url) { openingUrl = null } }

    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete message?", style = IosType.headline) },
            text = { Text("This message will be permanently deleted from this phone.", style = IosType.subhead) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMessages(listOf(m.id)); pendingDelete = null }) {
                    Text("Delete", color = p.red, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = p.accent) } },
        )
    }
}

@Composable
private fun MessageBubble(
    m: Message,
    tail: Boolean,
    onCopyCode: (String) -> Unit,
    onSelectText: () -> Unit,
    onLink: (LinkTarget) -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val otp: Otp? = remember(m.body) { if (m.isOutgoing) null else OtpParser.parse(m.body, m.address) }
    var menu by remember { mutableStateOf(false) }
    // The bubble lifts slightly while its menu is open, like iOS.
    val lift by animateFloatAsState(if (menu) 1.04f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
    val shape = remember(m.isOutgoing, tail) { BubbleShape(m.isOutgoing, tail) }
    val textColor = if (m.isOutgoing) Color.White else p.label

    val linkHandler by rememberUpdatedState(onLink)
    val text: AnnotatedString = remember(m.body, otp) {
        buildAnnotatedString {
            if (otp == null || otp.end > m.body.length) {
                append(m.body)
            } else {
                append(m.body.substring(0, otp.start))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
                    append(m.body.substring(otp.start, otp.end))
                }
                append(m.body.substring(otp.end))
            }
            Links.find(m.body).forEachIndexed { i, link ->
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "link$i",
                        styles = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
                    ) { linkHandler(link.target) },
                    link.start,
                    link.end,
                )
            }
        }
    }

    val items = buildList {
        add(MenuItem("Copy", Icons.Rounded.ContentCopy) { clipboard.setText(AnnotatedString(m.body)) })
        if (otp != null) add(MenuItem("Copy Code ${otp.pretty}", Icons.Rounded.Key) { onCopyCode(otp.code) })
        add(MenuItem("Select Text", Icons.Rounded.TextFields, onClick = onSelectText))
        add(
            MenuItem("Share", Icons.Rounded.Share) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, m.body)
                context.startActivity(Intent.createChooser(send, null))
            },
        )
        add(MenuItem("Forward", Icons.AutoMirrored.Rounded.Forward, onClick = onForward))
        add(MenuItem("Delete", Icons.Rounded.Delete, destructive = true, onClick = onDelete))
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m.isFailed) {
            Icon(Icons.Rounded.ErrorOutline, "Not delivered", tint = p.red, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (m.isOutgoing) Alignment.End else Alignment.Start) {
            Box {
                Box(
                    Modifier
                        .widthIn(max = 300.dp)
                        .graphicsLayer {
                            alpha = if (m.isPending) 0.6f else 1f
                            scaleX = lift
                            scaleY = lift
                            transformOrigin = TransformOrigin(if (m.isOutgoing) 1f else 0f, 0.5f)
                        }
                        .clip(shape)
                        .background(
                            if (m.isOutgoing) Brush.verticalGradient(listOf(p.outgoingTop, p.outgoingBottom))
                            else Brush.verticalGradient(listOf(p.incomingBubble, p.incomingBubble)),
                        )
                        .combinedClickable(
                            onClick = { menu = true },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menu = true
                            },
                        )
                        .padding(
                            start = if (m.isOutgoing) 12.dp else 18.dp,
                            end = if (m.isOutgoing) 18.dp else 12.dp,
                            top = 7.dp,
                            bottom = 7.dp,
                        ),
                ) {
                    Text(text, style = IosType.body, color = textColor)
                }
                GlassMenu(expanded = menu, onDismiss = { menu = false }, items = items, alignEnd = m.isOutgoing)
            }
            if (otp != null) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.padding(start = 8.dp)) { CodeChip("Copy ${otp.pretty}") { onCopyCode(otp.code) } }
            }
            if (m.isFailed) {
                Text("Not Delivered", style = IosType.caption, color = p.red, modifier = Modifier.padding(top = 2.dp, end = 8.dp))
            }
        }
    }
}

/** Shows where a link really goes, with warnings for common scam patterns, before opening it. */
@Composable
private fun LinkSheet(url: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val warnings = remember(url) { Links.warnings(url) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(p.secondaryGroupedBackground)
                .padding(20.dp),
        ) {
            Text("Open link?", style = IosType.title3, color = p.label)
            Spacer(Modifier.height(10.dp))
            Text(Links.host(url), style = IosType.headline, color = if (warnings.isEmpty()) p.label else p.red)
            Text(url, style = IosType.footnote, color = p.secondaryLabel, maxLines = 3)
            warnings.forEach { w ->
                Spacer(Modifier.height(8.dp))
                Row {
                    Icon(Icons.Rounded.WarningAmber, null, tint = p.orange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(w, style = IosType.footnote, color = p.label)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Copy",
                    style = IosType.body,
                    color = p.accent,
                    modifier = Modifier
                        .clickable {
                            CodeActions.copy(context, url)
                            onDismiss()
                        }
                        .padding(8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("Cancel", style = IosType.body, color = p.accent, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (warnings.isEmpty()) "Open" else "Open Anyway",
                    style = IosType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (warnings.isEmpty()) p.accent else p.red,
                    modifier = Modifier
                        .clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Links.uriFor(url))) }
                            onDismiss()
                        }
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Full message in a sheet where any part can be selected with the system handles. */
@Composable
private fun SelectTextDialog(body: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(p.secondaryGroupedBackground)
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Select Text", style = IosType.headline, color = p.label, modifier = Modifier.weight(1f))
                Text(
                    "Done",
                    style = IosType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = p.accent,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SelectionContainer(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(body, style = IosType.body, color = p.label)
            }
        }
    }
}
