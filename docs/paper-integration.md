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
on a running server. Its unit test covers key normalization; resolution itself
was verified by the server smoke test below (Paper 1.21.11, 2026-09-21).

## Compatibility

| Paper | Compiles against | Unit tests (offline adapters) | Server smoke test |
|---|---|---|---|
| 1.21.11 (`paper-api:1.21.11-R0.1-SNAPSHOT`) | yes | yes | passed 2026-09-21, checklist 7/7 (generation, failed reload keeps snapshot and file, `Sound` via registry, invalid sound, merge of removed key, no-op restart) |

Paper publishes its API only as snapshots; the version is pinned in
`gradle/libs.versions.toml`. Other 1.21.x versions have not been compiled or
tested; the adapters use only `Material.matchMaterial`, `Particle.values()`,
`Registry.SOUNDS`, `NamespacedKey.fromString` and MiniMessage, which exist across
the 1.21 line, but that is an inference, not a verified result.

## Server smoke test

Manual, needs network and JDK 21; downloads the pinned Paper server into
`leafconfig-example/run/` (git-ignored) and starts it with the freshly built
example plugin installed. The EULA is accepted by the task's JVM flag.

```bash
./gradlew :leafconfig-example:runServer
```

Checklist while the console is attached:

1. Startup log contains `Enabling LeafConfigExample v0.1.0` and no stack trace.
2. `run/plugins/LeafConfigExample/config.yml` equals the generated file shown in
   the README.
3. Set `max-players: 500` in that file, run `leafconfigexample reload` in the
   console: expect `max-players [OUT_OF_RANGE]` and "previous configuration
   kept"; the file is not modified.
4. Set `max-players: 100`, `reward-sound: entity.player.levelup`, reload:
   expect "Configuration reloaded" (covers `Registry.SOUNDS` resolution).
5. Set `reward-sound: not.a.sound`, reload: expect
   `reward-sound [INVALID_VALUE]`.
6. Add an unknown key and a comment above `debug`, delete `cooldown`, reload:
   `cooldown: 5m` is re-inserted with its comment, everything else untouched.
7. `stop`. Startup again must not rewrite the file (unchanged timestamp).

Record the Paper build number and the outcome in the compatibility table
above.
