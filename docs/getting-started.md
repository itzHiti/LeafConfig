# Getting started

1. Add `io.github.itzhiti:leafconfig-paper` to your plugin (see
   [distribution-modes.md](distribution-modes.md) for how it reaches the
   server). Artifacts are not published yet; build locally with
   `./gradlew build` and use the jars from `*/build/libs`.
2. Write a configuration class: a `final` class with a zero-argument
   constructor, private instance fields with initializers, and `@ConfigFile`.
   Nested sections are nested classes with their own zero-argument constructor.
3. In `onEnable()`:

   ```java
   manager = LeafConfig.forPlugin(this).build();
   try {
     config = manager.load(MainConfig.class);
   } catch (ConfigLoadException e) {
     getLogger().severe(e.getMessage()); // lists every problem with path and line
     getServer().getPluginManager().disablePlugin(this);
     return;
   }
   ```

4. Read values through `config.get()`. Keep the handle, not the snapshot, if
   you want to see reloads.
5. In `onDisable()` call `manager.close()`.

The complete flow is in
[`ExamplePlugin`](../leafconfig-example/src/main/java/dev/leafconfig/example/ExamplePlugin.java).

## Model rules

- Instance fields are persisted unless `static`, `transient`, synthetic or
  `@Ignore`.
- `final` fields, fields inherited from a superclass, duplicate keys, keys with
  dots or YAML indicator characters, and recursive object graphs are rejected
  with `INVALID_MODEL` diagnostics when the type is first loaded.
- Keys default to `lower-kebab-case` of the field name (`maxPlayers` becomes
  `max-players`); `@Key` overrides a single segment.
- Declaration order is the generated order.
- `null` is legal only for reference types without `@Required` and is written
  as `null`.
- Configuration classes are data: no services, plugins, loggers or paths.
