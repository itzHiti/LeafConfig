# Annotations

All annotations live in `dev.leafconfig.annotation` and have runtime retention.

| Annotation | Target | Effect |
|---|---|---|
| `@ConfigFile(String)` | type | Default file of a root type, required for `load(Class)`. Relative file name inside the manager base directory (`config.yml`, `messages/en.yml`). Absolute names and traversal are rejected with `UNSAFE_PATH`. Types loaded only through `load(Class, String)` may omit it. |
| `@Key(String)` | field | Overrides the generated key. Exactly one segment matching `[A-Za-z0-9_-]+`. |
| `@Comment(String...)` | field, type | Comment lines written above a key when it is generated or when the existing key has no comment. On a root type: file header. |
| `@Ignore` | field | Excludes the field. |
| `@Required` | field | Missing key or explicit `null` fails with `MISSING_REQUIRED`. |
| `@NotBlank` | `CharSequence` field | Empty or whitespace-only values fail with `BLANK`. |
| `@Range(min, max)` | numeric field | Inclusive bounds, compared exactly as decimals. `OUT_OF_RANGE`. |
| `@Pattern(String)` | `CharSequence` field | Whole value must match. Compiled at discovery; `PATTERN_MISMATCH`. |
| `@ConfigVersion(int)` | type | Schema version, at least 1, stored as `config-version`. Enables migrations; see [migrations.md](migrations.md). |
| `@Secret` | field | Diagnostics at or below the key keep code, path and line, but their message is replaced; dry-run diffs show `***`. The value is still loaded and written normally, nothing is encrypted. Exception text from a failing migration step is reported at `config-version` and is not redacted. |
| `@FormerlyKnownAs(String...)` | field | Former keys in the same mapping. A lone former key is renamed in place with its comments; conflicts fail with `RENAME_CONFLICT`. |

Misplaced validation annotations (for example `@NotBlank` on an `int`) are a
model error, not a runtime warning.

## Programmatic validators

Cross-field rules use `ConfigValidator<T>` registered on the builder:

```java
ConfigManager.builder(dir)
    .validator(MainConfig.class, (config, ctx) -> {
      if (config.maxPlayers() < config.minPlayers()) {
        ctx.error(ConfigPath.of("max-players"), "must be at least min-players");
      }
    })
    .build();
```

Validators run only when decoding and annotation validation produced no
errors, so they always see a fully decoded instance. Errors use
`VALIDATION_FAILED` unless a custom code is given; warnings never block
publication.
