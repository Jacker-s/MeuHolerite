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
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val IosLightColorScheme = lightColorScheme(
    primary = IosBlue,
    secondary = IosGray,
    tertiary = IosIndigo,
    background = IosLightBackground,
    surface = IosSystemBackground,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun MeuHoleriteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Desabilitamos dynamicColor para manter a fidelidade ao design iOS
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
