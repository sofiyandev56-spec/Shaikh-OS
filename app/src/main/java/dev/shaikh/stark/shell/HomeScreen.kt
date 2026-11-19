package dev.shaikh.stark.shell

import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.shaikh.stark.security.SecurityState
import dev.shaikh.stark.security.SecurityState.Level
import dev.shaikh.stark.ui.ArcGauge
import dev.shaikh.stark.ui.ArcReactor
import dev.shaikh.stark.ui.ButtonTone
import dev.shaikh.stark.ui.DecodeText
import dev.shaikh.stark.ui.HudBackdrop
import dev.shaikh.stark.ui.HudButtonShape
import dev.shaikh.stark.ui.Panel
import dev.shaikh.stark.ui.Readout
import dev.shaikh.stark.ui.SectionLabel
import dev.shaikh.stark.ui.Stark
import dev.shaikh.stark.ui.StarkButton
import dev.shaikh.stark.ui.StatusChip
import dev.shaikh.stark.ui.VisorFrame
import dev.shaikh.stark.ui.color
import dev.shaikh.stark.ui.hudLabel
import dev.shaikh.stark.ui.rememberHudClock
import dev.shaikh.stark.ui.rememberTilt
import dev.shaikh.stark.ui.senseRipple
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

/** Holds the reactor's live position without recomposing on every scroll frame. */
private class ReactorAnchor { var center: Offset? = null }

private data class Command(val label: String, val tone: ButtonTone, val action: () -> Unit)

/**
 * The STARK HUD. Every figure on it is live: clock, battery, storage, uplink, uptime,
 * key hardware and each enforced protection.
 *
 * @param intro 0..1 boot sequence progress (1 when finished).
 * @param powerDown 0..1 Lockdown sequence progress.
 */
@Composable
fun HomeScreen(
    state: SecurityState,
    apps: List<StarkApp>?,
    liveApps: Set<String>,
    telemetry: State<Telemetry>,
    reduceMotion: Boolean,
    intro: Float,
    powerDown: Float,
    scanReport: String?,
    onRescan: () -> Unit,
    onOpenApp: (StarkApp) -> Unit,
    onToggleLive: (StarkApp) -> Unit,
    onGetApps: () -> Unit,
    onSecurity: () -> Unit,
    onLock: () -> Unit,
    onLockdown: () -> Unit,
) {
    val clock = rememberHudClock(!reduceMotion)
    val tilt = rememberTilt(!reduceMotion)
    val anchor = remember { ReactorAnchor() }
    var latticeFocus by remember { mutableStateOf<Offset?>(null) }
    var ringCenter by remember { mutableStateOf<Offset?>(null) }

    fun phase(start: Float, length: Float) = ((intro - start) / length).coerceIn(0f, 1f)
    val ignition = phase(0.10f, 0.40f) * (1f - powerDown)
    val hud = phase(0.45f, 0.30f)
    val reveal = phase(0.50f, 0.40f)
    val surge = phase(0.20f, 0.25f) * (1f - phase(0.45f, 0.30f))
    val power = (1f + 0.45f * surge) * (1f - 0.7f * powerDown)
    val tint = lerp(Stark.Cyan, Stark.TextFaint, powerDown)

    val commands = listOf(
        Command("Security", ButtonTone.Quiet, onSecurity),
        Command("Lock", ButtonTone.Primary, onLock),
        Command("Lockdown", ButtonTone.Warning, onLockdown),
        Command("Get apps", ButtonTone.Primary, onGetApps),
    )

    Box(Modifier.fillMaxSize()) {
        HudBackdrop(time = { clock.value }, focus = latticeFocus, tilt = { tilt.value }, accent = tint)

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = fullSpan) {
                    Header(state, telemetry, { clock.value }, hud, reduceMotion, onSecurity)
                }
                item(span = fullSpan) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val reactorSize = min(maxWidth * 0.70f, 280.dp)
                        Box(Modifier.fillMaxWidth().height(reactorSize + 8.dp)) {
                            Box(
                                Modifier
                                    .size(reactorSize)
                                    .align(Alignment.TopCenter)
                                    .onGloballyPositioned { coords ->
                                        val c = coords.boundsInRoot().center
                                        anchor.center = c
                                        if (latticeFocus == null) latticeFocus = c
                                    },
                            ) {
                                ArcReactor(
                                    Modifier.fillMaxSize(),
                                    power = power,
                                    ignition = ignition,
                                    animate = !reduceMotion,
                                    tint = tint,
                                    tilt = { tilt.value },
                                    onTap = onRescan,
                                    onLongPress = { ringCenter = anchor.center },
                                    tapLabel = "Re-scan security",
                                    longPressLabel = "Open command ring",
                                )
                            }
                            val battery = telemetry.value
                            ArcGauge(
                                fraction = battery.batteryPercent?.let { it / 100f },
                                valueText = battery.batteryPercent?.let { "$it%" } ?: "--",
                                label = if (battery.charging) "CHARGING" else "BATTERY",
                                color = if ((battery.batteryPercent ?: 100) <= 15) Stark.Amber else Stark.Cyan,
                                modifier = Modifier.size(84.dp).align(Alignment.BottomStart).graphicsLayer { alpha = hud },
                                reveal = reveal,
                            )
                            ArcGauge(
                                fraction = battery.storageFreeFraction,
                                valueText = formatGb(battery.storageFreeBytes),
                                label = "FREE",
                                color = if ((battery.storageFreeFraction ?: 1f) < 0.1f) Stark.Amber else Stark.Cyan,
                                modifier = Modifier.size(84.dp).align(Alignment.BottomEnd).graphicsLayer { alpha = hud },
                                reveal = reveal,
                            )
                        }
                    }
                }
                item(span = fullSpan) {
                    StatusReadout(state, telemetry, scanReport, powerDown, hud, reduceMotion)
                }
                item(span = fullSpan) { ShieldStrip(state, hud, onSecurity) }
                state.findings().firstOrNull()?.let { top ->
                    item(span = fullSpan) {
                        Briefing(top, state.findings().size, Modifier.graphicsLayer { alpha = hud }, onSecurity)
                    }
                }
                item(span = fullSpan) {
                    SectionLabel(
                        "Applications" + (apps?.let { " · ${it.size}" } ?: ""),
                        Modifier.graphicsLayer { alpha = hud },
                        trailing = {
                            Text(
                                "GET APPS ›",
                                style = hudLabel(),
                                color = Stark.Cyan,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable(role = Role.Button, onClick = onGetApps)
                                    .padding(horizontal = 8.dp, vertical = 14.dp),
                            )
                        },
                    )
                }
                when {
                    apps == null -> item(span = fullSpan) {
                        Text("SCANNING…", style = Readout, color = Stark.TextFaint)
                    }
                    apps.isEmpty() -> item(span = fullSpan) {
                        Panel {
                            Text("No apps in STARK yet", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Install apps from STARK's own Play Store. Each gets its own data and accounts, " +
                                    "separate from your normal phone.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Stark.TextDim,
                            )
                            Spacer(Modifier.height(12.dp))
                            StarkButton("Open Play Store", onGetApps)
                        }
                    }
                    else -> itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        val appear = phase(0.55f + index.coerceAtMost(8) * 0.035f, 0.22f)
                        HoloTile(app, app.packageName in liveApps, appear, onOpenApp, onToggleLive)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = hud }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StarkButton("Security", onSecurity, Modifier.weight(1f), tone = ButtonTone.Quiet)
                StarkButton("Lock", onLock, Modifier.weight(1f))
                StarkButton("Lockdown", onLockdown, Modifier.weight(1f), tone = ButtonTone.Warning)
            }
        }

        VisorFrame(phase(0.35f, 0.35f), color = tint)

        ringCenter?.let { center ->
            CommandRing(center, commands, reduceMotion, onDismiss = { ringCenter = null })
        }
    }
}

@Composable
private fun Header(
    state: SecurityState,
    telemetry: State<Telemetry>,
    time: () -> Float,
    hud: Float,
    reduceMotion: Boolean,
    onSecurity: () -> Unit,
) {
    val findings = state.findings()
    val label = when (state.overall) {
        Level.OK -> "Secured"
        Level.ADVISORY -> if (findings.size == 1) "1 advisory" else "${findings.size} advisories"
        Level.CRITICAL -> "Action needed"
    }
    Column(Modifier.graphicsLayer { alpha = hud }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                DecodeText(
                    "STARK",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, letterSpacing = 8.sp),
                    color = Stark.Text,
                    start = hud > 0f,
                    animate = !reduceMotion,
                )
                Text(
                    "PRIVATE ENVIRONMENT · U${telemetry.value.profileUserId}",
                    style = Readout.copy(fontSize = 9.sp),
                    color = Stark.TextFaint,
                )
            }
            Clock(telemetry)
        }
        Spacer(Modifier.height(14.dp))
        StatusChip(
            label,
            state.overall.color(),
            Modifier
                .padding(horizontal = 20.dp)
                .senseRipple(active = state.overall != Level.OK, color = state.overall.color(), time = time),
            onClick = onSecurity,
        )
    }
}

@Composable
private fun Clock(telemetry: State<Telemetry>) {
    val context = LocalContext.current
    val now = telemetry.value.now
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(now)
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val hm = DateTimeFormatter.ofPattern(pattern).withZone(zone).format(instant)
    val sec = DateTimeFormatter.ofPattern("ss").withZone(zone).format(instant)
    val date = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.getDefault()).withZone(zone).format(instant)
    Column(horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(hm, style = Readout.copy(fontSize = 28.sp, letterSpacing = 1.sp), color = Stark.Text)
            Text(":$sec", style = Readout.copy(fontSize = 12.sp), color = Stark.Cyan, modifier = Modifier.padding(bottom = 5.dp))
        }
        Text(date.uppercase(), style = Readout.copy(fontSize = 9.sp), color = Stark.TextDim)
    }
}

@Composable
private fun StatusReadout(
    state: SecurityState,
    telemetry: State<Telemetry>,
    scanReport: String?,
    powerDown: Float,
    hud: Float,
    reduceMotion: Boolean,
) {
    Column(
        Modifier.fillMaxWidth().graphicsLayer { alpha = hud },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val headline = when {
            powerDown > 0f -> "LOCKDOWN · EVICTING KEYS"
            scanReport != null -> scanReport
            else -> "ENVIRONMENT ONLINE"
        }
        DecodeText(
            headline,
            style = MaterialTheme.typography.labelLarge,
            color = if (powerDown > 0f) Stark.Amber else Stark.Cyan,
            start = hud > 0f,
            animate = !reduceMotion,
        )
        Spacer(Modifier.height(6.dp))
        val t = telemetry.value
        Text(
            "KEYS ${keyLabel(state.keyHardware).uppercase()}  ·  ${t.uplink}  ·  UP ${formatUptime(t.shellUptimeMs)}",
            style = Readout.copy(fontSize = 10.sp),
            color = Stark.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** One pip per enforced protection, coloured by its real state. */
@Composable
private fun ShieldStrip(state: SecurityState, hud: Float, onSecurity: () -> Unit) {
    val hardware = state.keyHardware
    val pips = listOf(
        Triple("PIN", "STARK PIN", if (state.starkLockSet) Level.OK else Level.CRITICAL),
        Triple(
            "KEYS", "Hardware-backed keys",
            when (hardware) {
                SecurityState.KEY_STRONGBOX, SecurityState.KEY_TEE -> Level.OK
                SecurityState.KEY_SOFTWARE -> Level.CRITICAL
                else -> null
            },
        ),
        Triple("CAPT", "Screenshot block", if (state.screenCaptureBlocked) Level.OK else Level.ADVISORY),
        Triple("CLIP", "Copy-out block", if (state.copyPasteBlocked) Level.OK else Level.ADVISORY),
        Triple("LOCK", "Lock-screen redaction", if (state.lockscreenRedacted) Level.OK else Level.ADVISORY),
        Triple("CONT", "Hidden contacts", if (state.contactsHidden) Level.OK else Level.ADVISORY),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = hud }
            .clip(HudButtonShape)
            .clickable(role = Role.Button, onClickLabel = "Open security", onClick = onSecurity)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pips.forEach { (code, name, level) ->
            val color = level?.color() ?: Stark.TextFaint
            Column(
                Modifier.weight(1f).clearAndSetSemantics {
                    contentDescription = "$name: " + when (level) {
                        Level.OK -> "on"
                        null -> "measuring"
                        else -> "off"
                    }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(3.dp).background(color.copy(alpha = if (level == Level.OK) 0.9f else 1f)))
                Spacer(Modifier.height(5.dp))
                Text(code, style = Readout.copy(fontSize = 9.sp), color = color)
            }
        }
    }
}

/** Plain-language briefing on the most important open item. */
@Composable
private fun Briefing(top: SecurityState.Finding, count: Int, modifier: Modifier, onOpen: () -> Unit) {
    val color = top.level.color()
    Panel(modifier.clickable(role = Role.Button, onClickLabel = "Open security", onClick = onOpen), accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("BRIEFING", style = hudLabel(), color = color, modifier = Modifier.weight(1f))
            Text(if (count == 1) "1 ITEM" else "$count ITEMS", style = Readout.copy(fontSize = 9.sp), color = Stark.TextDim)
        }
        Spacer(Modifier.height(8.dp))
        Text(top.title, style = MaterialTheme.typography.titleMedium, color = Stark.Text)
        Spacer(Modifier.height(4.dp))
        Text(top.advice, style = MaterialTheme.typography.bodySmall, color = Stark.TextDim)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoloTile(
    app: StarkApp,
    live: Boolean,
    appear: Float,
    onOpen: (StarkApp) -> Unit,
    onToggleLive: (StarkApp) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, tween(110), label = "tile")
    val view = LocalView.current
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * 24.dp.toPx()
                    scaleX = scale
                    scaleY = scale
                }
                .clip(HudButtonShape)
                .background(Stark.Titanium.copy(alpha = if (pressed) 0.9f else 0.55f))
                .border(
                    1.dp,
                    when {
                        pressed -> Stark.Cyan.copy(alpha = 0.7f)
                        live -> Stark.Amber.copy(alpha = 0.55f)
                        else -> Stark.Line
                    },
                    HudButtonShape,
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Open ${app.label}",
                    onLongClickLabel = if (live) "Pause ${app.label} when STARK locks" else "Keep ${app.label} live while STARK is locked",
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onToggleLive(app)
                    },
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onOpen(app)
                }
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                app.label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Stark.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (live) {
            Text(
                "LIVE",
                style = Readout.copy(fontSize = 8.sp),
                color = Stark.Void,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { alpha = appear }
                    .background(Stark.Amber, HudButtonShape)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

/** Radial command ring around the reactor, opened by a long-press. */
@Composable
private fun CommandRing(center: Offset, commands: List<Command>, reduceMotion: Boolean, onDismiss: () -> Unit) {
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
    BackHandler(onBack = onDismiss)
    val a = appear.value
    Box(
        Modifier
            .fillMaxSize()
            .background(Stark.Void.copy(alpha = 0.78f * a))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Close command ring",
                onClick = onDismiss,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = 140.dp.toPx() * a
            drawCircle(Stark.Cyan.copy(alpha = 0.35f * a), radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(Stark.Cyan.copy(alpha = 0.15f * a), radius * 0.62f, center, style = Stroke(1.dp.toPx()))
            for (i in commands.indices) {
                val deg = Math.toRadians(-90.0 + i * 90.0)
                val dir = Offset(cos(deg).toFloat(), sin(deg).toFloat())
                drawLine(Stark.Cyan.copy(alpha = 0.4f * a), center + dir * radius * 0.62f, center + dir * (radius - 26.dp.toPx()), 1.dp.toPx())
            }
        }
        commands.forEachIndexed { i, command ->
            val deg = Math.toRadians(-90.0 + i * 90.0)
            Box(
                Modifier
                    .offset {
                        val r = 140.dp.toPx() * a
                        IntOffset(
                            (center.x + r * cos(deg).toFloat() - 54.dp.toPx()).roundToInt(),
                            (center.y + r * sin(deg).toFloat() - 24.dp.toPx()).roundToInt(),
                        )
                    }
                    .size(width = 108.dp, height = 48.dp)
                    .graphicsLayer { alpha = a; scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a },
            ) {
                StarkButton(
                    command.label,
                    onClick = {
                        onDismiss()
                        command.action()
                    },
                    modifier = Modifier.fillMaxSize(),
                    tone = command.tone,
                )
            }
        }
    }
}

internal fun keyLabel(level: String?): String = when (level) {
    null -> "hardware check…"
    SecurityState.KEY_STRONGBOX -> "StrongBox"
    SecurityState.KEY_TEE -> "TEE"
    SecurityState.KEY_SOFTWARE -> "software (not hardware-backed)"
    else -> "unknown hardware"
}
