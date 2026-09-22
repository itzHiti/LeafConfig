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
- Interfaces intended for users to implement (`TypeAdapter`,
  `TypeAdapterFactory`, `ConfigValidator`) only gain `default` methods.
- Interfaces implemented by LeafConfig (`ConfigHandle`, `DecodeContext`,
  `EncodeContext`, `ValidationContext`) may gain abstract methods.

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
