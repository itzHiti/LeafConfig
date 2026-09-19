package dev.leafconfig.yaml.internal.schema;

import java.util.List;
import java.util.function.Supplier;

/**
 * Immutable metadata of a configuration object type: how to instantiate it and which properties it
 * persists, in declaration order.
 *
 * @param type Java type
 * @param factory creates a fresh instance holding field defaults
 * @param comments type-level comment lines
 * @param properties persisted properties in declaration order
 */
public record ObjectSchema(
    Class<?> type,
    Supplier<Object> factory,
    List<String> comments,
    List<ConfigProperty> properties) {

  /** Copies the lists. */
  public ObjectSchema {
    comments = List.copyOf(comments);
    properties = List.copyOf(properties);
  }

  /** Creates a new instance with Java field defaults. */
  public Object instantiate() {
    return factory.get();
  }
}
