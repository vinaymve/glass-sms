package com.glasssms.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import com.glasssms.ui.glass.GlassMenu
import com.glasssms.ui.glass.MenuItem
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.glasssms.data.SwipeAction
import com.glasssms.ui.fx.BlastLayer
import com.glasssms.ui.fx.rememberBlastState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val blasts = rememberBlastState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    // Where each row and the blast overlay sit; plain refs (not state) so scrolling stays cheap.
    val rowCoords = remember { HashMap<Long, LayoutCoordinates>() }
    var overlayCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun delete(threadId: Long) {
        vm.deleteThread(threadId)
        if (!settings.deleteExplosion) return
        val row = rowCoords[threadId]?.takeIf { it.isAttached } ?: return
        val overlay = overlayCoords?.takeIf { it.isAttached } ?: return
        blasts.explode(overlay.localBoundingBoxOf(row), density)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    // Thread whose long-press menu is showing.
    var menuFor by remember { mutableStateOf<Long?>(null) }

    fun requestDelete(c: Conversation) {
        if (settings.confirmDelete) pendingDelete = c else delete(c.threadId)
    }
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

    val showPins = filter == 0 && query.isBlank()
    val pinned = if (showPins) visible.filter { it.threadId in settings.pinnedThreads } else emptyList()
    val rows = if (pinned.isEmpty()) visible else visible.filter { it.threadId !in settings.pinnedThreads }

    @Composable
    fun ConversationMenu(c: Conversation) {
        val isPinned = c.threadId in settings.pinnedThreads
        GlassMenu(
            expanded = menuFor == c.threadId,
            onDismiss = { menuFor = null },
            items = listOf(
                MenuItem(if (isPinned) "Unpin" else "Pin", if (isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin) {
                    vm.togglePin(c.threadId)
                },
                if (c.unreadCount > 0) MenuItem("Mark as Read", Icons.Rounded.MarkChatRead) { vm.toggleRead(c) }
                else MenuItem("Mark as Unread", Icons.Rounded.MarkChatUnread) { vm.toggleRead(c) },
                MenuItem("Delete", Icons.Rounded.Delete, destructive = true) { requestDelete(c) },
            ),
        )
    }

    fun openMenu(c: Conversation) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        menuFor = c.threadId
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 60.dp, bottom = insets.calculateBottomPadding() + if (filtersOpen) 156.dp else 96.dp),
        ) {
            if (pinned.isNotEmpty()) {
                item(key = "pins") {
                    Column(Modifier.padding(horizontal = 8.dp)) {
                        pinned.chunked(3).forEach { line ->
                            Row(Modifier.fillMaxWidth()) {
                                line.forEach { c ->
                                    Box(Modifier.weight(1f).onPlaced { rowCoords[c.threadId] = it }) {
                                        PinnedTile(c, onClick = { vm.openThread(c.threadId, c.address) }, onLongClick = { openMenu(c) })
                                        ConversationMenu(c)
                                    }
                                }
                                repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 6.dp), thickness = 0.5.dp, color = p.separator)
                }
            }
            when {
                conversations == null -> Unit
                visible.isEmpty() -> item { EmptyInbox(filtered = query.isNotBlank() || filter != 0) }
                else -> items(rows, key = { it.threadId }) { c ->
                  Box {
                    SwipeActionRow(
                        modifier = Modifier.onPlaced { rowCoords[c.threadId] = it },
                        right = settings.swipeRight,
                        left = settings.swipeLeft,
                        isUnread = c.unreadCount > 0,
                        removesRow = { it == SwipeAction.Delete && !settings.confirmDelete },
                        onAction = { action ->
                            when (action) {
                                SwipeAction.ToggleRead -> vm.toggleRead(c)
                                SwipeAction.Delete -> requestDelete(c)
                                SwipeAction.None -> Unit
                            }
                        },
                    ) {
                        ConversationRow(
                            c = c,
                            onClick = { vm.openThread(c.threadId, c.address) },
                            onLongClick = { openMenu(c) },
                            onCopyCode = { vm.copyCode(it, c.latest.id) },
                        )
                    }
                    ConversationMenu(c)
                  }
                }
            }
        }

        // Status-bar scroll edge
        Box(Modifier.fillMaxWidth().height(insets.calculateTopPadding() + 64.dp).edgeBlur(haze))

        // Top bar: the active filter (if any) on the left, one overflow menu on the right.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = insets.calculateTopPadding() + 6.dp, start = 16.dp, end = 16.dp)
                .height(44.dp),
        ) {
            AnimatedVisibility(
                filter != 0,
                Modifier.align(Alignment.CenterStart),
                enter = scaleIn(spring(dampingRatio = 0.6f, stiffness = 600f), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeIn(),
                exit = scaleOut(targetScale = 0.6f, transformOrigin = TransformOrigin(0f, 0.5f)) + fadeOut(),
            ) {
                GlassCapsule(haze, onClick = { filter = 0 }) {
                    Text(FILTERS.getOrElse(filter) { "" }, style = IosType.headline, color = p.label)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Close, "Clear filter", tint = p.secondaryLabel, modifier = Modifier.size(16.dp))
                }
            }
            Box(Modifier.align(Alignment.CenterEnd)) {
                GlassIconButton(Icons.Rounded.MoreHoriz, "Menu", haze, onClick = { menuOpen = true })
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
                GlassMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    alignEnd = true,
                    items = listOf(
                        MenuItem(if (activeCodes > 0) "Codes · $activeCodes active" else "Verification Codes", Icons.Rounded.Key) {
                            vm.push(Screen.Codes)
                        },
                        MenuItem("Mark All as Read", Icons.Rounded.DoneAll) { vm.markAllRead() },
                        MenuItem("Settings", Icons.Rounded.Settings) { vm.push(Screen.Settings) },
                    ),
                )
            }
        }

        // Bottom: filter toggle + glass search + compose; filters pop up above as a glass strip.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            AnimatedVisibility(
                visible = filtersOpen,
                enter = scaleIn(
                    spring(dampingRatio = 0.55f, stiffness = 480f),
                    initialScale = 0.4f,
                    transformOrigin = TransformOrigin(0.06f, 1f),
                ) + fadeIn(),
                exit = scaleOut(
                    spring(dampingRatio = 0.9f, stiffness = 700f),
                    targetScale = 0.4f,
                    transformOrigin = TransformOrigin(0.06f, 1f),
                ) + fadeOut(),
            ) {
                Box(
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .glass(haze, CircleShape)
                        .padding(5.dp),
                ) {
                    LiquidSegmentedControl(
                        options = FILTERS,
                        selected = filter,
                        onSelect = { i ->
                            filter = i
                            // Let the thumb finish its slide before the strip tucks away.
                            scope.launch {
                                delay(320)
                                filtersOpen = false
                            }
                        },
                        height = 40.dp,
                        trackColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    GlassIconButton(
                        if (filtersOpen) Icons.Rounded.Close else Icons.Rounded.FilterList,
                        "Filters",
                        haze,
                        onClick = { filtersOpen = !filtersOpen },
                        size = 50.dp,
                        iconTint = if (filter != 0) p.accent else p.label,
                    )
                    if (filter != 0 && !filtersOpen) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 4.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(p.accent),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
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
                                if (query.isEmpty()) {
                                    Text(
                                        if (filter == 0) "Search" else "Search ${FILTERS[filter]}",
                                        style = IosType.body,
                                        color = p.secondaryLabel,
                                        maxLines = 1,
                                    )
                                }
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

        BlastLayer(blasts, Modifier.onPlaced { overlayCoords = it })
    }

    BackHandler(enabled = filtersOpen) { filtersOpen = false }

    pendingDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation?", style = IosType.headline) },
            text = { Text("All messages with ${c.displayName} will be permanently deleted from this phone.", style = IosType.subhead) },
            confirmButton = {
                TextButton(onClick = { delete(c.threadId); pendingDelete = null }) {
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
private fun ConversationRow(c: Conversation, onClick: () -> Unit, onLongClick: () -> Unit, onCopyCode: (String) -> Unit) {
    val p = LocalPalette.current
    val otpFresh = c.otp != null && System.currentTimeMillis() < c.latest.date + c.otp.validForMillis
    Column(Modifier.fillMaxWidth().background(p.background).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
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

/** iOS-style pinned conversation: big avatar, name underneath, unread dot on the shoulder. */
@Composable
private fun PinnedTile(c: Conversation, onClick: () -> Unit, onLongClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Avatar(c.displayName, size = 72.dp)
            if (c.unreadCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(p.background)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(p.accent),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            c.displayName,
            style = IosType.footnote,
            fontWeight = if (c.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
            color = p.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
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

/**
 * Gmail-style swipe: drag past a third of the width and let go to run that side's action.
 * The background shows the action's colour and icon, which pops (with a haptic tick) once armed.
 *
 * The drag writes the offset synchronously and the action runs the moment the finger lifts, before
 * any animation; the fly-off/snap-back is purely cosmetic, so interrupting it can't drop the action.
 */
@Composable
private fun SwipeActionRow(
    modifier: Modifier = Modifier,
    right: SwipeAction,
    left: SwipeAction,
    isUnread: Boolean,
    /** True when the action makes the row disappear, so it should fly off instead of snapping back. */
    removesRow: (SwipeAction) -> Boolean,
    onAction: (SwipeAction) -> Unit,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Set once an action has fired: the action panel hides at once instead of trailing the row home.
    var committed by remember { mutableStateOf(false) }

    val action by remember(left, right) {
        derivedStateOf {
            when {
                dragX > 0.5f -> right
                dragX < -0.5f -> left
                else -> SwipeAction.None
            }
        }
    }
    val armed by remember { derivedStateOf { width > 0f && kotlin.math.abs(dragX) > width / 3f } }

    LaunchedEffect(armed) {
        if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    fun settleTo(target: Float, spec: AnimationSpec<Float>, then: () -> Unit = {}) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(dragX, target, animationSpec = spec) { v, _ -> dragX = v }
            then()
        }
    }

    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat() }) {
        if (action != SwipeAction.None && !committed) {
            val color = when (action) {
                SwipeAction.Delete -> p.red
                else -> p.accent
            }
            val bg by animateColorAsState(if (armed) color else p.fill.copy(alpha = 1f), label = "swipeBg")
            val iconScale by animateFloatAsState(
                if (armed) 1.25f else 0.9f,
                spring(dampingRatio = 0.45f, stiffness = 600f),
                label = "swipeIcon",
            )
            val fromStart = dragX > 0f
            Box(
                Modifier.matchParentSize().background(bg).padding(horizontal = 28.dp),
                contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
                ) {
                    val (icon, label) = when (action) {
                        SwipeAction.Delete -> Icons.Rounded.Delete to "Delete"
                        else -> if (isUnread) Icons.Rounded.MarkChatRead to "Read" else Icons.Rounded.MarkChatUnread to "Unread"
                    }
                    Icon(icon, null, tint = Color.White)
                    Text(label, style = IosType.caption, color = Color.White)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(dragX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = left != SwipeAction.None || right != SwipeAction.None,
                    state = rememberDraggableState { delta ->
                        val min = if (left == SwipeAction.None) 0f else -width
                        val max = if (right == SwipeAction.None) 0f else width
                        dragX = (dragX + delta).coerceIn(min, max)
                    },
                    onDragStarted = {
                        settleJob?.cancel()
                        committed = false
                    },
                    onDragStopped = { velocity ->
                        val a = action
                        val flung = kotlin.math.abs(velocity) > 2500f &&
                            kotlin.math.sign(velocity) == kotlin.math.sign(dragX) &&
                            kotlin.math.abs(dragX) > width / 6f
                        if (a != SwipeAction.None && (armed || flung)) {
                            onAction(a)
                            if (removesRow(a)) {
                                settleTo(kotlin.math.sign(dragX) * width, tween(160))
                                // The list normally drops this row within a frame or two; if the delete
                                // failed it's still here, so bring it back rather than leave a gap.
                                scope.launch {
                                    delay(1_500)
                                    settleTo(0f, spring(dampingRatio = 0.75f, stiffness = 450f))
                                }
                            } else {
                                committed = true
                                settleTo(0f, tween(180, easing = FastOutSlowInEasing)) { committed = false }
                            }
                        } else {
                            settleTo(0f, spring(dampingRatio = 0.75f, stiffness = 450f))
                        }
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
