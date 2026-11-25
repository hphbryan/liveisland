package io.github.hphbryan.liveisland.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import io.github.hphbryan.liveisland.R

/** Who makes the app, where its source and issues are, what it promises, and its known limits. */
@Composable
fun AboutScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    Column(
        Modifier
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Header(version)

        Group {
            LinkRow(Icons.Filled.Person, stringResource(R.string.about_developer), DEVELOPER) { open(context, DEVELOPER_URL) }
            GroupDivider()
            LinkRow(Icons.Filled.Build, stringResource(R.string.about_source), stringResource(R.string.about_source_desc)) {
                open(context, REPO_URL)
            }
            GroupDivider()
            LinkRow(Icons.AutoMirrored.Filled.List, stringResource(R.string.about_changes), stringResource(R.string.about_changes_desc)) {
                open(context, "$REPO_URL/releases")
            }
        }

        Group {
            LinkRow(Icons.Filled.Warning, stringResource(R.string.about_report), stringResource(R.string.about_report_desc)) {
                copyDeviceInfo(context, version)
                open(context, "$REPO_URL/issues/new/choose")
            }
            GroupDivider()
            LinkRow(Icons.Filled.Share, stringResource(R.string.about_copy_info), stringResource(R.string.about_copy_info_desc)) {
                copyDeviceInfo(context, version)
            }
        }

        Group {
            LinkRow(Icons.Filled.Lock, stringResource(R.string.about_privacy_title), stringResource(R.string.about_privacy)) {
                open(context, "$REPO_URL/blob/main/PRIVACY.md")
            }
            GroupDivider()
            LinkRow(Icons.Filled.Info, stringResource(R.string.about_license_title), stringResource(R.string.about_license)) {
                open(context, "$REPO_URL/blob/main/LICENSE")
            }
        }

        Text(
            stringResource(R.string.about_limits_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
        )
        Text(
            stringResource(R.string.about_limits),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun Header(version: String) {
    val context = LocalContext.current
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
    ) {
        Image(icon, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)))
        Spacer(Modifier.size(4.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun GroupDivider() =
    HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
private fun LinkRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Opens a web page in the browser. LiveIsland itself has no internet permission; the browser does the rest. */
private fun open(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}

/** What a bug report needs and nothing personal: app and Android versions, the phone model and its software build. */
private fun copyDeviceInfo(context: Context, version: String) {
    val info = listOf(
        "LiveIsland $version",
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT_FULL})",
        "${Build.MANUFACTURER} ${Build.MODEL}",
        "Build ${Build.DISPLAY}",
    ).joinToString("\n")
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("LiveIsland", info))
    Toast.makeText(context, R.string.about_copied, Toast.LENGTH_SHORT).show()
}

private const val DEVELOPER = "hphbryan"
private const val DEVELOPER_URL = "https://github.com/hphbryan"
private const val REPO_URL = "https://github.com/hphbryan/liveisland"
