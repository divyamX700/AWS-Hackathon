# ADR 0006: Downgraded to AGP 7.4.2 / Gradle 7.6.4 / Kotlin 1.9.24 (JDK 11) to get a real, verified build

**Status:** Accepted
**Date:** 2026-09-18

## Context

The original toolchain (recorded in the Day 1 commit) was AGP 8.7.2, Gradle
8.10.2, Kotlin 2.1.0, targeting JDK 17, compileSdk/targetSdk 35. None of it
had ever actually been compiled — the authoring session had no Android SDK
or JDK installed at all. This ADR documents what happened when we tried to
get a real, verified build on the actual dev machine.

## What broke, and why

### 1. This machine's JDK 17+ cannot open an NIO `Selector` at all

Reproduced with a 15-line pure-Java program, zero Gradle or Android code
involved:

```java
Selector sel = Selector.open(); // throws on this machine, JDK 17+
```

```
java.io.IOException: Unable to establish loopback connection
  ...
Caused by: java.net.SocketException: Invalid argument: connect
  at sun.nio.ch.UnixDomainSockets.connect0(Native Method)
```

Starting with JDK 17, `java.nio.channels.Pipe` (used internally by
`Selector.open()` for its wakeup mechanism) tries an AF_UNIX domain socket
loopback connection on Windows before falling back to anything else. On
this machine, **binding** an AF_UNIX socket succeeds but **connecting** to
it fails with `EINVAL` — a genuine, reproducible OS/Winsock-level problem,
confirmed:
- Independent of JDK vendor (Microsoft Build of OpenJDK and Eclipse Temurin,
  same JDK update train, same failure).
- Independent of JDK 17 patch version (tested 17.0.10 from Jan 2024 through
  17.0.20 from Aug 2026 — all fail identically).
- Independent of JDK major version *above* 17 (tested JDK 21.0.12 — same failure).
- Independent of the shell (reproduced identically from Git Bash and native
  PowerShell — ruling out an MSYS2/Git-Bash environment quirk).
- **Fixed** by using JDK 11 (`sun.nio.ch.WindowsSelectorProvider`, the
  pre-AF_UNIX-pipe implementation) — confirmed with the same reproduction.

No third-party antivirus/EDR was found on the machine (`Get-CimInstance
AntivirusProduct` reports only Windows Defender); `netsh winsock show
catalog` shows nested-virtualization network adapters, consistent with this
being a virtualized/sandboxed environment where the host's AF_UNIX-over-
loopback plumbing doesn't fully function. **We did not attempt a Winsock
catalog reset or any other system-level network fix** — that would be
modifying system network configuration, which is out of scope for what an
agent should do unilaterally on a user's machine (and isn't guaranteed to
work, or to be safe, in a sandboxed/virtualized environment to begin with).

### 2. Gradle cannot avoid `Selector.open()` even with `--no-daemon`

`gradle`/`gradlew` always forks a build process and communicates with it
over a local socket, using an NIO `Selector` for that communication —
confirmed by testing a trivial `gradle help --no-daemon` in an empty
directory with no `org.gradle.jvmargs` anywhere, which still triggered
"a single-use Daemon process will be forked" and hit the same failure.
`--no-daemon` means "don't persist the daemon after this build," not "don't
use IPC at all." There is no supported way to make Gradle skip this.

### 3. AGP 8.0+ requires JDK 17 to run

This is the actual blocker: JDK 17 is broken here (#1), Gradle can't avoid
needing a working JDK 17 process if AGP requires one (#2), so **any AGP 8.x
project is unbuildable on this machine** via the standard `gradle`/`gradlew`
CLI, regardless of which Gradle or Kotlin version is paired with it.

Mixing JDK versions (JDK 11 as the launcher, `org.gradle.java.home` pointing
the forked build daemon at JDK 17) does not help — the *daemon* process
still needs to run its own `Selector.open()` internally to accept the
client's connection, and dies silently (`"The first result from the daemon
was empty"`), because the daemon itself is the JDK 17+ process that's broken.

### 4. The fix: AGP 7.4.2, the last AGP line that only needs JDK 11

AGP 7.0 through 7.4 require JDK 11 to run (JDK 17 became a hard requirement
starting AGP 8.0). Verified end-to-end on this machine with JDK 11 as both
the Gradle launcher and the (only) build JVM — no forking across JDK
versions needed, everything runs on JDK 11 throughout, and `Selector.open()`
never touches the broken AF_UNIX path.

### 5. Consequences of the downgrade, each fixed in turn

Downgrading AGP/Gradle/Kotlin surfaced three further, unrelated problems —
each a real incompatibility between "very new AndroidX/Compose libraries"
and "AGP 7.4.2's toolchain," not further instances of the Selector bug:

**a. `packaging { }` doesn't exist in AGP 7.4.2.** Renamed from
`packagingOptions { }` in AGP 8.0. Fixed in `app/build.gradle.kts`.

**b. `com.southernstorm:noise-java:0.1.0` doesn't exist on any repository.**
This was flagged as a known risk in `docs/adr/0002` before ever attempting a
build, and the risk materialized exactly as predicted: rweather/noise-java
has no tags, no releases, and its `pom.xml` has a typo'd groupId
(`com.southerstorm`, missing the 'n') that nobody would guess. Fixed by
switching to JitPack (`com.github.rweather:noise-java:master-SNAPSHOT`),
which builds directly from the GitHub repo. The actual Java package
(`com.southernstorm.noise.*`, correctly spelled) matches what
`NoiseSession.kt` already imports, so no code changes were needed.

**c. AGP 7.4.2's bundled D8 crashes (bare `NullPointerException`, not a
version error) dexing class files from `androidx.camera:*:1.4.1` and
`androidx.lifecycle:lifecycle-livedata-core:2.8.7`** — both compiled with
tooling newer than this D8's understanding. Fixed by pinning to versions
contemporaneous with AGP 7.4.2's era: CameraX 1.3.1, Lifecycle/
activity-compose to their early-2024 releases. Compose itself (BOM
2024.06.00, compiler 1.5.14) dexes fine — the issue was isolated to these
two library groups.

**d. `build-tools;35.0.0`'s `aapt2` cannot even parse `platforms;android-35`'s
`android.jar`** (`RES_TABLE_TYPE_TYPE entry offsets overlap actual entry
data`) under AGP 7.4.2 — a genuine aapt2/AGP-era incompatibility, not a
version-support flag. Fixed by dropping `compileSdk`/`targetSdk` to 34 with
matching `build-tools;34.0.0`, well inside AGP 7.4.2's tested range. AGP
still prints an advisory ("tested up to compileSdk 33") but builds
successfully — a one-level gap is a soft warning, not a hard error the way
compileSdk 35 was.

## Decision

Pin the whole toolchain to the versions verified working end-to-end on this
machine:

| Component | Was | Now |
|---|---|---|
| AGP | 8.7.2 | **7.4.2** |
| Gradle | 8.10.2 | **7.6.4** |
| Kotlin | 2.1.0 | **1.9.24** |
| KSP | 2.1.0-1.0.29 | **1.9.24-1.0.20** |
| compileSdk / targetSdk | 35 | **34** |
| buildToolsVersion | (unset) | **34.0.0** |
| Compose compiler | (implicit, Kotlin-2.x plugin) | **1.5.14** (explicit `composeOptions`) |
| Compose BOM | 2024.12.01 | **2024.06.00** |
| CameraX | 1.4.1 | **1.3.1** |
| Lifecycle / activity-compose | 2.8.7 / 1.9.3 | **2.7.0 / 1.8.2** |
| noise-java coordinate | `com.southernstorm:noise-java:0.1.0` (nonexistent) | `com.github.rweather:noise-java:master-SNAPSHOT` (JitPack) |
| `compileOptions`/`kotlinOptions` JVM target | 17 | **11** |
| Build JVM | (JDK 17 assumed) | **JDK 11**, pinned per-machine via `~/.gradle/gradle.properties` → `org.gradle.java.home` (NOT committed to the shared repo — see that file's own comment) |

**Verified, not assumed:**
- `./gradlew assembleDebug` — `BUILD SUCCESSFUL`, produces a real
  `app-debug.apk` (~40 MB).
- `./gradlew testDebugUnitTest` — both unit test classes pass
  (`BinaryProtocolTest`: 6/6, `SeenMessageCacheTest`: 5/5), zero failures,
  zero skipped.
- Zero compiler warnings after cleanup (unused variable/parameter, three
  Kotlin deprecation warnings for pre-API-33 BLE GATT callback signatures —
  suppressed with `@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")` and a
  comment explaining why the deprecated overload is still the one that fires
  on minSdk 29-32 — and the deprecated non-AutoMirrored `Icons.Filled.*`).

## Consequences

- **This machine-specific pin should not be treated as a permanent project
  decision.** Any teammate whose machine doesn't have this AF_UNIX bug
  should feel free to bump back to AGP 8.x / Kotlin 2.x / compileSdk 35 —
  nothing about our actual application code depends on the older toolchain.
  If/when this gets bumped back up, re-verify with a real
  `./gradlew assembleDebug` before committing, the same way this ADR did,
  rather than assuming it'll work.
- The `org.gradle.java.home` JDK 11 pin lives in `~/.gradle/gradle.properties`
  (machine-local, not the repo) specifically so it doesn't silently force
  every teammate onto JDK 11 if their machine doesn't need it.
- CameraX 1.3.1 and Lifecycle 2.7.0 are both still perfectly capable for
  everything Day 1-3 needs (QR scanning, ViewModel/StateFlow) — nothing in
  `docs/PRD.md` requires a CameraX or Lifecycle feature newer than these
  versions ship.
- `docs/adr/0002-vendoring-and-porting-strategy.md`'s "Known risk: noise-java
  Maven coordinate" section called this exact failure mode in advance. It
  was right. Its Maven-coordinate risk section can be considered resolved by
  this ADR's JitPack fix.
