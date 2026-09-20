# ADR 0022: QR scan-to-pay, Flowpay-pattern (no redirect to a separate UPI app)

**Status:** Accepted — logic verified by unit test, UI **not** verified on a device
**Date:** 2026-09-20

## Context

The user pointed at Flowpay (`Flowpayup/Payments-Without-Internet`, Apache
2.0, already the named source for this app's offline-payment approach per
`docs/PRD.md` and `docs/adr/0002`) and asked specifically for its "Scan QR"
pattern: the whole flow — reading the code, confirming the payment — stays
inside the app, with no hand-off to a different UPI app (Google Pay,
PhonePe, etc.) the way a typical `upi://` deep link works.

This app's Pay tab already had two buttons ("USSD *99#", "UPI 123Pay") that
both just opened the dialer pre-filled with the bare `*99#` code — no QR
input, no VPA, no amount, a stub in all but name. Camera/QR support had
also just been removed one commit earlier (`08b5e67`) as literal dead code:
a dependency block with no code path using it at all. Re-adding it only
makes sense if this time it's actually wired to something real.

## Decision

**Ported, not reimplemented, from Flowpay where it already solved the
hard part.** `UpiQrParser` (`payments/UpiQrParser.kt`) is adapted from
Flowpay's `QRCodeParser.kt` under the sourcing policy `docs/PRD.md`
already states ("any open-source repo referenced in this document may be
lifted whole or in part... Copy files, port line-by-line"). Same two
accepted shapes (a `upi://` URI or a bare VPA), same rejection reasons,
same discipline of never regex-fishing a payee out of arbitrary scanned
text. One real adaptation: Flowpay's version uses `android.net.Uri` for
query-string parsing; this project's unit tests are plain JVM with no
Robolectric configured, and `Uri.parse()` throws "not mocked" outside an
instrumented test — so the query-parameter split is hand-rolled instead,
matching the same Android-free discipline every other domain class in
this codebase already follows (`KnowledgeDocumentParser`, `SosPacket`).

**`UpiUssdScanToPayBuilder` is new, not ported** — Flowpay's own public
source only exposed `Upi123CallStringBuilder` (the DTMF dial-string for
its *manual-entry* IVR path); its scan-to-pay `*99*1*3#` builder wasn't at
a path this project could read. `*99*1*3*<vpa>*<amount>#` is built here
from NPCI's own published `*99#` menu structure (`1` = Send Money, `3` =
"To VPA") — the same mechanism Flowpay's README describes for its own
"Scan QR" entry point, just a fresh implementation of the public spec
rather than a copy of Flowpay's code. **Honestly caveated**: no live `*99#`
USSD session was available to confirm a real carrier gateway accepts this
exact digit sequence.

**The actual "no redirect" mechanism is `UssdDialer`, unchanged.** It
already existed for the manual `*99#` button and already does exactly what
Flowpay's own README describes as its own boundary ("the app only triggers
the dialer... user's own tap"): `Intent.ACTION_DIAL`, never `ACTION_CALL`,
so a real financial action is never fired by software alone. The QR flow
reuses it as-is — scanning just replaces manual entry as the way the VPA
and amount get into the same dialer hand-off, it doesn't change what
happens after.

**Library choice: `zxing-android-embedded` alone, not CameraX.** The
dependency block removed in `08b5e67` had `zxing-android-embedded` plus
three CameraX artifacts (Flowpay's own scanner is a hand-rolled CameraX
`ImageAnalysis` pipeline feeding zxing-core directly). This integration
instead uses `zxing-android-embedded`'s own bundled `ScanContract` +
`CaptureActivity` — it manages its own camera internally (Camera1/Camera2,
not CameraX), so `PayScreen.kt` only calls
`rememberLauncherForActivityResult(ScanContract())` and never touches a
camera API directly. Smaller surface area to get right with no device
available to test a hand-rolled preview against, and it avoids
reintroducing three dependencies that were dead weight before for the
one that's now actually used.

## What's verified and what isn't

**Verified**, by unit test (`UpiQrParserTest`, `UpiUssdScanToPayBuilderTest`,
21 new tests, all passing) and by a real build:
- `UpiQrParser` correctly accepts/rejects every shape Flowpay's own tests
  cover: a full `upi://` URI, a bare VPA, missing/malformed/oversized
  amounts, a missing or structurally invalid payee address, and — the
  specific regression Flowpay's own parser exists to prevent — a random
  QR containing an `@` character (e.g. a poster with an email address on
  it) is rejected, not silently treated as a payment.
- `UpiUssdScanToPayBuilder` produces the exact expected `*99*1*3*vpa*amt#`
  string for valid input and rejects every invalid case with a specific
  reason.
- The full project — this feature plus everything already on this
  branch — compiles, dexes, and assembles cleanly
  (`./gradlew testDebugUnitTest assembleDebug`, 90/90 tests passing). The
  merged manifest was inspected directly (`aapt2 dump badging`): `CAMERA`
  is declared, nothing unexpected leaked in from the library's own
  manifest.

**Not verified** — there was no connected device for the remainder of this
session:
- The actual scan screen has never been opened on a phone. `ScanContract`/
  `ScanOptions`' exact API surface in `zxing-android-embedded:4.3.0` was
  used from memory of the library's documented usage, not confirmed
  against a real compile-and-run of the scanning Activity itself (the code
  compiles and dexes, which rules out a signature-level mistake, but not a
  runtime one).
- The camera permission request flow (`ActivityResultContracts.RequestPermission`)
  has never been exercised live.
- The `*99*1*3*<vpa>*<amount>#` USSD string has never been dialed against
  a live carrier.

## Consequences

- Do not describe this feature as "working" in any handoff document
  without first scanning a real UPI QR code on a device and confirming
  the confirmation dialog shows the right payee/amount, and ideally
  dialing the resulting USSD code to see how a real carrier responds to
  the digit sequence (ADR text above already flags that the sequence
  itself is unverified).
- If `*99*1*3*<vpa>*<amount>#` turns out to be wrong against a live
  network, only `UpiUssdScanToPayBuilder.build()`'s return string needs to
  change — `UssdDialer`, `UpiQrParser`, and the confirmation UI are all
  independent of the exact digit format.
- `RECEIVE_SMS` was deliberately NOT re-added: Flowpay's real transaction
  confirmation relies on parsing the bank's SMS receipt
  (`SmsTransactionParser.kt`), which this pass does not build. This
  feature ends at "the dialer opens with the right request ready" — it
  does not know or record whether a payment actually succeeded, matching
  this app's existing IOU/USSD buttons' own current honesty level (see
  `PayViewModel`'s doc comment), not a regression introduced by this pass.
