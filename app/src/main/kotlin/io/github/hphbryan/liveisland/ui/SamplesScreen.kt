package io.github.hphbryan.liveisland.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.platform.SampleLog
import io.github.hphbryan.liveisland.platform.SettingsStore
import java.text.DateFormat
import java.util.Date

@Composable
fun SamplesScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val log = remember { SampleLog.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    val entries by log.entries.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var confirmShare by rememberSaveable { mutableStateOf(false) }
    val shareSubject = stringResource(R.string.diagnostics_export_subject)
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LazyColumn(contentPadding = padding) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.diagnostics_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .toggleable(value = settings.recordSamples, role = Role.Switch, onValueChange = store::setRecordSamples),
                ) {
                    Text(stringResource(R.string.diagnostics_record), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = settings.recordSamples, onCheckedChange = null)
                }
                Text(
                    pluralStringResource(R.plurals.diagnostics_count, entries.size, entries.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    FilledTonalButton(onClick = { confirmShare = true }, enabled = entries.isNotEmpty()) {
                        Text(stringResource(R.string.diagnostics_export))
                    }
                    OutlinedButton(onClick = { confirmClear = true }, enabled = entries.isNotEmpty()) {
                        Text(stringResource(R.string.diagnostics_clear))
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.diagnostics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        // No item keys: the same notification can legitimately be recorded twice with identical content.
        items(entries) { entry ->
            ListItem(
                headlineContent = { Text(entry.title ?: "—", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        "${entry.summary} · ${entry.packageName} · ${timeFormat.format(Date(entry.time))}",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.diagnostics_clear_confirm)) },
            confirmButton = { TextButton(onClick = { log.clear(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmShare) {
        AlertDialog(
            onDismissRequest = { confirmShare = false },
            text = { Text(stringResource(R.string.diagnostics_export_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShare = false
                    // As a file: hundreds of samples as one text extra can exceed what Android passes between apps.
                    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                    val file = java.io.File(dir, "liveisland-samples.jsonl").apply { writeText(log.export()) }
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, shareSubject)
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, null))
                }) { Text(stringResource(R.string.diagnostics_export)) }
            },
            dismissButton = { TextButton(onClick = { confirmShare = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
