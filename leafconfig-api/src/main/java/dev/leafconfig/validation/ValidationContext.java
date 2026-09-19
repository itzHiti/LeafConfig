package dev.leafconfig.validation;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;

/** Collects validation problems. */
public interface ValidationContext {

  /** Reports an error with a custom code. */
  void error(ConfigPath path, String code, String message);

  /** Reports an error with code {@link DiagnosticCodes#VALIDATION_FAILED}. */
  default void error(ConfigPath path, String message) {
    error(path, DiagnosticCodes.VALIDATION_FAILED, message);
  }

  /** Reports a warning; warnings never block publication. */
  void warning(ConfigPath path, String code, String message);
}
