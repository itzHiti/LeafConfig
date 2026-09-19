package dev.leafconfig.yaml.internal;

import dev.leafconfig.ConfigDiagnostic;
import java.util.List;

/**
 * Internal signal that a load did not produce a usable instance. Converted into {@link
 * dev.leafconfig.ConfigLoadException} on first load and into a failed {@link
 * dev.leafconfig.ReloadResult} on reload.
 */
public final class LoadFailure extends Exception {

  private static final long serialVersionUID = 1L;

  private final transient List<ConfigDiagnostic> diagnostics;

  /** Creates a failure carrying {@code diagnostics}. */
  public LoadFailure(List<ConfigDiagnostic> diagnostics) {
    super(
        diagnostics.isEmpty() ? "load failed" : diagnostics.get(0).toString(), null, false, false);
    this.diagnostics = List.copyOf(diagnostics);
  }

  /** Returns the diagnostics, errors and warnings in recording order. */
  public List<ConfigDiagnostic> diagnostics() {
    return diagnostics;
  }
}
