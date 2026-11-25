package io.github.hphbryan.liveisland.platform

import android.content.ComponentName
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.hphbryan.liveisland.BuildConfig
import io.github.hphbryan.liveisland.core.Classifier
import io.github.hphbryan.liveisland.core.Classifier.Decision
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.NotificationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Watches notifications from the apps the user enabled and mirrors them as Live Updates. */
class RelayListenerService : NotificationListenerService() {
    private lateinit var settings: SettingsStore
    private lateinit var relay: Relay
    private lateinit var samples: SampleLog
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var settingsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore.get(this)
        relay = Relay.get(this)
        samples = SampleLog.get(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        reconcile()
        settingsJob?.cancel()
        settingsJob = scope.launch {
            settings.state
                .map {
                    listOf(
                        it.enabled, it.flash, it.people, it.keywords, it.sharedKeywords, it.paused, it.channelChoices,
                        it.chipSources, it.first, it.overview,
                    )
                }
                .distinctUntilChanged().drop(1)
                .collect { reconcile() }
        }
    }

    override fun onListenerDisconnected() {
        settingsJob?.cancel()
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.isNotificationListenerAccessGranted(component(this))) {
            // One UI's battery management sometimes unbinds listeners; ask to be bound again.
            requestRebind(component(this))
        } else {
            // Access was turned off: nothing would update or remove the copies any more.
            relay.clearAll()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) {
        if (sbn.packageName == packageName) return
        val recording = isRecording(sbn)
        // Called for every notification on the phone: drop what can never be relayed (or logged) before
        // reading anything from it.
        if (!recording && (sbn.packageName in KnownApps.SYSTEM || sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)) return
        val enabled = settings.isEnabled(sbn.packageName)
        val ranking = rankingMap.rankingFor(sbn.key)
        val snapshot = SnapshotReader.read(sbn, ranking, withSender = enabled)
        if (Classifier.isCandidate(snapshot) && Classifier.isOngoingActivity(snapshot)) settings.markSeen(snapshot.packageName)
        if (enabled) {
            // Offered on the app's page, to choose which kinds of its notifications to show.
            val channel = ranking?.channel
            if (channel != null && !channel.name.isNullOrBlank()) settings.rememberChannel(sbn.packageName, channel.id, channel.name.toString())
        }
        if (snapshot.isConversation && enabled) {
            // Offered when adding people. Group name first so the sender ends up newest.
            settings.rememberPeople(snapshot.packageName, listOfNotNull(snapshot.conversationTitle, snapshot.sender))
        }
        val explanation = Classifier.explain(snapshot, settings)
        // Debug builds only, and never notification text: package, channel and decision.
        if (BuildConfig.DEBUG) Log.d(TAG, "posted ${sbn.packageName} channel=${snapshot.channelId} -> $explanation")
        if (isWorthLogging(snapshot, explanation)) ActivityLog.record(sbn.key, sbn.packageName, explanation)
        // Everything from enabled apps, including what is skipped and why, so app rules can be written from evidence.
        if (recording) samples.record(sbn, explanation)
        when (val decision = Classifier.classify(snapshot, settings)) {
            is Decision.Relay -> relay.show(decision.content, sbn.notification)
            Decision.Remove -> relay.hide(sbn.key, ended = false)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (sbn.packageName == packageName) {
            // Our own cancels already keep the home screen in step; one may also be a copy taken away to be
            // added again at once (see Relay), which is still on screen.
            if (sbn.id == Relay.NOTIFICATION_ID && reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL) {
                sbn.tag?.let(relay::copyRemoved)
            }
            // The test swiped away in the shade leaves the home screen's island too.
            if (sbn.id == Relay.TEST_NOTIFICATION_ID) Relay.removeShown(Relay.TEST_KEY)
            return
        }
        if (isRecording(sbn)) samples.recordRemoval(sbn, reason)
        relay.hide(sbn.key, ended = true)
    }

    /**
     * Everything from enabled apps except structural noise, plus notifications from other apps that would
     * have been shown had the app been on (so the log can suggest turning it on).
     */
    private fun isWorthLogging(s: NotificationSnapshot, explanation: String) = when {
        explanation == "skip:app-off" -> Classifier.isOngoingActivity(s) || s.isConversation
        explanation in NOISE -> false
        else -> settings.isEnabled(s.packageName)
    }

    private fun isRecording(sbn: StatusBarNotification) =
        sbn.packageName != packageName && settings.state.value.recordSamples && settings.isEnabled(sbn.packageName)

    /**
     * Makes the relayed copies match the active source notifications: ongoing activities and unread messages
     * from picked people. Brief (flash) notifications are never replayed.
     */
    private fun reconcile() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val ranking = runCatching { currentRanking }.getOrNull()
        val live = HashSet<String>()
        for (sbn in active) {
            if (sbn.packageName == packageName) continue
            val snapshot = SnapshotReader.read(sbn, ranking?.rankingFor(sbn.key), withSender = settings.isEnabled(sbn.packageName))
            if (!Classifier.isCandidate(snapshot)) continue
            if (Classifier.isOngoingActivity(snapshot)) settings.markSeen(snapshot.packageName)
            val decision = Classifier.classify(snapshot, settings)
            if (decision is Decision.Relay && decision.content.kind != LiveContent.Kind.FLASH) {
                live += sbn.key
                relay.show(decision.content, sbn.notification)
            }
        }
        relay.retainOnly(live, active.mapTo(HashSet()) { it.key })
    }

    private fun RankingMap.rankingFor(key: String): Ranking? = Ranking().takeIf { getRanking(key, it) }

    companion object {
        private const val TAG = "LiveIsland"
        private val NOISE = setOf("skip:system", "skip:group-summary")

        fun component(context: Context) = ComponentName(context, RelayListenerService::class.java)
    }
}
