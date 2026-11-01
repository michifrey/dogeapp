package de.dogeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.dogeapp.R

// ---------------------------------------------------------------------------------------------
// Palette: "collage poster" – cream paper, ink black, loud orange, mustard stickers, soft pastels.
// ---------------------------------------------------------------------------------------------

/** Card / surface paper. */
val Paper = Color(0xFFFBF3E6)

/** Page background paper (a touch darker than the cards). */
val PaperDeep = Color(0xFFF6EBD9)

/** Cream used as text colour in dark mode. */
val Cream = Color(0xFFF6EBD9)

/** Body text, outlines of doodles, the stop button. */
val Ink = Color(0xFF1E1A16)

/** Brand orange for decorations, script words, torn paper and highlights. */
val Orange = Color(0xFFEF5B25)

/**
 * Slightly deeper orange used as Material `primary` (filled buttons, switches, sliders):
 * white bold text on it stays readable (≈ 4.3:1).
 */
val OrangeDeep = Color(0xFFD84B1A)

/** Sticker yellow. */
val Mustard = Color(0xFFF6C531)

/** Soft pastels for the icon circles (like the subject rows of the reference). */
val Sage = Color(0xFFCFE0BF)
val Powder = Color(0xFFC9D8F2)
val Butter = Color(0xFFF9E1A0)
val Lilac = Color(0xFFDFD1F6)
val Peach = Color(0xFFFAD3BE)
val Rose = Color(0xFFF7C9D3)

/** Order of the pastel circles in grids. */
val Pastels = listOf(Sage, Powder, Butter, Lilac, Peach, Rose)

/** "Done" tick in progress rows. */
val DoneGreen = Color(0xFF3E8E57)

private val NightPaper = Color(0xFF1A1411)
private val NightCard = Color(0xFF231C17)

/** Pastel circle colour for index [i]; muted in dark mode so emojis still pop. */
@Composable
fun pastel(i: Int): Color {
    val c = Pastels[((i % Pastels.size) + Pastels.size) % Pastels.size]
    return if (isSystemInDarkTheme()) c.copy(alpha = 0.30f).compositeOver(NightCard) else c
}

// ---------------------------------------------------------------------------------------------
// Fonts
// ---------------------------------------------------------------------------------------------

/** Anton: condensed bold display face for the huge uppercase poster headlines. */
val DisplayFont = FontFamily(Font(R.font.anton_regular))

/** Caveat Brush: hand-written script accents ("lass uns spielen", notes, sticker footnotes). */
val ScriptFont = FontFamily(Font(R.font.caveat_brush))

/** Default script style (orange script words under headlines). Colour is set at the call site. */
val ScriptStyle = TextStyle(fontFamily = ScriptFont, fontSize = 34.sp, lineHeight = 38.sp)

// ---------------------------------------------------------------------------------------------
// Colour schemes
// ---------------------------------------------------------------------------------------------

private val Light = lightColorScheme(
    primary = OrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Peach,
    onPrimaryContainer = Ink,
    secondary = Mustard,
    onSecondary = Ink,
    secondaryContainer = Butter,
    onSecondaryContainer = Ink,
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFFCEBB8),
    onTertiaryContainer = Ink,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DAD4),
    onErrorContainer = Color(0xFF5C1210),
    background = PaperDeep,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE2CC),
    onSurfaceVariant = Color(0xFF5A4E44),
    outline = Color(0xFF7A6A5A),
    outlineVariant = Color(0xFFD9C8AE),
    inverseSurface = Ink,
    inverseOnSurface = Cream,
    inversePrimary = Color(0xFFFF9A70),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFBF4),
    surfaceContainerLow = Paper,
    surfaceContainer = Color(0xFFF7ECDB),
    surfaceContainerHigh = Color(0xFFF1E4CF),
    surfaceContainerHighest = Color(0xFFEBDCC4),
)

private val Dark = darkColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5A2614),
    onPrimaryContainer = Color(0xFFFFDCCB),
    secondary = Mustard,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4F3F10),
    onSecondaryContainer = Color(0xFFFCE7A6),
    tertiary = Mustard,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFF4F3F10),
    onTertiaryContainer = Color(0xFFFCE7A6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = NightPaper,
    onBackground = Cream,
    surface = NightCard,
    onSurface = Cream,
    surfaceVariant = Color(0xFF2E251F),
    onSurfaceVariant = Color(0xFFD8C9B5),
    outline = Color(0xFFA8957E),
    outlineVariant = Color(0xFF4A3D33),
    inverseSurface = Cream,
    inverseOnSurface = Ink,
    inversePrimary = OrangeDeep,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF15100D),
    surfaceContainerLow = NightCard,
    surfaceContainer = Color(0xFF281F1A),
    surfaceContainerHigh = Color(0xFF2F2620),
    surfaceContainerHighest = Color(0xFF382E27),
)

// ---------------------------------------------------------------------------------------------
// Shapes & type
// ---------------------------------------------------------------------------------------------

private val PosterShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(50), // pills
)

private val Base = Typography()

private fun display(size: Int, line: Int) =
    TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.3.sp)

/** Anton for display/headline styles, default sans (medium/bold, ink) for everything else. */
private val PosterTypography = Base.copy(
    displayLarge = display(64, 62),
    displayMedium = display(52, 52),
    displaySmall = display(42, 44),
    headlineLarge = display(36, 40),
    headlineMedium = display(30, 34),
    headlineSmall = display(24, 28),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = Base.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp),
    bodyMedium = Base.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodySmall = Base.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Bold),
)

@Composable
fun DogeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = PosterShapes,
        typography = PosterTypography,
        content = content,
    )
}
