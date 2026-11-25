package io.github.hphbryan.liveisland.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.ActionPicker
import io.github.hphbryan.liveisland.core.ChipText
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.LockScreen

/**
 * Builds notifications that qualify as Android 16 Live Updates: ongoing, titled, not colorized,
 * no custom views, not a group summary, Standard/BigText/Progress style, and requesting promotion.
 */
object LiveNotifications {
    const val CHANNEL_ID = "live"

    /** Same value as Notification.EXTRA_REQUEST_PROMOTED_ONGOING (API 36.1); as an extra it also works on 36.0. */
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    // One UI's own Live Notification extras, read by Samsung SystemUI and ignored elsewhere.
    // Observed on One UI 9: nowbarIcon and aodRemoteApp* replace our icon/name in the Now Bar and on AOD.
    private const val SEC_NOWBAR_ICON = "android.ongoingActivityNoti.nowbarIcon"
    private const val SEC_REMOTE_APP_ICON = "android.ongoingActivityNoti.aodRemoteAppIcon"
    private const val SEC_REMOTE_APP_NAME = "android.ongoingActivityNoti.aodRemoteAppName"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // LOW: silent and no heads-up, but still eligible for promotion (only MIN is excluded).
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_live_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_live_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel) // No-op for an existing channel apart from name/description.
    }

    fun build(
        context: Context,
        content: LiveContent,
        smallIcon: Icon,
        largeIcon: Icon?,
        app: AppIdentity?,
        contentIntent: PendingIntent?,
        actions: List<Notification.Action> = emptyList(),
        timeoutMillis: Long? = content.timeoutMillis,
    ): Notification {
        val b = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })

        actions.forEach(b::addAction)
        if (app != null) {
            // The card tints to the app's colour, so it reads as that app's rather than ours.
            app.color?.let(b::setColor)
            // The app's icon goes on the card's right unless the source has its own picture (a contact photo);
            // then name the app instead, unless the title already does.
            if (largeIcon != null && !content.title.contains(app.name, ignoreCase = true)) b.setSubText(app.name)
        }
        (largeIcon ?: app?.icon)?.let { b.setLargeIcon(it) }
        content.chip?.let { b.setShortCriticalText(chipLabel(context, it, content.chipDetail)) }
        content.chronometer?.let {
            b.setWhen(it.whenMillis).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(it.countsDown)
        }
        timeoutMillis?.let { b.setTimeoutAfter(it) }
        if (content.kind != LiveContent.Kind.ONGOING) {
            // One-off content goes away when tapped; its source opens and the message is read.
            b.setAutoCancel(true)
        }
        if (content.lockScreen == LockScreen.HIDDEN) {
            b.setVisibility(Notification.VISIBILITY_SECRET)
        } else if (content.kind != LiveContent.Kind.ONGOING || content.lockScreen == LockScreen.APP_NAME_ONLY) {
            // Messages and one-offs carry what someone wrote, now in the chip too, and some sources ask for
            // their content to stay off the lock screen. When it is hidden, show only which app it is from.
            b.setVisibility(Notification.VISIBILITY_PRIVATE)
            b.setPublicVersion(
                Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(smallIcon)
                    .setContentTitle(app?.name ?: context.getString(R.string.app_name))
                    .apply { if (content.kind != LiveContent.Kind.ONGOING) setShortCriticalText(context.getString(R.string.chip_new)) }
                    .build(),
            )
        }
        if (app != null) {
            b.addExtras(Bundle().apply {
                putParcelable(SEC_NOWBAR_ICON, app.icon)
                putParcelable(SEC_REMOTE_APP_ICON, app.icon)
                putString(SEC_REMOTE_APP_NAME, app.name)
            })
        }

        when (val p = content.progress) {
            is LiveContent.Progress.Determinate -> b.setStyle(
                Notification.ProgressStyle()
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(p.max).apply { app?.color?.let(::setColor) }))
                    .setProgress(p.value)
                    // The app's own icon rides the bar, so how far along it is reads as whose it is.
                    .apply { app?.let { setProgressTrackerIcon(it.icon) } },
            )
            LiveContent.Progress.Indeterminate -> b.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            null -> content.text?.let { b.setStyle(Notification.BigTextStyle().bigText(it)) }
        }
        return b.build()
    }

    /**
     * The source notification's buttons worth carrying over (see [ActionPicker]). The actions are reused
     * as-is, so their PendingIntents and RemoteInputs still go to the source app: an inline reply typed on
     * the relayed copy is delivered to that app exactly as if it had been typed on the original.
     */
    fun sourceActions(source: Notification): List<Notification.Action> {
        val actions = source.actions ?: return emptyList()
        return ActionPicker.pick(actions.map { ActionPicker.SourceAction(it.title?.toString(), it.isContextual) })
            .map { actions[it] }
    }

    /**
     * The chip text: a value with what it is about ("3 min 12 to Central Station"), a message ("Amy: on my
     * way"), or the notification's own words. One UI fades out what does not fit.
     */
    fun chipLabel(context: Context, chip: ChipText, detail: String?): String {
        val main = when (chip) {
            is ChipText.Minutes -> context.getString(R.string.chip_minutes, chip.minutes)
            is ChipText.Distance -> "${chip.amount} ${chip.unit}"
            is ChipText.ClockTime -> chip.time
            is ChipText.Percent -> "${chip.percent}%"
            // The chip already shows the app's icon, and its width is a few characters: no name.
            ChipText.Replied -> context.getString(R.string.chip_replied)
            is ChipText.Message -> chip.preview?.let { context.getString(R.string.chip_message, chip.sender, it) }
                ?: chip.sender
            is ChipText.Headline -> chip.text
            ChipText.Arriving -> context.getString(R.string.chip_arriving)
            is ChipText.Stops -> context.resources.getQuantityString(R.plurals.chip_stops, chip.stops, chip.stops)
        }
        return if (detail == null) main else "$main $detail"
    }
}
