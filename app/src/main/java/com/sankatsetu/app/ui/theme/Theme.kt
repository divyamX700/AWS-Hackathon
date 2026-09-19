package com.sankatsetu.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Dark is the primary design target (see docs/adr/0018-ui-revamp.md's trend
// research: a near-black instrument-panel canvas with one accent reads as
// premium and suits a crisis tool's "serious, not playful" register better
// than a bright default) — but light stays fully designed, not an
// afterthought, since Apple's own HIG treats both as first-class and a
// judge's phone may be in either mode. Dynamic Color (Material You) stays
// off, unchanged from the prior pass: a demo phone should show this app's
// own palette, not per-device wallpaper theming.
private val DarkColors = darkColorScheme(
    primary = SankatSetuColors.SignalBlueDark,
    onPrimary = Color(0xFF00234D),
    primaryContainer = Color(0xFF1E3A66),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = SankatSetuColors.InkMutedOnDark,
    onSecondary = SankatSetuColors.SurfaceBase,
    secondaryContainer = SankatSetuColors.SurfaceOverlayHigh,
    onSecondaryContainer = SankatSetuColors.InkOnDark,
    tertiary = SankatSetuColors.StatusCaution,
    onTertiary = Color(0xFF3D2900),
    tertiaryContainer = Color(0xFF5C3D00),
    onTertiaryContainer = Color(0xFFFFDDAE),
    error = SankatSetuColors.StatusCritical,
    onError = Color(0xFF3D0002),
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFDAD4),
    background = SankatSetuColors.SurfaceBase,
    onBackground = SankatSetuColors.InkOnDark,
    surface = SankatSetuColors.SurfaceRaised,
    onSurface = SankatSetuColors.InkOnDark,
    surfaceVariant = SankatSetuColors.SurfaceOverlay,
    onSurfaceVariant = SankatSetuColors.InkMutedOnDark,
    surfaceContainer = SankatSetuColors.SurfaceRaised,
    surfaceContainerHigh = SankatSetuColors.SurfaceOverlay,
    surfaceContainerHighest = SankatSetuColors.SurfaceOverlayHigh,
    surfaceContainerLow = SankatSetuColors.SurfaceBase,
    surfaceContainerLowest = SankatSetuColors.SurfaceBase,
    outline = SankatSetuColors.HairlineOnDark,
    outlineVariant = SankatSetuColors.HairlineOnDark
)

private val LightColors = lightColorScheme(
    primary = SankatSetuColors.SignalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF002D6B),
    secondary = SankatSetuColors.InkMutedOnLight,
    onSecondary = Color.White,
    secondaryContainer = SankatSetuColors.SurfaceOverlayHighLight,
    onSecondaryContainer = SankatSetuColors.InkOnLight,
    tertiary = SankatSetuColors.StatusCaution,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4BB),
    onTertiaryContainer = Color(0xFF4A2E00),
    error = SankatSetuColors.StatusCritical,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    background = SankatSetuColors.SurfaceBaseLight,
    onBackground = SankatSetuColors.InkOnLight,
    surface = SankatSetuColors.SurfaceRaisedLight,
    onSurface = SankatSetuColors.InkOnLight,
    surfaceVariant = SankatSetuColors.SurfaceOverlayHighLight,
    onSurfaceVariant = SankatSetuColors.InkMutedOnLight,
    surfaceContainer = SankatSetuColors.SurfaceRaisedLight,
    surfaceContainerHigh = SankatSetuColors.SurfaceOverlayHighLight,
    surfaceContainerHighest = Color(0xFFE7E8EB),
    surfaceContainerLow = SankatSetuColors.SurfaceBaseLight,
    surfaceContainerLowest = Color.White,
    outline = SankatSetuColors.HairlineOnLight,
    outlineVariant = SankatSetuColors.HairlineOnLight
)

@Composable
fun SankatSetuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SankatSetuTypography,
        shapes = SankatSetuShapes,
        content = content
    )
}
