package com.jack.meuholerite.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val IosDarkColorScheme = darkColorScheme(
    primary = IosBlue,
    secondary = IosGray,
    tertiary = IosIndigo,
    background = Color.Black,
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    surfaceContainer = Color(0xFF232326),
    surfaceContainerHigh = Color(0xFF2A2A2D),
    surfaceContainerLow = Color(0xFF1F1F22),
    primaryContainer = Color(0xFF1B3C6B),
    secondaryContainer = Color(0xFF353C44),
    tertiaryContainer = Color(0xFF2A3154),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE1E2E7)
)

private val IosLightColorScheme = lightColorScheme(
    primary = IosBlue,
    secondary = IosGray,
    tertiary = IosIndigo,
    background = IosLightBackground,
    surface = IosSystemBackground,
    surfaceVariant = Color(0xFFF0F2F7),
    surfaceContainer = Color(0xFFF7F8FC),
    surfaceContainerHigh = Color(0xFFF1F4FB),
    surfaceContainerLow = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEAFF),
    secondaryContainer = Color(0xFFE8EBF1),
    tertiaryContainer = Color(0xFFE4E7FF),
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF5E6472)
)

@Composable
fun MeuHoleriteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeAccent: String = AppThemePalettes.Blue.key,
    // Desabilitamos dynamicColor para manter a fidelidade ao design iOS
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = AppThemePalettes.fromKey(themeAccent)
    val colorScheme = if (darkTheme) {
        IosDarkColorScheme.copy(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.darkBackground,
            surface = palette.darkSurface,
            surfaceVariant = palette.darkSurfaceVariant,
            surfaceContainer = palette.darkSurface.copy(alpha = 0.96f),
            surfaceContainerHigh = palette.darkSurfaceVariant,
            surfaceContainerLow = palette.darkBackground.copy(alpha = 0.92f),
            primaryContainer = palette.primary.copy(alpha = 0.26f),
            secondaryContainer = palette.secondary.copy(alpha = 0.22f),
            tertiaryContainer = palette.tertiary.copy(alpha = 0.22f)
        )
    } else {
        IosLightColorScheme.copy(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.lightBackground,
            surface = palette.lightSurface,
            surfaceVariant = palette.lightSurfaceVariant,
            surfaceContainer = palette.lightBackground.copy(alpha = 0.88f),
            surfaceContainerHigh = palette.lightSurfaceVariant,
            surfaceContainerLow = palette.lightSurface,
            primaryContainer = palette.primary.copy(alpha = 0.14f),
            secondaryContainer = palette.secondary.copy(alpha = 0.14f),
            tertiaryContainer = palette.tertiary.copy(alpha = 0.14f)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
