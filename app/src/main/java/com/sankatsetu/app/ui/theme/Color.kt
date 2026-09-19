package com.sankatsetu.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for every color in the app — no inline Color(0x…)
 * anywhere else (Flowpay's own CI-enforced rule, see docs/adr/0002;
 * enforced by convention only, no CI gate wired up yet).
 *
 * Design direction (2026 UI revamp, see docs/adr/0018-ui-revamp.md):
 * a restrained, mostly-monochrome instrument palette — near-black/near-white
 * surfaces carrying almost all of the UI — with exactly one accent (signal
 * blue, the app's own "trust" color) and three status colors reserved
 * strictly for real state, never decoration. This replaces the prior IMD
 * four-color scheme (docs/adr/0014) as the *primary* palette, but keeps its
 * semantics — real severity, colorblind-legible via icon+text pairing, not
 * color alone — since that research was sound; what changed is execution,
 * not the underlying idea of "borrow India's own trusted alert language."
 * Red stays reserved for genuine danger. Never used decoratively.
 */
object SankatSetuColors {
    // --- The one accent: signal blue. Used for the primary interactive
    // color, focus states, and the mesh's own "in range and ready" glow —
    // never for status (status has its own three colors below), so a
    // person never has to wonder "is blue good or bad here."
    val SignalBlue = Color(0xFF3D8BFF)
    val SignalBlueDark = Color(0xFF6BA6FF) // lighter on dark surfaces for AA contrast

    // --- Status: exactly three meanings, each colorblind-legible via a
    // paired icon/glyph + word, never color alone (see ConsoleReadoutStyle
    // usage sites). Kept conceptually anchored to the IMD scale's severity
    // ladder but re-tuned to sit correctly against the new dark-first
    // neutral stack instead of the old light-first Material baseline.
    val StatusSafe = Color(0xFF30D07C)      // delivered, ready, settled, all-clear
    val StatusCaution = Color(0xFFF5A623)   // connecting, pending, handle-soon
    val StatusCritical = Color(0xFFFF5449)  // genuine danger only — never decorative

    // --- Neutral surface stack (dark-first — see Theme.kt for why dark is
    // the primary design target while light stays fully supported).
    // Named by elevation, not by literal shade, so component code reads as
    // "how far off the base is this" rather than a color guess.
    val SurfaceBase = Color(0xFF0B0C0E)         // window background
    val SurfaceRaised = Color(0xFF141519)       // cards, list rows
    val SurfaceOverlay = Color(0xFF1D1F24)      // sheets, dialogs, the raised nav bar
    val SurfaceOverlayHigh = Color(0xFF26282E)  // popovers, menus — one step above overlay
    val HairlineOnDark = Color(0x1FFFFFFF)      // 12% white — card borders, dividers

    val InkOnDark = Color(0xFFF2F3F5)           // primary text on dark surfaces
    val InkMutedOnDark = Color(0xFFA0A4AC)      // secondary text / captions on dark

    // --- Light theme mirror — same semantics, inverted stack.
    val SurfaceBaseLight = Color(0xFFF7F7F8)
    val SurfaceRaisedLight = Color(0xFFFFFFFF)
    val SurfaceOverlayLight = Color(0xFFFFFFFF)
    val SurfaceOverlayHighLight = Color(0xFFEFF0F2)
    val HairlineOnLight = Color(0x14000000)     // 8% black

    val InkOnLight = Color(0xFF16171A)
    val InkMutedOnLight = Color(0xFF6B6F76)

    // --- Legacy references still used by a few call sites during the
    // revamp — kept narrowly scoped, not part of the new palette's public
    // vocabulary. OfflineGray/ReadBlue are conventions users already read
    // correctly (a dead link isn't alarming; a blue tick means "seen" the
    // way it does in every chat app) so they survive unchanged.
    val OfflineGray = Color(0xFF6B7280)
    val ReadBlue = Color(0xFF3D8BFF)
}
