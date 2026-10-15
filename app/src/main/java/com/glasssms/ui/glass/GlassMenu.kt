package com.glasssms.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.glasssms.ui.theme.IosType
import com.glasssms.ui.theme.LocalPalette

data class MenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * iOS-style context menu that springs out of its anchor (the composable it's placed in).
 * Opens below the anchor, or above when there isn't room. Popups live in their own window, so
 * this is a frosted panel rather than a live blur of the content behind.
 */
@Composable
fun GlassMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<MenuItem>,
    alignEnd: Boolean = false,
) {
    val p = LocalPalette.current
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return

    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
    val provider = remember(alignEnd, gapPx) { AnchorProvider(alignEnd, gapPx) }
    val origin = TransformOrigin(if (alignEnd) 1f else 0f, if (provider.openedAbove) 1f else 0f)

    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        AnimatedVisibility(
            visibleState = state,
            enter = scaleIn(spring(dampingRatio = 0.62f, stiffness = 650f), initialScale = 0.6f, transformOrigin = origin) +
                fadeIn(tween(110)),
            exit = scaleOut(tween(120), targetScale = 0.85f, transformOrigin = origin) + fadeOut(tween(110)),
        ) {
            val shape = RoundedCornerShape(18.dp)
            val panel = if (p.isDark) Color(0xF72C2C2E) else Color(0xF7F7F7F8)
            Column(
                Modifier
                    .padding(6.dp)
                    .width(240.dp)
                    .shadow(28.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
                    .clip(shape)
                    .background(panel)
                    .drawBehind { drawRect(Brush.verticalGradient(0f to p.glassHighlight, 0.5f to Color.Transparent)) }
                    .border(0.8.dp, Brush.verticalGradient(listOf(p.glassRimStrong, p.glassRimWeak)), shape),
            ) {
                items.forEachIndexed { i, item ->
                    if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = p.separator.copy(alpha = 0.6f))
                    val tint = if (item.destructive) p.red else p.label
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, style = IosType.body, color = tint, modifier = Modifier.weight(1f), maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Icon(item.icon, null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private class AnchorProvider(private val alignEnd: Boolean, private val gap: Int) : PopupPositionProvider {
    // State so the scale animation's origin follows where the menu actually opened.
    var openedAbove by mutableStateOf(false)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gap
        // Keep clear of the floating bottom bar too.
        openedAbove = below + popupContentSize.height > windowSize.height - windowSize.height / 8
        val y = if (openedAbove) (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0) else below
        return IntOffset(x, y)
    }
}
