# Setup scripts

Written because this session's network was too unstable to finish these
installs live (see `handoff.md`'s update and `docs/adr/0017`) — these
capture the exact commands so a resume doesn't need to re-derive them.
Run in this order, each is independently resumable/idempotent:

1. `setup-corretto.sh` — Amazon Corretto 11 JDK, prints the
   `gradle.properties` line to pin it (see `docs/adr/0006`).
2. `setup-android-sdk.sh` — Android SDK command-line tools, platform 34,
   build-tools 34.0.0, platform-tools, and the NDK; writes `local.properties`.
3. `build-cedar-ffi.sh` — cross-compiles Cedar's native FFI for Android
   ARM ABIs and drops the `.so` files into `app/src/main/jniLibs/` (see
   `docs/adr/0017-cedar-cross-compile.md`). Needs steps 1-2 done first
   (uses the NDK from step 2) plus a working `rustup` with internet access.
4. `setup-strands-agent.sh` — Ollama + the matching model + the Strands
   Agents SDK (see `gateway/README.md`).

After all four, verify with:

```bash
cd "D:\Amazon AWS hack"
./gradlew testDebugUnitTest   # JVM tests, including MessageRouterAuthorizationTest
./gradlew assembleDebug       # full APK build
python -m unittest gateway.tests.test_knowledge -v
python -m gateway.agent.main "how do I treat a snake bite"
```
