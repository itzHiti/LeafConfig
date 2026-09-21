# Distribution modes

Three ways for LeafConfig to reach a server. They are not equivalent.

## A. Shaded and relocated (available now)

The plugin bundles LeafConfig and SnakeYAML Engine and relocates them.
Strongest isolation, works everywhere, largest jar, classes duplicated per
plugin on big servers. **Relocation is mandatory**; an unrelocated copy can
clash with another plugin's copy.

Gradle (Shadow, `com.gradleup.shadow`), as used by `leafconfig-example`:

```kotlin
plugins { id("com.gradleup.shadow") version "9.6.1" }

dependencies { implementation("io.github.itzhiti:leafconfig-paper:0.1.0") }

tasks.shadowJar {
    relocate("dev.leafconfig", "com.example.myplugin.libs.leafconfig")
    relocate("org.snakeyaml.engine", "com.example.myplugin.libs.snakeyaml")
}
```

Maven Shade:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <configuration>
    <relocations>
      <relocation><pattern>dev.leafconfig</pattern><shadedPattern>com.example.myplugin.libs.leafconfig</shadedPattern></relocation>
      <relocation><pattern>org.snakeyaml.engine</pattern><shadedPattern>com.example.myplugin.libs.snakeyaml</shadedPattern></relocation>
    </relocations>
  </configuration>
</plugin>
```

Paper API and Adventure are `compileOnly` and must never be shaded.

## B. Paper `plugin.yml` `libraries:` (after Maven Central publication)

```yaml
libraries:
  - io.github.itzhiti:leafconfig-paper:0.1.0
```

Paper downloads the artifact and its dependencies and adds them to the
plugin's class loader. No shading, small jar. Each plugin still gets its own
class-loader view; this mode does not guarantee one shared class definition or
runtime instance across plugins. Not usable until the artifacts are on Maven
Central; do not point this at a private repository for a public plugin.

## C. Provider plugin (planned, release 0.5.0)

A separate `leafconfig-provider-paper` plugin owns one engine for many
coordinated plugins that compile against `leafconfig-api` only. Intended for
networks controlling all their plugins. Not implemented yet.

## Decision guide

| Consumer | Mode |
|---|---|
| Public standalone Paper plugin | B once published; A until then or for maximum portability |
| Legacy or non-Paper server | A |
| Private network, tens or hundreds of coordinated plugins | C once stable |

Never require both a shaded copy and the provider, and never switch a plugin
between modes silently.
