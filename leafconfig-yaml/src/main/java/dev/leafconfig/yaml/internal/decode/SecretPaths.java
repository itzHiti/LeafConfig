package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.yaml.internal.codec.ObjectAdapter;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Decides whether a path lies at or below a {@code @Secret} property, walking the schema through
 * nested sections, map values and collection elements. Map keys and sequence indices are dynamic,
 * so the answer is computed per path rather than from a precomputed prefix list.
 */
public final class SecretPaths implements Predicate<ConfigPath> {

  private final ObjectSchema root;
  private final TypeAdapterLookup adapters;

  /** Creates a predicate for documents of {@code root}. */
  public SecretPaths(ObjectSchema root, TypeAdapterLookup adapters) {
    this.root = root;
    this.adapters = adapters;
  }

  @Override
  public boolean test(ConfigPath path) {
    ObjectSchema schema = root;
    Type container = null;
    for (String segment : path.segments()) {
      if (schema != null) {
        ConfigProperty property = find(schema, segment);
        if (property == null) {
          return false;
        }
        if (property.secret()) {
          return true;
        }
        schema = sectionOf(property.adapter());
        container = schema == null ? property.type() : null;
        continue;
      }
      // This segment is a map key or a sequence index of `container`; step to its element type.
      Type element = elementOf(container);
      if (element == null) {
        return false;
      }
      schema = sectionOf(adapters.find(element).orElse(null));
      container = schema == null ? element : null;
    }
    return false;
  }

  private static ConfigProperty find(ObjectSchema schema, String key) {
    for (ConfigProperty property : schema.properties()) {
      if (property.key().equals(key)) {
        return property;
      }
    }
    return null;
  }

  private static ObjectSchema sectionOf(TypeAdapter<?> adapter) {
    return adapter instanceof ObjectAdapter section ? section.schema() : null;
  }

  private static Type elementOf(Type container) {
    if (!(container instanceof ParameterizedType parameterized)) {
      return null;
    }
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
    if (raw == Map.class) {
      return arguments[1];
    }
    if (raw == List.class || raw == Set.class) {
      return arguments[0];
    }
    return null;
  }
}
