package com.glasssms.ui.glass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.abs

/**
 * Liquid glass: a blurred, tinted copy of whatever scrolls underneath ([haze] must be attached to
 * that content with `hazeSource`), a soft specular highlight on the top half, and a bright rim
 * that catches light on the top-left and bottom-right edges.
 *
 * On Android 11 and below there is no RenderEffect, so Haze falls back to a near-opaque tint.
 */
fun Modifier.glass(
    haze: HazeState,
    shape: Shape,
    tint: Color? = null,
    blur: Dp = 22.dp,
    elevation: Dp = 10.dp,
): Modifier = composed {
    val p = LocalPalette.current
    val rim = remember(p) {
        Brush.linearGradient(
            0f to p.glassRimStrong,
            0.35f to p.glassRimWeak,
            0.7f to p.glassRimWeak,
            1f to p.glassRimStrong.copy(alpha = p.glassRimStrong.alpha * 0.6f),
        )
    }
    val highlight = remember(p) { Brush.verticalGradient(0f to p.glassHighlight, 0.55f to Color.Transparent) }
    this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
        .clip(shape)
        .hazeEffect(state = haze) {
            backgroundColor = p.background
            tints = listOf(HazeTint(tint ?: p.glassTint))
            fallbackTint = HazeTint(p.glassFallback)
            blurRadius = blur
            noiseFactor = 0f
        }
        .drawBehind { drawRect(highlight) }
        .border(0.8.dp, rim, shape)
}

/** Blurs content scrolling under the status bar, fading out downwards (iOS "scroll edge effect"). */
fun Modifier.edgeBlur(haze: HazeState, fromTop: Boolean = true): Modifier = composed {
    val p = LocalPalette.current
    hazeEffect(state = haze) {
        backgroundColor = p.background
        tints = listOf(HazeTint(p.background.copy(alpha = 0.55f)))
        fallbackTint = HazeTint(p.background.copy(alpha = 0.9f))
        blurRadius = 14.dp
        noiseFactor = 0f
        progressive = if (fromTop) {
            HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
        } else {
            HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
        }
    }
}

/** Springy "jelly" press: the element swells and stretches a little wider than tall. */
fun Modifier.liquidPress(interaction: MutableInteractionSource, amount: Float = 0.08f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 1f + amount else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f),
        label = "liquidPress",
    )
    graphicsLayer {
        scaleX = s
        scaleY = 1f + (s - 1f) * 0.65f
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    haze: HazeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconTint: Color = LocalPalette.current.label,
    tint: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .liquidPress(interaction)
            .size(size)
            .glass(haze, CircleShape, tint = tint)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = iconTint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun GlassCapsule(
    haze: HazeState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val base = if (onClick != null) modifier.liquidPress(interaction) else modifier
    Row(
        base
            .height(44.dp)
            .glass(haze, CircleShape, tint = tint)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * iOS segmented control whose thumb slides with an underdamped spring and stretches while it
 * travels, which reads as "liquid". Not glass itself, since it lives inside scrolling content.
 */
@Composable
fun LiquidSegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    BoxWithConstraints(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .drawBehind { drawRect(p.fill) }
            .padding(2.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "thumbX",
        )
        val travel = abs((x - segment * selected).value) / segment.value
        val stretch = 1f + travel.coerceAtMost(1f) * 0.18f
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleX = stretch
                    scaleY = 1f - (stretch - 1f) * 0.35f
                }
                .shadow(3.dp, RoundedCornerShape(8.dp), clip = false)
                .clip(RoundedCornerShape(8.dp))
                .drawBehind { drawRect(if (p.isDark) Color(0xFF636366) else Color.White) },
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosType.footnote,
                        fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = p.label,
                    )
                }
            }
        }
    }
}
