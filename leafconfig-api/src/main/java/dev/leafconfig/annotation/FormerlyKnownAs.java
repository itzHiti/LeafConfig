package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names keys this property was stored under in earlier releases, so that a simple rename does not
 * lose the administrator's value.
 *
 * <p>When the current key is absent from its mapping and exactly one former key is present, the
 * existing entry is renamed in place: value, position and comments are kept. If the current key and
 * a former key are both present, or several former keys are present, the load fails with {@link
 * dev.leafconfig.DiagnosticCodes#RENAME_CONFLICT} rather than guessing. Each former key is a single
 * segment inside the same mapping; moves across sections need a versioned migration.
 *
 * <p>Renames are applied after versioned migrations and before decoding. They do not require {@link
 * ConfigVersion}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FormerlyKnownAs {
  /** Former keys, most recent first. */
  String[] value();
}
