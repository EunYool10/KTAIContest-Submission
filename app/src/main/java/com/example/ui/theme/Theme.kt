package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
  primary = Teal, onPrimary = Color.White,
  primaryContainer = TealSoft, onPrimaryContainer = TealDeep,
  secondary = InkSoft, onSecondary = Color.White,
  secondaryContainer = Color(0xFFE6EAE7), onSecondaryContainer = Ink,
  background = Paper, onBackground = Ink,
  surface = Color.White, onSurface = Ink,
  surfaceVariant = Color(0xFFEDF0EE), onSurfaceVariant = InkSoft,
  surfaceContainer = Color(0xFFF0F2F0),
  surfaceContainerHigh = Color(0xFFE9ECEA),
  surfaceContainerHighest = Color(0xFFE2E6E3),
  surfaceContainerLow = Color(0xFFF8F9F8),
  surfaceContainerLowest = Color.White,
  outline = Color(0xFF8D9691), outlineVariant = Line,
  error = Red, onError = Color.White,
  errorContainer = RedSoft, onErrorContainer = RedDeep,
)

private val Dark = darkColorScheme(
  primary = TealDark, onPrimary = Color(0xFF00382F),
  primaryContainer = TealSoftDark, onPrimaryContainer = Color(0xFFBDEFE2),
  secondary = InkSoftDark, onSecondary = PaperDark,
  secondaryContainer = SurfaceDark2, onSecondaryContainer = InkDark,
  background = PaperDark, onBackground = InkDark,
  surface = SurfaceDark, onSurface = InkDark,
  surfaceVariant = SurfaceDark2, onSurfaceVariant = InkSoftDark,
  surfaceContainer = Color(0xFF1C211F),
  surfaceContainerHigh = Color(0xFF232927),
  surfaceContainerHighest = Color(0xFF2B3230),
  surfaceContainerLow = Color(0xFF161A18),
  surfaceContainerLowest = Color(0xFF0D100F),
  outline = Color(0xFF6C7671), outlineVariant = LineDark,
  error = RedDark, onError = Color(0xFF5A0B0E),
  errorContainer = RedSoftDark, onErrorContainer = Color(0xFFFFDAD6),
)

/** Material 색 체계에 없는 '주의' 색 */
@Immutable
data class ExtraColors(val warning: Color, val warningContainer: Color, val onWarningContainer: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Amber, AmberSoft, Color(0xFF4A2C00)) }

object AppTheme {
  val extra: ExtraColors
    @Composable get() = LocalExtraColors.current
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val scheme: ColorScheme = if (darkTheme) Dark else Light
  val extra = if (darkTheme) ExtraColors(AmberDark, AmberSoftDark, Color(0xFFFFDDB0))
  else ExtraColors(Amber, AmberSoft, Color(0xFF4A2C00))
  CompositionLocalProvider(LocalExtraColors provides extra) {
    MaterialTheme(
      colorScheme = scheme,
      typography = Typography,
      shapes = Shapes(
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp),
      ),
      content = content,
    )
  }
}
