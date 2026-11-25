package io.github.hphbryan.liveisland.platform

import android.app.Notification
import android.os.Bundle
import android.app.NotificationManager
import android.os.Parcelable
import android.os.Process
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import io.github.hphbryan.liveisland.core.LockScreen
import io.github.hphbryan.liveisland.core.NotificationSnapshot

/** Converts a posted notification into the platform-independent [NotificationSnapshot]. */
object SnapshotReader {
    /** Present on notifications that use One UI's own Live Notification format. */
    private const val SAMSUNG_LIVE_STYLE = "android.ongoingActivityNoti.style"

    /**
     * Channels Android's notification classification moves notifications into (NotificationChannel's
     * PROMOTIONS_ID, NEWS_ID, RECS_ID and SOCIAL_MEDIA_ID).
     */
    private val BULK_CHANNELS = setOf("android.app.promotions", "android.app.news", "android.app.recs", "android.app.social")

    /**
     * [ranking]: what the system decided about the notification (channel importance, Do Not Disturb, ...).
     * [withSender]: also find who sent the latest message, which means decoding the whole conversation;
     * only needed for apps the user turned on.
     */
    fun read(sbn: StatusBarNotification, ranking: Ranking?, withSender: Boolean = true): NotificationSnapshot {
        val n = sbn.notification
        val e = n.extras
        return NotificationSnapshot(
            key = sbn.key,
            packageName = sbn.packageName,
            channelId = n.channelId,
            channelName = ranking?.channel?.name?.toString(),
            category = n.category,
            title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isPromoted = n.flags and Notification.FLAG_PROMOTED_ONGOING != 0 ||
                e.getBoolean(LiveNotifications.EXTRA_REQUEST_PROMOTED_ONGOING, false),
            isSamsungLiveNotification = e.containsKey(SAMSUNG_LIVE_STYLE),
            isMedia = e.containsKey(Notification.EXTRA_MEDIA_SESSION),
            progress = e.getInt(Notification.EXTRA_PROGRESS, 0),
            progressMax = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0),
            progressIndeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false),
            showsChronometer = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            chronometerCountsDown = e.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
            whenMillis = n.`when`,
            sender = if (withSender) latestSender(e) else null,
            conversationTitle = e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
            isConversation = e.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name,
            isHiddenBySystem = ranking != null &&
                (ranking.importance <= NotificationManager.IMPORTANCE_MIN || ranking.isSuspended),
            interruptsNow = ranking?.matchesInterruptionFilter() ?: true,
            lockScreen = lockScreen(sbn, ranking),
            isClassifiedAsBulk = ranking?.channel?.id in BULK_CHANNELS,
        )
    }

    private fun lockScreen(sbn: StatusBarNotification, ranking: Ranking?): LockScreen {
        val override = ranking?.lockscreenVisibilityOverride ?: Ranking.VISIBILITY_NO_OVERRIDE
        return when {
            // The user set this app or channel to hide its content (PRIVATE) or itself (SECRET) on the lock
            // screen. Our copy cannot carry a per-app setting, so it stays off the lock screen entirely.
            sbn.notification.visibility == Notification.VISIBILITY_SECRET ||
                override == Notification.VISIBILITY_SECRET || override == Notification.VISIBILITY_PRIVATE -> LockScreen.HIDDEN
            // A work-profile notification re-posted in the personal profile would escape the work profile's
            // own lock screen rules.
            sbn.user != Process.myUserHandle() -> LockScreen.APP_NAME_ONLY
            else -> LockScreen.DEFAULT
        }
    }

    /** Sender of the newest message in a MessagingStyle notification. */
    private fun latestSender(extras: Bundle): String? {
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java) ?: return null
        val last = Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles).lastOrNull() ?: return null
        return (last.senderPerson?.name ?: return null).toString().trim().ifEmpty { null }
    }
}
