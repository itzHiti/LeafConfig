package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches comment lines to a field or, when placed on a root type, to the top of the generated
 * file.
 *
 * <p>Each array element becomes one {@code # } line. Comments are written when a key is generated
 * and when an existing key has no leading comment; user-written comments are never replaced.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface Comment {
  /** Comment lines without the leading {@code #}. */
  String[] value();
}
