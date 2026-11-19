package dev.shaikh.stark.shell

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.shaikh.stark.admin.StarkPolicy
import dev.shaikh.stark.lock.StarkLock
import dev.shaikh.stark.security.KeyHardware
import dev.shaikh.stark.security.SecurityState.Level
import dev.shaikh.stark.ui.ButtonTone
import dev.shaikh.stark.ui.Panel
import dev.shaikh.stark.ui.Shutter
import dev.shaikh.stark.ui.Stark
import dev.shaikh.stark.ui.StarkButton
import dev.shaikh.stark.ui.hudLabel
import dev.shaikh.stark.ui.rememberReduceMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ShellScreen { CreatePin, Locked, Home, Security }

/** The STARK environment itself. Only ever composed inside the STARK work profile. */
@Composable
fun ShellRoot(resumeTick: Int, onLocked: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val policy = remember { StarkPolicy(context) }
    val lock = remember { StarkLock(context) }
    val reduceMotion = rememberReduceMotion()
    val telemetry = rememberTelemetry()

    var keyHardware by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(policy.readState(null, lock)) }
    var apps by remember { mutableStateOf<List<StarkApp>?>(null) }
    var liveApps by remember { mutableStateOf(lock.liveApps) }
    var configured by remember { mutableStateOf(lock.isConfigured) }
    var locked by remember { mutableStateOf(lock.isLocked) }
    var showSecurity by rememberSaveable { mutableStateOf(false) }
    var booted by rememberSaveable { mutableStateOf(reduceMotion) }
    var confirmLockdown by remember { mutableStateOf(false) }
    var scanReport by remember { mutableStateOf<String?>(null) }
    var scanSeq by remember { mutableIntStateOf(0) }

    val intro = remember { Animatable(if (booted) 1f else 0f) }
    val shutter = remember { Animatable(0f) }
    val powerDown = remember { Animatable(0f) }

    fun refresh() {
        state = policy.readState(keyHardware, lock)
        liveApps = lock.liveApps
    }

    LaunchedEffect(Unit) {
        keyHardware = withContext(Dispatchers.Default) {
            runCatching { KeyHardware.measure() }.getOrDefault("UNKNOWN")
        }
    }
    LaunchedEffect(Unit) {
        if (intro.value < 1f) {
            intro.animateTo(1f, tween(1800, easing = LinearEasing))
            booted = true
        }
    }
    LaunchedEffect(resumeTick, keyHardware) {
        configured = lock.isConfigured
        // The sentinel may have locked STARK while it was in the background.
        locked = lock.isLocked
        refresh()
    }
    LaunchedEffect(resumeTick) {
        apps = withContext(Dispatchers.IO) { AppCatalog.load(context) }
        if (shutter.value > 0f) shutter.animateTo(0f, tween(if (reduceMotion) 0 else 450, easing = FastOutSlowInEasing))
        powerDown.snapTo(0f)
    }

    BackHandler(enabled = showSecurity) { showSecurity = false }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    val motion = if (reduceMotion) 0 else 1

    suspend fun enterHud() {
        withContext(Dispatchers.IO) { lock.unlock() }
        configured = true
        locked = false
        refresh()
        if (!reduceMotion) {
            intro.snapTo(0.25f)
            scope.launch { intro.animateTo(1f, tween(1300, easing = LinearEasing)) }
        }
    }

    fun lockStark() = scope.launch {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        shutter.animateTo(1f, tween(380 * motion, easing = FastOutSlowInEasing))
        withContext(Dispatchers.IO) { lock.lock() }
        locked = true
        showSecurity = false
        onLocked()
    }

    fun lockdown() = scope.launch {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        powerDown.animateTo(1f, tween(1300 * motion, easing = LinearEasing))
        withContext(Dispatchers.IO) { lock.lock() }
        locked = true
        showSecurity = false
        runCatching { policy.lockdown() }
            .onFailure { toast("Android refused Lockdown: ${it.message}") }
        shutter.animateTo(1f, tween(380 * motion))
        onLocked()
    }

    fun rescan() {
        refresh()
        val findings = state.findings()
        val critical = findings.count { it.level == Level.CRITICAL }
        val advisory = findings.count { it.level == Level.ADVISORY }
        scanReport = "SCAN · $critical CRITICAL · $advisory ADVISORY"
        val mySeq = ++scanSeq
        scope.launch {
            delay(4000)
            if (scanSeq == mySeq) scanReport = null
        }
    }

    val screen = when {
        !configured -> ShellScreen.CreatePin
        locked -> ShellScreen.Locked
        showSecurity -> ShellScreen.Security
        else -> ShellScreen.Home
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = screen, animationSpec = tween(if (reduceMotion) 0 else 350), label = "screen") { target ->
            when (target) {
                ShellScreen.CreatePin, ShellScreen.Locked -> LockScreen(
                    creating = target == ShellScreen.CreatePin,
                    reduceMotion = reduceMotion,
                    lockoutRemainingMs = { lock.lockoutRemainingMs() },
                    verify = { pin -> lock.verify(pin) },
                    create = { pin -> lock.createPin(pin) },
                    onUnlocked = { enterHud() },
                )
                ShellScreen.Security -> SecurityScreen(
                    state = state,
                    policy = policy,
                    onChanged = ::refresh,
                    onBack = { showSecurity = false },
                )
                ShellScreen.Home -> HomeScreen(
                    state = state,
                    apps = apps,
                    liveApps = liveApps,
                    telemetry = telemetry,
                    reduceMotion = reduceMotion,
                    intro = intro.value,
                    powerDown = powerDown.value,
                    scanReport = scanReport,
                    onRescan = ::rescan,
                    onOpenApp = { app ->
                        if (!AppCatalog.launch(context, app.packageName)) toast("${app.label} can't be opened.")
                    },
                    onToggleLive = { app ->
                        val nowLive = app.packageName !in liveApps
                        lock.setLive(app.packageName, nowLive)
                        refresh()
                        toast(
                            if (nowLive) "${app.label} stays live while STARK is locked"
                            else "${app.label} pauses when STARK locks",
                        )
                    },
                    onGetApps = {
                        if (!AppCatalog.launch(context, AppCatalog.PLAY_STORE)) {
                            toast("Play Store isn't available inside STARK.")
                        }
                    },
                    onSecurity = { showSecurity = true },
                    onLock = { lockStark() },
                    onLockdown = { confirmLockdown = true },
                )
            }
        }

        // Faceplate: opens during boot, closes on Lock / Lockdown.
        val bootShutter = 1f - ((intro.value - 0.02f) / 0.2f).coerceIn(0f, 1f)
        Shutter(closed = maxOf(bootShutter, shutter.value))
    }

    if (confirmLockdown) {
        Dialog(onDismissRequest = { confirmLockdown = false }) {
            Panel(accent = Stark.Amber) {
                Text("LOCKDOWN", style = hudLabel(), color = Stark.Amber)
                Spacer(Modifier.height(10.dp))
                Text("Seal STARK and encrypt it at rest?", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Every app inside STARK pauses, STARK's encryption keys are removed from memory, and " +
                        "your phone locks. The next unlock needs your phone PIN (not face or fingerprint). " +
                        "Android takes about 30 seconds to finish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Stark.TextDim,
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StarkButton("Cancel", { confirmLockdown = false }, Modifier.weight(1f), tone = ButtonTone.Quiet)
                    StarkButton(
                        "Lockdown",
                        {
                            confirmLockdown = false
                            lockdown()
                        },
                        Modifier.weight(1f),
                        tone = ButtonTone.Warning,
                    )
                }
            }
        }
    }
}
