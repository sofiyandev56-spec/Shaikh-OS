package dev.shaikh.stark0

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import java.security.KeyStore

/**
 * STARK-0 feasibility probe. Throwaway code: it only reports facts about the
 * platform and, on explicit request, provisions or removes a test work profile.
 */
class ProbeActivity : Activity() {

    private lateinit var output: TextView
    private val admin by lazy { adminComponent() }
    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val um by lazy { getSystemService(UserManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply { setTextIsSelectable(true); setPadding(32, 32, 32, 32) }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(button("Run checks (read-only)") { runChecks() })
        if (dpm.isProfileOwnerApp(packageName)) {
            // ACTION_SET_NEW_PASSWORD launched from inside the profile sets the profile's own
            // (separate) challenge rather than the phone lock.
            layout.addView(button("U3: Set separate STARK lock") {
                startActivity(Intent(DevicePolicyManager.ACTION_SET_NEW_PASSWORD))
            })
            layout.addView(button("U4a: Lock STARK now (keys stay cached)") { lock(0) })
            layout.addView(button("U4b: Lock STARK + evict CE keys") {
                lock(DevicePolicyManager.FLAG_EVICT_CREDENTIAL_ENCRYPTION_KEY)
            })
            layout.addView(button("Remove STARK-0 test profile") { confirmRemove() })
        } else {
            layout.addView(button("Create STARK-0 test profile") { confirmProvision() })
        }
        layout.addView(output)
        setContentView(ScrollView(this).apply { addView(layout) })
        runChecks()
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    private fun runChecks() {
        val r = StringBuilder()
        fun line(k: String, v: Any?) = r.append(k).append(" = ").append(v).append('\n')
        fun safe(k: String, block: () -> Any?) =
            line(k, try { block() } catch (t: Throwable) { "ERROR ${t.javaClass.simpleName}: ${t.message}" })

        line("device", "${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        line("build", Build.DISPLAY)
        line("security patch", Build.VERSION.SECURITY_PATCH)
        line("running as user", android.os.Process.myUserHandle())
        safe("this user is managed profile") { um.isManagedProfile }
        safe("this app is profile owner") { dpm.isProfileOwnerApp(packageName) }
        safe("U2 provisioning allowed (managed profile)") {
            dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
        }
        if (dpm.isProfileOwnerApp(packageName)) {
            safe("U3 profile uses unified (shared) lock") { dpm.isUsingUnifiedPassword(admin) }
            safe("storage encryption status (5 = per-user FBE)") { dpm.storageEncryptionStatus }
        }
        val km = getSystemService(KeyguardManager::class.java)
        safe("keyguard secure (this user)") { km.isDeviceSecure }
        safe("keyguard locked (this user)") { km.isDeviceLocked }

        safe("visible profiles") { um.userProfiles.size }
        um.userProfiles.forEach { h ->
            safe("  $h unlocked (CE keys available)") { um.isUserUnlocked(h) }
            safe("  $h quiet mode (paused)") { um.isQuietModeEnabled(h) }
        }

        safe("U6 TEE key security level") { keyLevel("stark0_tee", strongBox = false) }
        safe("U6 StrongBox key security level") { keyLevel("stark0_sb", strongBox = true) }

        val text = r.toString()
        output.text = text
        text.lines().forEach { Log.i(TAG, it) }
    }

    private fun keyLevel(alias: String, strongBox: Boolean): String {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        ks.deleteEntry(alias)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val key: SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
        val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        ks.deleteEntry(alias)
        return when (info.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "STRONGBOX"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TRUSTED_ENVIRONMENT"
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "SOFTWARE"
            else -> "OTHER(${info.securityLevel})"
        }
    }

    private fun lock(flags: Int) {
        Log.i(TAG, "lockNow(flags=$flags)")
        try {
            dpm.lockNow(flags)
            Log.i(TAG, "lockNow(flags=$flags) returned OK")
        } catch (t: Throwable) {
            Log.e(TAG, "lockNow(flags=$flags) failed: $t")
            output.append("\nlockNow failed: $t\n")
        }
    }

    private fun confirmProvision() {
        AlertDialog.Builder(this)
            .setTitle("Create test work profile?")
            .setMessage("Creates an empty STARK-0 work profile. Secure Folder is not touched. Remove it later from inside the profile copy of this app.")
            .setPositiveButton("Create") { _, _ -> provision() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun provision() {
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            .putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, admin)
        try {
            startActivityForResult(intent, REQ_PROVISION)
        } catch (t: Throwable) {
            output.append("\nprovision launch failed: $t\n")
            Log.e(TAG, "provision launch failed", t)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROVISION) {
            val msg = "provisioning result = ${if (resultCode == RESULT_OK) "OK" else "CANCELED/FAILED ($resultCode)"}"
            Log.i(TAG, msg)
            output.append("\n$msg\n")
        }
    }

    private fun confirmRemove() {
        AlertDialog.Builder(this)
            .setTitle("Remove test work profile?")
            .setMessage("Deletes the STARK-0 profile and everything inside it.")
            .setPositiveButton("Remove") { _, _ ->
                Log.i(TAG, "removing profile")
                @Suppress("DEPRECATION")
                dpm.wipeData(0)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val TAG = "STARK0"
        private const val REQ_PROVISION = 1
    }
}

internal fun Context.adminComponent() = ComponentName(this, ProbeAdminReceiver::class.java)
