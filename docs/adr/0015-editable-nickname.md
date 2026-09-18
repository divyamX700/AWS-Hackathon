# ADR 0015: Editable nickname, suggested from Bluetooth device name

## Status

Accepted (Day 3)

## Context

The peer list showed `builder-xxxx` nicknames — stable per install
(derived from the identity keypair) but meaningless to a user who wants to
recognize an actual person nearby. Asked directly: can it show real
Bluetooth device names instead.

Two things are true at once here:

1. The mesh protocol already carries a free-text `nickname` field in every
   announce packet (`AnnouncementPacket`) — nothing technical stops a peer
   from broadcasting a real name.
2. A phone's **OS-level Bluetooth device name is very often someone's real
   name** ("Priya's Galaxy S23", "Rahul's OnePlus"). Silently defaulting
   the broadcast nickname to that name would mean every user reveals their
   real name to any nearby stranger the first time they open the app in a
   disaster zone, with no visible choice in the matter — a real privacy
   regression, not a neutral convenience.

## Decision

Default nickname stays the existing anonymous `builder-xxxx` form —
**no behavior change** for anyone who never opens the rename dialog. A new
pencil icon in the Chat tab's top bar opens a rename dialog showing the
current nickname (editable) plus a one-tap suggestion button reading the
phone's actual Bluetooth device name, if the app already holds
`BLUETOOTH_CONNECT` permission. Choosing to use it, or typing any other
name, is an explicit, visible, reversible action, not a silent default.

This directly answers "can I see real names": **yes, if the other person
chooses to set their own nickname to one** — including via this same
one-tap suggestion, which most people will probably take. It does **not**
mean the app can unilaterally read a stranger's Bluetooth name off the
air: this app's BLE GATT advertising never broadcasts the OS device name
at all, only whatever nickname string each installation's `NicknameStore`
holds. A name only ever reaches another phone if that phone chose it.

Persistence: `NicknameStore` (`SharedPreferences`, same convention as
`Identity`'s Noise key storage — see `Identity.kt`'s own doc comment).
Renaming re-announces immediately (`ChatViewModel.renameSelf`) rather than
waiting for the next scheduled announce cycle, so a nearby peer sees the
new name within seconds, not up to 30s later.

## What this pass did not do

No settings screen exists yet for other identity-adjacent choices (panic
wipe, officer key exchange — both PRD-scoped, not built); the rename
affordance lives directly on the Chat tab's top bar rather than a general
Settings surface, since that's the only such preference that exists today.
