package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the YAML key generated from the field name.
 *
 * <p>The value is exactly one YAML path segment and must match {@code [A-Za-z0-9_-]+}. Dots,
 * whitespace and YAML indicator characters are rejected during schema discovery. Nested paths are
 * expressed through nested Java objects, not dotted keys.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Key {
  /** The YAML key for the annotated field. */
  String value();
}
