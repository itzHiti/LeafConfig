package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.NullNode;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code Optional<T>} adapter for scalar-like {@code T}: YAML {@code null} is {@link
 * Optional#empty()}, any other value is decoded by the adapter of {@code T} at the same path.
 *
 * <p>Nested sections, collections, maps and nested optionals are refused: they already have their
 * own null or empty semantics, and a second way to say "absent" would be ambiguous.
 */
public final class OptionalAdapterFactory implements TypeAdapterFactory {

  @Override
  public Optional<TypeAdapter<?>> create(Type type, TypeAdapterLookup lookup) {
    if (!(type instanceof ParameterizedType parameterized)
        || parameterized.getRawType() != Optional.class) {
      return Optional.empty();
    }
    Type element = parameterized.getActualTypeArguments()[0];
    if (!Types.isConcrete(element) || !isScalarLike(element)) {
      return Optional.empty();
    }
    Optional<TypeAdapter<?>> found = lookup.find(element);
    if (found.isEmpty() || found.get() instanceof ObjectAdapter) {
      return Optional.empty();
    }
    @SuppressWarnings("unchecked") // the registry resolved this adapter for exactly this type
    TypeAdapter<Object> adapter = (TypeAdapter<Object>) found.get();
    return Optional.of(
        new TypeAdapter<Optional<Object>>() {
          @Override
          public Optional<Object> decode(ConfigNode node, DecodeContext context) {
            return node instanceof NullNode
                ? Optional.empty()
                : Optional.of(adapter.decode(node, context));
          }

          @Override
          public ConfigNode encode(Optional<Object> value, EncodeContext context) {
            return value.isEmpty() ? NullNode.instance() : adapter.encode(value.get(), context);
          }
        });
  }

  private static boolean isScalarLike(Type element) {
    Type raw = element instanceof ParameterizedType p ? p.getRawType() : element;
    return raw != List.class && raw != Set.class && raw != Map.class && raw != Optional.class;
  }
}
