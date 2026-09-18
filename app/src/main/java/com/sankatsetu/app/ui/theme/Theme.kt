package com.sankatsetu.app.ui.theme

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

// Primary is the mesh's own "all clear" color — IMD Green — rather than an
// invented brand blue: a working app IS the normal operating state, so its
// everyday chrome earns the color that means exactly that. Yellow lives
// outside the Material role system as a direct SankatSetuColors reference
// (signal bars) because Material has no built-in "watch" role between
// primary and tertiary. See docs/adr/0014-field-radio-design-language.md.
//
// Every *Container/on*Container role is set explicitly below — leaving any
// of them out doesn't inherit our palette, it silently falls back to
// Material's own hardcoded demo-app tones (a violet primaryContainer on
// this baseline), which is exactly the kind of invented, uncommitted color
// this whole pass exists to remove. A real-device screenshot of the "Send
// Mesh IOU" card is what caught this — it rendered stock purple.
private val LightColors = lightColorScheme(
    primary = SankatSetuColors.ImdGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F0D3),
    onPrimaryContainer = Color(0xFF0B3D1D),
    secondary = SankatSetuColors.ConsoleSlate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E0E6),
    onSecondaryContainer = Color(0xFF1A222B),
    tertiary = SankatSetuColors.ImdOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDBD),
    onTertiaryContainer = Color(0xFF5A2B00),
    error = SankatSetuColors.ImdRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = SankatSetuColors.NeutralSurface,
    onBackground = SankatSetuColors.NeutralInk
)

private val DarkColors = darkColorScheme(
    primary = SankatSetuColors.ImdGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF14532A),
    onPrimaryContainer = Color(0xFFB9F2C8),
    secondary = SankatSetuColors.ConsoleSlateDark,
    onSecondary = Color(0xFF1A222B),
    secondaryContainer = Color(0xFF232B33),
    onSecondaryContainer = Color(0xFFC7D0D9),
    tertiary = SankatSetuColors.ImdOrange,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF5C2E00),
    onTertiaryContainer = Color(0xFFFFD9AE),
    error = SankatSetuColors.ImdRed,
    onError = Color.White,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFB4AB),
    background = SankatSetuColors.NeutralSurfaceDark,
    onBackground = SankatSetuColors.NeutralSurface
)

@Composable
fun SankatSetuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // off by default: judges' demo phones should show OUR palette, not per-device Material You
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SankatSetuTypography,
        content = content
    )
}
