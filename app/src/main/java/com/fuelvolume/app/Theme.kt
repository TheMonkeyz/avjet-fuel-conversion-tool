package com.fuelvolume.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/*
 * Light blue, high contrast. Light blue is used only for fills (top bar, tabs, cards, selected
 * buttons). Text and lines use near-black or dark blue, because light blue text on white is
 * only 2.3:1. Every text / background pair here is at least 7:1 (WCAG AAA), so the screen
 * stays readable in direct sun and at night.
 */

private val LightBlue = Color(0xFF8FD0FF)
private val PaleBlue = Color(0xFFCDEBFF)
private val DeepBlue = Color(0xFF00507D)
private val Ink = Color(0xFF001F33)

private val LightColors = lightColorScheme(
    primary = DeepBlue, // 8.6:1 on white
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = Ink, // 13.6:1
    secondary = DeepBlue,
    onSecondary = Color.White,
    secondaryContainer = LightBlue,
    onSecondaryContainer = Ink, // 10.2:1
    tertiary = DeepBlue,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0A1620), // 18.3:1
    surface = Color.White,
    onSurface = Color(0xFF0A1620),
    surfaceVariant = Color(0xFFE3F2FC),
    onSurfaceVariant = Color(0xFF26323D), // 13.1:1 on white
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4FAFE),
    surfaceContainer = Color(0xFFF0F7FC),
    surfaceContainerHigh = Color(0xFFEDF6FC),
    surfaceContainerHighest = Color(0xFFEAF4FB),
    outline = Color(0xFF3B4A57), // 9.1:1
    outlineVariant = Color(0xFF8A9BA8),
    error = Color(0xFF9A0019), // 8.8:1 on white, 7.1:1 on the pale blue cards
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = LightBlue, // 11.3:1 on the dark surface
    onPrimary = Ink,
    primaryContainer = Color(0xFF123447),
    onPrimaryContainer = Color.White, // 13.1:1
    secondary = LightBlue,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF1E4A63),
    onSecondaryContainer = Color.White, // 9.5:1
    tertiary = LightBlue,
    onTertiary = Ink,
    background = Color(0xFF0B1218),
    onBackground = Color.White, // 18.9:1
    surface = Color(0xFF0B1218),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1B2731),
    onSurfaceVariant = Color(0xFFD0DCE6), // 13.5:1
    surfaceContainerLowest = Color(0xFF070C10),
    surfaceContainerLow = Color(0xFF111A21),
    surfaceContainer = Color(0xFF141F27),
    surfaceContainerHigh = Color(0xFF18242D),
    surfaceContainerHighest = Color(0xFF1B2731),
    outline = Color(0xFFA9B8C4),
    outlineVariant = Color(0xFF4A5A66),
    error = Color(0xFFFFB4AB), // 7.7:1 on the dark cards
    onError = Color(0xFF690005),
)

/** Colours of the top bar and the tab row. */
@Immutable
data class BarColors(val container: Color, val content: Color, val contentDim: Color, val indicator: Color)

@Composable
@ReadOnlyComposable
fun barColors(): BarColors = if (isSystemInDarkTheme()) {
    BarColors(container = Color(0xFF123447), content = Color.White, contentDim = Color(0xFFD0DCE6), indicator = LightBlue)
} else {
    BarColors(container = LightBlue, content = Ink, contentDim = Color(0xFF14303F), indicator = DeepBlue)
}

@Composable
fun UpliftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
