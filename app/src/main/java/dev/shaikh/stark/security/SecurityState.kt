package dev.shaikh.stark.security

/**
 * A snapshot of STARK's real protections, read from Android at runtime.
 * Pure data so the rules that turn it into UI status are unit-testable.
 */
data class SecurityState(
    val isProfileOwner: Boolean,
    /** STARK's own PIN exists (the lock that pauses apps). */
    val starkLockSet: Boolean,
    /** Android's work-profile lock is separate from the phone lock (a second, system PIN screen). */
    val androidLockSeparate: Boolean,
    val perProfileEncryption: Boolean,
    /** "STRONGBOX", "TRUSTED_ENVIRONMENT", "SOFTWARE", or null while still being measured. */
    val keyHardware: String?,
    val screenCaptureBlocked: Boolean,
    val copyPasteBlocked: Boolean,
    val lockscreenRedacted: Boolean,
    val contactsHidden: Boolean,
    /** Apps the owner chose to keep running (and openable) while STARK is locked. */
    val liveApps: Int,
    val recoveryConfigured: Boolean,
) {
    enum class Level { OK, ADVISORY, CRITICAL }

    data class Finding(val level: Level, val title: String, val advice: String)

    /** Everything that is not fully protected, most severe first. */
    fun findings(): List<Finding> = buildList {
        if (!isProfileOwner) add(Finding(Level.CRITICAL, "STARK is not managing this profile", "Reinstall STARK. Its protections can't be enforced right now."))
        if (!starkLockSet) add(Finding(Level.CRITICAL, "No STARK PIN", "Create a STARK PIN so STARK's apps pause whenever it locks."))
        if (!perProfileEncryption) add(Finding(Level.CRITICAL, "Per-profile encryption not active", "Android isn't encrypting STARK separately. Keep sensitive data out until this is fixed."))
        if (keyHardware == KEY_SOFTWARE) add(Finding(Level.CRITICAL, "Keys are not hardware-backed", "This device stores STARK's keys in software, so they're easier to extract."))
        if (liveApps > 0) {
            val apps = if (liveApps == 1) "1 app stays" else "$liveApps apps stay"
            add(Finding(Level.ADVISORY, "$apps live while locked", "Anyone with your phone PIN can open them from the Work tab. Long-press an app on the STARK home to change this."))
        }
        if (!screenCaptureBlocked) add(Finding(Level.ADVISORY, "Screenshots allowed", "Turn on the screenshot block in Security."))
        if (!copyPasteBlocked) add(Finding(Level.ADVISORY, "Copying out of STARK allowed", "Turn on the copy-out block in Security."))
        if (!lockscreenRedacted) add(Finding(Level.ADVISORY, "Lock screen shows message content", "Hide message content on the lock screen in Security."))
        if (!contactsHidden) add(Finding(Level.ADVISORY, "STARK contacts visible to phone app", "Hide STARK contacts from your phone app in Security."))
        if (!recoveryConfigured) add(Finding(Level.ADVISORY, "No recovery code set", "A forgotten STARK PIN can't be recovered yet. Recovery codes arrive in a later version."))
    }.sortedByDescending { it.level.ordinal }

    val overall: Level
        get() = findings().maxOfOrNull { it.level } ?: Level.OK

    companion object {
        const val KEY_STRONGBOX = "STRONGBOX"
        const val KEY_TEE = "TRUSTED_ENVIRONMENT"
        const val KEY_SOFTWARE = "SOFTWARE"
    }
}
