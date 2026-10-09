package me.bnfy.blanc.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// --- Brand Colors (Bowser Design System & Sunrise Theme) ---
val SunriseGold = Color(0xFFD4A359)
val SunriseGoldDark = Color(0xFFB8863A)
val SunriseWarmIvory = Color(0xFFFAF7F2)
val SunriseWarmSurface = Color(0xFFF2ECE1)
val SunriseWarmSurfaceHigh = Color(0xFFE9E1D3)
val SunriseWarmCharcoal = Color(0xFF1E1A16)
val SunriseWarmMuted = Color(0xFF6E6458)

// Dark Theme Colors
val DarkBackground = Color(0xFF0F0F12)
val DarkSurface = Color(0xFF16161B)
val DarkSurfaceContainer = Color(0xFF1E1E24)
val DarkSurfaceContainerHigh = Color(0xFF26262F)
val DarkTextPrimary = Color(0xFFEDEDF0)
val DarkTextSecondary = Color(0xFFA0A0AB)
val DarkOutline = Color(0xFF32323D)

// Light Theme Colors
val LightBackground = Color(0xFFF9F9FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFF2F2F6)
val LightSurfaceContainerHigh = Color(0xFFE8E8EE)
val LightTextPrimary = Color(0xFF18181B)
val LightTextSecondary = Color(0xFF71717A)
val LightOutline = Color(0xFFE4E4E7)

private val DarkColorScheme = darkColorScheme(
    primary = SunriseGold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = Color(0xFF3D2C00),
    onPrimaryContainer = SunriseGold,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = Color(0xFF0A0A0D),
    surfaceContainerLow = Color(0xFF131317),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF2D2D38),
    outline = DarkOutline,
    outlineVariant = Color(0xFF262630)
)

private val LightColorScheme = lightColorScheme(
    primary = SunriseGoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF2D6),
    onPrimaryContainer = Color(0xFF3D2C00),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFFDEDEE6),
    outline = LightOutline,
    outlineVariant = Color(0xFFEDEDF2)
)

private val SunriseColorScheme = lightColorScheme(
    primary = SunriseGold,
    onPrimary = Color(0xFF2E1C00),
    primaryContainer = SunriseWarmSurfaceHigh,
    onPrimaryContainer = Color(0xFF382500),
    background = SunriseWarmIvory,
    onBackground = SunriseWarmCharcoal,
    surface = Color(0xFFFFFDF9),
    onSurface = SunriseWarmCharcoal,
    surfaceVariant = SunriseWarmSurface,
    onSurfaceVariant = SunriseWarmMuted,
    surfaceContainerLowest = Color(0xFFFFFDF9),
    surfaceContainerLow = Color(0xFFF7F0E5),
    surfaceContainer = SunriseWarmSurface,
    surfaceContainerHigh = SunriseWarmSurfaceHigh,
    surfaceContainerHighest = Color(0xFFDFD5C4),
    outline = Color(0xFFDCD2C0),
    outlineVariant = Color(0xFFEAE2D4)
)

/**
 * Material 3 Expressive Shape Tokens.
 */
object ExpressiveShapes {
    /** Pill shape for floating command bar and chip controls (50% circular or 28dp radius). */
    val Pill = RoundedCornerShape(28.dp)
    /** Expressive Card shape with large, friendly corners (24dp). */
    val Card = RoundedCornerShape(24.dp)
    /** Expressive Sheet shape with rounded top corners for bottom drawers & sheets (28dp). */
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    /** Expressive Button shape (16dp). */
    val Button = RoundedCornerShape(16.dp)
    /** Small interactive tile shape (12dp). */
    val Small = RoundedCornerShape(12.dp)
}

/**
 * Material 3 Expressive Motion Specs.
 */
object ExpressiveMotion {
    /** Playful, lively spring for interactive touch feedback, dialogs, and sheets. */
    val BouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Snappy spring for quick responsive translations (e.g. scroll hide/reveal). */
    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val PillOffsetSpring = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Blanc Dynamic Theme supporting System Default, Light, Dark, and Sunrise palettes.
 */
@Composable
fun BlancTheme(
    theme: String = "system",
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (theme.lowercase()) {
        "dark" -> true
        "light" -> false
        "sunrise" -> false
        else -> systemInDark
    }

    val colorScheme: ColorScheme = when (theme.lowercase()) {
        "dark" -> DarkColorScheme
        "light" -> LightColorScheme
        "sunrise" -> SunriseColorScheme
        else -> if (systemInDark) DarkColorScheme else LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
