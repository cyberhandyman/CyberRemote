package dev.companionremote.app.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.companionremote.app.data.AppSkin

/**
 * A skin bundles an accent-tinted [ColorScheme], a page background gradient and
 * a translucent "glass" palette for the remote keys. Skins are orthogonal to
 * light/dark: every skin has both variants so the appearance toggle still works.
 *
 * The glass look is achieved without a real backdrop blur (expensive / API-gated):
 * keys are translucent with a top sheen and a hairline border, layered over the
 * page gradient so it bleeds through — which reads convincingly as frosted glass.
 */

/** Translucent styling for remote keys. */
data class GlassPalette(
    val fill: Color,
    val highlight: Color,
    val border: Color,
    val dark: Boolean,
)

private val MidnightDarkGlass = GlassPalette(
    fill = Color.White.copy(alpha = 0.075f),
    highlight = Color.White.copy(alpha = 0.12f),
    border = Color.White.copy(alpha = 0.16f),
    dark = true,
)

/** Provided so leaf composables can pick up the current glass styling. */
val LocalGlass = staticCompositionLocalOf { MidnightDarkGlass }

/**
 * Translucent glass background + top sheen + hairline border, clipped to
 * [shape]. Deliberately draws **no** elevation shadow: Android's outline
 * shadow renders circular shapes as octagons on some devices (e.g. Samsung
 * One UI). Depth comes from the sheen gradient and the border instead.
 */
@Composable
fun Modifier.glass(shape: Shape): Modifier {
    val g = LocalGlass.current
    return this
        .clip(shape)
        .background(g.fill, shape)
        .background(Brush.verticalGradient(listOf(g.highlight, Color.Transparent)), shape)
        .border(1.dp, g.border, shape)
}

// --- Accents per skin ------------------------------------------------------

private data class Accent(
    val primary: Color,
    val container: Color,
    val onContainer: Color,
)

private fun accent(skin: AppSkin, dark: Boolean): Accent = when (skin) {
    AppSkin.Midnight -> if (dark)
        Accent(Color(0xFF7AA2F7), Color(0xFF283452), Color(0xFFD6E0FF))
    else
        Accent(Color(0xFF3B5BDB), Color(0xFFDCE3FF), Color(0xFF0A1B4D))
    AppSkin.Graphite -> if (dark)
        Accent(Color(0xFF5FD6C4), Color(0xFF1E3B38), Color(0xFFBFF3EA))
    else
        Accent(Color(0xFF12897A), Color(0xFFCFEFE9), Color(0xFF06322C))
    AppSkin.Aurora -> if (dark)
        Accent(Color(0xFFC6A2FF), Color(0xFF382952), Color(0xFFE8DBFF))
    else
        Accent(Color(0xFF7A3FF2), Color(0xFFEADFFF), Color(0xFF2A0E63))
    AppSkin.Sunset -> if (dark)
        Accent(Color(0xFFFF9E7A), Color(0xFF4A2A2A), Color(0xFFFFDBCB))
    else
        Accent(Color(0xFFE5603C), Color(0xFFFFDDD0), Color(0xFF5A1B0A))
}

// --- Color schemes ---------------------------------------------------------

private fun baseDark(a: Accent) = darkColorScheme(
    primary = a.primary,
    onPrimary = Color(0xFF0A1022),
    primaryContainer = a.container,
    onPrimaryContainer = a.onContainer,
    secondary = Color(0xFF9AA5CE),
    onSecondary = Color(0xFF10141F),
    secondaryContainer = Color(0xFF232A3B),
    onSecondaryContainer = Color(0xFFD3DAF0),
    background = Color(0xFF0B0E13),
    onBackground = Color(0xFFE6E9F2),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFE6E9F2),
    surfaceVariant = Color(0xFF1B2029),
    onSurfaceVariant = Color(0xFF9CA3B4),
    surfaceContainerLowest = Color(0xFF0A0D12),
    surfaceContainerLow = Color(0xFF14171F),
    surfaceContainer = Color(0xFF161A22),
    surfaceContainerHigh = Color(0xFF1E232D),
    surfaceContainerHighest = Color(0xFF252B37),
    outline = Color(0xFF2E3542),
    outlineVariant = Color(0xFF222834),
    error = Color(0xFFF7768E),
)

private fun baseLight(a: Accent) = lightColorScheme(
    primary = a.primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = a.container,
    onPrimaryContainer = a.onContainer,
    secondary = Color(0xFF5A6478),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E7F3),
    onSecondaryContainer = Color(0xFF1A2233),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF1A1C22),
    surface = Color(0xFFF7F8FB),
    onSurface = Color(0xFF1A1C22),
    surfaceVariant = Color(0xFFE6E9F0),
    onSurfaceVariant = Color(0xFF5A6072),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F8),
    surfaceContainer = Color(0xFFEEF1F6),
    surfaceContainerHigh = Color(0xFFE8ECF3),
    surfaceContainerHighest = Color(0xFFE1E6EF),
    outline = Color(0xFFC4CAD6),
    outlineVariant = Color(0xFFDCE0E9),
    error = Color(0xFFD03A56),
)

/** The [ColorScheme] for a skin + light/dark. */
fun skinColorScheme(skin: AppSkin, dark: Boolean): ColorScheme {
    val a = accent(skin, dark)
    return if (dark) baseDark(a) else baseLight(a)
}

// --- Page background gradients ---------------------------------------------

private fun darkGradient(skin: AppSkin): List<Color> = when (skin) {
    AppSkin.Midnight -> listOf(Color(0xFF141B31), Color(0xFF0B0E15), Color(0xFF090B10))
    AppSkin.Graphite -> listOf(Color(0xFF20242B), Color(0xFF121419), Color(0xFF0C0D11))
    AppSkin.Aurora -> listOf(Color(0xFF1E1638), Color(0xFF130F24), Color(0xFF0B0916))
    AppSkin.Sunset -> listOf(Color(0xFF2C1626), Color(0xFF1A1017), Color(0xFF0F0A0E))
}

private fun lightGradient(skin: AppSkin): List<Color> = when (skin) {
    AppSkin.Midnight -> listOf(Color(0xFFEAF0FF), Color(0xFFF6F8FF), Color(0xFFFCFDFF))
    AppSkin.Graphite -> listOf(Color(0xFFEBEEF2), Color(0xFFF5F6F9), Color(0xFFFBFCFD))
    AppSkin.Aurora -> listOf(Color(0xFFF0E9FF), Color(0xFFF8F4FF), Color(0xFFFDFCFF))
    AppSkin.Sunset -> listOf(Color(0xFFFFEDE4), Color(0xFFFFF6F1), Color(0xFFFFFCFA))
}

/** The full-page gradient painted behind everything (top→bottom, tinted). */
fun skinBackground(skin: AppSkin, dark: Boolean): Brush =
    Brush.verticalGradient(if (dark) darkGradient(skin) else lightGradient(skin))

/** A representative accent colour for a skin, used for settings swatches. */
fun skinAccentPreview(skin: AppSkin): Color = accent(skin, dark = true).primary

/** Glass key palette for a skin + light/dark. */
fun skinGlass(dark: Boolean): GlassPalette = if (dark) {
    MidnightDarkGlass
} else {
    // Frosted white tiles that stay visible over the light gradient, defined
    // by a cool hairline border (no shadow — see [glass]).
    GlassPalette(
        fill = Color.White.copy(alpha = 0.82f),
        highlight = Color.White.copy(alpha = 0.55f),
        border = Color(0xFF3A4A66).copy(alpha = 0.16f),
        dark = false,
    )
}
