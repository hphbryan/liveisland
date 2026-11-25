package io.github.hphbryan.liveisland.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.platform.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app's label and icon, loaded off the main thread. */
data class AppInfo(val label: String, val icon: ImageBitmap?)

@Composable
fun rememberAppInfo(packageName: String): AppInfo {
    val context = LocalContext.current
    val info by produceState(AppInfo(packageName, null), packageName) {
        value = withContext(Dispatchers.IO) { loadApp(context, packageName) }
    }
    return info
}

@Composable
fun AppIcon(app: AppInfo, size: Int = 40) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(size.dp)) }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing()
    }
}

/**
 * An enabled app on the home screen: icon, name, what it shows beyond the default, and whether it is on
 * the island right now. Opens the app's page.
 */
@Composable
fun EnabledAppRow(packageName: String, settings: SettingsStore.State, live: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = rememberAppInfo(packageName)
    val summary = summary(packageName, settings)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = stringResource(R.string.app_settings, app.label), onClick = onOpen)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        AppIcon(app)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                summary.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary.needsAttention) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (live) LiveBadge()
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun LiveBadge() {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.live_now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** The line under an app's name. [needsAttention]: the app is on but, as set up, will show almost nothing. */
data class Summary(val text: String, val needsAttention: Boolean = false)

/** What an enabled app shows beyond the default, e.g. "2 people picked · one-offs for 8 seconds". */
@Composable
fun summary(packageName: String, settings: SettingsStore.State): Summary {
    val parts = mutableListOf<String>()
    if (packageName == KnownApps.GOOGLE_APP) parts += stringResource(R.string.app_gemini_only)
    settings.people[packageName]?.size?.takeIf { it > 0 }?.let {
        parts += pluralStringResource(R.plurals.app_mode_people, it, it)
    }
    if (packageName in settings.flash) {
        val seconds = settings.flashSecondsFor(packageName)
        parts += pluralStringResource(R.plurals.app_mode_flash, seconds, seconds)
    }
    return when {
        // Parts are written to follow " · ", so the line itself starts with a capital.
        parts.isNotEmpty() -> Summary(parts.joinToString(" · ").replaceFirstChar { it.titlecase() })
        // A chat app with no one picked and brief display off: its messages never appear.
        KnownApps.hasPeople(packageName) -> Summary(stringResource(R.string.app_mode_no_people), needsAttention = true)
        else -> Summary(stringResource(R.string.app_mode_ongoing))
    }
}

fun installedPackages(context: Context, candidates: Collection<String>): Set<String> =
    candidates.filterTo(HashSet()) { runCatching { context.packageManager.getApplicationInfo(it, 0) }.isSuccess }

private fun loadApp(context: Context, packageName: String): AppInfo = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    val icon = pm.getApplicationIcon(info).toBitmap(width = 120, height = 120).asImageBitmap()
    AppInfo(pm.getApplicationLabel(info).toString(), icon)
}.getOrDefault(AppInfo(packageName, null))
