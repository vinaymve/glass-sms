package com.glasssms.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasssms.ui.glass.glass
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import com.glasssms.ui.theme.Mta
import com.glasssms.ui.theme.SubwayFont
import dev.chrisbanes.haze.HazeState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Contact avatar drawn as an NYC subway route bullet, coloured per contact. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    val initials = remember(name) { initialsOf(name) }
    val (bullet, ink) = remember(name) { Mta.forKey(name) }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bullet),
        contentAlignment = Alignment.Center,
    ) {
        if (initials.isEmpty()) {
            Icon(Icons.Rounded.Person, null, tint = ink, modifier = Modifier.size(size * 0.62f))
        } else {
            Text(
                initials,
                color = ink,
                fontFamily = SubwayFont,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * if (initials.length == 1) 0.56f else 0.42f).sp,
                letterSpacing = (-0.5).sp,
            )
        }
    }
}

private fun initialsOf(name: String): String {
    if (name.none { it.isLetter() }) return ""
    val words = name.split(' ', '-', '_').filter { w -> w.firstOrNull()?.isLetter() == true }
    return when {
        words.size >= 2 -> "${words.first().first()}${words.last().first()}"
        words.size == 1 -> words[0].first().toString()
        else -> ""
    }.uppercase()
}

/** iMessage bubble: rounded body plus an optional hooked tail on the sender's side. */
class BubbleShape(
    private val outgoing: Boolean,
    private val tail: Boolean,
    private val radius: Dp = 18.dp,
    private val tailWidth: Dp = 6.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }.coerceAtMost(size.height / 2)
        val t = with(density) { tailWidth.toPx() }
        val w = size.width
        val h = size.height
        // Built for the outgoing (right-hand) case, mirrored for incoming.
        fun x(v: Float) = if (outgoing) v else w - v
        val right = w - t

        val body = Path().apply {
            addRoundRect(RoundRect(x(0f).coerceAtMost(x(right)), 0f, x(0f).coerceAtLeast(x(right)), h, CornerRadius(r)))
        }
        if (!tail) return Outline.Generic(body)

        val hook = Path().apply {
            moveTo(x(right), h - r)
            cubicTo(x(right), h - r * 0.45f, x(right + t * 0.4f), h - 1f, x(w), h)
            cubicTo(x(w - t * 1.5f), h, x(right - 4f), h - 2f, x(right - r * 0.7f), h - 3f)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, hook))
    }
}

/** Floating glass message field with a send button that pops in once there is text. */
@Composable
fun GlassInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier,
    placeholder: String = "Text Message · SMS",
) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 44.dp)
            .glass(haze, RoundedCornerShape(22.dp))
            .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            textStyle = IosType.body.copy(color = p.label),
            cursorBrush = SolidColor(p.accent),
            maxLines = 6,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text(placeholder, style = IosType.body, color = p.tertiaryLabel)
                    inner()
                }
            },
        )
        Spacer(Modifier.width(6.dp))
        AnimatedVisibility(
            visible = text.isNotBlank(),
            enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 600f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(p.accent)
                    .clickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** iOS grouped-table section: rounded white card on a grey page. */
@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Text(
                header.uppercase(),
                style = IosType.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.secondaryGroupedBackground),
        ) { content() }
        if (footer != null) {
            Text(
                footer,
                style = IosType.footnote,
                color = p.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
        }
    }
}

object Times {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val shortDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    private val weekday = DateTimeFormatter.ofPattern("EEEE")
    private val headerDate = DateTimeFormatter.ofPattern("EEE, d MMM")

    /** Conversation list: "9:41 AM", "Yesterday", "Tuesday", "12/03/25". */
    fun listLabel(millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now(zone))
        return when {
            days <= 0L -> time.format(dt)
            days == 1L -> "Yesterday"
            days < 7L -> weekday.format(dt)
            else -> shortDate.format(dt)
        }
    }

    /** Thread separator: "Today 9:41 AM", "Yesterday 8:02 PM", "Tue, 3 Dec 9:41 AM". */
    fun headerLabel(millis: Long): String {
        val dt = Instant.ofEpochMilli(millis).atZone(zone)
        val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now(zone))
        val day = when {
            days <= 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> weekday.format(dt)
            else -> headerDate.format(dt)
        }
        return "$day ${time.format(dt)}"
    }

    fun countdown(remainingMillis: Long): String {
        val s = (remainingMillis / 1000).coerceAtLeast(0)
        return if (s >= 3600) "${s / 3600}h ${(s % 3600) / 60}m" else "%d:%02d".format(s / 60, s % 60)
    }
}
