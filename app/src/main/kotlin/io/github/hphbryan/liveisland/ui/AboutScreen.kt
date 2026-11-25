package io.github.hphbryan.liveisland.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hphbryan.liveisland.R

@Composable
fun AboutScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    Column(
        Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
        Section(R.string.about_privacy_title, R.string.about_privacy)
        Section(R.string.about_limits_title, R.string.about_limits)
        Section(R.string.about_license_title, R.string.about_license)
    }
}

@Composable
private fun Section(title: Int, body: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
    Text(stringResource(body), style = MaterialTheme.typography.bodyMedium)
}
