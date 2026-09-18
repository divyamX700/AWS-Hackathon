package com.sankatsetu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = SankatSetuColors.OperateBlue,
    secondary = SankatSetuColors.SafeGreen,
    tertiary = SankatSetuColors.CautionAmber,
    error = SankatSetuColors.CrisisRed,
    background = SankatSetuColors.NeutralSurface,
    onBackground = SankatSetuColors.NeutralInk
)

private val DarkColors = darkColorScheme(
    primary = SankatSetuColors.OperateBlue,
    secondary = SankatSetuColors.SafeGreen,
    tertiary = SankatSetuColors.CautionAmber,
    error = SankatSetuColors.CrisisRed,
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
