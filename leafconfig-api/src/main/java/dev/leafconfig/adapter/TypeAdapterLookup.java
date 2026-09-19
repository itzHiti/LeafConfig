package dev.leafconfig.adapter;

import java.lang.reflect.Type;
import java.util.Optional;

/** Resolves the adapter for a type, following the manager's precedence rules. */
public interface TypeAdapterLookup {

  /** Returns the adapter for {@code type}, or empty when no adapter or factory supports it. */
  Optional<TypeAdapter<?>> find(Type type);
}
