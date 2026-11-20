package com.musictracker.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musictracker.R

private val Orange = Color(0xFFFF8216)
private val Ink = Color(0xFF181A20)
private val Panel = Color(0xFF1F222A)
private val PanelHigh = Color(0xFF262A35)
private val PanelHighest = Color(0xFF2E323D)
private val Line = Color(0xFF35383F)
private val Grey = Color(0xFF9E9E9E)

/** Always dark with orange accents. */
private val colors = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A2614),
    onPrimaryContainer = Color(0xFFFFB27A),
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A2614),
    onSecondaryContainer = Orange,
    tertiary = Color(0xFFFFB27A),
    tertiaryContainer = Color(0xFF3A2614),
    onTertiaryContainer = Color(0xFFFFB27A),
    background = Ink,
    onBackground = Color.White,
    surface = Ink,
    onSurface = Color.White,
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Grey,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    surfaceBright = PanelHighest,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFF75555),
    errorContainer = Color(0xFF3B1F22),
    onErrorContainer = Color(0xFFFF9C9C),
)

/** The full player's background, from near-black at the top to deep blue at the bottom. */
val PlayerGradient = listOf(Color(0xFF050608), Color(0xFF0E1120), Color(0xFF1B2140))

/** Colours for the art tiles shown where a song has no cover. */
val ArtPalette = listOf(
    Color(0xFFFF8216) to Color(0xFFE5375B),
    Color(0xFF7B5CFF) to Color(0xFF2EC4F1),
    Color(0xFF16C79A) to Color(0xFF2E7DF1),
    Color(0xFFF1C40F) to Color(0xFFFF6B35),
    Color(0xFFE5375B) to Color(0xFF7B2CBF),
    Color(0xFF2EC4F1) to Color(0xFF16C79A),
)

@OptIn(ExperimentalTextApi::class)
private fun urbanist(weight: Int) = Font(
    R.font.urbanist,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val Urbanist = FontFamily(urbanist(400), urbanist(500), urbanist(600), urbanist(700), urbanist(800))

private val typography = Typography().let { t ->
    fun TextStyle.u(weight: FontWeight) = copy(fontFamily = Urbanist, fontWeight = weight)
    Typography(
        displayLarge = t.displayLarge.u(FontWeight.Bold),
        displayMedium = t.displayMedium.u(FontWeight.Bold),
        displaySmall = t.displaySmall.u(FontWeight.Bold),
        headlineLarge = t.headlineLarge.u(FontWeight.Bold),
        headlineMedium = t.headlineMedium.u(FontWeight.Bold),
        headlineSmall = t.headlineSmall.u(FontWeight.Bold),
        titleLarge = t.titleLarge.u(FontWeight.Bold),
        titleMedium = t.titleMedium.u(FontWeight.Bold),
        titleSmall = t.titleSmall.u(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.u(FontWeight.Medium),
        bodyMedium = t.bodyMedium.u(FontWeight.Medium),
        bodySmall = t.bodySmall.u(FontWeight.Medium),
        labelLarge = t.labelLarge.u(FontWeight.SemiBold),
        labelMedium = t.labelMedium.u(FontWeight.SemiBold),
        labelSmall = t.labelSmall.u(FontWeight.SemiBold),
    )
}

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun MusicTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes, content = content)
}
