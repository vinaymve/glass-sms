package com.glasssms.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.Screen
import com.glasssms.data.Conversation
import com.glasssms.ui.glass.GlassCapsule
import com.glasssms.ui.glass.GlassIconButton
import com.glasssms.ui.glass.LiquidSegmentedControl
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.glass.glass
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val FILTERS = listOf("All", "Known", "Unknown", "Codes")

@Composable
fun InboxScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val codes by vm.codes.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var openRow by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val listState = rememberLazyListState()
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 } }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val now = System.currentTimeMillis()
    val activeCodes = codes.count { it.isActive(now) }

    val visible = remember(conversations, query, filter) {
        conversations.orEmpty().filter { c ->
            val matchesFilter = when (filter) {
                1 -> c.isKnown
                2 -> !c.isKnown
                3 -> c.otp != null
                else -> true
            }
            matchesFilter && (query.isBlank() ||
                c.displayName.contains(query, ignoreCase = true) ||
                c.latest.body.contains(query, ignoreCase = true))
        }
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 56.dp, bottom = insets.calculateBottomPadding() + 96.dp),
        ) {
            item { SignTitle("Messages") }
            item {
                LiquidSegmentedControl(
                    options = FILTERS,
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                conversations == null -> Unit
                visible.isEmpty() -> item { EmptyInbox(filtered = query.isNotBlank() || filter != 0) }
                else -> items(visible, key = { it.threadId }) { c ->
                    SwipeToRevealRow(
                        isOpen = openRow == c.threadId,
                        onOpenChange = { open -> openRow = if (open) c.threadId else null },
                        onDelete = { pendingDelete = c },
                    ) {
                        ConversationRow(
                            c = c,
                            onClick = {
                                if (openRow != null) openRow = null else vm.openThread(c.threadId, c.address)
                            },
                            onCopyCode = { vm.copyCode(it, c.latest.id) },
                        )
                    }
                }
            }
        }

        // Status-bar scroll edge
        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 64.dp).edgeBlur(haze))

        // Top bar
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp, end = 16.dp)
                .height(44.dp),
        ) {
            GlassIconButton(Icons.Rounded.Settings, "Settings", haze, onClick = { vm.push(Screen.Settings) }, modifier = Modifier.align(Alignment.CenterStart))
            AnimatedVisibility(collapsed, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
                GlassCapsule(haze) { Text("Messages", style = IosType.headline, color = p.label) }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                GlassIconButton(Icons.Rounded.Key, "Verification codes", haze, onClick = { vm.push(Screen.Codes) }, iconTint = p.accent)
                if (activeCodes > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(p.red),
                        contentAlignment = Alignment.Center,
                    ) { Text("$activeCodes", style = IosType.caption2, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // Bottom: glass search + compose
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .glass(haze, CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = p.secondaryLabel, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = IosType.body.copy(color = p.label),
                    cursorBrush = SolidColor(p.accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text("Search", style = IosType.body, color = p.secondaryLabel)
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Cancel, "Clear", tint = p.tertiaryLabel,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { query = "" },
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Edit, "New message", haze, onClick = { vm.openNewMessage() }, size = 50.dp, iconTint = p.accent)
        }
    }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation?", style = IosType.headline) },
            text = { Text("All messages with ${c.displayName} will be permanently deleted from this phone.", style = IosType.subhead) },
            confirmButton = {
                TextButton(onClick = { vm.deleteThread(c.threadId); pendingDelete = null; openRow = null }) {
                    Text("Delete", color = p.red, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = p.accent) } },
        )
    }
}

/** Large title with the thin rule that runs along the top of NYC subway signs. */
@Composable
fun SignTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.label))
        Spacer(Modifier.height(8.dp))
        Text(text, style = IosType.largeTitle, color = p.label)
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit, onCopyCode: (String) -> Unit) {
    val p = LocalPalette.current
    val otpFresh = c.otp != null && System.currentTimeMillis() < c.latest.date + c.otp.validForMillis
    Column(Modifier.fillMaxWidth().background(p.background).clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Box(Modifier.width(16.dp).height(48.dp), contentAlignment = Alignment.Center) {
                if (c.unreadCount > 0) Box(Modifier.size(10.dp).clip(CircleShape).background(p.accent))
            }
            Avatar(c.displayName, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.displayName,
                        style = IosType.headline,
                        color = p.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Times.listLabel(c.latest.date), style = IosType.subhead, color = p.secondaryLabel)
                    Icon(Icons.Rounded.ChevronRight, null, tint = p.tertiaryLabel, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(2.dp))
                if (otpFresh && c.otp != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            c.otp.service?.let { "$it code" } ?: "Verification code",
                            style = IosType.subhead,
                            color = p.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        CodeChip(c.otp.pretty) { onCopyCode(c.otp.code) }
                    }
                } else {
                    Text(
                        c.latest.body,
                        style = IosType.subhead,
                        color = p.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 80.dp), thickness = 0.5.dp, color = p.separator)
    }
}

@Composable
fun CodeChip(label: String, onCopy: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(p.accent.copy(alpha = 0.12f))
            .clickable(onClick = onCopy)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = IosType.subhead, fontWeight = FontWeight.Bold, color = p.accent)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Rounded.ContentCopy, "Copy code", tint = p.accent, modifier = Modifier.size(14.dp))
    }
}

/** iOS-style swipe left to reveal a red Delete action. */
@Composable
private fun SwipeToRevealRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val actionWidth = 88.dp
    val actionPx = with(LocalDensity.current) { actionWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val settle = spring<Float>(dampingRatio = 0.8f, stiffness = 500f)

    LaunchedEffect(isOpen) { offset.animateTo(if (isOpen) -actionPx else 0f, settle) }

    Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(actionWidth)
                .background(p.red)
                .clickable(onClick = onDelete),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Delete, null, tint = Color.White)
            Text("Delete", style = IosType.footnote, color = Color.White)
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-actionPx * 1.4f, 0f)) }
                    },
                    onDragStopped = { velocity ->
                        val open = offset.value < -actionPx / 2 || velocity < -1200f
                        offset.animateTo(if (open) -actionPx else 0f, settle)
                        onOpenChange(open)
                    },
                ),
        ) { content() }
    }
}

@Composable
private fun EmptyInbox(filtered: Boolean) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (filtered) "No Results" else "No Messages", style = IosType.title2, color = p.label)
        Spacer(Modifier.height(6.dp))
        Text(
            if (filtered) "Try a different search or filter." else "Messages you send and receive will appear here.",
            style = IosType.subhead,
            color = p.secondaryLabel,
        )
    }
}
