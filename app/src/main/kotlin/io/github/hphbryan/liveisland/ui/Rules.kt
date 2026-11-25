package io.github.hphbryan.liveisland.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import io.github.hphbryan.liveisland.R
import io.github.hphbryan.liveisland.core.RulesText
import io.github.hphbryan.liveisland.platform.SettingsStore

/** Copies the choices for [packages] as text, to paste on another phone or send to someone. */
fun copyRules(context: Context, store: SettingsStore, packages: List<String>) {
    if (packages.isEmpty()) {
        Toast.makeText(context, R.string.rules_none_on, Toast.LENGTH_SHORT).show()
        return
    }
    val text = RulesText.format(packages.map(store::rulesOf))
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(RulesText.HEADER, text))
    Toast.makeText(context, context.resources.getQuantityString(R.plurals.rules_copied, packages.size, packages.size), Toast.LENGTH_SHORT).show()
}

/**
 * Applies the rules on the clipboard to the apps they are for that are installed here, turning them on, and
 * says what happened. Returns the app whose page to open when the rules were for exactly one.
 */
fun pasteRules(context: Context, store: SettingsStore): String? {
    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    val rules = RulesText.parse(text)
    if (rules.isEmpty()) {
        Toast.makeText(context, R.string.rules_paste_none, Toast.LENGTH_LONG).show()
        return null
    }
    val (here, missing) = rules.partition { isInstalled(context, it.packageName) }
    here.forEach(store::apply)
    val message = when {
        here.isEmpty() -> context.getString(R.string.rules_paste_missing)
        missing.isEmpty() -> context.resources.getQuantityString(R.plurals.rules_pasted, here.size, here.size)
        else -> context.resources.getQuantityString(R.plurals.rules_pasted_some, here.size, here.size, missing.size)
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    return here.singleOrNull()?.packageName
}

private fun isInstalled(context: Context, packageName: String) = try {
    context.packageManager.getApplicationInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) {
    false
}
