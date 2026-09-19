# ADR 0017: Cedar cross-compile — committing to the Rust/NDK path

## Status

Accepted, in progress (native `.so` not yet built — see "Current status")

## Context

`handoff.md` §4a flagged Cedar as genuinely blocked: `cedar-java`'s
published artifacts (`com.cedarpolicy:cedar-java:4.3.1` on Maven Central)
bundle a native JNI library built for desktop JVMs only (Linux/Mac/Windows,
x86_64/aarch64) — nothing for Android's ARM ABIs. Cedarling (the other
candidate) requires a full from-source Rust+UniFFI build with no prebuilt
Android artifact either. The handoff explicitly asked for a real decision
here, not more effort thrown at the same wall.

The user chose: commit to the Rust/NDK cross-compile, on-device, at the
real `MessageRouter.handleInboundBytes()` choke point — not a desktop-side
demo, not dropping Cedar.

## What we verified before writing any code

Direct research against the actual `cedar-policy/cedar-java` repo (cloned,
not just documentation-guessed):

- The Rust FFI crate is `cedar-java-ffi` (`CedarJavaFFI/` in the repo),
  `crate-type = ["cdylib"]`. Its dependency tree is pure Rust except
  `cedar-policy-core`'s transitive dependency on `stacker`/`psm` (small
  asm stack-probe stubs compiled via the `cc` crate) — `cargo-ndk` sets up
  the NDK's clang as `CC_<target>`/`AR_<target>` automatically, so this is
  expected to cross-compile transparently, not a hard blocker.
- No OpenSSL, no protoc/gRPC dependency in the standard (non-`experimental`)
  feature set — confirmed from `cedar-policy`'s own `Cargo.toml`.
- **Zero prior Android attempts exist upstream** — no GitHub issues,
  discussions, or PRs in `cedar-policy/cedar-java` mention Android at all.
  This is genuinely unexplored territory, not a "we tried and gave up"
  situation.
- `com.cedarpolicy:cedar-java:4.3.1` publishes a plain jar (classes only)
  and a separate `-uber.jar` (bundles the 5 supported desktop
  platforms' native libs). We depend on the **plain jar** in
  `app/build.gradle.kts` — the uber jar's desktop `.so`/`.dll`/`.dylib`
  files are dead weight we can't use and don't want bundled into an APK.
- The Java-side loader, `com.cedarpolicy.loader.LibraryLoader`, does *not*
  hardcode `System.loadLibrary(...)`. It checks an environment variable
  first:
  ```java
  private static final String LIBRARY_PATH_VARIABLE_NAME = "CEDAR_JAVA_FFI_LIB";
  ...
  if (libraryPath == null || libraryPath.isEmpty()) {
      JNE.loadLibrary(LIBRARY_NAME);   // com.fizzed:jne — no concept of Android as an OS
  } else {
      System.load(libraryPath);          // exactly what we need
  }
  ```
  This is the real integration seam: we never need to fork or patch
  cedar-java. We just need `CEDAR_JAVA_FFI_LIB` set to the absolute path of
  our own cross-compiled `.so` *before* any `com.cedarpolicy.*` class is
  touched (its static initializer is what triggers the load).

## Decision

1. **Build**: cross-compile `cedar-java-ffi` for `arm64-v8a` and
   `armeabi-v7a` with `cargo-ndk`:
   ```bash
   rustup target add aarch64-linux-android armv7-linux-androideabi
   cargo install cargo-ndk
   cd CedarJavaFFI   # inside a clone of cedar-policy/cedar-java
   cargo ndk -t arm64-v8a -t armeabi-v7a -o <repo>/app/src/main/jniLibs build --release
   ```
   Output: `app/src/main/jniLibs/arm64-v8a/libcedar_java_ffi.so` and the
   `armeabi-v7a` equivalent — standard Android native-lib packaging, no
   custom bundling logic needed; Gradle/AAPT picks these up automatically
   and the OS extracts them into the app's `nativeLibraryDir` at install
   time.
2. **Load**: `CedarAuthorizer.prepareNativeLibraryPath(context)`
   (`mesh/authz/CedarAuthorizer.kt`) calls `android.system.Os.setenv("CEDAR_JAVA_FFI_LIB", "<nativeLibraryDir>/libcedar_java_ffi.so", true)`
   before anything else touches `com.cedarpolicy.*`. Called once, early, in
   `AppContainer`'s construction order (before the `CedarAuthorizer`
   instance itself is built).
3. **Scope the policy to what the app actually has**, not the PRD's
   invented `#chat`/`#sos`/`#official` channel model (this protocol has no
   channels — see `mesh/protocol/MessageType.kt`). The real vocabulary:
   `Peer` (principal, keyed by hex peer ID), `Action::"relay"` (the only
   action), `MessageKind::"public"|"directed"|"sos"|"iou"|"announce"`
   (resource), `context.messagesLastMinute` (a sliding-window count
   `CedarAuthorizer` itself maintains per sender+kind). See
   `assets/cedar/policies.cedar` for the actual policy set and
   `assets/cedar/schema.cedarschema.json` for the schema (currently
   documentation-only — no code path passes it into an
   `AuthorizationRequest` for validation yet).
4. **Fail open, always.** If the native lib isn't present for this device's
   ABI, if `Os.setenv` isn't available, if the policy text fails to parse,
   or if `isAuthorized()` itself throws — every one of these degrades to
   "allow the packet," never "drop everything" or "crash the mesh." Cedar
   here is one flood-control layer on top of the TTL hop budget and
   `SeenMessageCache`'s dedup, which run regardless of Cedar's state — not
   the only thing standing between the mesh and abuse. This matches the
   codebase's existing degrade-honestly pattern (see `AssistantEngine`'s
   extractive fallback).
5. **Testability**: `MessageRouter` depends on a small `MeshAuthorizer`
   functional interface, not `CedarAuthorizer` directly, specifically so a
   JVM unit test (which has no native Cedar library to load at all) can
   inject a fake and prove the router actually calls it and drops a denied
   packet — see `MessageRouterAuthorizationTest`. This is a real,
   already-passing test of the *wiring*, independent of whether the actual
   cross-compiled `.so` exists on a given dev machine yet.

## Current status (honest, updated after a second session)

**The native `.so` is now real and built.** `app/src/main/jniLibs/arm64-v8a/libcedar_java_ffi.so`
and the `armeabi-v7a` equivalent are committed, produced by an actual
`cargo ndk` cross-compile of `cedar-java-ffi` (which pulled in real
`cedar-policy`/`cedar-policy-core` v4.13.0 from the `cedar-policy/cedar`
main branch) run on this project's own dev machine, not reasoned about
from a distance. `./gradlew assembleDebug` succeeds with both `.so` files
packaged into the APK.

**Two more real bugs found and fixed getting here, beyond the original
diagnosis above:**

1. **No working host linker at all.** This dev machine had no Visual
   Studio, so `x86_64-pc-windows-msvc` (rustup's default host toolchain)
   couldn't link anything — `cargo install cargo-ndk` and the Cedar build's
   own `build.rs` scripts (which always compile and run *for the host*,
   even when cross-compiling a different target) both failed with
   `link.exe` errors. Two-part fix: (a) `cargo-ndk` itself was installed as
   a **prebuilt** binary release
   (`bbqsrc/cargo-ndk`'s `x86_64-pc-windows-msvc` zip) instead of compiling
   it, sidestepping the linker entirely for that one tool; (b) for Cedar's
   own build (whose `build.rs` scripts still needed a real host
   toolchain), installed a standalone MinGW-w64 GCC
   (`brechtsanders/winlibs_mingw`, no Visual Studio needed) and switched
   rustup's default host toolchain to `stable-x86_64-pc-windows-gnu`.
   Non-obvious follow-up: **rustup targets are per-toolchain** — switching
   the default host toolchain from msvc to gnu meant re-running
   `rustup target add aarch64-linux-android armv7-linux-androideabi`,
   since the targets added earlier were only registered against the old
   msvc toolchain.
2. **`cedar-java-4.3.1.jar` cannot be dexed as published, on any D8
   available on this machine.** Both AGP 7.4.2's bundled D8 (R8 4.0.52)
   *and* Android SDK 34's own bundled D8 (R8 8.2.2-dev) crash with a
   `NullPointerException` on `com/cedarpolicy/serializer/PolicySetSerializer.class`
   — confirmed by running the SDK's own `d8` binary directly on the
   unmodified jar, independent of Gradle/AGP. Root cause (confirmed via a
   real Google Issue Tracker report with the identical stack trace):
   a genuine upstream R8/D8 bug parsing an empty-name entry in the
   `MethodParameters` class-file attribute — common on compiler-generated
   bridge methods (here, `JsonSerializer<T>`'s generic `serialize` method
   bridging to the concrete `PolicySet` overload) — fixed upstream only in
   R8 8.0.44+/8.1.44+, a fix apparently not present in either D8 build on
   this machine despite the newer one's higher-looking dev version string.
   The transitive `error_prone_annotations:2.36.0` jar (pulled in via
   Guava) independently crashes with the *exact same* bug, confirmed by
   dexing it alone.

   **Fix, not a workaround-by-avoidance**: a small ASM-based tool
   (`StripMethodParameters`, ~50 lines, not committed to the repo — it's a
   one-time patching step, see below) strips the `MethodParameters`
   attribute from every class in `cedar-java-4.3.1.jar` by visiting each
   method and never forwarding `visitParameter` calls to the `ClassWriter`.
   That attribute only carries reflection-visible parameter names
   (`java.lang.reflect.Parameter.getName()`); nothing in this app or
   cedar-java's own runtime behavior depends on it, so removing it is
   behaviorally invisible. Verified: the patched jar dexes cleanly with
   the identical `d8` invocation that crashed on the original.
   `app/libs/cedar-java-4.3.1-methodparams-stripped.jar` (the patched jar,
   committed) replaces the plain Maven dependency in `build.gradle.kts`,
   with cedar-java's real transitive dependencies (Jackson, `com.fizzed:jne`,
   Guava) declared explicitly since a local `files()` dependency doesn't
   trigger POM-based transitive resolution. `error_prone_annotations` is
   excluded project-wide (`configurations.all { exclude(...) }`) rather
   than patched, since it's compile-time-only annotation metadata safe to
   drop entirely.

**Verified end-to-end, including real hardware.** `./gradlew testDebugUnitTest`
(47 tests, 0 failures) and `./gradlew assembleDebug` pass. Once a phone was
connected, a temporary on-device self-test (32 rapid `isAllowed()` calls
against the real `PUBLIC` policy, added to `AppContainer`'s `init` block
just for this verification pass, then removed) produced this real `adb
logcat` output on an actual Android 16 device:

```
CedarSelfTest: isAvailable=true
CedarSelfTest: call #1: allowed=true
...
CedarSelfTest: call #30: allowed=true
CedarSelfTest: call #31: allowed=false
CedarSelfTest: call #32: allowed=false
```

This is the real native `cedar-java-ffi` engine parsing the real
`policies.cedar` text and evaluating a real `AuthorizationRequest` —
not a JVM-test fake — flipping from allow to deny at exactly call #31,
matching the policy's `forbid ... when { context.messagesLastMinute > 30 }`
rule to the call. Cedar is now genuinely *used*, not just built.

**A second real, on-device-only bug found and fixed getting here** (the
first install attempt crashed immediately on launch with
`java.lang.IncompatibleClassChangeError: Class
'com.google.common.base.Suppliers$NonSerializableMemoizingSupplier' does
not implement interface 'java.util.function.Supplier'` inside
`EntityTypeName.toString()`). This one took real investigative work to
pin down, because `./gradlew :app:dependencies` was actively misleading:
it showed Guava correctly and unambiguously resolving to the requested
`33.4.0-jre` coordinate everywhere. The real cause, found only by
inspecting the actual dexed bytecode (`dexdump` on the built APK) and then
`./gradlew :app:dependencyInsight --dependency guava`: Guava's
`33.4.0-jre` coordinate is a single Gradle module that internally
publishes **two Gradle Module Metadata variants** —
`jreRuntimeElements` and `androidRuntimeElements` — and every Android
Gradle Plugin module automatically sets a consumer attribute
(`org.gradle.jvm.environment = "android"`) that silently selects the
android-flavored variant regardless of the `-jre` label in the dependency
coordinate. A version `force`, a `capabilitiesResolution` rule, and a
manual `configurations.all { attributes {...} } ` override were all tried
and all failed (AGP re-applies its own attributes after the build script
runs). The fix that actually works, in `settings.gradle.kts`: a second
`mavenCentral()` repository declaration scoped via `content{}` filtering
to *only* `com.google.guava:guava`, with
`metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }` —
forcing Gradle to resolve that one module from its plain Maven POM
(which has no variant concept at all) instead of its Gradle Module
Metadata. Doing this project-wide instead of scoped to just Guava was
tried first and broke Kotlin Multiplatform's own use of Gradle Module
Metadata to redirect `kotlinx-coroutines-core` to its `-jvm` artifact
(`mergeDebugJavaResource` failed on a genuine duplicate
`META-INF/kotlinx_coroutines_core.version`) — the content-filtered,
single-module version fixes Guava without that collateral damage.

**Reproducing this from scratch on a new machine**: see
`scripts/build-cedar-ffi.sh` for the cross-compile itself; the MinGW/GNU
toolchain switch and the jar-patching step above aren't yet captured in
that script (still MSVC/cargo-install-based) — worth updating if this
needs to run unattended on another machine.
