package dev.leafconfig.yaml.internal.schema;

import dev.leafconfig.ConfigModelException;

/**
 * Discovers schema metadata. The reflection implementation is the only one for now; a generated
 * implementation must produce identical {@link ObjectSchema} instances.
 */
public interface SchemaFactory {

  /**
   * Discovers the schema of a root type annotated with {@code @ConfigFile}.
   *
   * @throws ConfigModelException when the type is not a valid configuration model
   */
  ConfigSchema create(Class<?> type);

  /**
   * Discovers the schema of a nested object type.
   *
   * @throws ConfigModelException when the type is not a valid configuration model
   */
  ObjectSchema objectSchema(Class<?> type);
}
