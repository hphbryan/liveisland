package io.github.hphbryan.liveisland.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.platform.ActivityLog

/** Plain-language text for an [ActivityLog.Entry.outcome] from [packageName]. */
fun outcomeText(outcome: String, packageName: String): Int = when (outcome) {
    "relay:ongoing" -> R.string.out_ongoing
    "relay:ongoing-fallback", "relay:flash-fallback" -> R.string.out_chip_fallback
    "relay:flash" -> R.string.out_flash
    "relay:person" -> R.string.out_person
    "relay:person-until-read" -> R.string.out_person_until_read
    "skip:app-off" -> R.string.out_app_off
    // In a chat app, a message that is not shown is one from someone not picked.
    "skip:not-ongoing" -> if (KnownApps.hasPeople(packageName)) R.string.out_not_picked else R.string.out_not_ongoing
    "skip:keyword" -> R.string.out_keyword
    "skip:hidden" -> R.string.out_hidden
    "skip:dnd" -> R.string.out_dnd
    "skip:paused" -> R.string.out_paused
    "skip:channel" -> R.string.out_channel
    "skip:promo" -> R.string.out_promo
    "skip:background" -> R.string.out_background
    "skip:already-live-update" -> R.string.out_native
    "skip:already-samsung-live" -> R.string.out_samsung
    "skip:media" -> R.string.out_media
    "skip:call" -> R.string.out_call
    "skip:no-title" -> R.string.out_no_title
    "skip:not-relevant" -> R.string.out_not_relevant
    else -> R.string.out_other
}

fun isShown(outcome: String) = outcome.startsWith("relay:")

/** What happened to recent notifications and why. Tapping an entry opens that app's page. */
@Composable
fun ActivityScreen(padding: PaddingValues, onOpenApp: (String) -> Unit) {
    val entries by ActivityLog.entries.collectAsStateWithLifecycle()
    val undo = rememberUndo()
    val cleared = stringResource(R.string.activity_cleared)

    LazyColumn(contentPadding = padding) {
        item(key = "note", contentType = "note") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
                Text(
                    stringResource(if (entries.isEmpty()) R.string.activity_empty else R.string.activity_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                if (entries.isNotEmpty()) TextButton(onClick = {
                    val old = entries
                    ActivityLog.clear()
                    undo(cleared) { ActivityLog.restore(old) }
                }) { Text(stringResource(R.string.activity_clear)) }
            }
        }
        items(entries, key = { "${it.key}|${it.outcome}" }, contentType = { "entry" }) { e ->
            ActivityRow(e, onClick = { onOpenApp(e.packageName) })
        }
    }
}

@Composable
fun ActivityRow(entry: ActivityLog.Entry, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val app = rememberAppInfo(entry.packageName)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClickLabel = stringResource(R.string.app_settings, app.label), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(outcomeText(entry.outcome, entry.packageName)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isShown(entry.outcome)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(relativeTime(entry.timeMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "Just now" for the last minute, then "5 min. ago" and so on. */
@Composable
fun relativeTime(timeMillis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - timeMillis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(timeMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
