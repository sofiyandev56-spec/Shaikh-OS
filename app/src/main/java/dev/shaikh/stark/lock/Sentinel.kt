package dev.shaikh.stark.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import dev.shaikh.stark.R
import dev.shaikh.stark.admin.StarkPolicy

/**
 * Runs only while STARK is unlocked, and only to re-lock STARK the moment the screen turns off.
 * Its notification is silent and minimal (and hidden unless you allow STARK notifications).
 */
class Sentinel : Service() {

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                Log.i(StarkPolicy.TAG, "screen off: locking STARK")
                StarkLock(context).lock()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        ContextCompat.registerReceiver(
            this,
            screenOff,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "STARK sentinel", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while STARK is unlocked. STARK locks when the screen turns off."
                setShowBadge(false)
            },
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("STARK is unlocked")
            .setContentText("Locks when the screen turns off.")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "stark_sentinel"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, Sentinel::class.java)) }
                .onFailure { Log.w(StarkPolicy.TAG, "sentinel not started: $it") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, Sentinel::class.java))
        }
    }
}

/** Locks STARK on boot (before its storage unlocks) and after STARK itself is updated. */
class LockOnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> StarkLock(context).lock()
        }
    }
}
