package dev.shaikh.stark.security

import dev.shaikh.stark.security.SecurityState.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityStateTest {

    private val secured = SecurityState(
        isProfileOwner = true,
        starkLockSet = true,
        androidLockSeparate = false,
        perProfileEncryption = true,
        keyHardware = SecurityState.KEY_STRONGBOX,
        screenCaptureBlocked = true,
        copyPasteBlocked = true,
        lockscreenRedacted = true,
        contactsHidden = true,
        liveApps = 0,
        recoveryConfigured = true,
    )

    @Test
    fun `fully protected state has no findings`() {
        assertTrue(secured.findings().isEmpty())
        assertEquals(Level.OK, secured.overall)
    }

    @Test
    fun `missing STARK PIN is critical`() {
        assertEquals(Level.CRITICAL, secured.copy(starkLockSet = false).overall)
    }

    @Test
    fun `android lock mode is information, not a finding`() {
        assertEquals(Level.OK, secured.copy(androidLockSeparate = true).overall)
    }

    @Test
    fun `software keys are critical`() {
        assertEquals(Level.CRITICAL, secured.copy(keyHardware = SecurityState.KEY_SOFTWARE).overall)
    }

    @Test
    fun `unmeasured key hardware is not reported as a problem`() {
        assertEquals(Level.OK, secured.copy(keyHardware = null).overall)
    }

    @Test
    fun `live apps are an advisory that names the count`() {
        val findings = secured.copy(liveApps = 2).findings()
        assertEquals(Level.ADVISORY, findings.single().level)
        assertTrue(findings.single().title.startsWith("2 apps"))
    }

    @Test
    fun `missing recovery is an advisory`() {
        val state = secured.copy(recoveryConfigured = false)
        assertEquals(Level.ADVISORY, state.overall)
        assertEquals(1, state.findings().size)
    }

    @Test
    fun `critical findings are listed before advisories`() {
        val state = secured.copy(screenCaptureBlocked = false, starkLockSet = false)
        assertEquals(listOf(Level.CRITICAL, Level.ADVISORY), state.findings().map { it.level })
    }
}
