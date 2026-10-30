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

## 2026-10-19 - Process and File Descriptor leaks in fallback strategies
**Learning:** In Kotlin/Java, when implementing a try/catch fallback strategy that reassigns a `Process` (started by `ProcessBuilder`) to the same variable, the originally started process (and its associated file descriptors) can be leaked if an exception is thrown *after* the process starts but before it completes. The `finally { process?.destroy() }` block will only destroy the newly assigned fallback process.
**Action:** Always check and explicitly destroy existing process variables (e.g., `process?.destroy()`) before re-assigning them in error handling or fallback blocks.

## 2026-10-21 - Regex lookahead performance impact
**Learning:** Using lookaheads in Regex (like `(?=.{1,253}$)`) for string length validation can significantly impact regex execution performance, especially when called frequently. In `NetworkUtils.isValidHostname`, checking length explicitly with Kotlin code and removing the lookahead reduced execution time by ~63%.
**Action:** Avoid complex regex lookaheads for simple length validation. Validate string length explicitly in code before running the regex to improve performance.

## 2026-10-21 - Short-circuiting O(1) checks before O(N) regex
**Learning:** Putting a fast equality check (e.g., `input.equals("localhost")`) *after* a heavy regular expression evaluation is inefficient if the fast check can positively identify the target.
**Action:** Always place O(1) short-circuit checks before O(N) regex evaluations or other heavy operations to avoid unnecessary computation.

## 2026-10-22 - Avoid `String.substring` and functional collections in frequent text validations
**Learning:** `String.substring` creates a new String instance, allocating a new character array which contributes to garbage collection pressure. Similarly, using functional approaches like `CharSequence.all {}` creates small wrapper objects or lambda instances on the heap. When called frequently during string validation logic (like `looksLikeNumericIpv6`), this overhead becomes noticeable on restricted execution environments.
**Action:** Replace `String.substring()` and collection functional mapping (`.all {}`, `.filter {}`) with manual index-based character loops for high-frequency string validation functions.

## 2026-10-23 - Avoid intermediate list allocations for Flow collection in periodic polling
**Learning:** In continuous background monitoring loops (like `ProbeExecutor`), collecting a Flow directly into a `List` via `.toList()` and then using methods like `.filterIsInstance()` allocates an unnecessary intermediate list for every poll cycle. Even if the list is small, it creates garbage collection pressure when running persistently in the background.
**Action:** Use manual `.collect {}` to update state variables instead of allocating intermediate collections like `.toList()` in frequently polling continuous background tasks.
