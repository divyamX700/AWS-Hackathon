# ADR 0020: Deterministic image attachment for knowledge-base answers

**Status:** Accepted
**Date:** 2026-09-20

## Context

The Assistant's retrieval-augmented answers (see `docs/adr/0016-on-device-agent-architecture.md`)
are grounded entirely in `assets/kb/docs/*.txt` — until this pass, short,
lightly-sourced text files. The user asked for two things together: (1)
substantially deeper, real, citable content (WHO, ICRC/IFRC, NDMA India, CDC)
instead of the existing brief summaries, and (2) images attached to specific
answers — "if I ask how do I put on a bandage... the image is also fetched
with that concurrently... a deterministic system," explicit that the LLM
must play no role in choosing or describing an image, only in the surrounding
text.

## Decision

**Images are joined by exact (document, section) match, never by the model.**
`assets/kb/images/manifest.json` is a flat array of
`{doc, section, image, caption, attribution}` entries. `KnowledgeBaseLoader`
(the one Android-specific file in this feature — see its own doc comment)
loads this manifest once, then for every parsed `KnowledgeChunk` looks up
`(fileNameWithoutExtension, chunk.section)` against it. A match sets
`KnowledgeChunk.image`; no match leaves it null. `KnowledgeDocumentParser`
itself never touches images — it stays pure Kotlin, no Android dependency,
same as before this pass — which is what keeps the manifest join a single,
auditable step rather than something threaded through parsing.

From there, the image just rides along with the chunk through the existing
pipeline with zero new logic: `AssistantEngine.answer()` copies
`chunk.image` onto the `AssistantSource` it already builds for every
retrieved match, and the UI (`AssistantScreen.kt`'s `TurnCard` and
`DocsBrowser`) renders whichever source (if any) carries an image. The LLM
prompt itself (`buildPrompt`/`buildGuidePrompt`) is completely unchanged —
it never sees the image, never names it, never decides whether to include
it. Asking a question that happens to retrieve an imageless section simply
shows no image, which is the correct behavior, not a bug to work around.

**Images are decoded straight from `assets/`, no new dependency.** All five
images shipped this pass are JPEG/PNG, loaded via
`AssetManager.open()` + `BitmapFactory.decodeStream()` inside a small
`KnowledgeImageCard` composable, `remember`'d per asset path. This was a
deliberate choice over adding an image-loading library (Coil, Glide): this
project's Gradle/D8 toolchain has already hit two real upstream bugs this
build (Cedar's MethodParameters/D8 crash, Guava's Gradle Module Metadata
variant selection — see `docs/adr/0017`) and a third dependency addition
this close to submission was judged not worth the risk for something plain
`BitmapFactory` already does with zero extra jars.

**One candidate image was dropped for the same reason.** A `Recovery
position` diagram was found on Wikimedia Commons only as an SVG
(`Recovery_position.svg`, CC BY-SA 3.0 FR). Android's Compose `Image` cannot
decode SVG without a library (Coil's `coil-svg` module, specifically), and
no local SVG rasterizer was available to convert it to a PNG once instead.
Rather than add a dependency or hand-translate ~114 lines of nested-group
SVG path data into Android's separate VectorDrawable XML dialect (a real
risk of a subtly wrong rendering that's hard to catch without a device to
screenshot against every path), the image was dropped from this pass's
scope. There is no "Recovery Position" image in `manifest.json` — the
`## Recovery Position` text section (if added to `cpr_and_choking.txt` this
pass) stands alone. Revisit if `coil-svg` is ever added for other reasons.

## Real image sourcing (not placeholder art)

Five images, all fetched from Wikimedia Commons, verified public-domain or
Creative-Commons-licensed via the Commons API's `extmetadata` before
downloading, downscaled to a max 900px edge and re-compressed (JPEG quality
78) to keep the APK lean — originals ranged up to 4288×2848 / 1.5MB, unusable
bloat for a phone-shipped offline app:

| Section | Image | License / attribution |
|---|---|---|
| `bleeding_and_wounds` — When to Use a Tourniquet | `tourniquet_application.jpg` | Public domain, U.S. DoD photo by Fred W. Baker III |
| `cpr_and_choking` — Performing Chest Compressions | `cpr_chest_compressions.jpg` | CC BY-SA 2.0 FR, photo by Rama |
| `cpr_and_choking` — Choking in Adults and Children Over One Year | `heimlich_adult.jpg` | Public domain, U.S. Army Medical Dept. Center and School |
| `cpr_and_choking` — Choking in Infants Under One Year | `heimlich_infant.png` | CC BY-SA 4.0, illustration by BruceBlaus |
| `fractures_sprains_spinal` — Immobilizing a Fracture | `finger_splint.jpg` | CC BY-SA 4.0, photo by Salil Kumar Mukherjee |

Attribution text is stored per-entry in `manifest.json` and rendered under
every shown image — this is the "footnote" the user asked for on the image
side, mirroring the per-section citation the user asked for on the expanded
text content (see the knowledge-base files themselves for those).

## Consequences

- Five sections now show a real photo automatically; every other section
  (all newly-expanded content included) has none — an honest scope
  boundary, not every topic that would benefit from a photo has one yet
  (e.g. no burn-cooling, wound-bandaging-technique, or recovery-position
  image this pass).
- Extending this later is a two-step, code-free process: add an image file
  under `assets/kb/images/`, add one entry to `manifest.json` naming an
  exact existing `##` section heading. No Kotlin change needed.
- `KnowledgeBaseLoader` remains the only untested file in this feature
  (same as before this pass — it needs an Android `Context`, and this
  project has no Robolectric set up for JVM-side Android-asset tests).
  `KnowledgeDocumentParser` and `KnowledgeRetriever` keep their existing
  unit test coverage unchanged; the `image` field is optional
  (`= null` default) on both `KnowledgeChunk` and `AssistantSource`, so no
  existing test needed updating.
- Not built this pass: mirroring image-manifest awareness in the Python
  Strands reference agent (`gateway/agent/`) — that agent is a disconnected
  reference implementation with no UI to show an image in at all (see
  `gateway/README.md`), so there is nothing there for this feature to attach
  to.
