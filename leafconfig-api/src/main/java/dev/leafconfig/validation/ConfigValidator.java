package dev.leafconfig.validation;

/**
 * Programmatic validation of a fully decoded configuration, for cross-field rules that annotations
 * cannot express.
 *
 * <p>Validators run after annotation validation and before the instance is published. They must not
 * mutate the instance and must not depend on platform classes.
 *
 * @param <T> configuration type
 */
public interface ConfigValidator<T> {

  /** Inspects {@code config} and reports problems through {@code context}. */
  void validate(T config, ValidationContext context);
}
