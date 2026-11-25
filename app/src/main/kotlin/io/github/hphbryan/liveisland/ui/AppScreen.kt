package io.github.hphbryan.liveisland.ui

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import io.github.hphbryan.liveisland.platform.ChannelInfo
import androidx.compose.ui.res.pluralStringResource
import io.github.hphbryan.liveisland.platform.Relay
import io.github.hphbryan.liveisland.platform.ActivityLog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.KeywordFilter
import io.github.hphbryan.liveisland.core.KnownApps
import io.github.hphbryan.liveisland.core.SourcePolicy
import io.github.hphbryan.liveisland.platform.SettingsStore

/**
 * One app's settings, as a page: what its chip looks like, one main switch, then plain choices about
 * what to show. Chat apps lead with the people whose messages always appear.
 */
@Composable
fun AppScreen(packageName: String, padding: PaddingValues, onActivity: () -> Unit, onSharedKeywords: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    val app = rememberAppInfo(packageName)
    val enabled = packageName in settings.enabled
    val messaging = KnownApps.hasPeople(packageName)
    val shown by Relay.shown.collectAsStateWithLifecycle()
    val activity by ActivityLog.entries.collectAsStateWithLifecycle()
    val live = shown.firstOrNull { it.packageName == packageName }
    val recent = activity.filter { it.packageName == packageName }.take(RECENT_SHOWN)

    Column(
        Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        val sample = sampleChip(packageName)
        // What this app has on the island right now, if anything; otherwise an example.
        val pillText = live?.chipText ?: sample ?: app.label
        IslandStage(
            pill = { IslandPill(pillText, icon = app.icon) },
            description = pillText,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(
                    when {
                        live != null -> R.string.app_live_now
                        packageName == KnownApps.GOOGLE_APP -> R.string.app_gemini_only
                        sample == null -> R.string.app_preview_generic
                        else -> R.string.sheet_preview
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        MainSwitch(
            checked = enabled,
            onChange = { store.setEnabled(packageName, it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (!enabled) {
            Text(
                stringResource(if (messaging) R.string.app_off_hint_messaging else R.string.app_off_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            return@Column
        }
        val flash = packageName in settings.flash
        val seconds = settings.flashSecondsFor(packageName)
        RecentSection(
            recent = recent,
            // The latest one-off was skipped only because one-offs are off: offer that fix right here.
            offerOneOffs = !messaging && !flash && recent.firstOrNull()?.outcome == "skip:not-ongoing",
            onShowOneOffs = { store.setFlash(packageName, true) },
            onAll = onActivity,
        )
        if (messaging) {
            SectionHeader(stringResource(R.string.people_title))
            PeopleSection(packageName, settings, store)
            SectionHeader(stringResource(R.string.others_title))
            Choices {
                Choice(!flash, stringResource(R.string.others_hide), stringResource(R.string.others_hide_desc)) {
                    store.setFlash(packageName, false)
                }
                Choice(flash, stringResource(R.string.others_brief), stringResource(R.string.others_brief_desc)) {
                    store.setFlash(packageName, true)
                }
                if (flash) DurationChips(seconds) { store.setFlashSeconds(packageName, it) }
            }
        } else {
            SectionHeader(stringResource(R.string.show_title))
            Choices {
                Choice(!flash, stringResource(R.string.show_ongoing), stringResource(R.string.show_ongoing_desc)) {
                    store.setFlash(packageName, false)
                }
                Choice(flash, stringResource(R.string.show_also_oneoff), stringResource(R.string.show_also_oneoff_desc)) {
                    store.setFlash(packageName, true)
                }
                if (flash) DurationChips(seconds) { store.setFlashSeconds(packageName, it) }
            }
            // Apps that are not for chatting can still have people, e.g. from before or from recent senders.
            if (!settings.people[packageName].isNullOrEmpty()) {
                SectionHeader(stringResource(R.string.people_title))
                PeopleSection(packageName, settings, store)
            }
        }
        ChannelSection(
            channels = settings.channels[packageName].orEmpty(),
            choices = settings.channelChoices[packageName].orEmpty(),
            onChange = { id, on -> store.setChannel(packageName, id, on) },
        )
        SectionHeader(stringResource(R.string.keywords_title))
        KeywordChips(
            words = settings.keywords[packageName].orEmpty(),
            onChange = { store.setKeywords(packageName, it) },
        )
        SharedKeywordsLink(count = settings.sharedKeywords.size, onClick = onSharedKeywords)
    }
}

/** The page's one big switch, filled with the accent while on, as in Android's own settings. */
@Composable
private fun MainSwitch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Text(
                stringResource(R.string.app_main_switch),
                style = MaterialTheme.typography.titleMedium,
                color = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * What this app's latest notifications did and why, where its settings are changed, so the effect of a
 * setting is visible without leaving the page.
 */
@Composable
private fun RecentSection(
    recent: List<ActivityLog.Entry>,
    offerOneOffs: Boolean,
    onShowOneOffs: () -> Unit,
    onAll: () -> Unit,
) {
    SectionHeader(stringResource(R.string.app_recent_title)) {
        if (recent.isNotEmpty()) TextButton(onClick = onAll) { Text(stringResource(R.string.app_recent_all)) }
    }
    if (recent.isEmpty()) {
        Text(
            stringResource(R.string.app_recent_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            recent.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        stringResource(outcomeText(e.outcome, e.packageName)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isShown(e.outcome)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(relativeTime(e.timeMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (offerOneOffs) {
                Text(
                    stringResource(R.string.app_recent_oneoff_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                FilledTonalButton(onClick = onShowOneOffs, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.show_also_oneoff))
                }
            }
        }
    }
}

/**
 * The app's own kinds of notifications (its notification channels, by the names it gives them), each with
 * a switch. Kinds named like promotions start off; the rest start on. Filled in as the app posts.
 */
@Composable
private fun ChannelSection(channels: List<ChannelInfo>, choices: Map<String, Boolean>, onChange: (String, Boolean) -> Unit) {
    SectionHeader(stringResource(R.string.channels_title))
    if (channels.isEmpty()) {
        Text(
            stringResource(R.string.channels_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        return
    }
    Text(
        stringResource(R.string.channels_desc),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
    )
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            channels.forEach { c ->
                val promo = KnownApps.looksPromotional(c.name)
                val on = choices[c.id] ?: !promo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = on, role = Role.Switch) { onChange(c.id, it) }
                        .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        if (promo && c.id !in choices) {
                            Text(
                                stringResource(R.string.channels_promo_default),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}

/** Under an app's own words: the words every app skips, and where to change them. */
@Composable
private fun SharedKeywordsLink(count: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp)) {
        Text(
            if (count == 0) stringResource(R.string.shared_keywords_none_hint) else pluralStringResource(R.plurals.shared_keywords_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClick) { Text(stringResource(R.string.shared_keywords_edit)) }
    }
}

/** A rounded group of mutually exclusive choices. */
@Composable
fun Choices(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.selectableGroup().padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Choice(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationChips(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 68.dp, end = 16.dp, bottom = 8.dp),
    ) {
        SourcePolicy.FLASH_SECONDS_CHOICES.forEach { s ->
            FilterChip(
                selected = s == selected,
                onClick = { onSelect(s) },
                label = { Text(stringResource(R.string.duration_seconds, s)) },
            )
        }
    }
}

/** Skipped words as removable chips, plus one chip to add more (commas allowed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeywordChips(words: List<String>, onChange: (List<String>) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(
            stringResource(if (words.isEmpty()) R.string.keywords_none else R.string.keywords_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEach { word ->
                InputChip(
                    selected = false,
                    onClick = { onChange(words - word) },
                    label = { Text(word) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.keyword_remove, word), modifier = Modifier.size(18.dp))
                    },
                )
            }
            AssistChip(
                onClick = { adding = true },
                label = { Text(stringResource(R.string.keyword_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
    if (adding) {
        var text by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.keyword_add)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.sheet_keywords_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(KeywordFilter.parse(KeywordFilter.format(words) + "," + text))
                        adding = false
                    },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Latest outcomes shown on an app's page; the rest are on the Recent activity page. */
private const val RECENT_SHOWN = 3
