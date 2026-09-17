# ADR 0004: A demo-mode flag to disable panic wipe

**Status:** Accepted
**Date:** 2026-09-18

## Context

Bitchat's (and our) panic-wipe UX is a triple-tap on the logo that instantly
clears all identity keys, messages, and local state. This is a deliberate
safety feature for real crisis use. It is also exactly the kind of gesture a
judge fumbling around an unfamiliar demo phone during the hackathon's
video-only judging (First Commit Rule 04: no live demo, judges only see the
submitted video) could trigger by accident while handling the phone —
except judging is video-only, so the actual risk window is our own
rehearsal and recording sessions, not a live judge interaction. Still worth
guarding against during a multi-take video shoot.

## Decision

`app/build.gradle.kts`'s debug build type sets
`DEMO_MODE_DEFAULT = true` via `buildConfigField`. When true, the triple-tap
gesture shows a toast ("Panic wipe disabled in demo mode") instead of
executing `Identity.wipe()` / `AppDatabase.wipe()`. The release build type
does not set this field, so a real release build defaults to the panic wipe
being live — demo mode is a debug-build-only safety net, not a permanent
product decision.

## Consequences

- The demo video and any rehearsal builds should be built in debug mode
  specifically so an accidental triple-tap during recording doesn't wipe the
  identity keys and force re-pairing mid-shoot.
- Before submission, `README.md` must state clearly that panic wipe is
  disabled in the demo APK and how to re-enable it, so judges (who can, per
  the rules, inspect the repo even though they only *score* the video) don't
  mistake this for the feature being fake.
