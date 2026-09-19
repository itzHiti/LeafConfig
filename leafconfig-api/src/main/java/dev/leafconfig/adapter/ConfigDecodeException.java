package dev.leafconfig.adapter;

import java.util.Objects;

/**
 * Thrown by a {@link TypeAdapter} when a node cannot be decoded. The framework converts it into a
 * {@link dev.leafconfig.ConfigDiagnostic} with the current path and node position.
 */
public final class ConfigDecodeException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * Creates the exception.
   *
   * @param code stable code, normally one of {@link dev.leafconfig.DiagnosticCodes}
   * @param message human-readable message
   */
  public ConfigDecodeException(String code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  /** Returns the diagnostic code. */
  public String code() {
    return code;
  }
}
