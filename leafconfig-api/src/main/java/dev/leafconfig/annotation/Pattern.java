package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the whole value to match a {@link java.util.regex.Pattern} regular expression. Valid
 * only on {@link CharSequence} fields; the pattern is compiled during schema discovery so an
 * invalid expression fails early.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pattern {
  /** Regular expression matched against the entire value. */
  String value();
}
