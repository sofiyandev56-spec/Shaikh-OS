package dev.shaikh.stark.shell

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaikh.stark.lock.StarkLock
import dev.shaikh.stark.security.LockoutPolicy
import dev.shaikh.stark.security.PinKdf
import dev.shaikh.stark.ui.ArcReactor
import dev.shaikh.stark.ui.DecodeText
import dev.shaikh.stark.ui.HudBackdrop
import dev.shaikh.stark.ui.HudButtonShape
import dev.shaikh.stark.ui.Readout
import dev.shaikh.stark.ui.Stark
import dev.shaikh.stark.ui.rememberHudClock
import dev.shaikh.stark.ui.rememberTilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * STARK's own lock screen: a HUD keypad where each digit lights a reactor coil.
 * Used both to create the STARK PIN ([creating]) and to unlock.
 */
@Composable
fun LockScreen(
    creating: Boolean,
    reduceMotion: Boolean,
    lockoutRemainingMs: () -> Long,
    verify: (CharArray) -> StarkLock.Attempt,
    create: (CharArray) -> Unit,
    onUnlocked: suspend () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val clock = rememberHudClock(!reduceMotion)
    val tilt = rememberTilt(!reduceMotion)

    val digits = remember { mutableStateListOf<Char>() }
    var firstEntry by remember { mutableStateOf<CharArray?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var lockout by remember { mutableLongStateOf(lockoutRemainingMs()) }
    var shockKey by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }

    LaunchedEffect(lockout > 0) {
        while (lockout > 0) {
            delay(250)
            lockout = lockoutRemainingMs()
        }
    }

    val title = when {
        creating && firstEntry == null -> "CREATE STARK PIN"
        creating -> "CONFIRM STARK PIN"
        success -> "ACCESS GRANTED"
        else -> "STARK LOCKED"
    }
    val tint = when {
        error || lockout > 0 -> Stark.Amber
        else -> Stark.Cyan
    }

    fun fail(text: String) {
        error = true
        message = text
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        scope.launch {
            if (!reduceMotion) {
                for (x in listOf(-14f, 14f, -10f, 10f, -5f, 0f)) shake.animateTo(x, tween(45))
            }
            delay(700)
            error = false
        }
    }

    suspend fun succeed() {
        success = true
        message = null
        shockKey++
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!reduceMotion) delay(700)
        onUnlocked()
    }

    fun submit() {
        if (digits.size < PinKdf.MIN_LENGTH || busy || lockout > 0 || success) return
        val pin = digits.toCharArray()
        digits.clear()
        busy = true
        scope.launch {
            try {
                if (creating) {
                    val first = firstEntry
                    if (first == null) {
                        firstEntry = pin.copyOf()
                        message = null
                        return@launch
                    }
                    val same = first.contentEquals(pin)
                    first.fill('0')
                    firstEntry = null
                    if (!same) {
                        fail("PINS DIDN'T MATCH · START AGAIN")
                        return@launch
                    }
                    message = "SEALING PIN IN SECURE HARDWARE…"
                    withContext(Dispatchers.Default) { create(pin) }
                    succeed()
                } else {
                    message = "VERIFYING…"
                    when (val result = withContext(Dispatchers.Default) { verify(pin) }) {
                        StarkLock.Attempt.Success -> succeed()
                        is StarkLock.Attempt.Wrong -> {
                            if (result.lockoutMs > 0) lockout = result.lockoutMs
                            val free = LockoutPolicy.FREE_ATTEMPTS - result.failures
                            fail(
                                when {
                                    result.lockoutMs > 0 -> "INCORRECT"
                                    free <= 0 -> "INCORRECT · NEXT MISS STARTS A DELAY"
                                    else -> "INCORRECT · $free BEFORE A DELAY"
                                },
                            )
                        }
                        is StarkLock.Attempt.LockedOut -> {
                            lockout = result.remainingMs
                            message = null
                        }
                    }
                }
            } finally {
                pin.fill('0')
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HudBackdrop(time = { clock.value }, tilt = { tilt.value }, accent = tint)
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            DecodeText(
                title,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, letterSpacing = 5.sp),
                color = if (success) Stark.Cyan else Stark.Text,
                animate = !reduceMotion,
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ArcReactor(
                    Modifier.widthIn(max = 260.dp).fillMaxWidth(0.72f).aspectRatio(1f),
                    power = when {
                        success -> 1.5f
                        busy -> 1.25f
                        else -> 0.9f
                    },
                    speed = if (busy) 6f else 1f,
                    ignition = if (success) 1f else 0.05f + (digits.size / 10f).coerceAtMost(0.95f),
                    animate = !reduceMotion,
                    tint = tint,
                    tilt = { tilt.value },
                    shockKey = shockKey,
                )
            }
            PinSlots(digits.size, Modifier.graphicsLayer { translationX = shake.value * density })
            Spacer(Modifier.height(10.dp))
            val status = when {
                lockout > 0 -> "LOCKED OUT · " + formatClock(lockout)
                message != null -> message!!
                creating && firstEntry == null -> "${PinKdf.MIN_LENGTH}–${PinKdf.MAX_LENGTH} DIGITS · NOT YOUR PHONE PIN"
                creating -> "ENTER THE SAME PIN AGAIN"
                else -> "ENTER STARK PIN"
            }
            Text(
                status,
                style = Readout.copy(fontSize = 11.sp),
                color = if (error || lockout > 0) Stark.Amber else Stark.TextDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(16.dp))
            Keypad(
                enabled = !busy && lockout == 0L && !success,
                canSubmit = digits.size >= PinKdf.MIN_LENGTH,
                onDigit = { d ->
                    if (digits.size < PinKdf.MAX_LENGTH) {
                        digits.add(d)
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                },
                onDelete = { if (digits.isNotEmpty()) digits.removeAt(digits.lastIndex) },
                onSubmit = ::submit,
            )
            Spacer(Modifier.height(12.dp))
            if (!creating) {
                Text(
                    "No recovery code yet: a forgotten STARK PIN means removing STARK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Stark.TextFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PinSlots(filled: Int, modifier: Modifier = Modifier) {
    val slots = maxOf(PinKdf.MIN_LENGTH, filled)
    Row(
        modifier.clearAndSetSemantics { contentDescription = "$filled digits entered" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(slots) { i ->
            val on = i < filled
            val glow by animateFloatAsState(if (on) 1f else 0f, tween(140), label = "slot")
            Box(
                Modifier
                    .size(width = 18.dp, height = 6.dp)
                    .background(Stark.Cyan.copy(alpha = 0.15f + 0.85f * glow), HudButtonShape),
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    canSubmit: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit,
) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DEL", "0", "OK"))
    Column(Modifier.widthIn(max = 360.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val isOk = key == "OK"
                    Key(
                        label = key,
                        description = when (key) {
                            "DEL" -> "Delete"
                            "OK" -> "Enter"
                            else -> key
                        },
                        enabled = enabled && (!isOk || canSubmit),
                        accent = isOk,
                        modifier = Modifier.weight(1f),
                    ) {
                        when (key) {
                            "DEL" -> onDelete()
                            "OK" -> onSubmit()
                            else -> onDigit(key.single())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    label: String,
    description: String,
    enabled: Boolean,
    accent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(90), label = "key")
    val color = when {
        !enabled -> Stark.TextFaint
        accent -> Stark.Cyan
        else -> Stark.Text
    }
    Box(
        modifier
            .height(58.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                if (pressed) Stark.Cyan.copy(alpha = 0.18f) else Stark.Titanium.copy(alpha = if (accent) 0.9f else 0.6f),
                HudButtonShape,
            )
            .border(1.dp, if (pressed || accent) Stark.Cyan.copy(alpha = 0.7f) else Stark.Line, HudButtonShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Readout.copy(fontSize = if (label.length > 1) 13.sp else 22.sp, letterSpacing = 1.sp),
            color = color,
        )
    }
}

private fun formatClock(ms: Long): String {
    val s = (ms + 999) / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
