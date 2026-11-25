package io.github.hphbryan.liveisland.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import io.github.hphbryan.liveisland.ui.MainActivity
import java.time.Instant
import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.KnownApps
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
            is LiveContent.Progress.Determinate -> {
                val bar = app?.color?.let { ProgressTracker.barColor(context, it) }
                b.setStyle(
                    Notification.ProgressStyle()
                        .setProgressSegments(listOf(Notification.ProgressStyle.Segment(p.max).apply { bar?.let(::setColor) }))
                        .setProgress(p.value)
                        .apply {
                            val tracker = bar?.let { ProgressTracker.icon(context, KnownApps.categoryOf(content.packageName), it) }
                            tracker?.let(::setProgressTrackerIcon)
                        },
                )
            }
            LiveContent.Progress.Indeterminate -> b.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            null -> content.text?.let { b.setStyle(Notification.BigTextStyle().bigText(it)) }
        }
        return b.build()
    }

    /** "Adjust": opens the app's page in LiveIsland, where what its chip shows is chosen. */
    fun adjustAction(context: Context, packageName: String): Notification.Action {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_APP, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .setIdentifier("adjust:$packageName")
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_tune), context.getString(R.string.action_adjust_display), pending)
            .build()
    }

    /** One activity in the overview: the app's name and what its copy shows. */
    data class OverviewRow(val label: String, val content: LiveContent)

    /**
     * Android 17's overview of several activities at once: each one's value under its app's name. The first
     * row (an app put first, or the latest activity) is the one shown in the chip, so when the overview is the
     * notification holding the chip, the chip says the same as it would without it. Opens LiveIsland.
     */
    @RequiresApi(37)
    fun overview(context: Context, rows: List<OverviewRow>): Notification {
        val metrics = rows.take(MAX_METRICS).map { Notification.Metric(metricValue(context, it.content), it.label) }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_island)
            .setContentTitle(context.resources.getQuantityString(R.plurals.overview_title, rows.size, rows.size))
            .setContentText(rows.joinToString(" · ") { it.label })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setShowWhen(false)
            .setContentIntent(open)
            .setStyle(Notification.MetricStyle().setMetrics(metrics).setCriticalMetric(0))
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            .build()
    }

    /** A timer keeps running in the overview; minutes and percentages show as numbers; the rest as the chip says. */
    @RequiresApi(37)
    private fun metricValue(context: Context, c: LiveContent): Notification.Metric.MetricValue {
        c.chronometer?.let {
            val at = Instant.ofEpochMilli(it.whenMillis)
            val format = Notification.Metric.TimeDifference.FORMAT_CHRONOMETER
            return if (it.countsDown) Notification.Metric.TimeDifference.forTimer(at, format)
            else Notification.Metric.TimeDifference.forStopwatch(at, format)
        }
        return when (val chip = c.chip) {
            is ChipText.Minutes -> Notification.Metric.FixedInt(chip.minutes, context.getString(R.string.metric_minutes))
            is ChipText.Percent -> Notification.Metric.FixedInt(chip.percent, "%")
            null -> Notification.Metric.FixedText(ChipFit.fit(c.title))
            else -> Notification.Metric.FixedText(chipLabel(context, chip, null, compact = true))
        }
    }

    /** More than this would not fit the card side by side. */
    private const val MAX_METRICS = 3

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
     *
     * Other phones show the chip's text only when it fits whole, so there it is just the value ("3 min"),
     * or the start of the message or words, cut by [ChipFit].
     */
    fun chipLabel(context: Context, chip: ChipText, detail: String?, compact: Boolean = !Device.isSamsung): String {
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
        if (compact) return ChipFit.fit(main)
        return if (detail == null) main else "$main $detail"
    }
}
