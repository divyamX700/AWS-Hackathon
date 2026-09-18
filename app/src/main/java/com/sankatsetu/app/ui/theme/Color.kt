package com.sankatsetu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for every color in the app — no inline Color(0x…)
 * anywhere else, matching Flowpay's own CI-enforced rule (see docs/adr/0002).
 * We don't have their CI gate wired up yet (Day 4 polish item), so this is
 * enforced by convention only for now.
 *
 * The four Imd* colors are India's own four-stage disaster/weather alert
 * scale — Green / Yellow / Orange / Red — published by the India
 * Meteorological Department on every monsoon and cyclone warning, and the
 * language every target user already reads correctly from TV and SMS
 * alerts. See docs/adr/0014-field-radio-design-language.md: this app's
 * status system reuses that scale instead of an invented palette, and
 * keeps each color's real meaning — Red stays reserved for genuine danger,
 * never decoration (docs/adr/0013 already established this; this pass
 * grounds it in a real external standard rather than an internal rule).
 */
object SankatSetuColors {
    val ImdGreen = Color(0xFF1E8E3E)   // "no warning" — ready, safe, delivered
    val ImdYellow = Color(0xFFF2B705)  // "be aware" — connecting, handshake pending, watch
    val ImdOrange = Color(0xFFE8710A)  // "be prepared" — pending/unsettled, caution
    val ImdRed = Color(0xFFC62828)     // "take action" — genuine danger only: MaterialTheme.colorScheme.error

    // Instrument-panel neutral for the "console" register (peer IDs, hop
    // counts, signal readouts) — a dim slate, not a bright accent, so the
    // Imd* colors keep sole ownership of "something needs attention."
    val ConsoleSlate = Color(0xFF3A4552)
    val ConsoleSlateDark = Color(0xFFB8C2CC)

    val NeutralInk = Color(0xFF1B1B1F)
    val NeutralSurface = Color(0xFFFFFBFE)
    val NeutralSurfaceDark = Color(0xFF121212)
    val OfflineGray = Color(0xFF9E9E9E)   // peer link currently down — neutral, not alarming; see product principle in PRODUCT.md
    val ReadBlue = Color(0xFF34B7F1)      // WhatsApp-style read-receipt tick — a convention users already read correctly, kept as-is
    val HopBadgeBackground = Color(0xFFE0E0E0) // neutral chip background, not a status color
}
