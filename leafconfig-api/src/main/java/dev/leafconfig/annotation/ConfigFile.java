package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names the default YAML file of a root configuration type.
 *
 * <p>The value is a file name (for example {@code config.yml}) or a relative path with {@code /}
 * separators. It is always resolved inside the manager's base directory; absolute paths and path
 * traversal are rejected at load time.
 *
 * <p>The annotation is required for {@code load(Class)}. A type that is only loaded with an
 * explicit file name, such as one class per locale file, may omit it.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile {
  /** Relative file name inside the base directory. */
  String value();
}
