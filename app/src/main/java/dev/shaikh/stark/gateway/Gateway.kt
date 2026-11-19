package dev.shaikh.stark.gateway

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.UserHandle
import android.os.UserManager
import dev.shaikh.stark.StarkActivity
import dev.shaikh.stark.admin.StarkAdminReceiver

/** What the personal-side gateway can see of STARK, using only public cross-profile APIs. */
sealed interface GatewayState {
    data object CanInitialize : GatewayState
    data class Blocked(val reason: String) : GatewayState
    data class Ready(val profile: UserHandle, val keysLoaded: Boolean, val paused: Boolean) : GatewayState
}

object Gateway {

    fun read(context: Context): GatewayState {
        val profile = context.getSystemService(CrossProfileApps::class.java)
            .targetUserProfiles
            .firstOrNull()
        if (profile != null) {
            val um = context.getSystemService(UserManager::class.java)
            return GatewayState.Ready(
                profile = profile,
                keysLoaded = um.isUserUnlocked(profile),
                paused = um.isQuietModeEnabled(profile),
            )
        }
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return if (dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) {
            GatewayState.CanInitialize
        } else {
            GatewayState.Blocked(
                "Another work profile already exists on this phone, and Android allows only one. " +
                    "Remove it in Settings › Accounts and backup, then open STARK again.",
            )
        }
    }

    fun provisioningIntent(context: Context): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
            ComponentName(context, StarkAdminReceiver::class.java),
        )

    /** Opens the STARK shell inside the profile. Android shows the STARK PIN prompt if STARK is locked. */
    fun enter(activity: Activity, profile: UserHandle) {
        activity.getSystemService(CrossProfileApps::class.java)
            .startMainActivity(ComponentName(activity, StarkActivity::class.java), profile)
    }
}
