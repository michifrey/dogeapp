package de.dogeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Playful palette: purple (eyes), pink, sunny orange, sky cyan.
val Grape = Color(0xFF7C3AED)
val Bubblegum = Color(0xFFEC4899)
val Sunny = Color(0xFFF59E0B)
val Sky = Color(0xFF06B6D4)
val Mint = Color(0xFF10B981)
val Tomato = Color(0xFFEF4444)

/** Darker red for the stop button: white bold text on it stays readable (contrast > 4.5:1). */
val StopRed = Color(0xFFDC2626)

/**
 * Brand gradient. Used sparingly: only the top header and the one primary call to action
 * per screen (e.g. the big drive button or "Dog-E verbinden"). Everything else uses calm tonal surfaces.
 */
val FunGradient = Brush.linearGradient(listOf(Grape, Bubblegum, Sunny))

/** Secondary gradient (head button). */
val CoolGradient = Brush.linearGradient(listOf(Sky, Grape))

/** One accent per trick/reaction tile so the grid still looks like a toy box. */
val ButtonColors = listOf(Grape, Bubblegum, Sunny, Sky, Mint, Tomato, Color(0xFF8B5CF6), Color(0xFF14B8A6))

private val Light = lightColorScheme(
    primary = Grape,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFFBE185D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4F1),
    onSecondaryContainer = Color(0xFF500724),
    tertiary = Color(0xFF0E7490),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F7FC),
    onTertiaryContainer = Color(0xFF083344),
    error = Color(0xFFB91C1C),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1C1726),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1C1726),
    surfaceVariant = Color(0xFFEFE9F8),
    onSurfaceVariant = Color(0xFF4A4458),
    outline = Color(0xFF7A7289),
    outlineVariant = Color(0xFFD6CFE3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FD),
    surfaceContainer = Color(0xFFF2ECFA),
    surfaceContainerHigh = Color(0xFFECE5F6),
    surfaceContainerHighest = Color(0xFFE6DFF1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFF9A8D4),
    onSecondary = Color(0xFF500724),
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFFE4F1),
    tertiary = Color(0xFF67E8F9),
    onTertiary = Color(0xFF083344),
    tertiaryContainer = Color(0xFF155E75),
    onTertiaryContainer = Color(0xFFD5F7FC),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF15111C),
    onBackground = Color(0xFFEDE7F6),
    surface = Color(0xFF15111C),
    onSurface = Color(0xFFEDE7F6),
    surfaceVariant = Color(0xFF2A2236),
    onSurfaceVariant = Color(0xFFD9D0E6),
    outline = Color(0xFF968DA6),
    outlineVariant = Color(0xFF433A52),
    surfaceContainerLowest = Color(0xFF100C16),
    surfaceContainerLow = Color(0xFF1C1724),
    surfaceContainer = Color(0xFF211B2A),
    surfaceContainerHigh = Color(0xFF2B2435),
    surfaceContainerHighest = Color(0xFF362E41),
)

private val Rounded = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Slightly larger and bolder than the defaults so a young child can read the labels.
private val Base = Typography()
private val Playful = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
)

@Composable
fun DogeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Rounded,
        typography = Playful,
        content = content,
    )
}

/** Very soft background behind all screens (almost flat, so cards and buttons stand out). */
@Composable
fun backgroundBrush(): Brush =
    if (isSystemInDarkTheme()) Brush.verticalGradient(listOf(Color(0xFF18121F), Color(0xFF15111C)))
    else Brush.verticalGradient(listOf(Color(0xFFF7F1FF), Color(0xFFFBF8FF)))
