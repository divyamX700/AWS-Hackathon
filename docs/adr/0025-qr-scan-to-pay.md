# ADR 0022: QR scan-to-pay, Flowpay-pattern (no redirect to a separate UPI app)

**Status:** Accepted — logic and UI verified live on a device; USSD string corrected once against real carrier feedback
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

**`UpiUssdScanToPayBuilder` is new, not ported**, and went through a real
correction cycle against live feedback (see "A real bug, found and fixed
live" below) — its final, correct format is:

```
*99*1*3*<vpa>*<amount>*<remarks>#
```

`1` = Send Money, `3` = "To VPA", per the public NUUP (National Unified
USSD Platform, `*99#`) specification at
`github.com/librefin-in/nuup-specification` §1.3. `REMARKS` is a required
positional segment (`1` disables/skips it, per the spec's own convention),
not an optional trailing field.

**The actual "no redirect" mechanism is `UssdDialer`, unchanged.** It
already existed for the manual `*99#` button and already does exactly what
Flowpay's own README describes as its own boundary ("the app only triggers
the dialer... user's own tap"): `Intent.ACTION_DIAL`, never `ACTION_CALL`,
so a real financial action is never fired by software alone. The QR flow
reuses it as-is — scanning just replaces manual entry as the way the VPA
and amount get into the same dialer hand-off. The UPI PIN and the final
confirm digit are never part of the dial string either — the NUUP spec
itself shows them as separate USSD reply prompts the carrier's session
displays AFTER the initial string connects (`-> UPIPIN -> 2`), typed by
the person directly into the system's own USSD reply UI. This app cannot
see or intercept them even if it wanted to — they never pass through the
dial `Intent` at all.

**Library choice: `zxing-android-embedded` alone, not CameraX.** The
dependency block removed in `08b5e67` had `zxing-android-embedded` plus
three CameraX artifacts (Flowpay's own scanner is a hand-rolled CameraX
`ImageAnalysis` pipeline feeding zxing-core directly). This integration
instead uses `zxing-android-embedded`'s own bundled `ScanContract` +
`CaptureActivity` — it manages its own camera internally (Camera1/Camera2,
not CameraX), so `PayScreen.kt` only calls
`rememberLauncherForActivityResult(ScanContract())` and never touches a
camera API directly.

## A real bug, found and fixed live

The first build shipped `*99*1*3*<vpa>*<amount>#` — no `REMARKS` segment.
Verified on a real device against a real carrier: the camera permission
prompt, the scanner, and the parser all worked correctly against a real,
unplanned UPI QR code in the room (correct payee name and VPA shown in the
confirmation dialog) — but dialing the resulting string produced a
confusing **"not a valid UPI ID"** error from the carrier, even though the
VPA itself was scanned correctly and displayed correctly on-screen right
before dialing.

Re-checked directly against the public NUUP specification
(`github.com/librefin-in/nuup-specification` §1.3, not assumed or
guessed): the real format needs a `REMARKS` segment between `AMOUNT` and
the terminating `#` — "Dial `*99*1*3*VPA*AMOUNT*REMARKS` -> `UPIPIN` ->
`2` to make transaction and exit." A fixed-position USSD parser reading a
4-segment string where a 5-segment one was expected very plausibly reads
the wrong field as the payee ID, which lines up exactly with a "not a
valid UPI ID" response despite the actual VPA being correct. Fixed by
adding the `REMARKS` segment (set to `1`, the spec's own "disabled" value)
so the string is now `*99*1*3*<vpa>*<amount>*1#`. `UpiUssdScanToPayBuilderTest`
was updated to assert the corrected format and documents why in its own
comment. **Not yet re-verified against the same live carrier** — the
corrected build was pushed to the device but a second live dial attempt
had not completed as of this ADR's last edit.

## What's verified and what isn't

**Verified live, on a real device, this session:**
- Camera permission prompt fires correctly (`ActivityResultContracts.RequestPermission`).
- The scanner launches and successfully decodes a real UPI QR code.
- `UpiQrParser` correctly extracts payee name and VPA from a real scan
  (not just synthetic test strings).
- The confirmation dialog renders correctly with the parsed payee info.
- Cancelling out of the confirmation dialog is safe — confirmed via
  logcat that no `ACTION_DIAL` intent fires unless "Pay via *99#" is
  actually tapped.
- The first (incorrect) USSD string really did fail against a live
  carrier with a real, human-confirmed error message — this is not a
  hypothetical caveat anymore, it happened.

**Verified by unit test**, 21 tests covering `UpiQrParser` and
`UpiUssdScanToPayBuilder`, all passing, including the corrected string
format.

**Not yet verified:**
- Whether the corrected `*99*1*3*<vpa>*<amount>*1#` string succeeds
  against the same live carrier — re-test this first, before anything
  else, next time a device is available.
- What the carrier does with the `REMARKS=1` "disabled" convention
  specifically (the spec states the convention; this project has not
  independently confirmed a live gateway honors it identically).

## Consequences

- Do not describe the USSD string as "working" until the corrected
  version has actually been dialed successfully against a live carrier.
- If it still fails, the next thing to check is whether this specific
  carrier's `*99#` gateway has deprecated the direct-dial shortcut
  entirely in favor of the fully interactive menu (dial `*99#` alone,
  then reply to each prompt one at a time) — some circles have done this;
  the NUUP spec itself notes menu codes "can be updated periodically."
- `RECEIVE_SMS` was deliberately NOT re-added: Flowpay's real transaction
  confirmation relies on parsing the bank's SMS receipt
  (`SmsTransactionParser.kt`), which this pass does not build. This
  feature ends at "the dialer opens with the right request ready" — it
  does not know or record whether a payment actually succeeded, matching
  this app's existing IOU/USSD buttons' own current honesty level (see
  `PayViewModel`'s doc comment), not a regression introduced by this pass.
