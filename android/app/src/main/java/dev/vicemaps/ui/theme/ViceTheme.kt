package dev.vicemaps.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.vicemaps.R

/** Palette shared with the Vice Maps browser extension. */
object Vice {
  val Ink = Color(0xFF0F0C1A)
  val White = Color(0xFFFFFAFA)
  val Blush = Color(0xFFFEE8EA)
  val Cream = Color(0xFFFFF9CB)
  val Peach = Color(0xFFFFD4A8)
  val Pink = Color(0xFFFFC2D0)
  val PinkHover = Color(0xFFEF8FB6)
  val Plum = Color(0xFF78487A)
  val PlumDeep = Color(0xFF6A3E6C)
  val PlumLight = Color(0xFF955D8D)
  val FieldBorder = Color(0xFF855381)
  val Teal = Color(0xFF1E6A78)
  val Hairline = Color(0x26FFFAFA)

  /** The panel gradient (MapGenie-style "window"). */
  val Window =
      Brush.linearGradient(
          0f to PlumLight.copy(alpha = 0.94f),
          0.3f to FieldBorder.copy(alpha = 0.95f),
          0.65f to Plum.copy(alpha = 0.96f),
          1f to PlumDeep.copy(alpha = 0.97f),
      )

  /** Route line on the map. */
  val Route = Color(0xFFFF8FC0)
  val RouteBorder = Color(0xFF6A2F5C)
}

val DecoDisplay =
    FontFamily(
        Font(R.font.big_shoulders_bold, FontWeight.Bold),
        Font(R.font.big_shoulders_extrabold, FontWeight.ExtraBold),
    )

val DecoSans =
    FontFamily(
        Font(R.font.jost_regular, FontWeight.Normal),
        Font(R.font.jost_medium, FontWeight.Medium),
        Font(R.font.jost_semibold, FontWeight.SemiBold),
    )

private val ViceColors =
    darkColorScheme(
        primary = Vice.Pink,
        onPrimary = Vice.Ink,
        primaryContainer = Vice.PinkHover,
        onPrimaryContainer = Vice.Ink,
        secondary = Vice.Peach,
        onSecondary = Vice.Ink,
        secondaryContainer = Vice.PlumDeep,
        onSecondaryContainer = Vice.Blush,
        tertiary = Vice.Cream,
        onTertiary = Vice.Ink,
        background = Vice.Ink,
        onBackground = Vice.Blush,
        surface = Vice.Plum,
        onSurface = Vice.White,
        surfaceVariant = Vice.PlumDeep,
        onSurfaceVariant = Vice.Blush,
        surfaceContainer = Vice.Plum,
        surfaceContainerHigh = Vice.FieldBorder,
        surfaceContainerHighest = Vice.PlumLight,
        surfaceContainerLow = Vice.PlumDeep,
        outline = Vice.FieldBorder,
        outlineVariant = Vice.Hairline,
        error = Color(0xFFFF9AA8),
        onError = Vice.Ink,
    )

private fun display(size: Int, weight: FontWeight = FontWeight.ExtraBold) =
    TextStyle(
        fontFamily = DecoDisplay,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * 1.05).sp,
        letterSpacing = 0.02.em,
    )

private fun body(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = DecoSans, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).sp)

private val ViceTypography =
    Typography(
        displayLarge = display(57),
        displayMedium = display(45),
        displaySmall = display(36),
        headlineLarge = display(34),
        headlineMedium = display(30),
        headlineSmall = display(26, FontWeight.Bold), // turn instruction text
        titleLarge = display(28), // distance to next turn
        titleMedium = display(20, FontWeight.Bold), // ETA / trip numbers
        titleSmall = display(16, FontWeight.Bold),
        bodyLarge = body(17),
        bodyMedium = body(15),
        bodySmall = body(13),
        labelLarge = display(17, FontWeight.Bold).copy(letterSpacing = 0.05.em),
        labelMedium = body(13, FontWeight.Medium),
        labelSmall = body(12, FontWeight.Medium),
    )

@Composable
fun ViceTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = ViceColors, typography = ViceTypography, content = content)
}
