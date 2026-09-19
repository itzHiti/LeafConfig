package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a root configuration type and names the YAML file it is stored in.
 *
 * <p>The value is a file name (for example {@code config.yml}) or a relative path with {@code /}
 * separators. It is always resolved inside the manager's base directory; absolute paths and path
 * traversal are rejected at load time.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigFile {
  /** Relative file name inside the base directory. */
  String value();
}
