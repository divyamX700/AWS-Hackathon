# ADR 0013: Operate-mode color system, real icons, and stale-peer cleanup

## Status
Accepted (Day 3)

## Context

A design review of the running app (screenshots off a real phone, not a
simulator) surfaced three concrete problems, plus one raised directly by
the person testing it ("the chat box doesn't appear properly"):

1. **Every interactive element used `CrisisRed` as `MaterialTheme.colorScheme.primary`** —
   the send button, a focused text field's border, the selected bottom-nav
   tab, and outgoing chat bubbles all borrowed the same red an actual SOS
   action would use. On a real device this reads as a permanent low-grade
   alarm state: tapping into an ordinary message box lit up in the same red
   as an emergency, so red stopped meaning anything specific.
2. **Bottom-nav icons were emoji** (💬 💸 🤖), and a delivery-status glyph
   used a clock emoji. [`impeccable`](https://impeccable.style)'s
   `craft-floor.md` names this explicitly as a default to refuse: "Unicode
   glyphs or emoji standing in for an icon system... Icons are drawn, from
   a real library or authored SVG, in one consistent stroke and weight."
   This is also the concrete pattern behind "looks AI-generated" — a real
   icon set is table stakes for a native Android app (its own
   `android.md` platform reference calls out "icon drift" as a
   platform-conformance violation).
3. **The Pay screen repeated three identical full-width cards** (icon/title/
   subtitle) as its page structure — `craft-floor.md`'s "Refuse" list bans
   exactly this shape ("Same-size cards of icon plus heading plus text as
   the page structure... nested cards are always wrong").
4. **The peer list showed duplicate/stale entries** ("builder-9840" twice)
   with no way to clear them — accumulated dead identities from repeated
   reinstalls during development, and genuinely reachable by a real user
   whose neighbor's phone changed identity (a fresh install, a factory
   reset) or who simply doesn't want an old contact's history anymore. This
   is almost certainly what "the chat box doesn't appear properly" was
   describing — a cluttered, duplicate-looking list, not a rendering bug.

## Research basis

This pass used the [`impeccable`](https://impeccable.style) design skill
(installed via `npx impeccable install`, Apache-2.0), specifically its
`craft-floor.md` (mechanical quality floor + banned defaults, read before
any UI edit) and `android.md` (native-platform reference: Material 3
navigation, 48dp touch targets, Material color roles, real icon sets) —
both read in full before making changes, per the skill's own setup
instructions. Its automated web/React detector doesn't apply to a native
Compose app, so this was a manual pass guided by that same written
vocabulary, not the JS-based `impeccable detect` tool.

General crisis/disaster-app conventions (Red Cross emergency apps, FEMA
app, Signal/WhatsApp offline-messaging patterns) also inform two choices
already in this codebase before this pass and reaffirmed here: reserving
an unambiguous "danger" color for genuinely urgent actions only, and
favoring plain, direct UX copy over marketing language throughout.

## Decisions

1. **`primary` is now `OperateBlue` (`#3B5FC4`), a calm, trustworthy tone
   for everyday interactive chrome.** `CrisisRed` moved to
   `MaterialTheme.colorScheme.error` — Material's own role for exactly this
   meaning — and is used only where red already meant something specific
   (the IOU "rejected" status). A future explicit SOS/emergency action
   should use `colorScheme.error` deliberately, not `primary` picked up by
   default.
2. **`androidx.compose.material:material-icons-extended` added**, matched
   by version to the existing `compose-bom`. Bottom-nav icons are now
   `Icons.AutoMirrored.Filled.Chat` / `Filled.Payments` / `Filled.SmartToy`;
   the "sending" message-status glyph is now plain text ("sending…"),
   matching the existing "waiting…" (queued) convention instead of an
   emoji. The ✓/✓✓ delivery ticks stay as-is — WhatsApp's own established,
   universally-recognized plain-text convention, not decorative emoji.
3. **Pay screen's two USSD/IVR shortcuts collapsed into one compact
   side-by-side pair** of outlined buttons instead of two more full-width
   cards; the mesh IOU stays the one clearly-primary full-width card, since
   it's this app's own distinct capability, not a third instance of the
   same shape.
4. **`Scaffold`/`TopAppBar` added to all three tab root screens** (Chat,
   Pay, Assistant) — `android.md`: "Top app bar for screen context."
   Previously they were plain `Column`s with a floating `Text` title, not a
   real Material app bar.
5. **Long-press a peer row to forget it** (`PeerDao.delete`,
   `ChatViewModel.forgetPeer`), with a confirmation dialog that's explicit
   about scope: local-only, and the peer reappears if their phone announces
   again. This is a local-history removal, not a block — the mesh has no
   concept of banning a device.
6. **Generated-vs-extractive answer distinction in the Assistant tab moved
   from caption text alone to a small leading icon** (`AutoAwesome` for a
   real generation, `Description` for a knowledge-base excerpt) —
   `craft-floor.md` bans a colored `border-left`/`border-right` as the
   decorative-habit version of this same signal, so an icon was used
   instead of that pattern.

## What this pass did not do

No dedicated SOS/emergency entry point was added — the PRD doesn't
currently spec one for the app shell, and inventing a new flagship feature
wasn't in scope for a UI-polish pass. A formal `impeccable audit` /
`critique` report (numeric health score, full P0-P3 findings table) was not
produced; fixes here came from direct visual inspection against
`craft-floor.md` and `android.md` instead, given the scope was "review what
exists and fix it," not a from-scratch redesign. `impeccable init`'s full
product interview was skipped for the same reason — a scoped fix to
existing code doesn't require it per the skill's own routing rules.
