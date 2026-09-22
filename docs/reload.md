# Reload semantics

`ConfigHandle<T>` is the stable reference a plugin keeps. Verified by
`ConfigManagerTest`.

- `get()` returns the latest successfully published snapshot and never blocks.
- `reload()` re-reads the file into a **new** instance. The current instance is
  never mutated.
- The new snapshot is published exactly once, after parsing, decoding,
  validation and any required merge write succeeded. If any step fails the
  previous snapshot stays active and the file is not touched.
- `ReloadResult` carries `successful()`, `current()` (the active snapshot after
  the call) and `diagnostics()`.
- `warnings()` returns the warnings of the load that published the current
  snapshot (for example `VERSION_ASSUMED`); it is empty after a clean load.
- Concurrent `reload()` calls on one handle are serialized with a lock; each
  caller gets the result of its own run.
- Listeners registered with `onReload` run on the reloading thread after
  publication. A throwing listener is reported as a `LISTENER_FAILED` warning in
  the result; it cannot roll back the publication.
- After `ConfigManager.close()`, `get()` still works, `reload()` throws
  `IllegalStateException`, and listeners are dropped.

LeafConfig creates no threads, watchers or schedulers. File I/O happens on the
calling thread; on Paper, call `reload()` off the main thread if the file is
large and hand the result back to the main thread yourself.
