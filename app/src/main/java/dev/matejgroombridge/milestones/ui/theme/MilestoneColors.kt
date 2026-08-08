package dev.matejgroombridge.milestones.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * A pastel-tone palette used to colour milestone cards. Each entry has a light
 * variant (used as the card background in light mode) and a dark variant
 * (used in dark mode), plus a stronger accent for the icon tile and charts.
 *
 * Stored on a milestone by [key] so the palette can be re-ordered or extended
 * without breaking persisted data — unknown keys fall back to [defaultEntry].
 */
data class MilestoneColorEntry(
    val key: String,
    val label: String,
    val light: Color,
    val dark: Color,
    val accent: Color,
    val onColor: Color,
)

object MilestoneColors {

    // A curated 8-colour palette covering the main hue families: warm pinks,
    // oranges/yellows, greens, blues, purples, and a neutral. Picking 8 keeps
    // the colour row in the editor dialog uncluttered and easy to scan.
    val palette: List<MilestoneColorEntry> = listOf(
        MilestoneColorEntry(
            key = "blush",
            label = "Blush",
            light = Color(0xFFFFE0E6),
            dark = Color(0xFF5A3A42),
            accent = Color(0xFFF7A6B5),
            onColor = Color(0xFF3A1F25),
        ),
        MilestoneColorEntry(
            key = "peach",
            label = "Peach",
            light = Color(0xFFFFE3D1),
            dark = Color(0xFF5A3F30),
            accent = Color(0xFFFFB48A),
            onColor = Color(0xFF3A2418),
        ),
        MilestoneColorEntry(
            key = "butter",
            label = "Butter",
            light = Color(0xFFFFF4C2),
            dark = Color(0xFF55502B),
            accent = Color(0xFFFFE066),
            onColor = Color(0xFF3A330A),
        ),
        MilestoneColorEntry(
            key = "mint",
            label = "Mint",
            light = Color(0xFFD1F0DA),
            dark = Color(0xFF2E4D3A),
            accent = Color(0xFF8DD6A4),
            onColor = Color(0xFF143222),
        ),
        MilestoneColorEntry(
            // Sits between mint (green) and sky (blue) so the palette flows
            // smoothly along the green→cyan→blue axis.
            key = "teal",
            label = "Teal",
            light = Color(0xFFCFE8E4),
            dark = Color(0xFF2F4D49),
            accent = Color(0xFF8DCDC4),
            onColor = Color(0xFF143230),
        ),
        MilestoneColorEntry(
            key = "sky",
            label = "Sky",
            light = Color(0xFFD3E8F5),
            dark = Color(0xFF2F4756),
            accent = Color(0xFF8FC4E0),
            onColor = Color(0xFF12303F),
        ),
        MilestoneColorEntry(
            key = "lavender",
            label = "Lavender",
            light = Color(0xFFE3DAF5),
            dark = Color(0xFF3F354F),
            accent = Color(0xFFB7A5DD),
            onColor = Color(0xFF231A38),
        ),
        MilestoneColorEntry(
            key = "fog",
            label = "Fog",
            light = Color(0xFFE2E5EA),
            dark = Color(0xFF40454D),
            accent = Color(0xFFB6BCC6),
            onColor = Color(0xFF22262D),
        ),
    )

    private val byKey: Map<String, MilestoneColorEntry> = palette.associateBy { it.key }

    val defaultEntry: MilestoneColorEntry get() = palette.first()

    fun entry(key: String): MilestoneColorEntry = byKey[key] ?: defaultEntry
}

/**
 * Resolves the appropriate background colour for the current theme.
 */
@Composable
@ReadOnlyComposable
fun MilestoneColorEntry.containerColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.let {
        // Cheap proxy: dark theme → background luminance is low.
        (it.red * 0.299f + it.green * 0.587f + it.blue * 0.114f) < 0.5f
    }
    return if (isDark) dark else light
}

/**
 * Foreground colour suitable for text on top of [containerColor]. In light
 * mode we use the entry's [MilestoneColorEntry.onColor]; in dark mode we use
 * the theme's onSurface so contrast stays comfortable.
 */
@Composable
@ReadOnlyComposable
fun MilestoneColorEntry.contentColor(): Color {
    val bg = MaterialTheme.colorScheme.background
    val isDark = (bg.red * 0.299f + bg.green * 0.587f + bg.blue * 0.114f) < 0.5f
    return if (isDark) MaterialTheme.colorScheme.onSurface else onColor
}
