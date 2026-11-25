package io.github.hphbryan.liveisland.platform

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
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
    /** LiveIsland's own "Live updates" notification type is not turned off in system settings. */
    val channelEnabled: Boolean = true,
) {
    val ready get() = listenerGranted && postGranted && promotionAllowed && channelEnabled

    companion object {
        fun read(context: Context): SetupStatus {
            val nm = context.getSystemService(NotificationManager::class.java)
            return SetupStatus(
                listenerGranted = nm.isNotificationListenerAccessGranted(RelayListenerService.component(context)),
                postGranted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                promotionAllowed = runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false),
                batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
                // Not created yet counts as on: it is created, on, before the first relayed copy is posted.
                channelEnabled = nm.getNotificationChannel(LiveNotifications.CHANNEL_ID)
                    ?.let { it.importance != NotificationManager.IMPORTANCE_NONE } ?: true,
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

    /** The switch that lets LiveIsland's notifications be shown as Live Updates. */
    fun promotionSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** LiveIsland's notification settings: the fallback for every notification-related step. */
    fun notificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The "Live updates" notification type's own settings. */
    fun channelSettings(context: Context) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, LiveNotifications.CHANNEL_ID)

    /** This app's App info page, where restricted settings are allowed for a sideloaded install. */
    fun appInfo(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(android.net.Uri.fromParts("package", context.packageName, null))

    /** The battery optimisation list, if App info cannot be opened. */
    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * Opens the first of [intents] this phone has. Some phones leave out a settings page, and opening a
     * missing one would crash the app. Returns false if none could be opened.
     */
    fun open(context: Context, vararg intents: Intent): Boolean = intents.any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

/** Facts about this phone that change what the app says. */
object Device {
    /** Only Samsung's One UI has the Now Bar and the developer option for Live Updates of all apps. */
    val isSamsung: Boolean = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
}
