## 2024-06-25 - Extracted Regex compilation in NetworkUtils
**Learning:** Found a regex compilation inside `isValidHostname` which was called frequently.
**Action:** Lifted `Regex` instance to `NetworkUtils` singleton property to avoid re-compiling it on every invocation. Check for inline Regex declarations in utility functions next time.

## 2026-10-10 - Kotlin-side micro-optimizations are negligible in AmIOnline
**Learning:** Almost all the wall-clock time in a ping run is spent spawning the `ping`/`ping6` process and waiting on network round-trips (seconds). The Kotlin work around it (address classification, regex checks, a few `TextView.append` calls) runs in microseconds, so changes like hoisting a `Regex` are real but imperceptible to users.
**Action:** Before optimizing here, measure where time actually goes. Only target the in-app code if it sits on a hot path, such as a per-line console update during a long-running ping. Expect small, safe wins only. The Gradle build also needs Maven Central, so it may not run in restricted sandboxes; plan verification around that.
