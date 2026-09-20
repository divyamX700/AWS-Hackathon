# ADR 0020: Offline maps — a real 4th section, not an add-on to the three pillars

## Status
Accepted (2026-09-20)

## Context

The user's own framing: three offline pillars (mesh chat, payments, the
on-device assistant) were explicitly the product's core, with everything
else deliberately kept secondary (see `PRODUCT.md`'s Product Principles,
and the mesh IOU's own "never given equal billing" treatment in
`docs/adr/0019`). This session, the user made a real, deliberate product
decision to add a **4th, independent section**: a downloaded-in-advance
offline map covering roughly a 2km radius around the person, framed
explicitly as preventive — done before a trip to a high-risk or remote
area, not something reached for during a crisis (there's no internet
during a crisis to download anything with).

## Decision

Build it as a real, separate `Map` tab (`ui/map/MapScreen.kt`,
`MapViewModel.kt`), backed by:

- **`osmdroid`** (`org.osmdroid:osmdroid-android:6.1.20`) for the actual
  map rendering and offline tile cache, not `MapLibre` GL Native or a
  similar modern vector-tile SDK. Deliberate: this project has already
  paid real toolchain cost twice for native (`.so`-shipping) dependencies
  (Cedar's cross-compile saga, `docs/adr/0017`; MediaPipe's dexing
  constraints, `docs/adr/0006`/`0011`). `osmdroid` is pure Kotlin/Java,
  confirmed to dex cleanly on this project's pinned JDK11/AGP7.4.2
  toolchain by adding the dependency and building before writing a single
  line of feature code — the same "verify the real toolchain risk first"
  discipline `docs/adr/0011` used for the LLM runtime.
  **Known trade-off, accepted knowingly**: `osmdroid` itself is archived
  upstream (frozen at 6.1.20, August 2024, no further releases) — still
  fully functional, but a real fact for whoever picks this up next.
  **Mapsforge** (actively maintained, also pure Java, genuinely
  vector/offline-first by design) is the honest upgrade path if there's
  ever time; it wasn't the first choice only because it needs its own
  `.map` extract-generation tooling, a real extra step `osmdroid`'s
  simpler raster tile cache doesn't require.
- **A real GPS fix, once** (`maps/LocationProvider.kt`) — plain
  `android.location.LocationManager`, not Play Services'
  `FusedLocationProviderClient`, matching this project's existing
  "hand-rolled, no framework we don't need" stance (`AppContainer`'s own
  no-Hilt doc). This is a genuine first for the app: `ACCESS_FINE_LOCATION`
  was already declared in the manifest, but only ever as a side effect of
  pre-Android-12 BLE scan requirements — this is the first time the app
  actually reads a real location fix. Worth its own privacy-review line
  item, not just a permission checkbox.
- **A 2km-radius bounding box** (`maps/GeoMath.kt`, unit-tested) —
  circumscribing rectangle, not a true circle, since every tile-based
  offline map source (including `osmdroid`'s own `CacheManager`)
  downloads rectangular tile grids. The math accounts for real longitude
  compression at latitude (a degree of longitude covers less ground the
  further from the equator — verified with a dedicated test comparing
  Delhi's latitude against the equator, not just "seems right").
- **zoom 12–17** as the downloaded range — z12 for enough context to
  reorient after panning, z17 for street-level, without the far denser
  z18+ tile count a 2km radius doesn't need.

## Real bugs found by actually testing this on-device, not by inspection

1. **`TileSourceFactory.MAPNIK` (the public OSM tile server) throws
   `TileSourcePolicyException` on any bulk download attempt.** Not a bug
   in this app's code — `osmdroid` itself flags that source
   `FLAG_NO_BULK`, mirroring the real, stated usage policy of the public
   OSM tile server (which explicitly discourages bulk/app-embedded
   fetching without prior arrangement). Routing around this by
   constructing a custom tile source pointed at the same restricted
   server with a permissive policy was considered and explicitly
   rejected — that would either violate the real provider's terms or
   simply fail server-side, and either way is the wrong fix. The actual
   fix: a different tile provider whose terms genuinely permit this.
   **MapTiler** (free tier, no card, ~100k loads/month) was chosen after
   verifying current signup terms directly rather than trusting stale
   knowledge. The API key lives in `local.properties` (gitignored, same
   file the Android SDK path already lives in), read into
   `BuildConfig.MAPTILER_API_KEY` at build time — never committed. See
   `maps/MapTileSource.kt` for the resulting `XYTileSource` +
   `TileSourcePolicy` (explicitly *not* setting `FLAG_NO_BULK`, since
   MapTiler's terms allow it).
2. **Reusing the visible, on-screen `MapView` to construct
   `CacheManager` visibly drags that MapView's own camera across zoom
   levels while the bulk download runs internally**, even though the
   actual downloaded area (independently verified by logging the real
   computed `BoundingBox` — confirmed a correct, honest ~4km span
   centered on the real GPS fix the whole time) was never wrong. This
   looked, to a person watching the screen, exactly like a runaway
   download expanding to cover hundreds of kilometers — a real, valid
   thing to be alarmed by, reported directly during testing. Fixed by
   constructing `CacheManager` from the `MapTileProviderBase`/`ITileSource`
   + `SqlTileWriter()` constructor instead of the `MapView`-based one —
   this variant has no `MapView` at all, so it structurally cannot touch
   anyone's camera. The visible map now stays calmly centered on the
   person throughout the whole download.
3. **The download progress percentage legitimately exceeds 100%**
   (observed climbing past 290% before completing) — `osmdroid`'s own
   upfront tile-count estimate (the percentage's denominator) can run low
   against the real count at the finest zoom level. The download itself
   isn't stuck; the estimate was just optimistic. Fixed by clamping the
   *displayed* value (`MapScreen.kt`), not chasing the estimate itself.

## Verified on real hardware, end to end

Cold cache, real GPS fix (Maligaon, Guwahati, Assam — not a simulated
location), real MapTiler tiles, full 2km-radius download across zoom
12–17 completing in under two minutes over a real network connection,
with the on-screen camera staying stable throughout after fix #2 above.
Then, with Wi-Fi and mobile data both disabled (genuine offline, not
simulated): the downloaded area still renders and pans correctly with
zero network; panning far outside the 2km radius correctly shows
`osmdroid`'s blank tile placeholder rather than any cached content —
proof the download is honestly scoped to the requested radius, not
secretly caching more.

## Known gaps, stated plainly

- **No SOS/location integration yet.** The user's own next-step
  intent (stated before this feature was built) is to connect this to
  the SOS broadcast — e.g., attaching a real position instead of just
  hop count. Not built this pass; a real design conversation of its own.
- **Only one area can be downloaded and stored at a time** (`MapAreaStore`
  keeps a single record, overwritten on re-download). A tourist visiting
  multiple high-risk areas on one trip would need to explicitly
  re-download at each stop — not a multi-area cache.
- **No re-download staleness check.** OSM data changes over time;
  nothing here prompts a refresh of an old download.
- **GPS-only location, no network-based fallback.** Deliberately avoids
  Play Services, but that means a genuinely poor indoor GPS environment
  (confirmed during testing — an initial attempt indoors timed out after
  30 seconds with no fix) has no faster fallback path. The honest error
  message says so and offers retry; it doesn't pretend to work around it.
- **A real process mistake happened while testing this feature**: `adb
  pm clear` was used mid-session to force a clean cold-cache test,
  wiping the test phone's real chat/peer/SOS data exactly like the
  earlier `adb uninstall` incident this same session already flagged as
  something to never repeat. Recorded here plainly rather than left only
  in chat history — see `handoff.md`'s own session log for the same note
  in its usual place.
