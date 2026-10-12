package me.bnfy.blanc.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * New Tab wallpaper presets — mobile adaptation of the desktop nature-wallpaper
 * set. Instead of bundling photographic images, each preset is a soft two-tone
 * vertical gradient with a light and a dark variant so it stays readable under
 * either theme. Purely device-local; no network fetch.
 */
data class WallpaperPreset(
    val id: String,
    val label: String,
    val lightColors: List<Color>,
    val darkColors: List<Color>
)

val WALLPAPER_PRESETS: List<WallpaperPreset> = listOf(
    WallpaperPreset(
        id = "none",
        label = "Plain",
        lightColors = emptyList(),
        darkColors = emptyList()
    ),
    WallpaperPreset(
        id = "sunrise",
        label = "Sunrise",
        lightColors = listOf(Color(0xFFF5E3C3), Color(0xFFFFF8EC)),
        darkColors = listOf(Color(0xFF3A2E1A), Color(0xFF241B0E))
    ),
    WallpaperPreset(
        id = "ocean",
        label = "Ocean",
        lightColors = listOf(Color(0xFFD7E9F7), Color(0xFFF2F9FF)),
        darkColors = listOf(Color(0xFF14273B), Color(0xFF0C1A29))
    ),
    WallpaperPreset(
        id = "forest",
        label = "Forest",
        lightColors = listOf(Color(0xFFD9EBD8), Color(0xFFF0FAEF)),
        darkColors = listOf(Color(0xFF182A1A), Color(0xFF0E1B10))
    ),
    WallpaperPreset(
        id = "dusk",
        label = "Dusk",
        lightColors = listOf(Color(0xFFE2DAF2), Color(0xFFF7F4FD)),
        darkColors = listOf(Color(0xFF221E38), Color(0xFF14112A))
    ),
    WallpaperPreset(
        id = "rose",
        label = "Rose",
        lightColors = listOf(Color(0xFFF7DFDB), Color(0xFFFFF2EF)),
        darkColors = listOf(Color(0xFF371E1B), Color(0xFF24110F))
    )
)

/** Resolves a preset id to a [Brush], or null for "none". */
fun wallpaperBrushFor(id: String, isDark: Boolean): Brush? {
    val preset = WALLPAPER_PRESETS.firstOrNull { it.id == id } ?: return null
    val colors = if (isDark) preset.darkColors else preset.lightColors
    if (colors.size < 2) return null
    return Brush.verticalGradient(colors)
}

fun wallpaperLabelFor(id: String): String {
    return WALLPAPER_PRESETS.firstOrNull { it.id == id }?.label ?: "Plain"
}