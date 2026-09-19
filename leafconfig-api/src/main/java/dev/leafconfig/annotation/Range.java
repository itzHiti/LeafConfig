package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a numeric field to the inclusive range {@code [min, max]}.
 *
 * <p>Valid only on primitive numeric fields, their wrappers, {@link java.math.BigInteger} and
 * {@link java.math.BigDecimal}. Comparison is exact: values are compared as decimals, never through
 * a lossy narrowing conversion.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Range {
  /** Inclusive lower bound. */
  long min() default Long.MIN_VALUE;

  /** Inclusive upper bound. */
  long max() default Long.MAX_VALUE;
}
