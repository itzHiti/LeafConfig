# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
semantic versioning once `1.0.0` is released. Before that, breaking changes are
allowed and listed under **Changed**.

## [Unreleased]

### Added

- `leafconfig-api`: annotations `@ConfigFile`, `@Key`, `@Comment`, `@Ignore`,
  `@Required`, `@NotBlank`, `@Range`, `@Pattern`; `ConfigHandle`,
  `ReloadResult`, `ConfigDiagnostic`, `ConfigPath`, `DiagnosticCodes`,
  `ConfigLoadException`, `ConfigModelException`; `TypeAdapter`,
  `TypeAdapterFactory`, `ConfigValidator` contracts; backend-neutral
  `ConfigNode` model.
- `leafconfig-yaml`: `ConfigManager` with builder, reflection schema
  discovery, built-in adapters (strings, booleans, all numeric types,
  `BigInteger`/`BigDecimal`, enums, `Duration`, `UUID`, `Path`, `List`, `Set`,
  `Map<String, T>`, nested objects), comment-preserving non-destructive merge,
  atomic writes, base-directory containment, resource limits (`YamlLimits`),
  serialized reloads with listeners.
- `leafconfig-paper`: `LeafConfig.forPlugin(JavaPlugin)` and adapters for
  Adventure `Component` (MiniMessage), `Material`, `Particle`, `Sound`,
  `NamespacedKey`.
- `leafconfig-example`: shaded, relocated example plugin with a reload command
  and a manual `runServer` smoke-test task.
- `leafconfig-benchmarks`: JMH baseline suite (cold discovery and load, no-op
  reload, merge-and-write reload), results in `docs/benchmarks.md`.
- Publishing: Maven Central coordinates `io.github.itzhiti:leafconfig-*`,
  sources and Javadoc jars, POM metadata, signed staging uploads from the
  `Release` workflow on `v*` tags.
- Build: Java 21 toolchain, Spotless, Error Prone, JaCoCo (80% line coverage
  gate on `leafconfig-yaml`), GitHub Actions CI.

### Design decisions recorded

- `ConfigManager` lives in `leafconfig-yaml`, not in the API module, so the API
  module stays free of the YAML implementation.
- `ConfigManager.Builder` has no `registerDefaults()`: built-in adapters are
  always present. `LeafConfig.forPlugin` registers the Paper adapters at module
  precedence instead of exposing `registerPaperAdapters()`.
- `Optional<T>` is not supported yet (deferred until its null/missing semantics
  are covered by tests).
- Inherited persisted fields are rejected rather than partially supported.
- A file containing only comments keeps them verbatim above generated content.
