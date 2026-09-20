# ADR 0021: Real GPS location on an SOS broadcast

## Status
Accepted (2026-09-20)

## Context

The user's own explicit next step after offline maps shipped: "whenever
SOS is sent, then it should send its lat and long along with it. this
should be displayed in the emergency log instead of the 'x hops away'
line." Before this, the SOS broadcast (`docs/TODO.md`'s ideation,
`mesh/emergency/SosManager.kt`) carried only a category and a
timestamp — real position was never available anywhere in the app until
`docs/adr/0020-offline-maps.md`'s `LocationProvider` existed to read it.

## Decision

- **`SosPacket`** gains two optional TLVs (`LATITUDE`/`LONGITUDE`, raw
  IEEE-754 double bits via `Double.toRawBits()`/`Double.fromBits()`,
  the same encoding style every other TLV in this wire format already
  uses) — written together or not at all. Nullable, not required: there
  is no honest case for blocking an emergency broadcast on a GPS fix
  that may never come.
- **`SosManager.broadcastSos`** calls `LocationProvider.getCurrentFix`
  with a **5-second** timeout, not the 30-second default the Map
  feature uses. This is a deliberate, real trade-off: PRODUCT.md's own
  Product Principle 2 ("clarity beats flair under stress") and the
  press-and-hold SOS UX both treat this as a one-handed emergency
  action — sending late because GPS hasn't locked yet would be worse
  than sending without coordinates. A cached fix under 5 minutes old
  (e.g. from having already opened the Map tab) still resolves near
  instantly regardless of this timeout.
- **`SosEntity`** gains matching nullable `latitude`/`longitude` columns
  (`AppDatabase` migration 4→5, a real migration, never destructive).
- **UI** (`ChatScreen.kt`'s `SosLogRow`): when a fix is present, the
  card shows `26.1445°N, 91.7362°E` (4 decimal places, ~11m precision)
  in place of the old "X hops away, relayed" line, with a small map
  icon button next to it. Tapping the button switches to the Map tab
  with that specific report highlighted (see `docs/adr/0022`). When no
  fix was attached, the old hop-count text is still shown — a fix isn't
  guaranteed, and silently showing nothing would be worse.

## Real bugs found by testing, not by inspection

Building this feature is what surfaced two real, pre-existing bugs in
the maps feature it depends on (`LocationProvider`, `MapScreen.kt`) —
recorded in `docs/adr/0020`'s own update, not duplicated here, since
they aren't SOS-specific: the live map streaming tiles over the network
once online, a visible delay before the map rendered right after a
download finished, and a location fix that could take 30+ seconds on
raw GPS alone.

## Verified

Send → sender's own log shows real coordinates → the map button opens
the Map tab centered on that exact point (`docs/adr/0022`). Receive path
verified via `debug/SosSimulator.kt` (BuildConfig.DEBUG-gated, see that
file's own doc) feeding a real, correctly-encoded packet into
`MessageRouter.handleInboundBytes` — the genuine decode → Cedar gate →
Room → Compose pipeline, not a UI mockup, just injected at the BLE
transport boundary instead of over an actual radio. **Not verified**:
an actual over-the-air SOS-with-location delivery between two real
phones — the same project-wide gap every other mesh feature has.

## Known gaps, stated plainly

- No re-verification that Cedar's 5/minute SOS cap still behaves
  correctly with the larger payload (two more TLVs) — the cap is on
  packet count, not size, so this is a low-risk gap, but genuinely
  untested this pass.
- A real, minor UI lag found during testing: an incoming SOS can need
  the app brought back to the foreground before it appears in the log,
  if the phone's screen went idle in between. This is Compose pausing
  recomposition while the Activity is backgrounded, then catching up
  on resume — not a data-loss bug (the row is genuinely in the
  database the whole time), but worth knowing before a demo.
