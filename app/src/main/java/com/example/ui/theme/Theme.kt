package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light = lightColorScheme(
  primary = Blue, onPrimary = Color.White,
  primaryContainer = BlueWeak, onPrimaryContainer = BlueDeep,
  secondary = Gray700, onSecondary = Color.White,
  secondaryContainer = Gray100, onSecondaryContainer = Gray900,
  background = Gray50, onBackground = Gray900,
  surface = Color.White, onSurface = Gray900,
  surfaceVariant = Gray100, onSurfaceVariant = Gray600,
  surfaceTint = Color.Transparent,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color.White,
  surfaceContainer = Color.White,
  surfaceContainerHigh = Color.White,
  surfaceContainerHighest = Gray200,
  outline = Gray500, outlineVariant = Gray200,
  error = Red, onError = Color.White,
  errorContainer = RedWeak, onErrorContainer = RedDeep,
)

private val Dark = darkColorScheme(
  primary = DarkBlue, onPrimary = Color(0xFF0B1E45),
  primaryContainer = DarkBlueWeak, onPrimaryContainer = Color(0xFFD6E3FC),
  secondary = DarkText2, onSecondary = DarkBg,
  secondaryContainer = DarkSurface2, onSecondaryContainer = DarkText,
  background = DarkBg, onBackground = DarkText,
  surface = DarkSurface, onSurface = DarkText,
  surfaceVariant = DarkSurface2, onSurfaceVariant = DarkText2,
  surfaceTint = Color.Transparent,
  surfaceContainerLowest = DarkSurface,
  surfaceContainerLow = DarkSurface,
  surfaceContainer = DarkSurface,
  surfaceContainerHigh = DarkSurface2,
  surfaceContainerHighest = Color(0xFF30353B),
  outline = Color(0xFF6E767F), outlineVariant = DarkLine,
  error = DarkRed, onError = Color(0xFF3A0904),
  errorContainer = DarkRedWeak, onErrorContainer = Color(0xFFFFDAD5),
)

/** Material 색 체계에 없는 '주의' 색 */
@Immutable
data class ExtraColors(val warning: Color, val warningContainer: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Amber, AmberWeak) }

object AppTheme {
  val extra: ExtraColors
    @Composable get() = LocalExtraColors.current
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val extra = if (darkTheme) ExtraColors(DarkAmber, DarkAmberWeak) else ExtraColors(Amber, AmberWeak)
  CompositionLocalProvider(LocalExtraColors provides extra) {
    MaterialTheme(
      colorScheme = if (darkTheme) Dark else Light,
      typography = Typography,
      // 모서리는 작게: 버튼 8, 대화상자 12. 알약 모양은 쓰지 않습니다.
      shapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(12.dp),
      ),
      content = content,
    )
  }
}
