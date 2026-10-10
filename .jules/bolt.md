## 2024-06-25 - Extracted Regex compilation in NetworkUtils
**Learning:** Found a regex compilation inside `isValidHostname` which was called frequently.
**Action:** Lifted `Regex` instance to `NetworkUtils` singleton property to avoid re-compiling it on every invocation. Check for inline Regex declarations in utility functions next time.
