package dev.leafconfig.yaml.internal.schema;

/**
 * Reads and writes one persisted property of a configuration instance. Hides the reflection (or,
 * later, generated code) behind the schema so runtime code never touches {@code Field}.
 */
public interface PropertyAccessor {

  /** Returns the current value, possibly {@code null}. */
  Object get(Object instance);

  /** Sets the value; {@code null} is only passed for reference types. */
  void set(Object instance, Object value);
}
