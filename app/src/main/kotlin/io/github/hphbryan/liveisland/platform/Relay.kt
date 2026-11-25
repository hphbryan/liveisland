package io.github.hphbryan.liveisland.platform

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.hphbryan.liveisland.BuildConfig
import androidx.core.content.edit
import io.github.hphbryan.liveisland.core.ActionPicker
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.LockScreen
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
    private val settings = SettingsStore.get(ctx)

    /** What each relayed copy shows now, as last posted. */
    private val latest = HashMap<String, LiveContent>()

    /** The notification last posted for each copy, to add it again (see [keepFirstOnTop]). */
    private val posted = HashMap<String, Notification>()

    /**
     * When each copy was added to the shade (elapsed time). The status bar's one chip goes to the copy added
     * last; an update does not change that, but removing and adding it again does.
     */
    private val addedAt = HashMap<String, Long>()

    /** What the overview shows now, to post it only when that changes; null when it is not posted. */
    private var overviewShown: List<Any?>? = null

    /** False until the overview was first checked in this process (one may be left from before a restart). */
    private var overviewChecked = false

    /** The activities the overview listed when the user swiped it away. */
    private var overviewSwiped: Set<String>? = null

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
        updateOverview()
    }

    /** Our copy of [sourceKey] went away by itself (timed out, tapped, swiped); keep the home screen in step. */
    fun copyRemoved(sourceKey: String) {
        flashing -= sourceKey
        removeShown(sourceKey)
        // No longer on screen: not in the overview, and added afresh if its source posts again.
        latest -= sourceKey
        posted -= sourceKey
        addedAt -= sourceKey
        updateOverview()
    }

    /** Takes every relayed copy down, e.g. when notification access is revoked and nothing would update them. */
    fun clearAll() {
        nm.activeNotifications.filter { it.id == NOTIFICATION_ID }.forEach { nm.cancel(it.tag, NOTIFICATION_ID) }
        sources.keys.toList().forEach(::forget)
        updateOverview()
        editDismissed { isNotEmpty().also { clear() } }
    }

    /** Hides a relayed copy at the user's request, until its source notification goes away. */
    fun dismiss(sourceKey: String) {
        latest[sourceKey]?.let { Habits.get(ctx).dismissed(it.packageName, sources[sourceKey]?.channelId) }
        editDismissed { add(sourceKey) }
        flashing.remove(sourceKey)
        throttle.takeDeferred(sourceKey)
        removeShown(sourceKey)
        latest -= sourceKey
        posted -= sourceKey
        addedAt -= sourceKey
        nm.cancel(sourceKey, NOTIFICATION_ID)
        updateOverview()
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
        // An app may have just been put first.
        keepFirstOnTop()
        updateOverview()
    }

    private fun post(content: LiveContent) {
        if (content.sourceKey in dismissed) return
        val source = sources[content.sourceKey] ?: return
        val app = icons.identity(content.packageName)
        val actions = LiveNotifications.sourceActions(source)
        // Where to change what the chip shows, when the source's own buttons leave room for it.
        val adjust = LiveNotifications.adjustAction(ctx, content.packageName).takeIf { actions.size < ActionPicker.MAX_SOURCE_ACTIONS }
        val notification = LiveNotifications.build(
            context = ctx,
            content = content,
            smallIcon = icons.smallIcon(content.packageName, source.smallIcon),
            largeIcon = icons.largeIcon(content.packageName, source.getLargeIcon()),
            app = app,
            contentIntent = source.contentIntent,
            actions = actions + DismissReceiver.action(ctx, content.sourceKey, NOTIFICATION_ID) + listOfNotNull(adjust),
        )
        // A source's icons or buttons can be refused when re-posted (an image from another app, too large):
        // try once without them rather than letting one notification crash the listener.
        val result = runCatching { notification.also { nm.notify(content.sourceKey, NOTIFICATION_ID, it) } }.recoverCatching {
            LiveNotifications.build(
                context = ctx,
                content = content,
                smallIcon = icons.smallIcon(content.packageName, source.smallIcon),
                largeIcon = null,
                app = app,
                contentIntent = source.contentIntent,
                actions = listOf(DismissReceiver.action(ctx, content.sourceKey, NOTIFICATION_ID)),
            ).also { nm.notify(content.sourceKey, NOTIFICATION_ID, it) }
        }
        if (result.isFailure) {
            sources -= content.sourceKey
            return
        }
        throttle.markPosted(content)
        val added = content.sourceKey !in posted
        latest[content.sourceKey] = content
        posted[content.sourceKey] = result.getOrThrow()
        if (added) addedAt[content.sourceKey] = SystemClock.elapsedRealtime()
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
        if (added) keepFirstOnTop()
        updateOverview()
    }

    /**
     * Keeps an activity from an app the user put first as the last one added, so it keeps the status bar
     * chip: when another activity was added after it, it is taken away and added again (its card stays
     * where it was). One-offs and messages are left to take the chip while they last.
     */
    private fun keepFirstOnTop() {
        val ongoing = latest.values.filter { it.kind == LiveContent.Kind.ONGOING && it.sourceKey in posted }
        val newestOther = ongoing.filterNot { settings.isFirst(it.packageName) }.maxOfOrNull { addedAt[it.sourceKey] ?: 0L } ?: return
        ongoing.filter { settings.isFirst(it.packageName) && (addedAt[it.sourceKey] ?: 0L) < newestOther }
            .forEach { addAgain(it.sourceKey) }
    }

    /** Takes [sourceKey]'s copy away and adds it again, making it the last added, the one with the chip. */
    private fun addAgain(sourceKey: String) {
        val notification = posted[sourceKey] ?: return
        nm.cancel(sourceKey, NOTIFICATION_ID)
        runCatching { nm.notify(sourceKey, NOTIFICATION_ID, notification) }
        addedAt[sourceKey] = SystemClock.elapsedRealtime()
    }

    /**
     * On Android 17, while several activities are on, one more card lists them all, each with its value
     * (Notification.MetricStyle). Only activities whose copies may show on the lock screen are listed.
     */
    private fun updateOverview() {
        val items = overviewItems()
            // The one holding the chip first: an app put first, then the latest added.
            .sortedWith(
                compareByDescending<LiveContent> { settings.isFirst(it.packageName) }.thenByDescending { addedAt[it.sourceKey] ?: 0L },
            )
        if (Build.VERSION.SDK_INT < 37 || !settings.state.value.overview || items.size < 2) {
            // Before the first check in this process, one may be left from before a restart.
            if (overviewShown != null || !overviewChecked) nm.cancel(OVERVIEW_ID)
            overviewChecked = true
            overviewShown = null
            overviewSwiped = null
            return
        }
        overviewChecked = true
        // Swiped away: not again for the same activities.
        val keys = items.mapTo(HashSet()) { it.sourceKey }
        if (keys == overviewSwiped) return
        overviewSwiped = null
        val key = items.map { listOf(it.packageName, it.chip, it.chronometer, it.title) }
        if (key == overviewShown) return
        val rows = items.map { LiveNotifications.OverviewRow(icons.identity(it.packageName)?.name ?: it.title, it) }
        val added = overviewShown == null
        runCatching { nm.notify(OVERVIEW_ID, LiveNotifications.overview(ctx, rows)) }
            .onSuccess {
                overviewShown = key
                // Just added, the overview would take the chip, with LiveIsland's icon and a bare number:
                // give it back to the activity that had it, with its app's icon and words.
                if (added) addAgain(items.first().sourceKey)
            }
            // Debug builds only, and no notification text.
            .onFailure { if (BuildConfig.DEBUG) Log.w(TAG, "overview not posted", it) }
    }

    /** Activities the overview lists: on, may show on the lock screen, not dismissed. */
    private fun overviewItems() = latest.values.filter {
        it.kind == LiveContent.Kind.ONGOING && it.lockScreen == LockScreen.DEFAULT && it.sourceKey !in dismissed && it.sourceKey in sources
    }

    /** The user swiped the overview away: leave it off until the activities in it change. */
    fun overviewRemoved() {
        if (overviewShown == null) return
        overviewSwiped = overviewItems().mapTo(HashSet()) { it.sourceKey }
        overviewShown = null
    }

    private fun forget(key: String) {
        throttle.forget(key)
        latest -= key
        posted -= key
        addedAt -= key
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

        /** The Android 17 card listing every activity at once. */
        const val OVERVIEW_ID = 3
        private const val TAG = "LiveIsland"

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
