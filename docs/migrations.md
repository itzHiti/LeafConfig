# Schema migrations

Verified by `MigrationTest`, `YamlConfigDocumentTest`, `ConfigDiffTest` and the
golden fixtures under `leafconfig-yaml/src/test/resources/golden/migration`.

## Versioning a configuration

```java
@ConfigFile("config.yml")
@ConfigVersion(3)
public final class MainConfig { ... }
```

The version is stored in the file as the top-level key `config-version`. The
key is reserved: a model may not declare a property under that name. Rules:

| File | Behaviour |
|---|---|
| no file | generated with `config-version: 3` as the first key (after the header comment) |
| `config-version: 3` | nothing to do |
| `config-version: 1` or `2` | migrated step by step to 3, see below |
| no `config-version` key | treated as version 1 with a `VERSION_ASSUMED` warning, then migrated |
| `config-version: 4` | rejected with `VERSION_TOO_NEW`; downgrades are never attempted |
| `config-version: two` | rejected with `INVALID_VALUE` at path `config-version` |

A type without `@ConfigVersion` is unversioned and behaves exactly as in 0.1.x.

## Registering steps

```java
ConfigManager manager = ConfigManager.builder(dir)
    .migrations(MainConfig.class, m -> m
        .from(1).to(2, doc -> {
          doc.rename("mysql.ip", "database.host");
          doc.rename("mysql.port", "database.port");
          doc.remove("mysql");
        })
        .from(2).to(3, doc -> doc.setIfMissing("database.pool-size", 10)))
    .build();
```

- Every step goes from `n` to `n + 1`; `from(1).to(3, ...)` and a second step
  for the same `from` are rejected with `IllegalArgumentException` when
  registered.
- Steps for a type without `@ConfigVersion`, or a step whose target is above
  the declared version, are `INVALID_MODEL` errors on the first `load`.
- A gap is allowed at registration time. A file that actually needs the missing
  step fails with `MIGRATION_MISSING`.

### `ConfigDocument`

A step receives a `ConfigDocument`, a mutable view of the parsed file. Paths are
dotted key paths (`database.host`); sequence elements cannot be addressed.

| Operation | Effect |
|---|---|
| `get(path)`, `contains(path)` | read; a missing or non-mapping intermediate segment reads as absent |
| `set(path, value)` | replace in place (key comments and position kept, inline comment on the value kept) or append; intermediate mappings are created |
| `setIfMissing(path, value)` | `set` only when absent; returns whether it wrote |
| `remove(path)` | drop the entry with its comments; returns whether it removed |
| `rename(from, to)` | move value and comments; inside one mapping the position is kept, across mappings the entry is appended to the target; fails when `to` exists |
| `root()` | immutable `ConfigNode` snapshot |

Values are `ConfigNode`s or plain `String`, `Boolean`, `Integer`, `Long`,
`BigInteger`, `Float`, `Double`, `BigDecimal`, `null`. Anything else is an
`IllegalArgumentException`. A step has no access to the file system, other
files or the manager; it only sees its own document.

## Simple renames without a version bump

```java
@FormerlyKnownAs("motd")
private String greeting = "Welcome!";
```

When `greeting` is absent from its mapping and exactly one former key is
present, the entry is renamed in place: value, comments and position are kept.
Both `greeting` and `motd` present, or two former keys present, fail with
`RENAME_CONFLICT`; nothing is guessed. Former keys are single segments inside
the same mapping; moving a value between sections needs a versioned step.
Renames run after versioned steps and work on unversioned types too.

No rename is ever inferred from a Java field name alone.

## The transaction

Every load, including `reload()`, runs these steps in order and stops at the
first failure:

1. Read and parse the file; safety checks (duplicate keys, anchors, tags,
   limits) run before any migration code.
2. Read `config-version`; reject newer files.
3. Apply each missing step to the in-memory tree, then stamp the target
   version (a new key goes first, or second when the first key carries the
   file header comment).
4. Apply `@FormerlyKnownAs` renames.
5. Decode and validate the result exactly like an ordinary load.
6. Merge missing defaults and schema comments.
7. If steps or renames rewrote data and the policy is `BEFORE_MIGRATION`
   (default), write the original bytes to `<file>.bak` next to the file.
8. Replace the file atomically.
9. Publish the snapshot.

On any failure the file and the active snapshot are untouched and the partially
migrated tree is discarded; the backup is written only immediately before the
replacement. Loading an already migrated file again runs no steps, writes
nothing and reports no warning.

### Backups

`BackupPolicy.BEFORE_MIGRATION` keeps at most one `<file>.bak`, overwritten on
the next migration. Loads that only add defaults or stamp `config-version`
never create a backup. `ConfigManager.builder(dir).backupPolicy(BackupPolicy.NONE)`
disables it.

## Dry run

```java
MigrationPreview preview = manager.previewMigration(MainConfig.class);
if (!preview.successful()) {
  preview.diagnostics().forEach(d -> logger.warning(d.toString()));
}
logger.info("Would change:\n" + preview.diff().render());
```

The preview runs the same pipeline without writing or publishing. It reports
`storedVersion()`, `targetVersion()`, `wouldWrite()`, the diagnostics a real
load would produce and a `ConfigDiff` of everything the load would write:
migration steps, renames, the version key and merged defaults.

```text
+ config-version: 3
+ greeting: "Hi there"
+ database.host: "10.0.0.1"
+ database.port: 3306
+ database.pool-size: 10
+ timeout: "30s"
- mysql.ip: "10.0.0.1"
- mysql.port: 3306
- motd: "Hi there"
```

`ConfigDiff` compares mappings key by key; scalars, nulls and sequences are
leaves (`~ path: before -> after`). Comments, order and quoting are not part of
the comparison.

## Diagnostic codes

| Code | Severity | Meaning |
|---|---|---|
| `VERSION_ASSUMED` | warning | versioned file without `config-version`; version 1 assumed |
| `VERSION_TOO_NEW` | error | file version above `@ConfigVersion` |
| `MIGRATION_MISSING` | error | no step registered for a needed `n -> n + 1` |
| `MIGRATION_FAILED` | error | a step threw; the message names the step and the exception |
| `RENAME_CONFLICT` | error | current key and a former key, or two former keys, are present |

Warnings of the load that published the current snapshot are available from
`ConfigHandle.warnings()`; on reload they are also part of `ReloadResult`.

## Limitations

- Migration steps cannot address sequence elements.
- A section emptied by `rename` stays in the file as `{}` until a step removes
  it.
- Comments attached to a key removed by a step are removed with it.
