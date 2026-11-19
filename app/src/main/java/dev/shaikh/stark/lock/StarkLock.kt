package dev.shaikh.stark.lock

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.util.Log
import dev.shaikh.stark.admin.StarkPolicy
import dev.shaikh.stark.security.LockoutPolicy
import dev.shaikh.stark.security.PinKdf
import dev.shaikh.stark.shell.AppCatalog

/**
 * STARK's own lock. "Locked" means every app inside STARK is suspended by the profile owner
 * (can't be opened, notifications hidden), except apps the owner marked always-live.
 *
 * The locked flag and the live list live in device-protected storage so STARK can lock at
 * boot, before the profile's credential-encrypted storage is available. PIN material lives in
 * credential-encrypted storage and is never the PIN itself.
 */
class StarkLock(context: Context) {

    sealed interface Attempt {
        data object Success : Attempt
        data class Wrong(val failures: Int, val lockoutMs: Long) : Attempt
        data class LockedOut(val remainingMs: Long) : Attempt
    }

    private val app = context.applicationContext
    private val policy = StarkPolicy(app)
    private val de: SharedPreferences =
        app.createDeviceProtectedStorageContext().getSharedPreferences("stark_lock_de", Context.MODE_PRIVATE)
    private val ce: SharedPreferences by lazy { app.getSharedPreferences("stark_lock", Context.MODE_PRIVATE) }

    val isConfigured: Boolean get() = ce.contains(KEY_VERIFIER)
    val isLocked: Boolean get() = de.getBoolean(KEY_LOCKED, true)
    val liveApps: Set<String> get() = de.getStringSet(KEY_LIVE, emptySet()).orEmpty().toSet()

    /** Blocking: derives the verifier. */
    fun createPin(pin: CharArray) {
        val salt = PinKdf.newSalt()
        val verifier = PinKdf.verifier(pin, salt)
        ce.edit()
            .putString(KEY_SALT, salt.b64())
            .putString(KEY_VERIFIER, verifier.b64())
            .putInt(KEY_ITERATIONS, PinKdf.ITERATIONS)
            .putInt(KEY_FAILURES, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0)
            .commit()
    }

    fun lockoutRemainingMs(): Long {
        val until = ce.getLong(KEY_LOCKOUT_UNTIL, 0)
        if (until == 0L) return 0
        if (ce.getInt(KEY_BOOT, -1) != bootCount()) {
            // The phone rebooted (elapsed time reset): restart the current penalty rather than skip it.
            val penalty = LockoutPolicy.delayAfter(ce.getInt(KEY_FAILURES, 0))
            ce.edit()
                .putLong(KEY_LOCKOUT_UNTIL, SystemClock.elapsedRealtime() + penalty)
                .putInt(KEY_BOOT, bootCount())
                .apply()
            return penalty
        }
        return (until - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

    /** Blocking: derives and compares in constant time. */
    fun verify(pin: CharArray): Attempt {
        lockoutRemainingMs().takeIf { it > 0 }?.let { return Attempt.LockedOut(it) }
        val salt = ce.getString(KEY_SALT, null)?.unb64() ?: return Attempt.Wrong(0, 0)
        val expected = ce.getString(KEY_VERIFIER, null)?.unb64() ?: return Attempt.Wrong(0, 0)
        val iterations = ce.getInt(KEY_ITERATIONS, PinKdf.ITERATIONS)
        val actual = PinKdf.verifier(pin, salt, iterations)
        if (PinKdf.matches(actual, expected)) {
            ce.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LOCKOUT_UNTIL, 0).apply()
            return Attempt.Success
        }
        val failures = ce.getInt(KEY_FAILURES, 0) + 1
        val delay = LockoutPolicy.delayAfter(failures)
        ce.edit()
            .putInt(KEY_FAILURES, failures)
            .putLong(KEY_LOCKOUT_UNTIL, if (delay > 0) SystemClock.elapsedRealtime() + delay else 0)
            .putInt(KEY_BOOT, bootCount())
            .apply()
        return Attempt.Wrong(failures, delay)
    }

    /** Pauses STARK's apps. Safe before the profile's storage unlocks (uses device-protected data only). */
    fun lock() {
        if (!policy.isProfileOwner()) return
        val targets = AppCatalog.launchablePackages(app) - liveApps
        val refused = policy.setAppsSuspended(targets, true)
        if (refused.isNotEmpty()) Log.w(StarkPolicy.TAG, "Android refused to pause: $refused")
        policy.setInstallsBlocked(true)
        de.edit().putBoolean(KEY_LOCKED, true).commit()
        Sentinel.stop(app)
    }

    fun unlock() {
        if (!policy.isProfileOwner()) return
        policy.setAppsSuspended(AppCatalog.launchablePackages(app), false)
        policy.setInstallsBlocked(false)
        de.edit().putBoolean(KEY_LOCKED, false).commit()
        Sentinel.start(app)
    }

    fun setLive(packageName: String, live: Boolean) {
        val updated = liveApps.toMutableSet().apply { if (live) add(packageName) else remove(packageName) }
        de.edit().putStringSet(KEY_LIVE, updated).commit()
        if (isLocked) policy.setAppsSuspended(listOf(packageName), !live)
    }

    private fun bootCount() = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun ByteArray.b64() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.unb64() = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_LOCKED = "locked"
        const val KEY_LIVE = "live"
        const val KEY_SALT = "salt"
        const val KEY_VERIFIER = "verifier"
        const val KEY_ITERATIONS = "iterations"
        const val KEY_FAILURES = "failures"
        const val KEY_LOCKOUT_UNTIL = "lockout_until"
        const val KEY_BOOT = "boot"
    }
}
