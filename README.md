# LeafConfig

Type-safe, annotation-driven YAML configuration for Paper plugins that evolves
administrator files without breaking them.

> **Status: pre-1.0, not yet published.** Version `0.1.0` is under active
> development. Artifacts are not on Maven Central yet; the installation snippets
> below show the intended coordinates. Publishing is wired up (see
> [CONTRIBUTING.md](CONTRIBUTING.md#releasing)) but no release has been cut. Public API, diagnostic codes and
> serialized defaults may still change before `1.0.0`; every breaking change is
> recorded in [CHANGELOG.md](CHANGELOG.md).

## Requirements

| | |
|---|---|
| Java | 21 |
| Paper | 1.21.x (`paper-api` is `compileOnly`, never bundled). Built and tested against `1.21.11-R0.1-SNAPSHOT`. |
| YAML engine | SnakeYAML Engine (internal dependency of `leafconfig-yaml`) |

`leafconfig-api` and `leafconfig-yaml` contain no Paper or Bukkit classes and
work in plain Java.

## Installation

Coordinates (group `io.github.itzhiti`):

- `leafconfig-api` – annotations and public contracts
- `leafconfig-yaml` – the engine, depends on `leafconfig-api`
- `leafconfig-paper` – Paper integration and adapters, depends on `leafconfig-yaml`

Gradle Kotlin DSL:

```kotlin
dependencies {
    implementation("io.github.itzhiti:leafconfig-paper:0.1.0")
}
```

Gradle Groovy:

```groovy
dependencies {
    implementation 'io.github.itzhiti:leafconfig-paper:0.1.0'
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.itzhiti</groupId>
  <artifactId>leafconfig-paper</artifactId>
  <version>0.1.0</version>
</dependency>
```

How the library reaches the server is a separate decision, see
[Distribution modes](#distribution-modes).

## Define a configuration

Ordinary private fields with initializers are the schema; annotations only
adjust behavior. This is the class used by the example plugin
([`MainConfig.java`](leafconfig-example/src/main/java/dev/leafconfig/example/MainConfig.java)):

```java
@ConfigFile("config.yml")
@Comment({"LeafConfig example plugin", "Edit and run /leafconfigexample reload"})
public final class MainConfig {

  @Comment("Enable verbose diagnostic messages")
  private boolean debug = false;

  @Comment("Maximum number of players allowed to use the reward")
  @Range(min = 1, max = 200)
  private int maxPlayers = 100;

  @Comment("Prefix shown before every message, MiniMessage format")
  private Component prefix =
      MiniMessage.miniMessage().deserialize("<gray>[<green>Leaf</green>]</gray> ");

  @Comment("Item handed out by the reward")
  private Material rewardItem = Material.GOLDEN_APPLE;

  @Comment("How long a player has to wait between rewards")
  private Duration cooldown = Duration.ofMinutes(5);

  @Comment({"Sound played on reward, for example entity.player.levelup", "null disables the sound"})
  private Sound rewardSound = null;

  private List<String> enabledWorlds = List.of("world");

  @Comment("Database connection settings")
  private Database database = new Database();

  // accessors omitted

  public static final class Database {
    @NotBlank private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 5432;
  }
}
```

## Load it

Paper plugin:

```java
ConfigManager manager = LeafConfig.forPlugin(this).build();
ConfigHandle<MainConfig> config = manager.load(MainConfig.class);
MainConfig current = config.get();
```

Plain Java:

```java
ConfigManager manager = ConfigManager.builder(dataDirectory).build();
ConfigHandle<MainConfig> config = manager.load(MainConfig.class);
```

`load` throws `ConfigModelException` for an invalid Java model (programming
error) and `ConfigLoadException` for an unusable file. Both carry structured
diagnostics; the file on disk is never modified when loading fails.

## Generated YAML

On first load the file is created from the Java defaults. This is the exact
output for the class above, proven by
[`MainConfigTest`](leafconfig-example/src/test/java/dev/leafconfig/example/MainConfigTest.java):

```yaml
# LeafConfig example plugin
# Edit and run /leafconfigexample reload

# Enable verbose diagnostic messages
debug: false
# Maximum number of players allowed to use the reward
max-players: 100
# Prefix shown before every message, MiniMessage format
prefix: '<gray>[<green>Leaf</green>]</gray> '
# Item handed out by the reward
reward-item: GOLDEN_APPLE
# How long a player has to wait between rewards
cooldown: 5m
# Sound played on reward, for example entity.player.levelup
# null disables the sound
reward-sound: null
enabled-worlds:
  - world
# Database connection settings
database:
  host: localhost
  port: 5432
```

## Reload and error handling

```java
ReloadResult<MainConfig> result = config.reload();
if (!result.successful()) {
  // result.current() is still the previous, valid snapshot
  for (ConfigDiagnostic d : result.diagnostics()) {
    getLogger().warning(d.toString());
  }
}
config.onReload(fresh -> cache.rebuild(fresh));
```

A reload publishes a new snapshot only after parsing, decoding, validation and
any required merge write have all succeeded. Snapshots are never mutated in
place. Diagnostics look like this:

```text
Invalid configuration: plugins/Example/config.yml
  - database.port [OUT_OF_RANGE]: expected 1..65535, got 70000 (line 14, column 9)
  - prefix [TYPE_MISMATCH]: expected string, got sequence (line 22, column 9)
```

Every independent error is reported in one pass. Codes are stable
(`DiagnosticCodes`), messages are not.

## Supported types

| Type | YAML | Notes |
|---|---|---|
| `String` | any scalar | text preserved exactly |
| `boolean`/`Boolean` | `true`/`false` | `yes`/`no`/`1` are rejected |
| `byte`, `short`, `int`, `long` and wrappers | integer | overflow and fractional values are errors |
| `float`, `double` and wrappers | float | `.inf`, `.nan` accepted; overflow is an error |
| `BigInteger`, `BigDecimal` | integer / float | exact |
| enums | constant name | case-insensitive read, exact name written |
| `Duration` | `30s`, `5m`, `2h`, `1d`, `1h30m`, `500ms` | ISO-8601 accepted as fallback |
| `UUID` | string | |
| `Path` | string | stored as written, never resolved |
| nested object | mapping | zero-argument constructor required |
| `List<T>`, `Set<T>` | sequence | unmodifiable; set keeps document order |
| `Map<String, T>` | mapping | unmodifiable, insertion order |
| `Component` (Paper) | MiniMessage string | `leafconfig-paper` |
| `Material`, `Particle` (Paper) | constant name | `Material` also accepts `minecraft:` keys |
| `Sound` (Paper) | namespaced key | resolved through the server registry |
| `NamespacedKey` (Paper) | `namespace:key` | |

Details and limits: [docs/supported-types.md](docs/supported-types.md).
Custom types: [docs/custom-adapters.md](docs/custom-adapters.md).

## Preservation guarantees

Verified by the golden-file suite in
[`leafconfig-yaml/src/test/resources/golden`](leafconfig-yaml/src/test/resources/golden):

- Existing values always win over Java defaults; invalid values fail the load
  instead of being replaced.
- Unknown keys, user comments, key order, quoted and block scalars are kept.
- Missing keys are inserted next to their schema neighbours with their
  `@Comment`; a schema comment is added to an existing key only when that key
  has no comment.
- Indentation width, indented vs. unindented sequences, CRLF line endings and a
  UTF-8 BOM are detected and kept.
- A semantically unchanged document is not rewritten (timestamp unchanged).
- Malformed YAML, duplicate keys, anchors/aliases and non-core tags are rejected
  and the file is left byte for byte untouched.

Known limitations:

- Line breaks inside folded scalars (`>`) are re-folded by the emitter.
- A file containing only comments keeps them verbatim above the generated
  content.
- Sequence entries inside `Map`s or `List`s are not merged; only mapping
  sections that correspond to nested configuration objects receive new keys.

Full semantics: [docs/yaml-merge-semantics.md](docs/yaml-merge-semantics.md).

## Distribution modes

| Your situation | Recommended mode |
|---|---|
| Public standalone plugin, Paper server | shaded + relocated (the only mode supported in 0.1.0); `plugin.yml` `libraries:` is planned for 0.1.1 once the artifacts are on Maven Central and verified |
| Legacy or non-Paper environment | shaded + relocated |
| Private network with many coordinated plugins | provider plugin (planned, not yet available) |

Never install both a shaded copy and the provider. See
[docs/distribution-modes.md](docs/distribution-modes.md) for Shadow, Maven
Shade and `libraries:` examples.

## More

- Example plugin: [`leafconfig-example`](leafconfig-example) (shaded, relocated
  jar built by `./gradlew :leafconfig-example:shadowJar`)
- [Getting started](docs/getting-started.md), [Annotations](docs/annotations.md),
  [Reload](docs/reload.md), [Paper integration](docs/paper-integration.md),
  [Architecture](docs/architecture.md), [Benchmarks](docs/benchmarks.md)
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md). Quality gate:
  `./gradlew clean check`.

License: [Apache-2.0](LICENSE).
