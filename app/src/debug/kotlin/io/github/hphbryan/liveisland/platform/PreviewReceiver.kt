package io.github.hphbryan.liveisland.platform

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.ChipText
import io.github.hphbryan.liveisland.core.Classifier
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.NotificationSnapshot
import io.github.hphbryan.liveisland.core.PersonRule
import io.github.hphbryan.liveisland.core.SourcePolicy

/**
 * Debug builds only. Runs a made-up notification through [Classifier] and posts the result, to see how
 * One UI lays out the chip without waiting for a real bus, ride or message:
 *
 *     adb shell "am broadcast -n io.github.hphbryan.liveisland.debug/io.github.hphbryan.liveisland.platform.PreviewReceiver \
 *         --es title '12 往火車站' --es text '約 3 分鐘到站' --ei id 0"
 *
 * Extras: `title`, `text`; `sender` makes it a chat message; `ongoing false` makes it a one-off;
 * `pkg` pretends it came from that app (its name is used, e.g. for "ChatGPT 已回覆"); `chip` posts that
 * exact chip text instead; `--ei progress 40` gives it a progress bar at that percent. The whole command is quoted so the device's shell keeps text with spaces together.
 *
 * `--es enable <package>` (or `disable`) turns an app on with brief display, or off, instead: with
 * `com.android.shell`, notifications from `adb shell cmd notification post` then go through the real
 * listener, for testing relaying, Dismiss and removal end to end. `--es dismiss <source key>` does what
 * the Dismiss button on that source's relayed copy does.
 */
class PreviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("enable")?.let { pkg ->
            SettingsStore.get(context).run { setEnabled(pkg, true); setFlash(pkg, true) }
            return
        }
        intent.getStringExtra("dismiss")?.let { sourceKey ->
            // What the Dismiss button on a relayed copy does, without tapping it in the shade.
            Relay.get(context).dismiss(sourceKey)
            return
        }
        intent.getStringExtra("disable")?.let { pkg ->
            SettingsStore.get(context).setEnabled(pkg, false)
            return
        }
        LiveNotifications.ensureChannel(context)
        val id = intent.getIntExtra("id", 0)
        val pkg = intent.getStringExtra("pkg") ?: "preview.app"
        val title = intent.getStringExtra("title") ?: "Preview"
        val text = intent.getStringExtra("text")
        val sender = intent.getStringExtra("sender")
        val content = intent.getStringExtra("chip")?.let {
            LiveContent("preview-$id", pkg, title, text, ChipText.Headline(it), null, null, null, LiveContent.Kind.ONGOING)
        } ?: classify(id, pkg, title, text, sender, intent.getBooleanExtra("ongoing", true), intent.getIntExtra("progress", 0))
            ?: return
        val notification = LiveNotifications.build(
            context = context,
            content = content,
            smallIcon = Icon.createWithResource(context, R.drawable.ic_island),
            largeIcon = null,
            app = AppIcons(context).identity(pkg),
            contentIntent = null,
            timeoutMillis = PREVIEW_MILLIS,
        )
        context.getSystemService(NotificationManager::class.java).notify("preview", PREVIEW_ID_BASE + id, notification)
    }

    private fun classify(id: Int, pkg: String, title: String, text: String?, sender: String?, ongoing: Boolean, progress: Int): LiveContent? {
        val snapshot = NotificationSnapshot(
            key = "preview-$id", packageName = pkg, channelId = "preview", category = null,
            title = title, text = text, bigText = null, isOngoing = ongoing, isGroupSummary = false,
            isPromoted = false, isSamsungLiveNotification = false, isMedia = false,
            progress = progress, progressMax = if (progress > 0) 100 else 0, progressIndeterminate = false,
            showsChronometer = false, chronometerCountsDown = false, whenMillis = System.currentTimeMillis(),
            sender = sender, isConversation = sender != null,
        )
        val everything = object : SourcePolicy {
            override fun isEnabled(packageName: String) = true
            override fun isFlashEnabled(packageName: String) = true
            override fun ignoredKeywords(packageName: String) = emptyList<String>()
            override fun people(packageName: String) = emptyList<PersonRule>()
        }
        return (Classifier.classify(snapshot, everything) as? Classifier.Decision.Relay)?.content
    }

    private companion object {
        const val PREVIEW_ID_BASE = 100
        const val PREVIEW_MILLIS = 120_000L
    }
}
