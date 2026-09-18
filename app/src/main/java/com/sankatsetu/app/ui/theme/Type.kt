package com.sankatsetu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sankatsetu.app.R

val SankatSetuTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)

/**
 * The "instrument panel" register — peer IDs, hop counts, timestamps,
 * signal readouts. Reserved narrowly for that data/measurement role per
 * craft-floor's ban on monospace as a generic "technical" costume; body
 * text, first-aid answers, and every other prose surface stay in the
 * system sans. See docs/adr/0014-field-radio-design-language.md.
 */
val FieldMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)

val ConsoleReadoutStyle = TextStyle(
    fontFamily = FieldMono,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 0.4.sp,
    lineHeight = 16.sp
)
