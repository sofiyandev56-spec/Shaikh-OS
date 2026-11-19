package dev.shaikh.stark.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Receives the "work profile created" event inside the new STARK profile. */
class StarkAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        StarkPolicy(context).ensureBaseline()
        Log.i(StarkPolicy.TAG, "STARK profile provisioned and enabled")
    }
}
