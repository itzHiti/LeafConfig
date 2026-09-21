# Supported types

Behaviour below is covered by `BuiltinAdaptersTest`, `ConfigManagerTest` and
`PaperAdaptersTest`.

## Scalars

| Java | Accepted YAML | Rejected | Written as |
|---|---|---|---|
| `String` | any scalar (`42`, `true`, quoted, block) | sequences, mappings | plain or quoted as needed |
| `boolean`, `Boolean` | `true`, `True`, `TRUE`, `false`, ... including quoted | `yes`, `no`, `on`, `1` | `true`/`false` |
| `byte`, `short`, `int`, `long` + wrappers | decimal, `0x1A`, `0o17`, `+7`, quoted digits, `10.0` | `1.5` (`PRECISION_LOSS`), out of range (`OVERFLOW`), booleans | decimal |
| `float`, `double` + wrappers | any decimal, `1e3`, `.inf`, `-.inf`, `.nan` | values that become infinite (`OVERFLOW`) | shortest round-trip form |
| `BigInteger` | as integers, unlimited size | fractional | decimal |
| `BigDecimal` | any decimal, exact scale kept | `.inf`, `.nan` | plain string form |
| enum | constant name, case-insensitive | unknown constant (`INVALID_VALUE`) | exact constant name |
| `Duration` | `500ms`, `30s`, `5m`, `2h`, `1d`, combinations like `1h30m`; ISO-8601 `PT90M` | anything else | shortest human form; ISO for negative or sub-millisecond values |
| `UUID` | canonical string | anything else | canonical string |
| `Path` | string | invalid path syntax | `Path.toString()`; never resolved |

Enums whose constants differ only by case are rejected as ambiguous when the
type is first used.

## Structures

| Java | YAML | Notes |
|---|---|---|
| nested class | mapping | needs a zero-argument constructor; `null` default writes `null` |
| `List<E>` | sequence | unmodifiable `List` |
| `Set<E>` | sequence | unmodifiable, document order, duplicates collapse to the first |
| `Map<String, V>` | mapping | unmodifiable, document order; keys must be `String` |

Declared types must be the interfaces `List`, `Set`, `Map`; raw types,
wildcards and non-`String` map keys are model errors. A `null` element inside a
sequence or mapping value is `NULL_NOT_ALLOWED`.

`Optional<T>` is not supported yet.

## Paper (`leafconfig-paper`)

| Java | YAML | Notes |
|---|---|---|
| `net.kyori.adventure.text.Component` | MiniMessage string | defaults are written in canonical MiniMessage form (`<b>` becomes `<bold>`); user text is never rewritten |
| `org.bukkit.Material` | `STONE`, `stone`, `minecraft:stone` | `Material.matchMaterial` |
| `org.bukkit.Particle` | constant name, case-insensitive | |
| `org.bukkit.Sound` | `minecraft:entity.player.levelup`, `entity.player.levelup`, legacy `ENTITY_PLAYER_LEVELUP` | resolved through `Registry.SOUNDS`; requires a running server, so it is not unit-tested offline |
| `org.bukkit.NamespacedKey` | `namespace:key` | |

Values the running server does not know fail with `INVALID_VALUE` naming the
value. Live objects (`Player`, `World`, `Location`) are intentionally not
supported.

## Limits (`YamlLimits`)

Defaults: 8 MiB per file, nesting depth 64, 100 000 entries per collection,
1 MiB per scalar. Exceeding any limit fails the load with `LIMIT_EXCEEDED`.
Anchors, aliases and tags outside the YAML core schema are rejected.
