package io.github.hphbryan.liveisland.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.SHARED_PROMOTION_WORDS
import io.github.hphbryan.liveisland.platform.SettingsStore

/**
 * Words that skip a notification from every app, set once instead of on each app's page, with the common
 * promotion words one tap away.
 */
@Composable
fun SharedKeywordsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val store = remember { SettingsStore.get(context) }
    val settings by store.state.collectAsStateWithLifecycle()
    val words = settings.sharedKeywords
    val missing = SHARED_PROMOTION_WORDS.filter { w -> words.none { it.equals(w, ignoreCase = true) } }

    Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.shared_keywords_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        KeywordChips(words = words, onChange = store::setSharedKeywords)
        if (missing.isNotEmpty()) {
            FilledTonalButton(
                onClick = { store.setSharedKeywords(words + missing) },
                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
            ) { Text(pluralStringResource(R.plurals.shared_keywords_add_common, missing.size, missing.size)) }
            Text(
                missing.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
            )
        }
    }
}
