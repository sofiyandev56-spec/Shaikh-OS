package dev.shaikh.stark.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Chamfered corners: the HUD's panel silhouette. */
val HudPanelShape = CutCornerShape(topStart = 14.dp, bottomEnd = 14.dp)
val HudButtonShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)

val Readout = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.5.sp)

/**
 * One frame clock (seconds) per screen. Read it only inside draw lambdas so the screen
 * redraws without recomposing. Compose pauses it automatically while the app is in the background.
 */
@Composable
fun rememberHudClock(animate: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return time
}

/**
 * Device tilt in -1..1 for parallax, smoothed. The sensor is only registered while the
 * screen is resumed, and not at all when motion is reduced.
 */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    if (enabled) {
        LifecycleResumeEffect(Unit) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var fx = 0f
            var fy = 0f
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = (-event.values[0] / 6f).coerceIn(-1f, 1f)
                    // Neutral when the phone is held at a natural reading angle (~45°).
                    val y = ((event.values[1] - 6.9f) / 4f).coerceIn(-1f, 1f)
                    fx += (x - fx) * 0.08f
                    fy += (y - fy) * 0.08f
                    tilt.value = Offset(fx, fy)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onPauseOrDispose { sm.unregisterListener(listener) }
        }
    }
    return tilt
}

/**
 * The environment behind every STARK screen: a dot grid, a web lattice radiating from the
 * reactor (a nod to the web-slinger's targeting grid), a slow scan band and a vignette.
 */
@Composable
fun HudBackdrop(
    modifier: Modifier = Modifier,
    time: () -> Float,
    focus: Offset? = null,
    tilt: () -> Offset = { Offset.Zero },
    accent: Color = Stark.Cyan,
) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val step = 22.dp.toPx()
                val canvasW = size.width
                val canvasH = size.height
                val dots = buildList {
                    var y = step / 2
                    while (y < canvasH) {
                        var x = step / 2
                        while (x < canvasW) {
                            add(Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }
                val origin = focus ?: Offset(size.width / 2, size.height * 0.32f)
                val reach = hypot(size.width, size.height)
                val lattice = Path().apply {
                    val spokes = 16
                    for (i in 0 until spokes) {
                        val a = i * (2 * Math.PI / spokes)
                        moveTo(origin.x, origin.y)
                        lineTo(origin.x + reach * cos(a).toFloat(), origin.y + reach * sin(a).toFloat())
                    }
                    var ring = 64.dp.toPx()
                    while (ring < reach) {
                        for (i in 0..spokes) {
                            val a = i * (2 * Math.PI / spokes)
                            val pt = Offset(origin.x + ring * cos(a).toFloat(), origin.y + ring * sin(a).toFloat())
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                        ring += 64.dp.toPx()
                    }
                }
                val band = 140.dp.toPx()
                val vignette = Brush.radialGradient(
                    listOf(Color.Transparent, Stark.Void.copy(alpha = 0.92f)),
                    center = origin,
                    radius = reach * 0.62f,
                )
                onDrawBehind {
                    drawRect(Stark.Void)
                    val t = tilt()
                    val shift = Offset(-t.x * 10.dp.toPx(), -t.y * 10.dp.toPx())
                    drawPoints(dots.map { it + shift }, PointMode.Points, accent.copy(alpha = 0.10f), 1.6.dp.toPx(), StrokeCap.Round)
                    drawPath(lattice, accent.copy(alpha = 0.05f), style = Stroke(1.dp.toPx()))
                    val y = ((time() % 9f) / 9f) * (size.height + band) - band
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, accent.copy(alpha = 0.05f), Color.Transparent),
                            startY = y,
                            endY = y + band,
                        ),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, band),
                    )
                    drawRect(vignette)
                }
            },
    )
}

/** Corner brackets that frame the screen like a visor; they draw in with [progress]. */
@Composable
fun VisorFrame(progress: Float, modifier: Modifier = Modifier, color: Color = Stark.Cyan) {
    if (progress <= 0f) return
    Canvas(modifier.fillMaxSize()) {
        val inset = 10.dp.toPx()
        val leg = 28.dp.toPx() * progress.coerceIn(0f, 1f)
        val w = 1.5.dp.toPx()
        val col = color.copy(alpha = 0.55f * progress.coerceIn(0f, 1f))
        val corners = listOf(
            Offset(inset, inset) to Offset(1f, 1f),
            Offset(size.width - inset, inset) to Offset(-1f, 1f),
            Offset(inset, size.height - inset) to Offset(1f, -1f),
            Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f),
        )
        for ((p, d) in corners) {
            drawLine(col, p, p + Offset(leg * d.x, 0f), w)
            drawLine(col, p, p + Offset(0f, leg * d.y), w)
        }
        val mid = size.width / 2
        drawLine(col, Offset(mid - leg, inset), Offset(mid + leg, inset), w)
        for (k in listOf(-1f, 1f)) {
            drawLine(col.copy(alpha = col.alpha * 0.6f), Offset(mid + k * (leg + 6.dp.toPx()), inset), Offset(mid + k * (leg + 10.dp.toPx()), inset), w)
        }
    }
}

/**
 * Two titanium plates that close over the screen like a helmet faceplate.
 * 0 = fully open (invisible), 1 = sealed. Used for entering, locking and booting.
 */
@Composable
fun Shutter(closed: Float, modifier: Modifier = Modifier) {
    if (closed <= 0.001f) return
    Canvas(modifier.fillMaxSize()) {
        val c = closed.coerceIn(0f, 1f)
        val h = size.height / 2f * c
        val notch = 14.dp.toPx() * c
        val w = size.width
        val top = Path().apply {
            moveTo(0f, 0f); lineTo(w, 0f); lineTo(w, h)
            lineTo(w * 0.64f, h); lineTo(w * 0.58f, h + notch); lineTo(w * 0.42f, h + notch); lineTo(w * 0.36f, h)
            lineTo(0f, h); close()
        }
        val bottomEdge = size.height - h
        val bottom = Path().apply {
            moveTo(0f, size.height); lineTo(w, size.height); lineTo(w, bottomEdge)
            lineTo(w * 0.64f, bottomEdge); lineTo(w * 0.58f, bottomEdge + notch); lineTo(w * 0.42f, bottomEdge + notch); lineTo(w * 0.36f, bottomEdge)
            lineTo(0f, bottomEdge); close()
        }
        val plate = Brush.verticalGradient(listOf(Color(0xFF1C232B), Color(0xFF0C1014), Color(0xFF1C232B)))
        drawPath(top, plate)
        drawPath(bottom, plate)
        val seam = Stark.Cyan.copy(alpha = 0.85f * c)
        drawPath(top, seam, style = Stroke(1.5.dp.toPx()))
        drawPath(bottom, seam, style = Stroke(1.5.dp.toPx()))
        // Panel lines.
        val line = Color.White.copy(alpha = 0.05f)
        for (f in listOf(0.35f, 0.7f)) {
            drawLine(line, Offset(0f, h * f), Offset(w, h * f), 1.dp.toPx())
            drawLine(line, Offset(0f, size.height - h * f), Offset(w, size.height - h * f), 1.dp.toPx())
        }
        if (c > 0.85f) {
            val glow = (c - 0.85f) / 0.15f
            drawCircle(
                Brush.radialGradient(listOf(Stark.Cyan.copy(alpha = 0.7f * glow), Color.Transparent), center = center, radius = 60.dp.toPx()),
                radius = 60.dp.toPx(),
                center = center,
            )
        }
    }
}

private const val GLYPHS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789#%/<>+"

/**
 * Text that resolves from cipher glyphs left to right. Screen readers always get the final text.
 */
@Composable
fun DecodeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    start: Boolean = true,
    animate: Boolean = true,
    durationMillis: Int = 650,
) {
    var shown by remember(text) { mutableStateOf(if (animate) scramble(text, 0f) else text) }
    LaunchedEffect(text, start, animate) {
        if (!animate) {
            shown = text
            return@LaunchedEffect
        }
        if (!start) return@LaunchedEffect
        val t0 = withFrameMillis { it }
        while (true) {
            val progress = (withFrameMillis { it } - t0) / durationMillis.toFloat()
            if (progress >= 1f) {
                shown = text
                break
            }
            shown = scramble(text, progress)
        }
    }
    Text(shown, modifier.clearAndSetSemantics { contentDescription = text }, style = style, color = color)
}

private fun scramble(text: String, progress: Float): String {
    val resolved = (text.length * progress).toInt()
    val seed = (progress * 37).toInt()
    return buildString {
        text.forEachIndexed { i, ch ->
            append(if (i < resolved || !ch.isLetterOrDigit()) ch else GLYPHS[(i * 7 + seed) % GLYPHS.length])
        }
    }
}

/** 270° instrument gauge with ticks. [fraction] null means "no reading" and draws only the track. */
@Composable
fun ArcGauge(
    fraction: Float?,
    valueText: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    reveal: Float = 1f,
) {
    val animated by animateFloatAsState((fraction ?: 0f) * reveal.coerceIn(0f, 1f), tween(700), label = "gauge")
    Box(
        modifier.aspectRatio(1f).clearAndSetSemantics { contentDescription = "$label $valueText" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.055f
            val inset = stroke * 1.6f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val tl = Offset(inset, inset)
            val sweep = 270f * reveal.coerceIn(0f, 1f)
            drawArc(Stark.Line, 135f, sweep, false, tl, arcSize, style = Stroke(stroke * 0.45f))
            val r = size.minDimension / 2
            for (i in 0..27) {
                val deg = 135f + i * 10f
                if (deg > 135f + sweep) break
                val a = Math.toRadians(deg.toDouble())
                val major = i % 9 == 0
                drawLine(
                    Stark.TextFaint.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(center.x + (r - inset * 0.25f) * cos(a).toFloat(), center.y + (r - inset * 0.25f) * sin(a).toFloat()),
                    Offset(center.x + (r - inset * (if (major) 0.9f else 0.6f)) * cos(a).toFloat(), center.y + (r - inset * (if (major) 0.9f else 0.6f)) * sin(a).toFloat()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (fraction != null && animated > 0f) {
                drawArc(color, 135f, 270f * animated, false, tl, arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                val end = Math.toRadians((135f + 270f * animated).toDouble())
                val rr = arcSize.width / 2
                drawCircle(color, stroke * 0.7f, Offset(center.x + rr * cos(end).toFloat(), center.y + rr * sin(end).toFloat()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(valueText, style = Readout.copy(fontSize = 15.sp, letterSpacing = 0.5.sp), color = Stark.Text)
            Spacer(Modifier.aspectRatio(8f))
            Text(label, style = Readout.copy(fontSize = 9.sp), color = Stark.TextDim)
        }
    }
}

/**
 * "Sense" tingle: jagged strokes that pulse out from both sides of an element when something
 * needs attention. Inspired by the web-slinger's danger sense; drawn only while [active].
 */
fun Modifier.senseRipple(active: Boolean, color: Color, time: () -> Float): Modifier =
    if (!active) this else drawBehind {
        val phase = (time() % 1.4f) / 1.4f
        val alpha = (1f - phase) * 0.85f
        val len = 6.dp.toPx() + phase * 10.dp.toPx()
        val gap = 4.dp.toPx() + phase * 6.dp.toPx()
        val zig = 3.dp.toPx()
        for (side in listOf(-1f, 1f)) {
            val x0 = if (side < 0) -gap else size.width + gap
            for (k in -1..1) {
                val y0 = size.height / 2 + k * size.height * 0.42f
                val dy = k * len * 0.45f
                val path = Path().apply {
                    moveTo(x0, y0)
                    lineTo(x0 + side * len * 0.33f, y0 + dy * 0.33f - zig)
                    lineTo(x0 + side * len * 0.66f, y0 + dy * 0.66f + zig)
                    lineTo(x0 + side * len, y0 + dy)
                }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }

@Composable
fun hudLabel(): TextStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
