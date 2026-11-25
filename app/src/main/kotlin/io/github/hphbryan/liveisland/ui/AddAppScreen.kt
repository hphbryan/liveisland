package io.github.hphbryan.liveisland.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.AppCategory
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.platform.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/**
 * Where apps get turned on: suggested apps by kind (each kind with a sample of its chip), apps seen
 * posting ongoing notifications, then every other app. Searching flattens it all into one list.
 */
@Composable
fun AddAppScreen(padding: PaddingValues, onOpenApp: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val installed = remember { installedPackages(context, KnownApps.ALL) }
    val all by produceState<List<Pair<String, String>>?>(null) {
        value = withContext(Dispatchers.IO) { launchableApps(context) }
    }
    val suggested = KnownApps.BY_CATEGORY.mapValues { (_, apps) -> apps.filter { it in installed } }.filterValues { it.isNotEmpty() }
    val seen = settings.seen.filter { it !in KnownApps.ALL }
    val listed = suggested.values.flatten().toSet() + seen
    val onToggle = { pkg: String, on: Boolean -> store.setEnabled(pkg, on) }

    LazyColumn(contentPadding = padding) {
        item(key = "search", contentType = "search") {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.picker_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search)) }
                    }
                },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val labels = all.orEmpty().toMap()
            val matches = (all.orEmpty().map { it.first } + listed).distinct().filter { pkg ->
                pkg.contains(q, ignoreCase = true) || labels[pkg]?.contains(q, ignoreCase = true) == true
            }
            if (all != null && matches.isEmpty()) item(key = "empty", contentType = "empty") { EmptyText(R.string.picker_empty) }
            appToggles(matches, settings, onToggle, onOpenApp)
        } else {
            // Chat apps need people picked first, or turning them on shows nothing; they are turned on one by one.
            val off = suggested.filterKeys { !it.hasPeople }.values.flatten().filter { it !in settings.enabled }
            if (off.size > 1) {
                item(key = "all-suggested", contentType = "bulk") { TurnOnAll(off.size) { store.enableAll(off) } }
            }
            for ((category, apps) in suggested) {
                val kindOff = apps.filter { it !in settings.enabled }
                item(key = "cat-${category.name}", contentType = "header") {
                    CategoryHeader(category, onTurnOnAll = if (kindOff.size > 1 && !category.hasPeople) ({ store.enableAll(kindOff) }) else null)
                }
                appToggles(apps, settings, onToggle, onOpenApp)
            }
            if (seen.isNotEmpty()) {
                item(key = "seen", contentType = "header") { SectionHeader(stringResource(R.string.apps_seen)) }
                appToggles(seen, settings, onToggle, onOpenApp)
            }
            item(key = "all", contentType = "header") { SectionHeader(stringResource(R.string.apps_all)) }
            appToggles(all.orEmpty().map { it.first }.filter { it !in listed }, settings, onToggle, onOpenApp)
        }
        item(key = "bottom", contentType = "spacer") { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** Turns on every suggested app installed here, each with its kind's starting setup, in one tap. */
@Composable
private fun TurnOnAll(count: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                pluralStringResource(R.plurals.apps_turn_on_suggested, count, count),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.apps_turn_on_suggested_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = onClick, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.apps_turn_on_all)) }
        }
    }
}

/** A kind of app: its name, a sample of its chip, and (when several are off) one tap to turn them all on. */
@Composable
private fun CategoryHeader(category: AppCategory, onTurnOnAll: (() -> Unit)?) {
    SectionHeader(stringResource(categoryTitle(category))) {
        if (onTurnOnAll != null) TextButton(onClick = onTurnOnAll) { Text(stringResource(R.string.apps_turn_on_all)) }
    }
    Box(Modifier.padding(start = 20.dp, bottom = 4.dp)) {
        val sample = when (category) {
            AppCategory.AI -> stringResource(R.string.chip_replied_by, "ChatGPT")
            AppCategory.MESSAGING -> stringResource(
                R.string.chip_message,
                stringResource(R.string.example_sender),
                stringResource(R.string.example_message),
            )
            else -> stringResource(categorySample(category))
        }
        IslandPill(sample, small = true)
    }
}

@Composable
private fun EmptyText(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

/** App rows with an on/off switch; tapping the row opens the app's page. Keyed by package. */
private fun LazyListScope.appToggles(
    packages: List<String>,
    settings: SettingsStore.State,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    items(packages, key = { it }, contentType = { "app" }) { pkg ->
        val app = rememberAppInfo(pkg)
        val on = pkg in settings.enabled
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .animateItem()
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = stringResource(R.string.app_settings, app.label)) { onOpen(pkg) }
                .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            AppIcon(app)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pkg == KnownApps.GOOGLE_APP) {
                    Text(
                        stringResource(R.string.app_gemini_only),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // The row opens the app's page; the switch is a separate target, so it needs the app's name.
            Switch(checked = on, onCheckedChange = { onToggle(pkg, it) }, modifier = Modifier.semantics { contentDescription = app.label })
        }
    }
}

/** (package, label) of every app with a launcher icon, except this app and system UI, sorted by label. */
private fun launchableApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val collator = Collator.getInstance()
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .asSequence()
        .filter { it.activityInfo.packageName != context.packageName && it.activityInfo.packageName !in KnownApps.SYSTEM }
        .distinctBy { it.activityInfo.packageName }
        .map { it.activityInfo.packageName to it.activityInfo.applicationInfo.loadLabel(pm).toString() }
        .sortedWith(compareBy(collator) { it.second })
        .toList()
}
