package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.model.AccentPalette
import com.example.data.model.ThemeMode

@Composable
fun AnimeBrowserTheme(
  themeMode: ThemeMode = ThemeMode.AMOLED,
  accentPalette: AccentPalette = AccentPalette.ZENITH_VIOLET,
  dynamicColor: Boolean = false,
  isPrivateMode: Boolean = false,
  content: @Composable () -> Unit
) {
  val primaryAccent = if (isPrivateMode) PrivateAccent else Color(accentPalette.primaryHex)
  val secondaryAccent = Color(accentPalette.secondaryHex)
  val isLight = themeMode == ThemeMode.LIGHT && !isPrivateMode

  val colorScheme = when {
    dynamicColor && !isPrivateMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      val base = if (isLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
      if (themeMode == ThemeMode.AMOLED) {
        base.copy(background = AmoledBlack, surface = CharcoalSurface)
      } else base
    }
    isLight -> lightColorScheme(
      primary = Color(0xFF7C3AED),
      onPrimary = Color.White,
      primaryContainer = Color(0xFFEDE9FE),
      onPrimaryContainer = Color(0xFF2E1065),
      secondary = Color(0xFFDB2777),
      onSecondary = Color.White,
      secondaryContainer = Color(0xFFFCE7F3),
      onSecondaryContainer = Color(0xFF500724),
      tertiary = ZenithCrescentCyan,
      background = LightBg,
      onBackground = TextPrimaryLight,
      surface = LightSurface,
      onSurface = TextPrimaryLight,
      surfaceVariant = LightSurfaceVariant,
      onSurfaceVariant = TextSecondaryLight,
      surfaceContainer = LightSurface,
      surfaceContainerHigh = LightSurfaceVariant,
      surfaceContainerHighest = Color(0xFFE2D7F8),
      outline = Color(0xFFC4B5E6),
      outlineVariant = Color(0xFFDDD4F2),
      error = StatusError,
      onError = Color.White
    )
    else -> {
      val bg = when {
        isPrivateMode -> Color(0xFF0A0418)
        themeMode == ThemeMode.AMOLED -> AmoledBlack
        else -> ObsidianBg
      }
      val surface = if (isPrivateMode) PrivateSurface else CharcoalSurface
      darkColorScheme(
        primary = primaryAccent,
        onPrimary = Color(0xFF0E0520),
        primaryContainer = Color(0xFF2D1B54),
        onPrimaryContainer = Color(0xFFF5EEFF),
        secondary = secondaryAccent,
        onSecondary = Color(0xFF1A0416),
        secondaryContainer = Color(0xFF3E1542),
        onSecondaryContainer = Color(0xFFFFE4F8),
        tertiary = ZenithCrescentCyan,
        onTertiary = Color(0xFF061529),
        background = bg,
        onBackground = TextPrimaryDark,
        surface = surface,
        onSurface = TextPrimaryDark,
        surfaceVariant = CharcoalSurfaceHigh,
        onSurfaceVariant = TextSecondaryDark,
        surfaceContainerLowest = AmoledBlack,
        surfaceContainerLow = ObsidianBg,
        surfaceContainer = CharcoalSurface,
        surfaceContainerHigh = CharcoalSurfaceHigh,
        surfaceContainerHighest = CharcoalSurfaceHighest,
        outline = Color(0xFF47337A),
        outlineVariant = Color(0xFF2E2054),
        error = StatusError,
        onError = Color.White
      )
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = AnimeBrowserTypography,
    content = content
  )
}
