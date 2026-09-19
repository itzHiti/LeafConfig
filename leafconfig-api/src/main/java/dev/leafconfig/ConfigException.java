package dev.leafconfig;

import java.util.List;

/** Base class for LeafConfig failures. Carries the structured diagnostics that caused it. */
public class ConfigException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<ConfigDiagnostic> diagnostics;

  /**
   * Creates an exception.
   *
   * @param message summary message
   * @param diagnostics diagnostics, copied defensively
   */
  public ConfigException(String message, List<ConfigDiagnostic> diagnostics) {
    super(message);
    this.diagnostics = List.copyOf(diagnostics);
  }

  /** Returns the unmodifiable diagnostics. */
  public List<ConfigDiagnostic> diagnostics() {
    return diagnostics;
  }
}
