package com.glasssms.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
                            style = IosType.caption,
                            fontWeight = FontWeight.Medium,
                            color = p.secondaryLabel,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp, bottom = 8.dp),
                        )
                    }
                    MessageBubble(
                        m = m,
                        tail = tail,
                        onCopyCode = { code -> vm.copyCode(code, m.id) },
                        onDelete = { vm.deleteMessages(listOf(m.id)) },
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

        GlassInputBar(
            text = draft,
            onTextChange = { draft = it },
            onSend = {
                vm.send(peer, draft)
                draft = ""
            },
            haze = haze,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MessageBubble(m: Message, tail: Boolean, onCopyCode: (String) -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    val clipboard = LocalClipboardManager.current
    val otp: Otp? = remember(m.body) { if (m.isOutgoing) null else OtpParser.parse(m.body, m.address) }
    var menu by remember { mutableStateOf(false) }
    val shape = remember(m.isOutgoing, tail) { BubbleShape(m.isOutgoing, tail) }
    val textColor = if (m.isOutgoing) Color.White else p.label

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
        }
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
                        .graphicsLayer { alpha = if (m.isPending) 0.6f else 1f }
                        .clip(shape)
                        .background(
                            if (m.isOutgoing) Brush.verticalGradient(listOf(p.outgoingTop, p.outgoingBottom))
                            else Brush.verticalGradient(listOf(p.incomingBubble, p.incomingBubble)),
                        )
                        .combinedClickable(onClick = {}, onLongClick = { menu = true })
                        .padding(
                            start = if (m.isOutgoing) 12.dp else 18.dp,
                            end = if (m.isOutgoing) 18.dp else 12.dp,
                            top = 7.dp,
                            bottom = 7.dp,
                        ),
                ) {
                    Text(text, style = IosType.body, color = textColor)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = { clipboard.setText(AnnotatedString(m.body)); menu = false })
                    if (otp != null) {
                        DropdownMenuItem(text = { Text("Copy Code") }, onClick = { onCopyCode(otp.code); menu = false })
                    }
                    DropdownMenuItem(text = { Text("Delete", color = p.red) }, onClick = { onDelete(); menu = false })
                }
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
