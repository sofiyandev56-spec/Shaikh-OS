package dev.shaikh.stark.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log
import dev.shaikh.stark.lock.StarkLock
import dev.shaikh.stark.security.SecurityState

/**
 * The only place STARK talks to DevicePolicyManager. Every method is a thin wrapper over a
 * documented profile-owner API, so the UI can never claim a protection Android isn't enforcing.
 */
class StarkPolicy(private val context: Context) {

    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(context, StarkAdminReceiver::class.java)

    /** True only inside the STARK work profile, where this app is the profile owner. */
    fun isProfileOwner(): Boolean = dpm.isProfileOwnerApp(context.packageName)

    /**
     * Applies STARK's default protections. Idempotent: called both from the provisioning
     * broadcast and on every shell start, so a missed broadcast can't leave STARK unprotected.
     */
    fun ensureBaseline() {
        if (!isProfileOwner()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dpm.setProfileName(admin, "STARK")
        dpm.setProfileEnabled(admin)
        if (prefs.getBoolean(KEY_BASELINE_APPLIED, false)) return

        setScreenCaptureBlocked(true)
        setCopyPasteBlocked(true)
        setLockscreenRedacted(true)
        setContactsHidden(true)
        prefs.edit().putBoolean(KEY_BASELINE_APPLIED, true).apply()
        Log.i(TAG, "baseline protections applied")
    }

    fun setScreenCaptureBlocked(blocked: Boolean) = dpm.setScreenCaptureDisabled(admin, blocked)

    /** Blocks pasting STARK clipboard content into the personal side (not the other way round). */
    fun setCopyPasteBlocked(blocked: Boolean) {
        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE)
        }
    }

    fun setLockscreenRedacted(redacted: Boolean) {
        val flag = DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS
        val current = dpm.getKeyguardDisabledFeatures(admin)
        dpm.setKeyguardDisabledFeatures(admin, if (redacted) current or flag else current and flag.inv())
    }

    @Suppress("DEPRECATION") // Replacement policy APIs need targetSdk 34 semantics we don't rely on yet.
    fun setContactsHidden(hidden: Boolean) {
        dpm.setCrossProfileCallerIdDisabled(admin, hidden)
        dpm.setCrossProfileContactsSearchDisabled(admin, hidden)
    }

    /**
     * Pauses (or resumes) apps inside STARK. Paused apps can't be opened and their notifications
     * are hidden. Returns the packages Android refused to pause.
     */
    fun setAppsSuspended(packages: Collection<String>, suspended: Boolean): List<String> {
        if (packages.isEmpty() || !isProfileOwner()) return emptyList()
        return dpm.setPackagesSuspended(admin, packages.toTypedArray(), suspended).toList()
    }

    /**
     * Blocks installing apps into STARK. Used while STARK is locked, because Android won't let the
     * Play Store itself be paused (it's the system installer).
     */
    fun setInstallsBlocked(blocked: Boolean) {
        if (!isProfileOwner()) return
        if (blocked) {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        } else {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        }
    }

    /**
     * Evicts STARK's credential-encrypted storage keys: Android stops the profile so its data is
     * encrypted at rest. With a shared (unified) Android lock this also locks the phone, and the
     * next unlock requires the phone PIN.
     */
    fun lockdown() = dpm.lockNow(DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY)

    @Suppress("DEPRECATION")
    fun readState(keyHardware: String?, lock: StarkLock): SecurityState = SecurityState(
        isProfileOwner = isProfileOwner(),
        starkLockSet = lock.isConfigured,
        androidLockSeparate = !dpm.isUsingUnifiedPassword(admin),
        perProfileEncryption =
            dpm.storageEncryptionStatus == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER,
        keyHardware = keyHardware,
        screenCaptureBlocked = dpm.getScreenCaptureDisabled(admin),
        copyPasteBlocked = dpm.getUserRestrictions(admin)
            .getBoolean(UserManager.DISALLOW_CROSS_PROFILE_COPY_PASTE),
        lockscreenRedacted = dpm.getKeyguardDisabledFeatures(admin) and
            DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS != 0,
        contactsHidden = dpm.getCrossProfileCallerIdDisabled(admin) &&
            dpm.getCrossProfileContactsSearchDisabled(admin),
        liveApps = lock.liveApps.size,
        recoveryConfigured = false,
    )

    companion object {
        const val TAG = "STARK"
        private const val PREFS = "stark_policy"
        private const val KEY_BASELINE_APPLIED = "baseline_applied_v1"
    }
}
