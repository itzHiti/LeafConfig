package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Enums parse case-insensitively and render the exact constant name. Enums whose constants differ
 * only by case are rejected as ambiguous.
 */
public final class EnumAdapterFactory implements TypeAdapterFactory {

  @Override
  public Optional<TypeAdapter<?>> create(Type type, TypeAdapterLookup lookup) {
    if (type instanceof Class<?> c && c.isEnum()) {
      return Optional.of(adapter(c));
    }
    return Optional.empty();
  }

  private static <E extends Enum<E>> TypeAdapter<E> adapter(Class<?> raw) {
    @SuppressWarnings("unchecked") // guarded by isEnum()
    Class<E> type = (Class<E>) raw;
    E[] constants = type.getEnumConstants();
    Map<String, E> byLowerName = new HashMap<>();
    for (E constant : constants) {
      E previous = byLowerName.put(constant.name().toLowerCase(Locale.ROOT), constant);
      if (previous != null) {
        throw new ConfigModelException(
            type,
            List.of(
                ConfigDiagnostic.error(
                    ConfigPath.root(),
                    DiagnosticCodes.INVALID_MODEL,
                    "enum constants '"
                        + previous.name()
                        + "' and '"
                        + constant.name()
                        + "' are ambiguous under case-insensitive parsing")));
      }
    }
    String expected = "one of " + Arrays.toString(constants);
    return new TypeAdapter<>() {
      @Override
      public E decode(ConfigNode node, DecodeContext context) {
        String text = Scalars.requireScalar(node, expected).value();
        E constant = byLowerName.get(text.strip().toLowerCase(Locale.ROOT));
        if (constant == null) {
          throw Scalars.invalid(expected, text);
        }
        return constant;
      }

      @Override
      public ConfigNode encode(E value, EncodeContext context) {
        return ScalarNode.ofString(value.name());
      }
    };
  }
}
