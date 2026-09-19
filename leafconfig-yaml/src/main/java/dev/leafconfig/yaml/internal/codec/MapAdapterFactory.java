package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** {@code Map<String, V>} adapter. Keys are taken as written; decoded maps are unmodifiable. */
public final class MapAdapterFactory implements TypeAdapterFactory {

  @Override
  public Optional<TypeAdapter<?>> create(Type type, TypeAdapterLookup lookup) {
    if (!(type instanceof ParameterizedType parameterized)
        || parameterized.getRawType() != Map.class) {
      return Optional.empty();
    }
    Type[] arguments = parameterized.getActualTypeArguments();
    Type value = arguments[1];
    if (arguments[0] != String.class || !Types.isConcrete(value) || lookup.find(value).isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new TypeAdapter<Map<String, Object>>() {
          @Override
          public Map<String, Object> decode(ConfigNode node, DecodeContext context) {
            if (!(node instanceof MappingNode mapping)) {
              throw Scalars.mismatch("mapping", node);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
              result.put(
                  entry.getKey(), context.decodeChild(entry.getKey(), value, entry.getValue()));
            }
            return Collections.unmodifiableMap(result);
          }

          @Override
          public ConfigNode encode(Map<String, Object> map, EncodeContext context) {
            Map<String, ConfigNode> nodes = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
              nodes.put(entry.getKey(), context.encodeChild(value, entry.getValue()));
            }
            return MappingNode.of(nodes);
          }
        });
  }
}
