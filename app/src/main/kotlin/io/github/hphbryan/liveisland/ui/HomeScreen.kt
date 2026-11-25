package io.github.hphbryan.liveisland.ui

import io.github.hphbryan.liveisland.platform.PauseTileService
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.widget.Toast
import android.content.ContextWrapper
import android.app.Activity
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.ChipText
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.core.LiveContent
import io.github.hphbryan.liveisland.core.TextShortener
import io.github.hphbryan.liveisland.platform.ActivityLog
import io.github.hphbryan.liveisland.platform.Device
import io.github.hphbryan.liveisland.platform.DismissReceiver
import io.github.hphbryan.liveisland.platform.LiveNotifications
import io.github.hphbryan.liveisland.platform.Habits
import io.github.hphbryan.liveisland.platform.Relay
import io.github.hphbryan.liveisland.platform.RelayListenerService
import io.github.hphbryan.liveisland.platform.SettingsStore
import io.github.hphbryan.liveisland.platform.SetupIntents
import io.github.hphbryan.liveisland.platform.SetupStatus

/** What the user can do from the island at the top of the home screen. */
private class SetupActions(
    val onListener: () -> Unit,
    val onPost: () -> Unit,
    val onPromotion: () -> Unit,
    val onChannel: () -> Unit,
    val onAppInfo: () -> Unit,
    val onBattery: () -> Unit,
    val onTest: () -> Unit,
)

/**
 * The island first: what is on it right now (or the steps still missing), then the apps that feed it.
 * Apps that are not on yet live on the Add page, so this screen stays short.
 */
@Composable
fun HomeScreen(
    padding: PaddingValues,
    onOpenApp: (String) -> Unit,
    onAddApp: () -> Unit,
    onActivity: () -> Unit,
    onTestSent: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    val shown by Relay.shown.collectAsStateWithLifecycle()
    val entries by ActivityLog.entries.collectAsStateWithLifecycle()
    val habits = remember { Habits.get(context) }
    val habitCounts by habits.state.collectAsStateWithLifecycle()
    var setup by remember { mutableStateOf(SetupStatus.read(context)) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        setup = SetupStatus.read(context)
    }

    LifecycleResumeEffect(Unit) {
        setup = SetupStatus.read(context)
        // Recover if One UI unbound the listener while the app was in the background.
        if (setup.listenerGranted) NotificationListenerService.requestRebind(RelayListenerService.component(context))
        onPauseOrDispose { }
    }

    // Opens the first settings page this phone has; if it has none, says where to go by hand.
    val openFailed = stringResource(R.string.setup_open_failed)
    fun open(vararg intents: android.content.Intent) {
        if (!SetupIntents.open(context, *intents)) Toast.makeText(context, openFailed, Toast.LENGTH_LONG).show()
    }
    val actions = SetupActions(
        onListener = { open(SetupIntents.listenerSettings(context), SetupIntents.appInfo(context)) },
        onPost = {
            // After two refusals Android stops showing the dialog, and asking again does nothing: go to the
            // app's notification settings instead.
            val activity = context.findActivity()
            val blocked = store.askedForNotifications() && activity != null &&
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (blocked) {
                open(SetupIntents.notificationSettings(context), SetupIntents.appInfo(context))
            } else {
                store.markAskedForNotifications()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onPromotion = { open(SetupIntents.promotionSettings(context), SetupIntents.notificationSettings(context)) },
        onChannel = { open(SetupIntents.channelSettings(context), SetupIntents.notificationSettings(context)) },
        onAppInfo = { open(SetupIntents.appInfo(context)) },
        // App info has the Battery page with "Unrestricted"; the system-wide list is harder to find it in.
        onBattery = { open(SetupIntents.appInfo(context), SetupIntents.batterySettings()) },
        onTest = {
            postTestNotification(context)
            onTestSent()
        },
    )
    // Suggested apps in their usual order, then any others the user added.
    // An app uninstalled while on is left out (it comes back as it was if reinstalled).
    val installedOn = remember(settings.enabled) { installedPackages(context, settings.enabled) }
    val on = KnownApps.ALL.filter { it in installedOn } + (installedOn - KnownApps.ALL.toSet()).sorted()
    val livePackages = shown.mapTo(HashSet()) { it.packageName }

    LazyColumn(contentPadding = padding) {
        item(key = "island", contentType = "island") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    // Paused is the user's own choice, so it shows even before setup is done.
                    settings.paused -> PausedIsland { setPaused(context, store, false) }
                    !setup.ready -> SetupIsland(setup, actions)
                    // Like the status bar: an app put first leads while it has something going on.
                    shown.isNotEmpty() -> LiveIsland(shown.sortedByDescending { it.packageName in settings.first }, setup, actions)
                    else -> IdleIsland(appsOn = on.size, setup, actions)
                }
            }
        }
        // Recording keeps notification text on the phone, so it is never on without the user seeing it.
        if (settings.recordSamples) {
            item(key = "recording", contentType = "recording") {
                RecordingBanner(onStop = { store.setRecordSamples(false) })
            }
        }
        // Something the user keeps dismissing: offer to stop showing it. One suggestion at a time.
        val less = lessSuggestion(habitCounts, settings, installedOn)
        // An app seen posting something in progress that is not on yet: one tap to turn it on.
        val suggestion = settings.seen.firstOrNull {
            it !in settings.enabled && it !in settings.suggestionsDismissed && it != context.packageName && !KnownApps.hasPeople(it)
        }
        if (less != null && setup.ready) {
            item(key = "less", contentType = "suggest") {
                val label = rememberAppInfo(less.packageName).label
                Suggestion(
                    packageName = less.packageName,
                    title = if (less.channelName != null) {
                        pluralStringResource(R.plurals.less_type_title, less.count, label, less.channelName, less.count)
                    } else {
                        pluralStringResource(R.plurals.less_app_title, less.count, label, less.count)
                    },
                    description = stringResource(if (less.channelName != null) R.string.less_type_desc else R.string.less_app_desc),
                    yes = stringResource(if (less.channelName != null) R.string.less_type_yes else R.string.less_app_yes),
                    no = stringResource(R.string.less_no),
                    onYes = {
                        if (less.channelId != null) store.setChannel(less.packageName, less.channelId, false)
                        else store.setEnabled(less.packageName, false)
                        habits.reset(less.packageName)
                    },
                    onNo = { habits.reset(less.packageName) },
                    modifier = Modifier.animateItem(),
                )
            }
        } else if (suggestion != null && setup.ready) {
            item(key = "suggest", contentType = "suggest") {
                Suggestion(
                    packageName = suggestion,
                    title = stringResource(R.string.suggest_title, rememberAppInfo(suggestion).label),
                    description = stringResource(R.string.suggest_desc),
                    yes = stringResource(R.string.suggest_yes),
                    no = stringResource(R.string.suggest_no),
                    onYes = { store.setEnabled(suggestion, true) },
                    onNo = { store.dismissSuggestion(suggestion) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "apps-header", contentType = "header") {
            SectionHeader(pluralStringResource(R.plurals.apps_on_count, on.size, on.size))
        }
        items(on, key = { it }, contentType = { "app" }) { pkg ->
            EnabledAppRow(pkg, settings, live = pkg in livePackages, onOpen = { onOpenApp(pkg) }, modifier = Modifier.animateItem())
        }
        item(key = "add", contentType = "add") { AddAppRow(onAddApp, prominent = on.isEmpty()) }
        item(key = "activity", contentType = "activity") {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            // Prefer what happened in an app the user turned on; the rest is mostly other apps' noise.
            ActivityLink(entries.firstOrNull { it.packageName in settings.enabled } ?: entries.firstOrNull(), onActivity)
        }
        item(key = "bottom", contentType = "spacer") { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun LiveIsland(shown: List<Relay.Shown>, setup: SetupStatus, actions: SetupActions) {
    val first = shown.first()
    val app = rememberAppInfo(first.packageName)
    IslandStage(
        pill = { IslandPill(first.chipText, icon = app.icon) },
        description = first.chipText,
    ) {
        // The test from the home screen is ours; say what it is rather than "LiveIsland is on LiveIsland".
        Caption(
            if (first.sourceKey == Relay.TEST_KEY) stringResource(R.string.island_test) else stringResource(R.string.island_showing, app.label),
        )
        // Anything else on the island, as One UI cycles through them.
        shown.drop(1).take(MORE_SHOWN).forEach { other ->
            val otherApp = rememberAppInfo(other.packageName)
            IslandPill(other.chipText, icon = otherApp.icon, small = true, modifier = Modifier.padding(top = 8.dp))
        }
        BatteryWarning(setup, actions)
    }
}

@Composable
private fun IdleIsland(appsOn: Int, setup: SetupStatus, actions: SetupActions) {
    IslandStage(pill = { IslandPill(stringResource(R.string.island_idle), muted = true) }) {
        Caption(stringResource(if (appsOn > 0) R.string.island_idle_desc else R.string.island_idle_no_apps))
        FilledTonalButton(onClick = actions.onTest, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.action_test))
        }
        BatteryWarning(setup, actions)
    }
}

@Composable
private fun SetupIsland(setup: SetupStatus, actions: SetupActions) {
    val steps = buildList {
        if (!setup.listenerGranted) {
            // An APK installed from a browser or file manager gets a greyed-out switch until restricted
            // settings are allowed in App info, so say how before the user gets stuck there.
            add(
                SetupStep(
                    R.string.setup_listener, R.string.setup_listener_desc, R.string.action_allow, actions.onListener,
                    extra = R.string.setup_listener_restricted, secondary = R.string.action_app_info to actions.onAppInfo,
                ),
            )
        }
        if (!setup.postGranted) add(SetupStep(R.string.setup_post, R.string.setup_post_desc, R.string.action_allow, actions.onPost))
        if (!setup.promotionAllowed) {
            add(SetupStep(R.string.setup_promoted, R.string.setup_promoted_desc, R.string.action_open, actions.onPromotion, R.string.setup_promoted_samsung.takeIf { Device.isSamsung }))
        }
        if (!setup.channelEnabled) {
            add(SetupStep(R.string.setup_channel, R.string.setup_channel_desc, R.string.action_open, actions.onChannel))
        }
    }
    IslandStage(
        pill = { IslandPill(pluralStringResource(R.plurals.status_steps_left, steps.size, steps.size), muted = true) },
    ) {
        Caption(stringResource(R.string.tagline))
        steps.forEachIndexed { i, step -> SetupStepRow(i + 1, step) }
    }
}

private class SetupStep(
    val title: Int,
    val description: Int,
    val action: Int,
    val onClick: () -> Unit,
    val extra: Int? = null,
    /** A second, smaller way forward under [extra]: its label and what it does. */
    val secondary: Pair<Int, () -> Unit>? = null,
)

@Composable
private fun SetupStepRow(number: Int, step: SetupStep) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(step.title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(step.description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            step.extra?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            step.secondary?.let { (label, onClick) ->
                TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = step.onClick) { Text(stringResource(step.action)) }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun BatteryWarning(setup: SetupStatus, actions: SetupActions) {
    if (setup.batteryUnrestricted) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.status_battery),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = actions.onBattery) { Text(stringResource(R.string.action_adjust)) }
    }
}

@Composable
private fun AddAppRow(onClick: () -> Unit, prominent: Boolean) {
    if (prominent) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                stringResource(R.string.apps_none_on),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.picker_title))
            }
        }
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.picker_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** "Recent activity", with what happened last, so "did it work?" is one glance away. */
@Composable
private fun ActivityLink(latest: ActivityLog.Entry?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.bodyLarge)
            val line = if (latest == null) {
                stringResource(R.string.activity_none_yet)
            } else {
                val app = rememberAppInfo(latest.packageName)
                val time = if (System.currentTimeMillis() - latest.timeMillis < DateUtils.MINUTE_IN_MILLIS) {
                    stringResource(R.string.just_now)
                } else {
                    DateUtils.getRelativeTimeSpanString(latest.timeMillis).toString()
                }
                "${app.label} · ${stringResource(outcomeText(latest.outcome, latest.packageName))} · $time"
            }
            Text(
                line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PausedIsland(onResume: () -> Unit) {
    IslandStage(pill = { IslandPill(stringResource(R.string.island_paused), muted = true) }) {
        Caption(stringResource(R.string.island_paused_desc))
        FilledTonalButton(onClick = onResume, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.action_resume)) }
    }
}

/** Something the user dismissed [count] times: a whole app, or one of its notification types if named. */
private data class LessSuggestion(val packageName: String, val channelId: String?, val channelName: String?, val count: Int)

/**
 * A notification type the user keeps dismissing, if the app has other types too and its name is known;
 * otherwise an app the user keeps dismissing. Only apps that are on and installed ([on]), and types still shown.
 */
private fun lessSuggestion(habits: Habits.State, settings: SettingsStore.State, on: Set<String>): LessSuggestion? {
    habits.types.entries
        .filter { (k, n) -> n >= Habits.TYPE_THRESHOLD && k.first in on && settings.channelChoices[k.first]?.get(k.second) != false }
        .sortedByDescending { it.value }
        .forEach { (k, n) ->
            val types = settings.channels[k.first].orEmpty()
            val name = types.firstOrNull { it.id == k.second }?.name
            if (name != null && types.size > 1) return LessSuggestion(k.first, k.second, name, n)
        }
    return habits.apps.entries
        .filter { (p, n) -> n >= Habits.APP_THRESHOLD && p in on }
        .maxByOrNull { it.value }
        ?.let { LessSuggestion(it.key, null, null, it.value) }
}

/** A suggestion about one app, such as "foodpanda just had something in progress. Show it on the island?". */
@Composable
private fun Suggestion(
    packageName: String,
    title: String,
    description: String,
    yes: String,
    no: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = rememberAppInfo(packageName)
    if (app.icon == null) return // Uninstalled since it was seen.
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            AppIcon(app)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    TextButton(onClick = onNo) { Text(no) }
                    TextButton(onClick = onYes) { Text(yes) }
                }
            }
        }
    }
}

/** Pauses or resumes relaying, and refreshes the Quick Settings tile so it shows the same. */
fun setPaused(context: Context, store: SettingsStore, paused: Boolean) {
    store.setPaused(paused)
    PauseTileService.refresh(context)
}

private const val MORE_SHOWN = 3

private fun postTestNotification(context: Context) {
    LiveNotifications.ensureChannel(context)
    val content = LiveContent(
        sourceKey = Relay.TEST_KEY,
        packageName = context.packageName,
        title = context.getString(R.string.test_title),
        text = context.getString(R.string.test_text),
        chip = ChipText.Minutes((TEST_DURATION_MS / 60_000).toInt()),
        chipDetail = TextShortener.fit(context.getString(R.string.test_title), columns = 12),
        progress = LiveContent.Progress.Determinate(70, 100),
        chronometer = LiveContent.Chronometer(System.currentTimeMillis() + TEST_DURATION_MS, countsDown = true),
        kind = LiveContent.Kind.ONGOING,
    )
    val notification = LiveNotifications.build(
        context = context,
        content = content,
        smallIcon = Icon.createWithResource(context, R.drawable.ic_island),
        largeIcon = null,
        app = null,
        // Tapping the test opens LiveIsland, like tapping a relayed copy opens its app.
        contentIntent = MainActivity.open(context),
        actions = listOf(DismissReceiver.action(context, tag = null, id = Relay.TEST_NOTIFICATION_ID)),
        timeoutMillis = TEST_DURATION_MS,
    )
    context.getSystemService(NotificationManager::class.java).notify(Relay.TEST_NOTIFICATION_ID, notification)
    // Mirror it on the home screen's island for as long as it lasts.
    val at = SystemClock.elapsedRealtime()
    val chip = LiveNotifications.chipLabel(context, content.chip!!, content.chipDetail)
    Relay.addShown(Relay.Shown(Relay.TEST_KEY, context.packageName, chip, at))
    Handler(Looper.getMainLooper()).postDelayed({ Relay.removeShown(Relay.TEST_KEY, at) }, TEST_DURATION_MS)
}

private const val TEST_DURATION_MS = 60_000L

/** The activity behind a Compose context, for permission questions only an activity can answer. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun RecordingBanner(onStop: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.recording_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStop) { Text(stringResource(R.string.recording_stop)) }
        }
    }
}
