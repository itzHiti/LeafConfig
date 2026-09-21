# Custom adapters

Implement `TypeAdapter<T>` from `dev.leafconfig.adapter`:

```java
final class ColorAdapter implements TypeAdapter<Color> {
  @Override
  public Color decode(ConfigNode node, DecodeContext context) {
    if (!(node instanceof ScalarNode scalar)) {
      throw new ConfigDecodeException(DiagnosticCodes.TYPE_MISMATCH, "expected #rrggbb");
    }
    try {
      return Color.decode(scalar.value());
    } catch (NumberFormatException e) {
      throw new ConfigDecodeException(DiagnosticCodes.INVALID_VALUE, "expected #rrggbb, got '" + scalar.value() + "'");
    }
  }

  @Override
  public ConfigNode encode(Color value, EncodeContext context) {
    return ScalarNode.ofString(String.format("#%06x", value.getRGB() & 0xFFFFFF));
  }
}
```

Register it:

```java
ConfigManager.builder(dir).adapter(Color.class, new ColorAdapter()).build();
```

Rules:

- Adapters must be stateless or thread-safe, must not log, and must not catch
  `Error`s.
- Throw `ConfigDecodeException` with a stable code; the framework adds the key
  path and source line.
- `decode` is never called with a YAML `null` and `encode` never with `null`;
  the framework handles null semantics.
- Composite adapters delegate to `context.decodeChild(segment, type, node)` so
  element failures are aggregated instead of aborting the load.

## Generic types and factories

`Builder.adapter(Type, TypeAdapter<?>)` registers an adapter for a specific
parameterized type. `TypeAdapterFactory` handles whole families:

```java
ConfigManager.builder(dir)
    .adapterFactory((type, lookup) -> type == MyBox.class ? Optional.of(new MyBoxAdapter()) : Optional.empty())
    .build();
```

## Precedence

1. user adapter for the exact type (`adapter(...)`)
2. module adapter (`moduleAdapter(...)`, used by `leafconfig-paper`)
3. built-in adapter for the exact type
4. user factories in registration order
5. built-in factories: enums, `List`/`Set`, `Map<String, V>`
6. nested configuration object

Registering two adapters for the same type at the same level fails in the
builder with `IllegalArgumentException` naming both. Resolution is cached per
manager and released by `ConfigManager.close()`.
