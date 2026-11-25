package io.github.hphbryan.liveisland.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * A fixed palette rather than wallpaper colours: the app's subject is the island, a dark pill the system
 * draws the same on every phone, so the app looks the same everywhere too. One teal accent marks what is
 * on or live; surfaces stay near neutral so the dark island stands out.
 */
@Composable
fun LiveIslandTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/** The island itself: what One UI draws in the status bar, whatever the app theme. */
object IslandColors {
    val Ink = Color(0xFF101315)
    val OnInk = Color(0xFFFFFFFF)
    val OnInkMuted = Color(0xFFA9B1B4)
    val Live = Color(0xFF3EE0C0)
}

private val Light = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA2F2E1),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201C),
    tertiary = Color(0xFF9A4521),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDBCE),
    onTertiaryContainer = Color(0xFF370E00),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF181C1B),
    surface = Color(0xFFF7F9F8),
    onSurface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFFDDE4E1),
    onSurfaceVariant = Color(0xFF414946),
    outline = Color(0xFF717976),
    outlineVariant = Color(0xFFC1C9C5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F3),
    surfaceContainer = Color(0xFFEBEFED),
    surfaceContainerHigh = Color(0xFFE5E9E7),
    surfaceContainerHighest = Color(0xFFDFE3E1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D6C6),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFA2F2E1),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF334B46),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFFFB598),
    onTertiary = Color(0xFF5A1C00),
    tertiaryContainer = Color(0xFF7A2F0B),
    onTertiaryContainer = Color(0xFFFFDBCE),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0F1211),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0F1211),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF414946),
    onSurfaceVariant = Color(0xFFC1C9C5),
    outline = Color(0xFF8B9390),
    outlineVariant = Color(0xFF414946),
    surfaceContainerLowest = Color(0xFF0A0D0C),
    surfaceContainerLow = Color(0xFF171B1A),
    surfaceContainer = Color(0xFF1B1F1E),
    surfaceContainerHigh = Color(0xFF252A28),
    surfaceContainerHighest = Color(0xFF303533),
)
