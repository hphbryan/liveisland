package io.github.hphbryan.liveisland.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings

/** Which of the one-time setup steps are done, and where to send the user for each. */
data class SetupStatus(
    val listenerGranted: Boolean,
    val postGranted: Boolean,
    val promotionAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val ready get() = listenerGranted && postGranted && promotionAllowed

    companion object {
        fun read(context: Context): SetupStatus {
            val nm = context.getSystemService(NotificationManager::class.java)
            return SetupStatus(
                listenerGranted = nm.isNotificationListenerAccessGranted(RelayListenerService.component(context)),
                postGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                promotionAllowed = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false),
                batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

object SetupIntents {
    fun listenerSettings(context: Context) = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            RelayListenerService.component(context).flattenToString(),
        )

    /** The first of these that resolves: promotion toggle, app notification settings, developer options. */
    fun promotionSettings(context: Context): Intent? = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    ).firstOrNull { it.resolveActivity(context.packageManager) != null }

    /** This app's App info page, where restricted settings are allowed for a sideloaded install. */
    fun appInfo(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null))

    /** The battery optimisation list; needs no special permission (unlike requesting an exemption directly). */
    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
