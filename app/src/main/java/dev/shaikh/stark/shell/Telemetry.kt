package dev.shaikh.stark.shell

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Every number the HUD shows. All read from Android; nothing is simulated. */
data class Telemetry(
    val now: Long,
    val batteryPercent: Int?,
    val charging: Boolean,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val uplink: String,
    val shellUptimeMs: Long,
    val profileUserId: Int,
) {
    val storageFreeFraction: Float?
        get() = if (storageTotalBytes > 0) storageFreeBytes.toFloat() / storageTotalBytes else null

    companion object {
        fun read(context: Context): Telemetry {
            val battery = context.getSystemService(BatteryManager::class.java)
            val stat = StatFs(Environment.getDataDirectory().path)
            return Telemetry(
                now = System.currentTimeMillis(),
                batteryPercent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 },
                charging = battery.isCharging,
                storageFreeBytes = stat.availableBytes,
                storageTotalBytes = stat.totalBytes,
                uplink = uplink(context),
                shellUptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
                // UserHandle.hashCode() is the user id; there's no public getter for it.
                profileUserId = Process.myUserHandle().hashCode(),
            )
        }

        private fun uplink(context: Context): String {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "OFFLINE"
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "ONLINE"
            }
        }
    }
}

/** Clock and uptime tick every second; battery, storage and network refresh every 10 seconds. */
@Composable
fun rememberTelemetry(): State<Telemetry> {
    val context = LocalContext.current
    return produceState(initialValue = Telemetry.read(context)) {
        var tick = 0
        while (true) {
            delay(1000)
            tick++
            value = if (tick % 10 == 0) {
                withContext(Dispatchers.IO) { Telemetry.read(context) }
            } else {
                value.copy(
                    now = System.currentTimeMillis(),
                    shellUptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
                )
            }
        }
    }
}

internal fun formatUptime(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

internal fun formatGb(bytes: Long): String = "%.0f GB".format(bytes / 1_000_000_000.0)
