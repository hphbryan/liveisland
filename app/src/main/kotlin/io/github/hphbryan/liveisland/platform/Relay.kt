package io.github.hphbryan.liveisland.platform

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.SourcePolicy
import io.github.hphbryan.liveisland.core.UpdateThrottle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Posts, updates and removes relayed Live Updates. Event driven only: no polling, wake locks or
 * foreground service. One per process, shared by the listener and the Dismiss button. Main thread only.
 */
class Relay private constructor(context: Context) {
    private val ctx = context.applicationContext
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val icons = AppIcons(ctx)
    private val throttle = UpdateThrottle(MIN_UPDATE_INTERVAL_MS) { SystemClock.elapsedRealtime() }

    /** Source notification each relayed copy was built from, for its icons and tap action. */
    private val sources = HashMap<String, Notification>()

    /** Flash copies currently on screen, with when each was posted; kept across a reconnect until they time out. */
    private val flashing = HashMap<String, Long>()

    /**
     * Sources the user dismissed. They are not shown again while the source notification lasts, even if
     * it keeps updating; the throttle keeps tracking them so that its removal still clears this entry.
     */
    private val dismissed = HashSet<String>()

    /**
     * [dismissed] on disk, so a copy the user dismissed does not come back when One UI restarts the process.
     * Written only when the set changes, which happens when the user dismisses something or its source ends.
     */
    private val dismissedPrefs = ctx.getSharedPreferences("relay", Context.MODE_PRIVATE)

    init {
        LiveNotifications.ensureChannel(ctx)
        dismissed += dismissedPrefs.getStringSet(KEY_DISMISSED, null).orEmpty()
    }

    /** Applies [change] to [dismissed] and saves it if anything changed. */
    private inline fun editDismissed(change: MutableSet<String>.() -> Boolean) {
        if (dismissed.change()) dismissedPrefs.edit { putStringSet(KEY_DISMISSED, HashSet(dismissed)) }
    }

    fun show(content: LiveContent, source: Notification) {
        if (content.sourceKey in dismissed) return
        sources[content.sourceKey] = source
        when (val action = throttle.offer(content)) {
            UpdateThrottle.Action.Post -> post(content)
            UpdateThrottle.Action.Skip -> Unit
            is UpdateThrottle.Action.Defer -> main.postDelayed({
                throttle.takeDeferred(content.sourceKey)?.let(::post)
            }, action.delayMillis)
        }
    }

    /**
     * Removes the relayed copy of [sourceKey]. [ended]: the source notification itself went away, so a
     * Dismiss on it is forgotten too; otherwise (the source just stopped qualifying, e.g. a paused timer)
     * a dismissed source stays dismissed if it qualifies again.
     */
    fun hide(sourceKey: String, ended: Boolean) {
        if (ended) editDismissed { remove(sourceKey) }
        if (!throttle.isTracked(sourceKey) && sourceKey !in sources) return
        forget(sourceKey)
        nm.cancel(sourceKey, NOTIFICATION_ID)
    }

    /** Our copy of [sourceKey] went away by itself (timed out, tapped, swiped); keep the home screen in step. */
    fun copyRemoved(sourceKey: String) {
        flashing -= sourceKey
        removeShown(sourceKey)
    }

    /** Takes every relayed copy down, e.g. when notification access is revoked and nothing would update them. */
    fun clearAll() {
        nm.activeNotifications.filter { it.id == NOTIFICATION_ID }.forEach { nm.cancel(it.tag, NOTIFICATION_ID) }
        sources.keys.toList().forEach(::forget)
        editDismissed { isNotEmpty().also { clear() } }
    }

    /** Hides a relayed copy at the user's request, until its source notification goes away. */
    fun dismiss(sourceKey: String) {
        editDismissed { add(sourceKey) }
        flashing.remove(sourceKey)
        throttle.takeDeferred(sourceKey)
        removeShown(sourceKey)
        nm.cancel(sourceKey, NOTIFICATION_ID)
    }

    /**
     * Removes relayed copies whose source is not in [keep], including ones left from before a restart, and
     * forgets everything about sources that are no longer posted at all ([active]).
     */
    fun retainOnly(keep: Set<String>, active: Set<String>) {
        nm.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.tag != null && it.tag !in keep && it.tag !in flashing }
            .forEach {
                forget(it.tag)
                nm.cancel(it.tag, NOTIFICATION_ID)
            }
        (sources.keys - active).forEach(::forget)
        editDismissed { retainAll(active) }
    }

    private fun post(content: LiveContent) {
        if (content.sourceKey in dismissed) return
        val source = sources[content.sourceKey] ?: return
        val app = icons.identity(content.packageName)
        val notification = LiveNotifications.build(
            context = ctx,
            content = content,
            smallIcon = icons.smallIcon(content.packageName, source.smallIcon),
            largeIcon = icons.largeIcon(content.packageName, source.getLargeIcon()),
            app = app,
            contentIntent = source.contentIntent,
            actions = LiveNotifications.sourceActions(source) +
                DismissReceiver.action(ctx, content.sourceKey, NOTIFICATION_ID),
        )
        // A source's icons or buttons can be refused when re-posted (an image from another app, too large):
        // try once without them rather than letting one notification crash the listener.
        val posted = runCatching { nm.notify(content.sourceKey, NOTIFICATION_ID, notification) }.recoverCatching {
            nm.notify(content.sourceKey, NOTIFICATION_ID, LiveNotifications.build(
                context = ctx,
                content = content,
                smallIcon = icons.smallIcon(content.packageName, source.smallIcon),
                largeIcon = null,
                app = app,
                contentIntent = source.contentIntent,
                actions = listOf(DismissReceiver.action(ctx, content.sourceKey, NOTIFICATION_ID)),
            ))
        }
        if (posted.isFailure) {
            sources -= content.sourceKey
            return
        }
        throttle.markPosted(content)
        val shownAt = SystemClock.elapsedRealtime()
        val chipText = content.chip?.let { LiveNotifications.chipLabel(ctx, it, content.chipDetail) } ?: content.title
        addShown(Shown(content.sourceKey, content.packageName, chipText, shownAt))
        if (content.kind == LiveContent.Kind.FLASH) {
            // The system removes it after the timeout; the throttle keeps its fingerprint so an
            // identical re-post of the same source notification does not flash again.
            flashing[content.sourceKey] = shownAt
            val timeout = content.timeoutMillis ?: SourcePolicy.DEFAULT_FLASH_MILLIS
            main.postDelayed({
                // Only if this is still the same flash; a newer one of the same source has its own timeout.
                if (flashing[content.sourceKey] == shownAt) flashing -= content.sourceKey
                removeShown(content.sourceKey, shownAt)
            }, timeout)
        }
    }

    private fun forget(key: String) {
        throttle.forget(key)
        sources -= key
        flashing -= key
        removeShown(key)
    }

    /** A relayed Live Update on screen now, as the home screen mirrors it. Held in memory only. */
    data class Shown(val sourceKey: String, val packageName: String, val chipText: String, val shownAt: Long)

    companion object {
        /** All relayed copies share this id and are told apart by tag (the source key). */
        const val NOTIFICATION_ID = 1
        const val TEST_NOTIFICATION_ID = 2

        /** [Shown.sourceKey] of the test Live Update posted from the home screen. */
        const val TEST_KEY = "test"
        private const val KEY_DISMISSED = "dismissed"
        private const val MIN_UPDATE_INTERVAL_MS = 1_000L

        private val shownState = MutableStateFlow<List<Shown>>(emptyList())

        /** What relayed Live Updates show right now, newest first. Cleared with the process. */
        val shown: StateFlow<List<Shown>> = shownState.asStateFlow()

        fun addShown(item: Shown) = shownState.update { list -> listOf(item) + list.filterNot { it.sourceKey == item.sourceKey } }

        /** Removes [sourceKey]; with [shownAt], only if it is still that post (a newer one stays). */
        fun removeShown(sourceKey: String, shownAt: Long? = null) = shownState.update { list ->
            list.filterNot { it.sourceKey == sourceKey && (shownAt == null || it.shownAt == shownAt) }
        }

        // Holds only the application context (see ctx), which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: Relay? = null

        fun get(context: Context): Relay =
            instance ?: synchronized(this) { instance ?: Relay(context).also { instance = it } }
    }
}
