package dev.leafconfig.yaml.internal.codec;

import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.adapter.TypeAdapterFactory;
import dev.leafconfig.adapter.TypeAdapterLookup;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.SequenceNode;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@code List<E>} and {@code Set<E>} adapters. Decoded collections are unmodifiable and keep
 * document order; duplicate set elements collapse to the first occurrence.
 */
public final class CollectionAdapterFactory implements TypeAdapterFactory {

  @Override
  public Optional<TypeAdapter<?>> create(Type type, TypeAdapterLookup lookup) {
    if (!(type instanceof ParameterizedType parameterized)) {
      return Optional.empty();
    }
    Type raw = parameterized.getRawType();
    if (raw != List.class && raw != Set.class) {
      return Optional.empty();
    }
    Type element = parameterized.getActualTypeArguments()[0];
    if (!Types.isConcrete(element) || lookup.find(element).isEmpty()) {
      return Optional.empty();
    }
    boolean set = raw == Set.class;
    return Optional.of(
        new TypeAdapter<Collection<Object>>() {
          @Override
          public Collection<Object> decode(ConfigNode node, DecodeContext context) {
            if (!(node instanceof SequenceNode sequence)) {
              throw Scalars.mismatch(set ? "sequence (set)" : "sequence (list)", node);
            }
            Collection<Object> result = set ? new LinkedHashSet<>() : new ArrayList<>();
            int index = 0;
            for (ConfigNode child : sequence.elements()) {
              result.add(context.decodeElement(index, element, child));
              index++;
            }
            return set
                ? Collections.unmodifiableSet((Set<Object>) result)
                : Collections.unmodifiableList((List<Object>) result);
          }

          @Override
          public ConfigNode encode(Collection<Object> value, EncodeContext context) {
            List<ConfigNode> nodes = new ArrayList<>(value.size());
            for (Object item : value) {
              nodes.add(context.encodeChild(element, item));
            }
            return SequenceNode.of(nodes);
          }
        });
  }
}
