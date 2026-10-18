# Bolt's Journal

## 2026-10-10 - Kotlin-side micro-optimizations are negligible in AmIOnline
**Learning:** Almost all the wall-clock time in a ping run is spent spawning the `ping`/`ping6` process and waiting on network round-trips (seconds). The Kotlin work around it (address classification, regex checks, a few `TextView.append` calls) runs in microseconds, so changes like hoisting a `Regex` are real but imperceptible to users.
**Action:** Before optimizing here, measure where time actually goes. Only target the in-app code if it sits on a hot path, such as a per-line console update during a long-running ping. Expect small, safe wins only. The Gradle build also needs Maven Central, so it may not run in restricted sandboxes; plan verification around that.

## 2026-10-10 - UI Thread Performance Regressions with TextView.append
**Learning:** Calling `append()` on a `TextView` multiple times instead of concatenating strings first is a performance regression in Android. Each `append()` modifies the internal `Editable`, triggers `TextWatcher`s, recalculates layout, and invalidates the View.
**Action:** Always combine text strings before appending to a `TextView` on Android, rather than splitting them into multiple append calls to save garbage collector time.

## 2026-10-10 - Eliminate Redundant DNS Lookups
**Learning:** `NetworkUtils.getAddressType()` contains a call to `InetAddress.getByName` inside `isIpv6Address()`. We found this was called redundantly during initialization of the ping command string since we already performed the address validation beforehand.
**Action:** Always avoid redundant calls to slow operations like DNS lookups by re-using already calculated context variables.

## 2026-10-14 - MessageQueue.removeCallbacks overhead on UI thread
**Learning:** `View.removeCallbacks(Runnable)` iterates through the main thread's `MessageQueue` linearly (O(N)). Using it for debouncing rapid events (like appending text per-line from a process output) causes significant CPU overhead.
**Action:** Use a simple `boolean` flag to track if an update is scheduled, changing the scheduling complexity to O(1) and avoiding queue manipulation.

## 2026-10-15 - Avoid `InetAddress.getByName()` for IPv6 format validation
**Learning:** `InetAddress.getByName()` was used inside `NetworkUtils.isIpv6Address()` just to check the string format. If the string was a hostname with colons, or just invalid, this triggered a synchronous, blocking DNS lookup, severely impacting UI responsiveness when resolving invalid inputs.
**Action:** Use a pre-compiled Regex for IP address format validation instead of the networking stack. Keep in mind not to use `android.util.Patterns.IP_ADDRESS` because it causes `RuntimeException` in local unit tests.

## 2026-10-16 - String.split and mapping overheads in Kotlin loops
**Learning:** Using `String.split` and functional collection mapping (like `.all {}`) inside frequently called parsing functions (like IP validation) causes significant garbage collection overhead due to repeated object allocations. On slow or memory-constrained environments, this leads to observable pauses during continuous polling.
**Action:** Replace `String.split` and intermediate collections with zero-allocation, manual character iteration loops for high-frequency string validation functions.

## 2026-10-18 - StringBuilder to CharSequence optimization in Android TextView
**Learning:** `TextView.append()` in Android accepts a `CharSequence`. When appending multiple lines from a process output sequentially, converting a `StringBuilder` (which implements `CharSequence`) to a `String` using `.toString()` before passing it to `.append()` causes unnecessary garbage collection and object allocations on the UI thread, causing micro-stutters. Creating new instances of `Runnable` on every event callback instead of hoisting them can also lead to similar memory pressure.
**Action:** Pass `StringBuilder` directly to methods expecting `CharSequence` instead of calling `.toString()`, and hoist instances of `Runnable` to class level properties when used in frequent post updates.
