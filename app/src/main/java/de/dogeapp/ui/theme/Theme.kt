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

/** Colorful gradient used for headers and the big buttons. */
val FunGradient = Brush.linearGradient(listOf(Grape, Bubblegum, Sunny))
val CoolGradient = Brush.linearGradient(listOf(Sky, Grape))

/** One accent per trick/reaction button so the screen looks like a toy box. */
val ButtonColors = listOf(Grape, Bubblegum, Sunny, Sky, Mint, Tomato, Color(0xFF8B5CF6), Color(0xFF14B8A6))

private val Light = lightColorScheme(
    primary = Grape,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Bubblegum,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4F1),
    onSecondaryContainer = Color(0xFF500724),
    tertiary = Sky,
    tertiaryContainer = Color(0xFFD5F7FC),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF3EEFB),
    onSurfaceVariant = Color(0xFF4A4458),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC4B5FD),
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFF9A8D4),
    secondaryContainer = Color(0xFF831843),
    onSecondaryContainer = Color(0xFFFFE4F1),
    tertiary = Color(0xFF67E8F9),
    background = Color(0xFF17121F),
    surface = Color(0xFF17121F),
    surfaceVariant = Color(0xFF2A2236),
    onSurfaceVariant = Color(0xFFD9D0E6),
)

private val Rounded = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val Base = Typography()
private val Playful = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
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

/** Soft pastel background behind all screens. */
@Composable
fun backgroundBrush(): Brush =
    if (isSystemInDarkTheme()) Brush.verticalGradient(listOf(Color(0xFF1D1530), Color(0xFF17121F), Color(0xFF0F2027)))
    else Brush.verticalGradient(listOf(Color(0xFFF5EEFF), Color(0xFFFFF1F7), Color(0xFFEFFBFF)))
