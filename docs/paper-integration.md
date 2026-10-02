# Paper integration

`leafconfig-paper` is a thin layer over `leafconfig-yaml`.

<!-- snippet: paper-integration-manager -->
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
| 1.21.11 build 132 (`paper-api:1.21.11-R0.1-SNAPSHOT`) | yes | yes | passed 2026-09-21, checklist 7/7 for both the shaded jar and the `libraries:` jar (generation, failed reload keeps snapshot and file, `Sound` via registry, invalid sound, merge of removed key, no-op restart) |
| 1.21.8 build 60 | no, runs the jar compiled against 1.21.11 | n/a | passed 2026-10-02, checklist 8/8, shaded jar of 0.4.0-SNAPSHOT |
| 1.21.4 build 232 | no, runs the jar compiled against 1.21.11 | n/a | passed 2026-10-02, checklist 8/8, shaded jar of 0.4.0-SNAPSHOT |
| 1.21.1 build 133 | no, runs the jar compiled against 1.21.11 | n/a | passed 2026-10-02, checklist 8/8, shaded jar of 0.4.0-SNAPSHOT |

Paper publishes its API only as snapshots; the version is pinned in
`gradle/libs.versions.toml`, and every release is compiled against that one
version only. The rows for older servers run that same jar, as users would.
Versions not listed (1.21, 1.21.3, 1.21.5 to 1.21.7, 1.21.9, 1.21.10) have not been run; that
they work is an inference from the tested versions on both sides, not a
verified result. The `libraries:` jar was smoke-tested on 1.21.11 only.

## Server smoke test

Manual, needs network and JDK 21; downloads the pinned Paper server into
`leafconfig-example/run/` (git-ignored) and starts it with the freshly built
example plugin installed. The EULA is accepted by the task's JVM flag.

```bash
./gradlew :leafconfig-example:runServer
```

To test another Paper release, pass its Minecraft version. The plugin is still
compiled against the `paper-api` pinned in `gradle/libs.versions.toml`, exactly
like a published plugin, so this checks what users get on older or newer
servers. Each version runs in its own directory, for example
`leafconfig-example/run-1.21.4/`, because an older server cannot open a world
written by a newer one:

```bash
./gradlew :leafconfig-example:runServer -Pleafconfig.paperVersion=1.21.4
./gradlew :leafconfig-example:runServerLibraries -Pleafconfig.paperVersion=1.21.4
```

PowerShell splits an unquoted `-Pname.with.dots=value` at the first dot and Gradle
then reports a missing task such as `.paperVersion=1.21.4`; quote the argument:

```powershell
.\gradlew :leafconfig-example:runServer "-Pleafconfig.paperVersion=1.21.4"
```

Checklist while the console is attached:

1. Startup log contains `Enabling LeafConfigExample v<project version>` and no stack trace.
2. `run/plugins/LeafConfigExample/config.yml` (or `run-<version>/...`) equals the
   generated file shown in the README.
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
8. Set `password: ""` under `database`, reload: expect
   `database.password [BLANK]: details hidden because the key is marked @Secret`.
   Added in 0.3.2; not yet run on a server.

Record the Paper build number and the outcome in the compatibility table
above.
