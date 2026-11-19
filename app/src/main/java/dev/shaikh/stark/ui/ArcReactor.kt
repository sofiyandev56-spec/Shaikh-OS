package dev.shaikh.stark.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val COILS = 10

/**
 * STARK's reactor: the anchor of every screen. Drawn procedurally (no copied assets).
 *
 * @param power overall energy. 1 is idle, up to 1.6 is a surge, below 1 dims it.
 * @param ignition 0..1 fraction of the ten coils that are lit (boot and power-down sequences).
 * @param tilt device tilt in -1..1, read in the draw phase only; layers shift by depth.
 * @param onTap / [onLongPress] make the reactor interactive. A tap also fires a shockwave.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArcReactor(
    modifier: Modifier = Modifier,
    power: Float = 1f,
    speed: Float = 1f,
    ignition: Float = 1f,
    animate: Boolean = true,
    tint: Color = Stark.Cyan,
    tilt: () -> Offset = { Offset.Zero },
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    tapLabel: String? = null,
    longPressLabel: String? = null,
    shockKey: Int = 0,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    var time by remember { mutableFloatStateOf(0f) }
    val currentSpeed by rememberUpdatedState(speed)
    val shock = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val particles = remember { List(22) { Particle.seeded(it) } }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    angle = (angle + dt * 14f * currentSpeed) % 360f
                    time += dt
                }
                last = now
            }
        }
    }

    LaunchedEffect(shockKey) {
        if (shockKey > 0 && animate) {
            shock.snapTo(0f)
            shock.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    val interactive = onTap != null || onLongPress != null
    val touch = if (!interactive) Modifier else Modifier.combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClickLabel = tapLabel,
        onLongClickLabel = longPressLabel,
        onLongClick = onLongPress?.let { action ->
            {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                action()
            }
        },
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (animate) scope.launch {
                shock.snapTo(0f)
                shock.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            onTap?.invoke()
        },
    )

    Canvas(modifier.then(touch)) {
        val pulse = if (animate) 0.86f + 0.14f * sin(time * 1.6f) else 1f
        drawReactor(
            angle = angle,
            time = time,
            pulse = pulse,
            power = power.coerceIn(0f, 1.6f),
            ignition = ignition.coerceIn(0f, 1f),
            tint = tint,
            tilt = tilt(),
            shock = shock.value,
            particles = particles,
        )
    }
}

private class Particle(val radius: Float, val speed: Float, val phase: Float, val size: Float, val alpha: Float) {
    companion object {
        fun seeded(i: Int): Particle {
            val r = Random(i * 7919 + 17)
            return Particle(
                radius = 0.60f + r.nextFloat() * 0.36f,
                speed = (0.3f + r.nextFloat() * 1.1f) * if (i % 3 == 0) -1f else 1f,
                phase = r.nextFloat() * 360f,
                size = 0.005f + r.nextFloat() * 0.009f,
                alpha = 0.25f + r.nextFloat() * 0.6f,
            )
        }
    }
}

private val TiDark = Color(0xFF141A20)
private val TiMid = Color(0xFF2B343E)
private val TiLight = Color(0xFF6B7A88)

private fun DrawScope.drawReactor(
    angle: Float,
    time: Float,
    pulse: Float,
    power: Float,
    ignition: Float,
    tint: Color,
    tilt: Offset,
    shock: Float,
    particles: List<Particle>,
) {
    val r = size.minDimension / 2f
    val c = center
    val p = power.coerceAtMost(1f)
    val flash = (1f - shock) * 0.6f
    val surge = (power - 1f).coerceAtLeast(0f) + flash
    val energy = p * (0.25f + 0.75f * ignition)
    val depth = r * 0.06f
    fun layer(z: Float, block: DrawScope.() -> Unit) =
        translate(tilt.x * depth * z, tilt.y * depth * z) { block() }

    // Bloom: additive light spilling past the bezel.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(tint.copy(alpha = (0.30f + surge * 0.35f) * pulse * energy), Color.Transparent),
            center = c,
            radius = r * 1.15f,
        ),
        radius = r * 1.15f,
        center = c,
        blendMode = BlendMode.Plus,
    )

    // Machined titanium bezel with bolts (deepest layer).
    layer(-0.4f) {
        drawCircle(
            brush = Brush.sweepGradient(listOf(TiDark, TiLight, TiMid, TiDark, TiLight, TiDark), c),
            radius = r * 0.95f,
            center = c,
            style = Stroke(r * 0.05f),
        )
        drawCircle(Color.Black.copy(alpha = 0.6f), r * 0.925f, c, style = Stroke(r * 0.006f))
        drawCircle(TiLight.copy(alpha = 0.5f), r * 0.975f, c, style = Stroke(r * 0.004f))
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 + 15.0)
            val bolt = Offset(c.x + r * 0.95f * cos(a).toFloat(), c.y + r * 0.95f * sin(a).toFloat())
            drawCircle(TiDark, r * 0.014f, bolt)
            drawCircle(TiLight.copy(alpha = 0.7f), r * 0.006f, bolt + Offset(-r * 0.003f, -r * 0.003f))
        }
    }

    // Instrument ticks + scanner sweep.
    layer(-0.2f) {
        rotate(angle * 0.25f, c) {
            for (i in 0 until 72) {
                val major = i % 6 == 0
                val a = Math.toRadians(i * 5.0)
                val outer = r * 0.90f
                val inner = r * if (major) 0.845f else 0.87f
                drawLine(
                    color = tint.copy(alpha = (if (major) 0.6f else 0.25f) * energy),
                    start = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                    end = Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                    strokeWidth = if (major) r * 0.011f else r * 0.005f,
                )
            }
        }
        rotate(angle * 3f, c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.10f to tint.copy(alpha = 0.55f * energy),
                    0.105f to Color.Transparent,
                    1f to Color.Transparent,
                    center = c,
                ),
                radius = r * 0.873f,
                center = c,
                style = Stroke(r * 0.05f),
            )
        }
    }

    // Data arcs: HUD-style partial rings at different speeds, amber accents counter-rotating.
    layer(-0.1f) {
        rotate(angle, c) {
            drawCircle(
                tint.copy(alpha = 0.40f * energy),
                r * 0.80f,
                c,
                style = Stroke(r * 0.010f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.09f, r * 0.045f))),
            )
        }
        arcs(c, r * 0.775f, -angle * 0.7f, listOf(10f to 70f, 130f to 40f, 220f to 95f), tint.copy(alpha = 0.5f * energy), r * 0.006f)
        arcs(c, r * 0.745f, -angle * 1.6f, listOf(20f to 38f, 200f to 38f), Stark.Amber.copy(alpha = 0.85f * energy), r * 0.016f)
    }

    // Coil housing and the ten wound coils.
    layer(0f) {
        drawCircle(TiDark, r * 0.585f, c, style = Stroke(r * 0.24f))
        drawCircle(TiMid.copy(alpha = 0.8f), r * 0.705f, c, style = Stroke(r * 0.006f))
        rotate(-angle * 0.5f, c) {
            for (i in 0 until COILS) {
                val lit = (ignition * COILS - i).coerceIn(0f, 1f)
                val flicker = 0.9f + 0.1f * sin(time * 7f + i * 1.3f)
                drawCoil(c, r, i * 36f + 18f, tint, lit * p * (0.62f + 0.38f * pulse) * flicker)
            }
        }
        drawCircle(tint.copy(alpha = 0.55f * energy), r * 0.445f, c, style = Stroke(r * 0.008f))
        rotate(angle * 0.8f, c) {
            for (i in 0 until 30) {
                val a = Math.toRadians(i * 12.0)
                drawLine(
                    tint.copy(alpha = 0.35f * energy),
                    Offset(c.x + r * 0.395f * cos(a).toFloat(), c.y + r * 0.395f * sin(a).toFloat()),
                    Offset(c.x + r * 0.425f * cos(a).toFloat(), c.y + r * 0.425f * sin(a).toFloat()),
                    strokeWidth = r * 0.005f,
                )
            }
        }
    }

    // Glass lens core (nearest layer).
    layer(0.5f) {
        val coreR = r * (0.30f + 0.05f * surge)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    Stark.Core.copy(alpha = energy),
                    tint.copy(alpha = 0.92f * energy * pulse),
                    tint.copy(alpha = 0f),
                ),
                center = c,
                radius = coreR,
            ),
            radius = coreR,
            center = c,
            blendMode = BlendMode.Plus,
        )
        drawCircle(Stark.Void.copy(alpha = 0.45f), r * 0.17f, c)
        drawCircle(Stark.Core.copy(alpha = 0.85f * energy), r * 0.17f, c, style = Stroke(r * 0.012f))
        drawCircle(tint.copy(alpha = 0.6f * energy), r * 0.12f, c, style = Stroke(r * 0.006f))
        drawCircle(Color.White.copy(alpha = (0.75f + 0.25f * pulse) * energy), r * 0.065f, c)
        drawOval(
            Color.White.copy(alpha = 0.22f * p),
            topLeft = Offset(c.x - r * 0.15f, c.y - r * 0.21f),
            size = Size(r * 0.13f, r * 0.06f),
        )
    }

    // Energy motes orbiting above everything.
    layer(0.8f) {
        for (pt in particles) {
            val a = Math.toRadians((pt.phase + angle * 2.2f * pt.speed).toDouble())
            val pos = Offset(c.x + r * pt.radius * cos(a).toFloat(), c.y + r * pt.radius * sin(a).toFloat())
            drawCircle(Stark.Core.copy(alpha = pt.alpha * energy * pulse), r * pt.size, pos, blendMode = BlendMode.Plus)
        }
    }

    // Shockwave from a tap.
    if (shock < 1f) {
        val k = shock
        drawCircle(
            tint.copy(alpha = (1f - k) * 0.8f),
            radius = r * (0.35f + 1.3f * k),
            center = c,
            style = Stroke(r * (0.035f * (1f - k) + 0.004f)),
            blendMode = BlendMode.Plus,
        )
        drawCircle(
            Stark.Amber.copy(alpha = (1f - k) * 0.35f),
            radius = r * (0.30f + 1.0f * k),
            center = c,
            style = Stroke(r * 0.006f),
        )
    }
}

private fun DrawScope.arcs(c: Offset, radius: Float, rotation: Float, spans: List<Pair<Float, Float>>, color: Color, width: Float) {
    rotate(rotation, c) {
        for ((start, sweep) in spans) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(c.x - radius, c.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
    }
}

/** One coil: a wedge of light with dark winding lines across it, like wire wrapped round a ring. */
private fun DrawScope.drawCoil(c: Offset, r: Float, centerDeg: Float, tint: Color, light: Float) {
    val rIn = r * 0.48f
    val rOut = r * 0.69f
    val halfOut = 14f
    val halfIn = 11.5f
    val path = Path().apply {
        arcTo(Rect(c, rOut), centerDeg - halfOut, halfOut * 2, true)
        val endIn = Math.toRadians((centerDeg + halfIn).toDouble())
        lineTo(c.x + rIn * cos(endIn).toFloat(), c.y + rIn * sin(endIn).toFloat())
        arcTo(Rect(c, rIn), centerDeg + halfIn, -halfIn * 2, false)
        close()
    }
    drawPath(path, TiMid)
    if (light > 0.01f) {
        drawPath(
            path,
            Brush.radialGradient(
                0.66f to Stark.Core.copy(alpha = light),
                0.80f to tint.copy(alpha = light),
                1f to tint.copy(alpha = light * 0.35f),
                center = c,
                radius = rOut,
            ),
            blendMode = BlendMode.Plus,
        )
    }
    // Windings.
    for (k in -2..2) {
        val a = Math.toRadians((centerDeg + k * 4.6f).toDouble())
        drawLine(
            Color.Black.copy(alpha = 0.45f),
            Offset(c.x + rIn * 1.02f * cos(a).toFloat(), c.y + rIn * 1.02f * sin(a).toFloat()),
            Offset(c.x + rOut * 0.98f * cos(a).toFloat(), c.y + rOut * 0.98f * sin(a).toFloat()),
            strokeWidth = r * 0.006f,
        )
    }
    drawPath(path, tint.copy(alpha = 0.25f + 0.5f * light), style = Stroke(r * 0.004f))
}
