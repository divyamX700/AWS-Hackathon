package com.sankatsetu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for every color in the app — no inline Color(0x…)
 * anywhere else, matching Flowpay's own CI-enforced rule (see docs/adr/0002).
 * We don't have their CI gate wired up yet (Day 4 polish item), so this is
 * enforced by convention only for now.
 */
object SankatSetuColors {
    // CrisisRed is wired as MaterialTheme.colorScheme.error, not primary —
    // see docs/adr/0013-operate-mode-color-and-icons.md. In a crisis app,
    // red has to mean one thing only. Using it as the app-wide primary
    // (the original Day 1 choice) meant a focused text field, the send
    // button, and the selected nav tab all borrowed the same red as an
    // actual SOS action would — real-device review found this reads as a
    // permanent low-grade alarm state rather than urgency reserved for
    // when it's earned.
    val CrisisRed = Color(0xFFC62828)      // emergency-specific accent ONLY — MaterialTheme.colorScheme.error
    val OperateBlue = Color(0xFF3B5FC4)    // calm, trustworthy everyday primary — buttons, focus, selected tab
    val SafeGreen = Color(0xFF2E7D32)      // delivered / success states
    val CautionAmber = Color(0xFFF9A825)   // pending / unsettled IOU
    val NeutralInk = Color(0xFF1B1B1F)
    val NeutralSurface = Color(0xFFFFFBFE)
    val NeutralSurfaceDark = Color(0xFF121212)
    val HopBadgeBackground = Color(0xFFE0E0E0)
    val OfflineGray = Color(0xFF9E9E9E)   // peer link currently down
    val ReadBlue = Color(0xFF34B7F1)      // WhatsApp-style read-receipt tick
}
