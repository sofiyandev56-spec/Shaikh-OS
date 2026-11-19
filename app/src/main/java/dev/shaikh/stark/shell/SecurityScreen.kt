package dev.shaikh.stark.shell

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.shaikh.stark.BuildConfig
import dev.shaikh.stark.admin.StarkPolicy
import dev.shaikh.stark.security.PinKdf
import dev.shaikh.stark.security.SecurityState
import dev.shaikh.stark.security.SecurityState.Level
import dev.shaikh.stark.ui.KeyValueRow
import dev.shaikh.stark.ui.Panel
import dev.shaikh.stark.ui.SectionLabel
import dev.shaikh.stark.ui.Stark
import dev.shaikh.stark.ui.DecodeText
import dev.shaikh.stark.ui.HudBackdrop
import dev.shaikh.stark.ui.rememberHudClock
import dev.shaikh.stark.ui.rememberReduceMotion
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp

/** Every value on this screen is read from Android at runtime; nothing is decorative. */
@Composable
fun SecurityScreen(
    state: SecurityState,
    policy: StarkPolicy,
    onChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    fun apply(action: () -> Unit) {
        runCatching(action).onFailure {
            Toast.makeText(context, "Android refused: ${it.message}", Toast.LENGTH_LONG).show()
        }
        onChanged()
    }

    val reduceMotion = rememberReduceMotion()
    val clock = rememberHudClock(!reduceMotion)
    Box(Modifier.fillMaxSize()) {
    HudBackdrop(time = { clock.value })
    LazyColumn(
        Modifier.fillMaxSize().systemBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "‹  HOME",
                style = MaterialTheme.typography.labelLarge,
                color = Stark.Cyan,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            DecodeText("SECURITY STATE", style = MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, letterSpacing = 6.sp), color = Stark.Text, animate = !reduceMotion)
            Spacer(Modifier.height(4.dp))
            Text(
                "Read from Android each time this screen opens. If a protection isn't enforced, it shows here.",
                style = MaterialTheme.typography.bodyMedium,
                color = Stark.TextDim,
            )
        }

        item {
            SectionLabel("Boundary")
            Spacer(Modifier.height(8.dp))
            Panel {
                KeyValueRow(
                    "Isolation",
                    if (state.isProfileOwner) "Android work profile managed by STARK" else "Not managed by STARK",
                    if (state.isProfileOwner) Level.OK else Level.CRITICAL,
                    "Separate app installs, data, accounts and encrypted storage from your normal phone.",
                )
                Divider()
                KeyValueRow(
                    "STARK lock",
                    if (state.starkLockSet) "Own PIN · ${PinKdf.ITERATIONS / 1000}k PBKDF2 rounds · hardware-sealed" else "No STARK PIN yet",
                    if (state.starkLockSet) Level.OK else Level.CRITICAL,
                    "Your PIN is stretched with PBKDF2-HMAC-SHA256 and sealed by a key that never leaves this " +
                        "phone's security chip. Wrong guesses trigger growing delays (30 s, doubling to 1 h), even across reboots.",
                )
                Divider()
                KeyValueRow(
                    "While STARK is locked",
                    if (state.liveApps == 0) "Every app paused" else "Apps paused · ${state.liveApps} always live",
                    if (state.liveApps == 0) Level.OK else Level.ADVISORY,
                    "Paused apps can't be opened, even from the Work tab, and their notifications are hidden. " +
                        "Always-live apps keep notifying, but anyone with your phone PIN could open them.",
                )
                Divider()
                KeyValueRow(
                    "Auto-lock",
                    "Screen off · reboot · STARK update",
                    Level.OK,
                    "While STARK is unlocked, a small STARK sentinel re-locks it the moment the screen turns off.",
                )
                Divider()
                KeyValueRow(
                    "Android profile lock",
                    if (state.androidLockSeparate) "Separate Android PIN as well (double lock)" else "Shared with your phone lock",
                    null,
                    if (state.androidLockSeparate) {
                        "Android shows its own PIN screen before STARK's. To use only STARK's lock, open Settings, " +
                            "search \"Use one lock\" and turn it on."
                    } else {
                        "Unlocking your phone makes STARK's storage readable to STARK's own apps. STARK's lock " +
                            "and paused apps guard what's inside; Lockdown re-encrypts it."
                    },
                )
                Divider()
                KeyValueRow(
                    "Storage encryption",
                    if (state.perProfileEncryption) "Per-profile file-based encryption" else "Not per-profile",
                    if (state.perProfileEncryption) Level.OK else Level.CRITICAL,
                    "STARK's files use their own encryption keys, separate from your normal phone's.",
                )
                Divider()
                KeyValueRow(
                    "Key hardware",
                    keyLabel(state.keyHardware).replaceFirstChar { it.uppercase() },
                    when (state.keyHardware) {
                        null, "UNKNOWN" -> null
                        SecurityState.KEY_SOFTWARE -> Level.CRITICAL
                        else -> Level.OK
                    },
                    "Measured by creating a test key in Android Keystore and reading where it lives.",
                )
                Divider()
                KeyValueRow(
                    "Lockdown",
                    "Pauses STARK, removes its keys from memory, locks the phone",
                    null,
                    "STARK's data stays encrypted at rest until your phone PIN is entered on the normal lock screen.",
                )
                Divider()
                KeyValueRow(
                    "Recovery",
                    if (state.recoveryConfigured) "Recovery code set" else "No recovery code yet",
                    if (state.recoveryConfigured) Level.OK else Level.ADVISORY,
                    "Today, a forgotten STARK PIN means removing STARK and losing what's inside it.",
                )
            }
        }

        item {
            SectionLabel("Protections")
            Spacer(Modifier.height(8.dp))
            Panel {
                ToggleRow(
                    "Block screenshots and recording",
                    "Applies to every app inside STARK.",
                    state.screenCaptureBlocked,
                ) { apply { policy.setScreenCaptureBlocked(it) } }
                Divider()
                ToggleRow(
                    "Block copying out of STARK",
                    "Text copied inside STARK can't be pasted on your normal side.",
                    state.copyPasteBlocked,
                ) { apply { policy.setCopyPasteBlocked(it) } }
                Divider()
                ToggleRow(
                    "Hide message content on lock screen",
                    "STARK notifications show only the app name until the phone is unlocked.",
                    state.lockscreenRedacted,
                ) { apply { policy.setLockscreenRedacted(it) } }
                Divider()
                ToggleRow(
                    "Hide STARK contacts from your phone app",
                    "Caller ID and contact search on the normal side won't see STARK contacts.",
                    state.contactsHidden,
                ) { apply { policy.setContactsHidden(it) } }
            }
        }

        item {
            SectionLabel("Outside STARK's protection")
            Spacer(Modifier.height(8.dp))
            Panel {
                listOf(
                    "Someone with your phone PIN can delete STARK in Settings. Its data is destroyed, not revealed.",
                    "Someone with your phone PIN plus root or forensic tools could read data of apps inside STARK.",
                    "A rooted or compromised phone operating system can read STARK while it is unlocked.",
                    "While STARK is unlocked, apps inside it decide what their notifications show.",
                    "Anyone who can see STARK on your phone can tell that it exists.",
                ).forEach { line ->
                    Text("—  $line", style = MaterialTheme.typography.bodyMedium, color = Stark.TextDim)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        item {
            Text(
                "STARK ${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · development build" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Stark.TextFaint,
            )
        }
    }
}
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Stark.TextDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Stark.Void,
                checkedTrackColor = Stark.Cyan,
                uncheckedThumbColor = Stark.TextDim,
                uncheckedTrackColor = Stark.TitaniumRaised,
                uncheckedBorderColor = Stark.Line,
            ),
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Stark.Line)
