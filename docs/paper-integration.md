# Paper integration

`leafconfig-paper` is a thin layer over `leafconfig-yaml`.

```java
ConfigManager manager = LeafConfig.forPlugin(this).build();
```

- Base directory is `plugin.getDataFolder()`.
- `Component`, `Material`, `Particle`, `Sound` and `NamespacedKey` adapters are
  registered at module precedence; `.adapter(...)` on the builder overrides
  them.
- Nothing is registered with the server: no commands, listeners, tasks or
  network access. Bukkit's `FileConfiguration` and `saveDefaultConfig()` are not
  used.
- Diagnostics stay structured. `ConfigLoadException.getMessage()` and
  `ConfigDiagnostics.render(...)` produce log-ready text for the plugin logger.
- Call `manager.close()` in `onDisable()` so listeners and cached class
  metadata are released with the plugin.

Loading in `onEnable()` is synchronous file I/O on the server thread. This is
acceptable for startup; do not reload large files repeatedly on the main
thread.

Paper adapters tolerate values unknown to the running server (for example a
`Material` added in a newer version) by failing with `INVALID_VALUE` naming the
value rather than throwing.

The `Sound` adapter resolves keys through `Registry.SOUNDS`, which exists only
on a running server. Its unit test therefore covers only key normalization; a
server-backed smoke test is planned and has
not been executed yet.

Built against `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT`. Paper
publishes its API only as snapshots; the version is pinned in
`gradle/libs.versions.toml`.
