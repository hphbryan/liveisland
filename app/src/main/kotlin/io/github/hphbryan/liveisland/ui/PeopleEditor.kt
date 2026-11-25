package io.github.hphbryan.liveisland.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.PersonRule
import io.github.hphbryan.liveisland.platform.SettingsStore

/**
 * People whose messages always appear, in a rounded group: an avatar, the name and how long it stays.
 * Tapping a person edits them; the last row adds one.
 */
@Composable
fun PeopleSection(packageName: String, settings: SettingsStore.State, store: SettingsStore) {
    val people = settings.people[packageName].orEmpty()
    // Index into [people] being edited; -1 adds a new person; null shows no dialog.
    var editing by rememberSaveable(packageName) { mutableStateOf<Int?>(null) }
    val seconds = settings.flashSecondsFor(packageName)

    Text(
        stringResource(if (people.isEmpty()) R.string.people_empty else R.string.people_desc),
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
            people.forEachIndexed { i, person ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable(onClickLabel = stringResource(R.string.person_edit)) { editing = i }
                        .padding(start = 16.dp, end = 4.dp),
                ) {
                    Avatar(person.name)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(person.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (person.untilRead) stringResource(R.string.people_until_read) else stringResource(R.string.people_brief, seconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { store.setPeople(packageName, people - person) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.people_remove, person.name))
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(role = Role.Button) { editing = -1 }
                    .padding(horizontal = 16.dp),
            ) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.people_add), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // Recent senders and groups, one tap each, so picking people does not mean typing names.
    val quick = settings.recentPeople[packageName].orEmpty().filter { name -> people.none { it.name == name } }.take(MAX_QUICK)
    if (quick.isNotEmpty()) {
        Text(
            stringResource(R.string.people_quick),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        QuickPeople(quick) { name -> store.setPeople(packageName, people + PersonRule(name, untilRead = true)) }
    }

    editing?.let { index ->
        val original = people.getOrNull(index)
        PersonDialog(
            initial = original,
            suggestions = settings.recentPeople[packageName].orEmpty().filter { name -> people.none { it.name == name } },
            briefSeconds = seconds,
            onSave = { rule ->
                val next = if (original == null) people + rule else people.map { if (it == original) rule else it }
                store.setPeople(packageName, next)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** The first character of a name on a tinted circle; the tint is stable per name. */
@Composable
private fun Avatar(name: String) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg) = when (Math.floorMod(name.hashCode(), 3)) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.secondaryContainer to colors.onSecondaryContainer
        else -> colors.tertiaryContainer to colors.onTertiaryContainer
    }
    val first = name.trim().let { n ->
        val it = java.text.BreakIterator.getCharacterInstance().apply { setText(n) }
        val end = it.next()
        if (end == java.text.BreakIterator.DONE) "?" else n.substring(0, end).uppercase()
    }
    Box(Modifier.size(40.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        Text(first, style = MaterialTheme.typography.titleMedium, color = fg)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonDialog(
    initial: PersonRule?,
    suggestions: List<String>,
    briefSeconds: Int,
    onSave: (PersonRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var untilRead by rememberSaveable { mutableStateOf(initial?.untilRead ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.people_add else R.string.person_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.person_name)) },
                    supportingText = { Text(stringResource(R.string.person_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.person_recent),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.take(MAX_SUGGESTIONS).forEach { s ->
                            SuggestionChip(onClick = { name = s }, label = { Text(s) })
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = untilRead, role = Role.Switch) { untilRead = it }
                        .padding(vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.people_until_read), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.person_until_read_desc, briefSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = untilRead, onCheckedChange = null)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(PersonRule(name.trim(), untilRead)) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickPeople(names: List<String>, onAdd: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
        names.forEach { name ->
            val label = stringResource(R.string.people_quick_add, name)
            AssistChip(
                onClick = { onAdd(name) },
                label = { Text(name) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

private const val MAX_SUGGESTIONS = 8
private const val MAX_QUICK = 6
