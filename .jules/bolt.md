# Bolt's Journal

## 2026-10-10 - Kotlin-side micro-optimizations are negligible in AmIOnline
**Learning:** Almost all the wall-clock time in a ping run is spent spawning the `ping`/`ping6` process and waiting on network round-trips (seconds). The Kotlin work around it (address classification, regex checks, a few `TextView.append` calls) runs in microseconds, so changes like hoisting a `Regex` are real but imperceptible to users.
**Action:** Before optimizing here, measure where time actually goes. Only target the in-app code if it sits on a hot path, such as a per-line console update during a long-running ping. Expect small, safe wins only. The Gradle build also needs Maven Central, so it may not run in restricted sandboxes; plan verification around that.

## 2026-10-10 - UI Thread Performance Regressions with TextView.append
**Learning:** Calling `append()` on a `TextView` multiple times instead of concatenating strings first is a performance regression in Android. Each `append()` modifies the internal `Editable`, triggers `TextWatcher`s, recalculates layout, and invalidates the View.
**Action:** Always combine text strings before appending to a `TextView` on Android, rather than splitting them into multiple append calls to save garbage collector time.
