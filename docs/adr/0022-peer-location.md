# ADR 0022: 1-hop peer location on the map, and a standardized marker system

## Status
Accepted (2026-09-20)

## Context

Explicit, demo-driven request, stated plainly by the user and recorded
here for that reason rather than framed as organic feature growth: "we
were thinking it would make it look good in the demo when our phones
both will show up on the map." Unlike `docs/adr/0021`'s SOS location
(a one-time, user-invoked emergency action), this is about *routine*
peer presence — a real, different privacy category worth being explicit
about, not just a technical extension of the same idea.

## Decision

- **`AnnouncementPacket`** (the presence beacon every phone already
  broadcasts every 4-30s, see that file's own doc) gains the same
  optional lat/lon TLV pattern as `SosPacket`.
- **`ChatViewModel.sendAnnounce()`** attaches only a **cached** fix —
  a new `LocationProvider.cachedFixOrNull()` method that reads
  `getLastKnownLocation` synchronously with no GPS/network request at
  all. This is the load-bearing decision: an announce fires every
  4-30s, far too often to block on a live fix the way a one-time SOS
  or map download reasonably can. A peer only shows a location to
  others once their own phone already has a recent fix cached from
  doing something else real (opening the Map tab, sending an SOS) —
  this is never continuous GPS polling, and a phone that's never used
  either of those features simply never shows a location to anyone.
- **`PeerEntity`** gains matching nullable `latitude`/`longitude`
  columns (`AppDatabase` migration 5→6).
- **Only 1-hop peers are shown on the map**, enforced in
  `MapViewModel.peerMarkers` (`peer.lastKnownHopCount > 1` is excluded).
  Deliberate: PRODUCT.md's own Product Principle 4 treats hop count as
  a real physical fact, not decoration, and a relayed peer's *last
  known* position could be stale by an unbounded, unknowable amount by
  the time a multi-hop announce reaches you — a 1-hop peer's own fresh
  announce doesn't have that problem.

## Marker system redesign, same pass

Every marker on the Map screen (the download-center "Your location"
pin, 1-hop peers, SOS reports) was standardized onto one hand-drawn
teardrop pin shape (`MapScreen.kt`'s `pinDrawable`), replacing both
`osmdroid`'s generic default marker (a "hand cursor" glyph that doesn't
distinguish what's being pointed at) and an earlier plain-circle marker
built for `docs/adr/0021`. Rendered into a real `Bitmap` via `Canvas`,
not a tinted copy of a system/library drawable or an unsized
`GradientDrawable`/`ShapeDrawable` — a `BitmapDrawable`'s intrinsic size
always matches the bitmap exactly, so there's no risk of `osmdroid`
rendering an invisible, zero-size marker.

Color is the whole visual vocabulary: blue = your downloaded area's
center, green = a 1-hop peer, red = an SOS report, amber-and-larger =
the specific SOS report a user tapped "View on map" for. Each pin's
name is baked directly into the same bitmap, drawn above the pin head
on a small dark pill background — not left to `osmdroid`'s
tap-to-open `Marker.title` info bubble, which the user explicitly
flagged: a name that only appears after tapping every pin individually
defeats the point of a "who's around me" map.

## Real bug found by testing

The camera only ever fit the downloaded area's own fixed 2km bounding
box (`GeoMath.boundingBoxForRadius` around the download center). A peer
or SOS marker placed genuinely outside that box was added to
`view.overlays` correctly — real data, real marker, real position — but
sat off-screen with nothing visibly wrong on screen, which looked
identical to "peers aren't showing" even though they were. Fixed by
computing a combined bounding box across every marker actually present
(download center + every peer + every SOS) and fitting the camera to
that instead, falling back to the fixed 2km box only when nothing else
exists to frame against.

## How this was tested without a second phone

`debug/SosSimulator.kt` (`BuildConfig.DEBUG`-gated, compiled out of any
release build) gained a second action, `SIMULATE_INCOMING_PEER`, which
builds a real `AnnouncementPacket` with a synthetic peer identity and
feeds it into `MessageRouter.handleInboundBytes` — the same real
decode → Cedar gate → Room → Compose pipeline a genuine BLE announce
uses. A third action, `CLEAR_SOS_LOG`, wipes the `sos_alerts` table for
a clean re-test. None of this proves two real phones can exchange these
packets over BLE — that remains this project's single largest untested
surface (see `handoff.md` §5) — it only proves the receive-side
rendering is correct once a packet does arrive.

One real, incidental data point *for* that untested surface: a second
physical phone came into range organically during this session's
testing and reached genuine 1-hop `READY` handshake status in the live
peer list. Observed, not a deliberately re-verified two-way chat/SOS
exchange, so still short of a real field-test claim — but a real signal
the mesh transport itself works between two actual devices, not just in
theory.

## Known gaps, stated plainly

- No live "you are here" dot for peers, any more than for your own
  downloaded area — every pin is a last-known-position snapshot from
  whenever that phone last had a cached fix to attach, not a
  continuously updating position.
- No explicit consent toggle for peer location beyond the fact that a
  phone with no cached fix simply has nothing to share. A future
  "share my location with nearby peers" opt-in, off by default, is the
  honest next step if this ships beyond a demo — see `docs/PRODUCT.md`'s
  own principle about offline/privacy defaults.
- Multi-hop peer location was explicitly considered and rejected for
  this pass (staleness, see Decision above) — not a gap so much as a
  deliberate boundary, but worth stating since it's the most obvious
  "why not just show everyone" question a reader might have.
