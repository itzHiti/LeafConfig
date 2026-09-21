# Annotations

All annotations live in `dev.leafconfig.annotation` and have runtime retention.

| Annotation | Target | Effect |
|---|---|---|
| `@ConfigFile(String)` | type | Required on a root type. Relative file name inside the manager base directory (`config.yml`, `messages/en.yml`). Absolute names and traversal are rejected with `UNSAFE_PATH`. |
| `@Key(String)` | field | Overrides the generated key. Exactly one segment matching `[A-Za-z0-9_-]+`. |
| `@Comment(String...)` | field, type | Comment lines written above a key when it is generated or when the existing key has no comment. On a root type: file header. |
| `@Ignore` | field | Excludes the field. |
| `@Required` | field | Missing key or explicit `null` fails with `MISSING_REQUIRED`. |
| `@NotBlank` | `CharSequence` field | Empty or whitespace-only values fail with `BLANK`. |
| `@Range(min, max)` | numeric field | Inclusive bounds, compared exactly as decimals. `OUT_OF_RANGE`. |
| `@Pattern(String)` | `CharSequence` field | Whole value must match. Compiled at discovery; `PATTERN_MISMATCH`. |

Misplaced validation annotations (for example `@NotBlank` on an `int`) are a
model error, not a runtime warning.

Reserved for later releases and intentionally absent: `@ConfigVersion`,
`@Aliases`, `@Secret`.

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
