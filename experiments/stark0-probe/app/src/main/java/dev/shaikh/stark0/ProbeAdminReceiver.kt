package dev.shaikh.stark0

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

class ProbeAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = context.adminComponent()
        dpm.setProfileName(admin, "STARK-0 test")
        dpm.setProfileEnabled(admin)
        Log.i(ProbeActivity.TAG, "profile provisioning complete; profile enabled")
    }
}

/** Android 12+ asks the DPC which mode to provision; STARK only ever wants a work profile. */
class GetProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(
            RESULT_OK,
            Intent().putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE,
            ),
        )
        finish()
    }
}

class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
