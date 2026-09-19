package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** Encodes Java values into backend-neutral nodes. Stateless apart from the adapter lookup. */
public final class Encoder implements EncodeContext {

  private final TypeAdapterLookup adapters;

  /** Creates an encoder. */
  public Encoder(TypeAdapterLookup adapters) {
    this.adapters = adapters;
  }

  /** Encodes every property of {@code instance} in declaration order. */
  public MappingNode encodeObject(ObjectSchema schema, Object instance) {
    Map<String, ConfigNode> entries = new LinkedHashMap<>();
    for (ConfigProperty property : schema.properties()) {
      entries.put(property.key(), encodeProperty(property, property.accessor().get(instance)));
    }
    return MappingNode.of(entries);
  }

  /** Encodes one property value; {@code null} becomes a null node. */
  public ConfigNode encodeProperty(ConfigProperty property, Object value) {
    return value == null ? NullNode.instance() : property.adapter().encode(value, this);
  }

  @Override
  public ConfigNode encodeChild(Type type, Object value) {
    if (value == null) {
      return NullNode.instance();
    }
    @SuppressWarnings("unchecked") // the registry resolved this adapter for exactly this type
    TypeAdapter<Object> adapter =
        (TypeAdapter<Object>)
            adapters
                .find(type)
                .orElseThrow(
                    () -> new IllegalStateException("no adapter for type " + type.getTypeName()));
    return adapter.encode(value, this);
  }
}
