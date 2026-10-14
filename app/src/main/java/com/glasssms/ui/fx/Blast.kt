package com.glasssms.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * 8-bit "video game" explosion: a flash, chunky pixel sparks that cool from white to red and fall
 * under gravity, then grey pixel smoke drifting up. Pure draw-phase animation, so it costs nothing
 * once it has finished.
 */
private const val DURATION_S = 1.1f

internal class Particle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val size: Float,
    val delay: Float,
    val life: Float,
    val smoke: Boolean,
)

internal class Blast(
    val id: Long,
    val bounds: Rect,
    val particles: List<Particle>,
    val grid: Float,
    val gravity: Float,
) {
    val progress = Animatable(0f)
}

@Stable
class BlastState internal constructor() {
    internal val blasts = mutableStateListOf<Blast>()
    private var nextId = 0L

    /** Blows up [bounds] (in the coordinate space of the [BlastLayer]). [density] is pixels per dp. */
    fun explode(bounds: Rect, density: Float) {
        val rnd = Random(System.nanoTime())
        fun r(min: Float, max: Float) = min + rnd.nextFloat() * (max - min)
        val dp = density
        val cy = bounds.center.y
        val centres = listOf(0.25f, 0.5f, 0.75f).map { bounds.left + bounds.width * it }

        val sparks = List(42) {
            val angle = r(0f, (2 * PI).toFloat())
            val speed = r(180f, 760f) * dp
            Particle(
                x = centres[it % centres.size] + r(-24f, 24f) * dp,
                y = cy + r(-0.2f, 0.2f) * bounds.height,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 260f * dp, // bias upwards, like debris thrown clear
                size = r(5f, 11f) * dp,
                delay = r(0f, 0.08f),
                life = r(0.45f, 0.85f),
                smoke = false,
            )
        }
        val smoke = List(14) {
            Particle(
                x = r(bounds.left, bounds.right),
                y = cy + r(-0.25f, 0.25f) * bounds.height,
                vx = r(-30f, 30f) * dp,
                vy = -r(40f, 110f) * dp,
                size = r(14f, 26f) * dp,
                delay = r(0.08f, 0.3f),
                life = r(0.55f, 0.8f),
                smoke = true,
            )
        }
        blasts += Blast(nextId++, bounds, smoke + sparks, grid = 3f * dp, gravity = 1400f * dp)
    }
}

@Composable
fun rememberBlastState(): BlastState = remember { BlastState() }

@Composable
fun BlastLayer(state: BlastState, modifier: Modifier = Modifier) {
    state.blasts.toList().forEach { b ->
        key(b.id) {
            LaunchedEffect(Unit) {
                b.progress.animateTo(1f, tween((DURATION_S * 1000).toInt(), easing = LinearEasing))
                state.blasts.remove(b)
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        state.blasts.forEach { drawBlast(it) }
    }
}

private val FLASH = Color(0xFFFFF3B0)
private val SPARK_COLOURS = listOf(Color.White, Color(0xFFFFE14D), Color(0xFFFF8C1A), Color(0xFFE83A1E))
private val SMOKE = Color(0xFF8E8E93)

private fun DrawScope.drawBlast(b: Blast) {
    val t = b.progress.value
    val seconds = t * DURATION_S

    if (t < 0.12f) {
        drawRect(FLASH.copy(alpha = (1f - t / 0.12f) * 0.7f), topLeft = b.bounds.topLeft, size = b.bounds.size)
    }

    for (p in b.particles) {
        val s = seconds - p.delay
        if (s < 0f) continue
        val local = s / p.life
        if (local >= 1f) continue

        val x: Float
        val y: Float
        val size: Float
        val color: Color
        if (p.smoke) {
            x = p.x + p.vx * s
            y = p.y + p.vy * s
            size = p.size * (1f + 1.5f * local)
            color = SMOKE.copy(alpha = 0.55f * (1f - local))
        } else {
            x = p.x + p.vx * s
            y = p.y + p.vy * s + 0.5f * b.gravity * s * s
            size = p.size * (1f - 0.5f * local)
            val heat = SPARK_COLOURS[(local * SPARK_COLOURS.size).toInt().coerceAtMost(SPARK_COLOURS.lastIndex)]
            color = heat.copy(alpha = 1f - local * local)
        }
        // Snap to a coarse grid so everything moves in chunky pixel steps.
        val gx = floor(x / b.grid) * b.grid
        val gy = floor(y / b.grid) * b.grid
        val gs = (floor(size / b.grid) * b.grid).coerceAtLeast(b.grid)
        drawRect(color, topLeft = Offset(gx - gs / 2, gy - gs / 2), size = Size(gs, gs))
    }
}
