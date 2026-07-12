package com.jack.meuholerite.ui.theme

import androidx.compose.ui.graphics.Color

data class AppThemePalette(
    val key: String,
    val label: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color
)

object AppThemePalettes {
    val Blue = AppThemePalette(
        key = "blue",
        label = "Azul",
        primary = IosBlue,
        secondary = IosGray,
        tertiary = IosIndigo,
        lightBackground = Color(0xFFF4F8FF),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVariant = Color(0xFFE9F1FF),
        darkBackground = Color(0xFF121B2B),
        darkSurface = Color(0xFF1B263B),
        darkSurfaceVariant = Color(0xFF24324F)
    )
    val Green = AppThemePalette(
        key = "green",
        label = "Verde",
        primary = IosGreen,
        secondary = Color(0xFF6F8F7A),
        tertiary = Color(0xFF2E7D5A),
        lightBackground = Color(0xFFF3FBF6),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVariant = Color(0xFFE3F4E9),
        darkBackground = Color(0xFF0D1F18),
        darkSurface = Color(0xFF162D24),
        darkSurfaceVariant = Color(0xFF224235)
    )
    val Orange = AppThemePalette(
        key = "orange",
        label = "Laranja",
        primary = IosOrange,
        secondary = Color(0xFF9A8A7D),
        tertiary = Color(0xFFCC6F2C),
        lightBackground = Color(0xFFFFF7F1),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVariant = Color(0xFFFFEBDD),
        darkBackground = Color(0xFF1F1712),
        darkSurface = Color(0xFF2C211A),
        darkSurfaceVariant = Color(0xFF3D2F25)
    )
    val Rose = AppThemePalette(
        key = "rose",
        label = "Rosa",
        primary = Color(0xFFE85D75),
        secondary = Color(0xFF9A7B86),
        tertiary = Color(0xFFB84C8A),
        lightBackground = Color(0xFFFFF5F8),
        lightSurface = Color(0xFFFFFFFF),
        lightSurfaceVariant = Color(0xFFFFE7EE),
        darkBackground = Color(0xFF22131C),
        darkSurface = Color(0xFF2E1B27),
        darkSurfaceVariant = Color(0xFF402737)
    )

    val all = listOf(Blue, Green, Orange, Rose)

    fun fromKey(key: String?): AppThemePalette {
        return all.firstOrNull { it.key == key } ?: Blue
    }
}
