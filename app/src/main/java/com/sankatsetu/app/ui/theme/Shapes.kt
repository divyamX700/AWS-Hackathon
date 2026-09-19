package com.sankatsetu.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * A graduated corner-radius scale, tighter than Material's stock defaults —
 * "precision instrument" density (per docs/adr/0018) rather than the
 * softer/rounder consumer-app look, reserving the largest radius for the
 * one primary CTA and full-screen sheets so it reads as deliberately
 * emphasized, not uniform. True continuous "squircle" corners (Apple's
 * actual corner curve) need a custom superellipse path or
 * `androidx.graphics:graphics-shapes`; skipped for this pass — a large,
 * consistent `RoundedCornerShape` reads as premium enough on its own and
 * carries far less risk this close to a submission deadline than a new
 * shape-morphing dependency.
 */
val SankatSetuShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // chips, status pills, small badges
    small = RoundedCornerShape(12.dp),       // buttons, compact controls
    medium = RoundedCornerShape(16.dp),      // cards, list rows, message bubbles
    large = RoundedCornerShape(22.dp),       // the primary CTA, highlighted cards
    extraLarge = RoundedCornerShape(28.dp)   // sheets, dialogs, full-bleed panels
)

/** Fully rounded — status pills, avatar-style glyphs. Not part of [Shapes] since Material doesn't have a "pill" slot. */
val PillShape = RoundedCornerShape(percent = 50)
