package dev.leafconfig.adapter;

import java.lang.reflect.Type;
import java.util.Optional;

/**
 * Creates adapters for families of types, for example every {@code List<T>} or every enum.
 *
 * <p>Factories are consulted after exact-type adapters, in registration order. The returned adapter
 * is cached per manager and type, so factories may do reflective work.
 */
public interface TypeAdapterFactory {

  /**
   * Returns an adapter for {@code type}, or empty when this factory does not handle it.
   *
   * @param type requested type including generic arguments
   * @param lookup resolves adapters for component types
   */
  Optional<TypeAdapter<?>> create(Type type, TypeAdapterLookup lookup);
}
