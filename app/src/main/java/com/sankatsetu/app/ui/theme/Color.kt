package com.sankatsetu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for every color in the app — no inline Color(0x…)
 * anywhere else, matching Flowpay's own CI-enforced rule (see docs/adr/0002).
 * We don't have their CI gate wired up yet (Day 4 polish item), so this is
 * enforced by convention only for now.
 */
object SankatSetuColors {
    val CrisisRed = Color(0xFFC62828)      // brand / SOS accent
    val SafeGreen = Color(0xFF2E7D32)      // delivered / success states
    val CautionAmber = Color(0xFFF9A825)   // pending / unsettled IOU
    val NeutralInk = Color(0xFF1B1B1F)
    val NeutralSurface = Color(0xFFFFFBFE)
    val NeutralSurfaceDark = Color(0xFF121212)
    val HopBadgeBackground = Color(0xFFE0E0E0)
}
