package dev.leafconfig.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the schema version of a root configuration type and enables migrations for it.
 *
 * <p>The version is stored in the file under the top-level key {@link #KEY} ({@code
 * config-version}). A file whose stored version is lower than the declared one is migrated step by
 * step with the migrations registered on the manager; a file with a higher version is rejected. A
 * versioned file without the key is treated as version {@code 1} and a {@link
 * dev.leafconfig.DiagnosticCodes#VERSION_ASSUMED} warning is reported.
 *
 * <p>The value must be at least {@code 1}. A model must not declare its own property under the
 * reserved key.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigVersion {

  /** Reserved top-level key that stores the version. */
  String KEY = "config-version";

  /** Current schema version, at least {@code 1}. */
  int value();
}
