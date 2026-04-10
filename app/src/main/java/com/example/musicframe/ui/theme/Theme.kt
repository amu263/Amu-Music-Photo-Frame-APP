package com.example.musicframe.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SaltColors.Primary,
    onPrimary = SaltColors.OnPrimary,
    primaryContainer = SaltColors.Primary.copy(alpha = 0.12f),
    onPrimaryContainer = SaltColors.PrimaryDark,
    secondary = SaltColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = SaltColors.Accent.copy(alpha = 0.12f),
    onSecondaryContainer = SaltColors.AccentDark,
    tertiary = SaltColors.Success,
    onTertiary = Color.White,
    background = SaltColors.Background,
    onBackground = SaltColors.OnBackground,
    surface = SaltColors.Surface,
    onSurface = SaltColors.OnSurface,
    surfaceVariant = SaltColors.SurfaceVariant,
    onSurfaceVariant = SaltColors.OnSurfaceVariant,
    outline = SaltColors.Border,
    outlineVariant = SaltColors.Border.copy(alpha = 0.5f),
    error = SaltColors.Error,
    onError = Color.White,
    scrim = SaltColors.Scrim
)

private val DarkColorScheme = darkColorScheme(
    primary = SaltColors.Primary,
    onPrimary = SaltColors.OnPrimary,
    primaryContainer = SaltColors.Primary.copy(alpha = 0.24f),
    onPrimaryContainer = SaltColors.Primary,
    secondary = SaltColors.Accent,
    onSecondary = Color.White,
    secondaryContainer = SaltColors.Accent.copy(alpha = 0.24f),
    onSecondaryContainer = SaltColors.Accent,
    tertiary = SaltColors.Success,
    onTertiary = Color.White,
    background = SaltColors.DarkBackground,
    onBackground = SaltColors.OnDarkBackground,
    surface = SaltColors.DarkSurface,
    onSurface = SaltColors.OnDarkSurface,
    surfaceVariant = SaltColors.DarkSurfaceVariant,
    onSurfaceVariant = SaltColors.OnDarkSurface,
    outline = SaltColors.DarkBorder,
    outlineVariant = SaltColors.DarkBorder.copy(alpha = 0.5f),
    error = SaltColors.Error,
    onError = Color.White,
    scrim = SaltColors.Scrim
)

@Composable
fun musicFrameTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // SaltUI 风格不使用动态颜色
    content: @Composable () -> Unit
) {
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
