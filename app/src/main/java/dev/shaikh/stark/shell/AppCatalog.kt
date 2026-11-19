package dev.shaikh.stark.shell

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An app installed inside the STARK profile (its own instance, data and accounts). */
data class StarkApp(val packageName: String, val label: String, val icon: ImageBitmap)

object AppCatalog {
    const val PLAY_STORE = "com.android.vending"
    private const val ICON_PX = 144

    /** Blocking; call off the main thread. Runs inside the profile, so it only sees STARK's apps. */
    fun load(context: Context): List<StarkApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                StarkApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Every launchable package in this profile except STARK itself. Works before the profile's storage unlocks. */
    fun launchablePackages(context: Context): Set<String> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.MATCH_DIRECT_BOOT_AWARE or PackageManager.MATCH_DIRECT_BOOT_UNAWARE
        return context.packageManager
            .queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(flags.toLong()))
            .map { it.activityInfo.packageName }
            .toSet() - context.packageName
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
