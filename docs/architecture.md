# Architecture

```
leafconfig-api      annotations, ConfigHandle, ReloadResult, diagnostics, ConfigNode,
                    TypeAdapter/TypeAdapterFactory, ConfigValidator,
                    migration (ConfigDocument, Migrations, ConfigDiff)   (no YAML, no Paper)
leafconfig-yaml     ConfigManager + everything under dev.leafconfig.yaml.internal
leafconfig-paper    LeafConfig.forPlugin, PaperAdapters                   (only Paper classes here)
leafconfig-example  documentation plugin, shaded + relocated
```

## `leafconfig-yaml` internals

| Package | Responsibility |
|---|---|
| `internal.schema` | `ReflectionSchemaFactory` is the only code that touches `java.lang.reflect` for user models. Produces immutable `ConfigSchema` / `ObjectSchema` / `ConfigProperty` with a `PropertyAccessor` per field. A future annotation processor implements `SchemaFactory` and produces the same records. |
| `internal.codec` | `AdapterRegistry` (manager-scoped, deterministic precedence, cycle detection for nested objects), built-in scalar adapters, enum/collection/map factories, `ObjectAdapter` for nested objects. |
| `internal.decode` | `Decoder` (aggregating `DecodeContext`), `Encoder`, `ConstraintValidator`, `DiagnosticCollector`. |
| `internal.yaml` | The only package importing SnakeYAML Engine: `YamlDocument` (parse settings, style detection), `NodeConverter` (SnakeYAML nodes to `ConfigNode` with safety checks and back), `DocumentMerger`, `YamlConfigDocument` (the `ConfigDocument` handed to migration steps), `YamlRenderer`. |
| `internal.migration` | `MigrationRunner`: reads `config-version`, applies steps and `@FormerlyKnownAs` renames to the in-memory tree, stamps the version. |
| `internal.io` | `SafePaths` (containment, symlink check), `AtomicFiles`. |
| `internal` | `ConfigLoader` pipeline, `DefaultConfigHandle`, `LoadFailure`. |

Public surface of the module: `ConfigManager` (including `previewMigration`),
`ConfigManager.Builder`, `YamlLimits`. Everything under `internal` may change
without notice.

## Compatibility policy before 1.0

Public packages are `dev.leafconfig`, `dev.leafconfig.annotation`,
`dev.leafconfig.adapter`, `dev.leafconfig.node`, `dev.leafconfig.validation`,
`dev.leafconfig.migration`,
`dev.leafconfig.yaml` (only `ConfigManager`, `ConfigManager.Builder`,
`YamlLimits`) and `dev.leafconfig.paper`. Everything under `internal` may change
in any release.

- Value types such as `ConfigDiagnostic`, `ReloadResult` and the node records
  are Java records. When a component is added, the previous canonical
  constructor is kept as an explicit overload so existing callers keep
  compiling and linking; accessors are never removed.
- `ConfigNode` is sealed. A new node kind before 1.0 is a breaking change for
  exhaustive `switch` statements in user adapters and is announced in the
  changelog.
- Diagnostic codes are only ever added; existing codes keep their meaning.
- `apiCompatibility` (japicmp, part of `check`) fails on binary-incompatible
  changes against the last release; deliberate pre-1.0 breaks must be listed in
  the changelog and accepted explicitly in `gradle.properties`.
- Interfaces intended for users to implement (`TypeAdapter`,
  `TypeAdapterFactory`, `ConfigValidator`) only gain `default` methods.
- Interfaces implemented by LeafConfig (`ConfigHandle`, `DecodeContext`,
  `EncodeContext`, `ValidationContext`) may gain abstract methods.

## Versioning from 1.0.0

From 1.0.0 LeafConfig follows [Semantic Versioning 2.0.0](https://semver.org/).
The rules below say what that means for each part of the library.

### Compatibility surface

These are covered by the guarantees:

- **Public API**: every public type and member in the public packages listed
  above. Packages named `internal` are not covered.
- **Annotations**: their names, targets, attributes and meaning.
- **Serialized form**: how keys are derived from field names, how built-in and
  Paper types are written (for example `5m` for a `Duration`, the exact enum
  constant name), the reserved `config-version` key, and the preservation
  guarantees in [yaml-merge-semantics.md](yaml-merge-semantics.md). A change
  here makes plugins rewrite administrator files, so it is treated like an API
  change.
- **Diagnostic codes**: their names and meaning.
- **Java baseline**: Java 21.

Not covered: diagnostic messages, the text of exceptions, log output, the
layout of a generated file beyond the guarantees above, internal packages, and
the versions of internal dependencies such as SnakeYAML Engine.

### Patch releases (1.0.x)

Bug fixes only. No new public API. A fix may change behaviour that contradicted
the documentation; the changelog says so.

### Minor releases (1.x.0)

Backward-compatible additions:

- new public types, methods, annotations, built-in adapters and diagnostic
  codes;
- new `default` methods on interfaces users implement (`TypeAdapter`,
  `TypeAdapterFactory`, `ConfigValidator`, `Migration`);
- new abstract methods on interfaces LeafConfig implements (`ConfigHandle`,
  `Registration`, `DecodeContext`, `EncodeContext`, `TypeAdapterLookup`,
  `ValidationContext`, `ConfigDocument`). Implementing these outside LeafConfig
  is not supported;
- new record components, keeping the previous canonical constructor as an
  overload;
- new constants in enums that users only pass in, such as `BackupPolicy`;
- deprecations, see below;
- support for additional Paper versions.

A minor release may drop support for an old Paper version line; the changelog
announces it. It never does so in a patch release.

### Major releases (2.0.0, ...)

Everything else, in particular:

- removing or changing a public type or member, including its return type;
- new constants in enums that LeafConfig returns (`Severity`, `ScalarTag`,
  `ConfigDiff.Kind`) and new `ConfigNode` kinds, because they break exhaustive
  `switch` statements;
- changing the meaning of an annotation or a diagnostic code;
- changing the serialized form;
- raising the Java baseline.

### Deprecation

An element to be removed is first marked `@Deprecated(since = "x.y")` in a minor
release, with its replacement named in the Javadoc and in the changelog. It is
removed no earlier than the next major release, and at least one minor release
ships the deprecation before that.

### Enforcement

- `apiCompatibility` (japicmp, part of `check`) compares every published module
  with the last release. From 1.0.0 the build refuses accepted breaks
  (`leafconfig.apiAcceptedBreaks`) unless the major version is higher than the
  baseline's.
- The golden-file tests fail on any change to the serialized form.

## Invariants

- No global mutable state. Caches (schemas, adapters) belong to a
  `ConfigManager` and are cleared by `close()`, so plugin class loaders are not
  pinned after disable.
- No threads, executors or watchers are created.
- A configuration instance is never mutated after publication; reload creates
  a new one.
- The file is written only by `AtomicFiles`, only after a successful decode and
  validation, and only when the merge changed the document.
- Adapters and validators never see SnakeYAML types.
