package dev.leafconfig;

import java.util.List;

/** Text rendering of diagnostics for logs and console output. */
public final class ConfigDiagnostics {

  private ConfigDiagnostics() {}

  /**
   * Renders a header line naming the file followed by one indented line per diagnostic.
   *
   * @param file file description shown in the header, typically a relative path
   * @param diagnostics diagnostics to render, in order
   * @return multi-line text without a trailing newline
   */
  public static String render(String file, List<ConfigDiagnostic> diagnostics) {
    StringBuilder out = new StringBuilder("Invalid configuration: ").append(file);
    for (ConfigDiagnostic diagnostic : diagnostics) {
      out.append(System.lineSeparator()).append("  - ").append(diagnostic);
    }
    return out.toString();
  }
}
