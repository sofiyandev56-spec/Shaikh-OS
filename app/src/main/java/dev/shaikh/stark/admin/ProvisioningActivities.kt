package dev.shaikh.stark.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Bundle

/** Android 12+ asks the DPC which mode to provision. STARK only ever creates a work profile. */
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

/** Baseline policies are applied by [StarkPolicy.ensureBaseline]; nothing extra to collect here. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
