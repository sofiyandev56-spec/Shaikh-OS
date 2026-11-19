package dev.shaikh.stark.gateway

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaikh.stark.ui.ArcReactor
import dev.shaikh.stark.ui.DecodeText
import dev.shaikh.stark.ui.HudBackdrop
import dev.shaikh.stark.ui.Readout
import dev.shaikh.stark.ui.Shutter
import dev.shaikh.stark.ui.Stark
import dev.shaikh.stark.ui.StarkButton
import dev.shaikh.stark.ui.VisorFrame
import dev.shaikh.stark.ui.rememberHudClock
import dev.shaikh.stark.ui.rememberReduceMotion
import dev.shaikh.stark.ui.rememberTilt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GatewayRoot(resumeTick: Int) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    var state by remember { mutableStateOf<GatewayState?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var entering by remember { mutableStateOf(false) }
    var finishingSetup by remember { mutableStateOf(false) }
    val shutter = remember { Animatable(0f) }
    val frameIn = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) { frameIn.animateTo(1f, tween(900, easing = LinearEasing)) }

    LaunchedEffect(resumeTick) {
        if (!finishingSetup) state = Gateway.read(context)
        entering = false
        shutter.snapTo(0f)
    }

    // Android returns from provisioning before the new profile is enabled, so wait for it to
    // appear instead of misreading the moment as "another work profile exists".
    LaunchedEffect(finishingSetup) {
        if (!finishingSetup) return@LaunchedEffect
        repeat(SETUP_POLL_ATTEMPTS) {
            val current = Gateway.read(context)
            if (current is GatewayState.Ready) {
                state = current
                finishingSetup = false
                entering = true
                return@LaunchedEffect
            }
            delay(1000)
        }
        finishingSetup = false
        state = Gateway.read(context)
        notice = "STARK was created but hasn't come online yet. Close and reopen STARK in a minute."
    }

    val provision = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            notice = null
            finishingSetup = true
        } else {
            state = Gateway.read(context)
            notice = "Setup was cancelled. Nothing was changed."
        }
    }

    val ready = state as? GatewayState.Ready
    LaunchedEffect(entering) {
        if (!entering || ready == null) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        if (!reduceMotion) {
            delay(250)
            shutter.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        runCatching { Gateway.enter(context as Activity, ready.profile) }
            .onFailure {
                entering = false
                launch { shutter.animateTo(0f, tween(300)) }
                notice = "Android refused to open STARK: ${it.message}"
            }
    }

    Box(Modifier.fillMaxSize()) {
        GatewayScreen(
            state = if (finishingSetup) null else state,
            entering = entering,
            notice = notice,
            reduceMotion = reduceMotion,
            frame = frameIn.value,
            onInitialize = { provision.launch(Gateway.provisioningIntent(context)) },
            onEnter = { notice = null; entering = true },
        )
        Shutter(closed = shutter.value)
    }
}

@Composable
private fun GatewayScreen(
    state: GatewayState?,
    entering: Boolean,
    notice: String?,
    reduceMotion: Boolean,
    frame: Float,
    onInitialize: () -> Unit,
    onEnter: () -> Unit,
) {
    val clock = rememberHudClock(!reduceMotion)
    val tilt = rememberTilt(!reduceMotion)
    val idlePower = when (state) {
        is GatewayState.Ready -> when {
            state.paused -> 0.3f
            !state.keysLoaded -> 0.5f
            else -> 1f
        }
        is GatewayState.Blocked -> 0.6f
        else -> 0.55f
    }
    val power by animateFloatAsState(
        targetValue = if (entering) 1.6f else idlePower,
        animationSpec = tween(if (reduceMotion) 0 else 600),
        label = "power",
    )
    val speed by animateFloatAsState(
        targetValue = if (entering) 9f else 1f,
        animationSpec = tween(if (reduceMotion) 0 else 600),
        label = "speed",
    )
    val ignition by animateFloatAsState(
        targetValue = when (state) {
            is GatewayState.Ready -> if (state.keysLoaded) 1f else 0.3f
            GatewayState.CanInitialize, null -> 0.2f
            is GatewayState.Blocked -> 0.5f
        },
        animationSpec = tween(if (reduceMotion) 0 else 900),
        label = "ignition",
    )
    val tint = if (state is GatewayState.Blocked) Stark.Amber else Stark.Cyan

    Box(Modifier.fillMaxSize()) {
        HudBackdrop(time = { clock.value }, tilt = { tilt.value }, accent = tint)
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DecodeText("PROJECT", style = Readout, color = Stark.TextFaint, animate = !reduceMotion)
            Spacer(Modifier.height(20.dp))
            ArcReactor(
                modifier = Modifier.fillMaxWidth().widthIn(max = 320.dp).aspectRatio(1f),
                power = power,
                speed = speed,
                ignition = ignition,
                animate = !reduceMotion,
                tint = tint,
                tilt = { tilt.value },
                onTap = {},
                tapLabel = "Reactor",
            )
            Spacer(Modifier.height(24.dp))
            DecodeText(
                "STARK",
                style = MaterialTheme.typography.displaySmall,
                color = Stark.Text,
                animate = !reduceMotion,
                durationMillis = 900,
            )
            Spacer(Modifier.height(10.dp))

            Column(
                Modifier.fillMaxWidth().widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state) {
                    null -> Status("Bringing STARK online…", "", reduceMotion)
                    GatewayState.CanInitialize -> {
                        Status(
                            "Not initialized",
                            "STARK creates a separate Android work profile on this phone. It has its own " +
                                "apps, accounts and encrypted storage, opened by its own STARK PIN. Your normal " +
                                "phone and Secure Folder are not changed. Removing STARK deletes everything inside it.",
                            reduceMotion,
                        )
                        StarkButton("Initialize STARK", onInitialize, Modifier.fillMaxWidth())
                        Fine("Android shows its own setup screens next.")
                    }
                    is GatewayState.Blocked -> Status("Can't initialize", state.reason, reduceMotion, warning = true)
                    is GatewayState.Ready -> {
                        when {
                            state.paused -> Status(
                                "Paused",
                                "STARK's apps are switched off. Turn on Work apps in the quick panel to enter.",
                                reduceMotion,
                                warning = true,
                            )
                            !state.keysLoaded -> Status(
                                "Lockdown",
                                "STARK's data is encrypted at rest. Entering asks for your STARK PIN and restarts its apps.",
                                reduceMotion,
                            )
                            else -> Status("Online", "Protected by your STARK PIN.", reduceMotion)
                        }
                        StarkButton(
                            if (entering) "Entering…" else "Enter STARK",
                            onEnter,
                            Modifier.fillMaxWidth(),
                            enabled = !entering && !state.paused,
                        )
                        Text(
                            "PROFILE U${state.profile.hashCode()}  ·  KEYS " + if (state.keysLoaded) "IN MEMORY" else "EVICTED",
                            style = Readout.copy(fontSize = 10.sp),
                            color = Stark.TextFaint,
                        )
                    }
                }
                if (notice != null) Fine(notice, warning = true)
            }
        }
        VisorFrame(frame, color = tint)
    }
}

@Composable
private fun Status(title: String, body: String, reduceMotion: Boolean, warning: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DecodeText(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = if (warning) Stark.Amber else Stark.Cyan,
            animate = !reduceMotion,
        )
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Stark.TextDim, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Fine(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) Stark.Amber else Stark.TextFaint,
        textAlign = TextAlign.Center,
    )
}

private const val SETUP_POLL_ATTEMPTS = 30
