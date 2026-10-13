package com.glasssms.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasssms.AppViewModel
import com.glasssms.data.OtpEntry
import com.glasssms.ui.glass.GlassIconButton
import com.glasssms.ui.glass.edgeBlur
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

/** Verification-code vault: every code from the last 24 hours, live countdown, tap to copy. */
@Composable
fun CodesScreen(vm: AppViewModel) {
    val p = LocalPalette.current
    val haze = rememberHazeState()
    val codes by vm.codes.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    var copiedId by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val active = codes.filter { it.isActive(now) }
    val expired = codes.filterNot { it.isActive(now) }

    LaunchedEffect(copiedId) {
        if (copiedId != null) {
            delay(1_600)
            copiedId = null
        }
    }

    Box(Modifier.fillMaxSize().background(p.groupedBackground)) {
        LazyColumn(
            Modifier.fillMaxSize().hazeSource(haze),
            contentPadding = PaddingValues(top = insets.calculateTopPadding() + 56.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        ) {
            item { SignTitle("Codes") }
            item {
                Text(
                    "One-time codes from the last 24 hours. Detected on this phone; nothing is uploaded.",
                    style = IosType.footnote,
                    color = p.secondaryLabel,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            if (active.isEmpty()) {
                item { EmptyCodes() }
            }
            items(active, key = { it.messageId }) { e ->
                CodeCard(e, now, copied = copiedId == e.messageId) {
                    vm.copyCode(e.otp.code, e.messageId)
                    copiedId = e.messageId
                }
            }
            if (expired.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("EXPIRED", style = IosType.footnote, color = p.secondaryLabel, modifier = Modifier.weight(1f))
                        Text(
                            "Delete All",
                            style = IosType.footnote,
                            color = p.red,
                            modifier = Modifier.clickable { confirmClear = true },
                        )
                    }
                }
                items(expired, key = { it.messageId }) { e ->
                    CodeCard(e, now, copied = copiedId == e.messageId, modifier = Modifier.alpha(0.5f)) {
                        vm.copyCode(e.otp.code, e.messageId)
                        copiedId = e.messageId
                    }
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

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete expired codes?", style = IosType.headline) },
            text = { Text("${expired.size} messages will be permanently deleted from this phone.", style = IosType.subhead) },
            confirmButton = {
                TextButton(onClick = { vm.deleteMessages(expired.map { it.messageId }); confirmClear = false }) {
                    Text("Delete", color = p.red, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = p.accent) } },
        )
    }
}

@Composable
private fun CodeCard(e: OtpEntry, now: Long, copied: Boolean, modifier: Modifier = Modifier, onCopy: () -> Unit) {
    val p = LocalPalette.current
    val remaining = e.expiresAt() - now
    val fraction = (remaining.toFloat() / e.otp.validForMillis).coerceIn(0f, 1f)
    val ringColor = when {
        fraction > 0.4f -> p.green
        fraction > 0.15f -> p.orange
        else -> p.red
    }
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(p.secondaryGroupedBackground)
            .clickable(onClick = onCopy)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(e.otp.service ?: e.displayName, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (e.otp.service ?: e.displayName).uppercase(),
                style = IosType.caption,
                fontWeight = FontWeight.Bold,
                color = p.secondaryLabel,
                maxLines = 1,
            )
            Text(e.otp.pretty, style = IosType.title2.copy(fontSize = 30.sp, letterSpacing = 1.5.sp), color = p.label)
            Text("${e.displayName} · ${Times.listLabel(e.date)}", style = IosType.caption, color = p.tertiaryLabel, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(44.dp)) {
                    val stroke = 4.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(p.fill, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(ringColor, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                AnimatedContent(copied, label = "copied") { done ->
                    Icon(
                        if (done) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        if (done) "Copied" else "Copy",
                        tint = if (done) p.green else p.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (remaining > 0) Times.countdown(remaining) else "Expired",
                style = IosType.caption2,
                color = p.secondaryLabel,
            )
        }
    }
}

@Composable
private fun EmptyCodes() {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Key, null, tint = p.tertiaryLabel, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(10.dp))
        Text("No Active Codes", style = IosType.title3, color = p.label)
        Text("New codes show up here and in a notification with a Copy button.", style = IosType.footnote, color = p.secondaryLabel)
    }
}
