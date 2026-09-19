package dev.leafconfig;

import java.util.Objects;

/**
 * One structured problem found while discovering a model, parsing, decoding or validating a
 * configuration.
 *
 * @param severity severity
 * @param path logical key path; {@link ConfigPath#root()} for document-level problems
 * @param code stable machine-readable code, see {@link DiagnosticCodes}
 * @param message human-readable explanation
 * @param line one-based source line, or {@code null} when unknown
 * @param column one-based source column, or {@code null} when unknown
 */
public record ConfigDiagnostic(
    Severity severity, ConfigPath path, String code, String message, Integer line, Integer column) {

  /** Validates required components. */
  public ConfigDiagnostic {
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
  }

  /** Creates an error without source position. */
  public static ConfigDiagnostic error(ConfigPath path, String code, String message) {
    return new ConfigDiagnostic(Severity.ERROR, path, code, message, null, null);
  }

  /** Creates a warning without source position. */
  public static ConfigDiagnostic warning(ConfigPath path, String code, String message) {
    return new ConfigDiagnostic(Severity.WARNING, path, code, message, null, null);
  }

  /** Returns a copy with the given source position. */
  public ConfigDiagnostic at(Integer newLine, Integer newColumn) {
    return new ConfigDiagnostic(severity, path, code, message, newLine, newColumn);
  }

  /** Returns {@code true} for {@link Severity#ERROR}. */
  public boolean isError() {
    return severity == Severity.ERROR;
  }

  /** Renders {@code path [CODE]: message (line N)}. */
  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    out.append(path.isRoot() ? "(document)" : path.toString());
    out.append(" [").append(code).append("]: ").append(message);
    if (line != null) {
      out.append(" (line ").append(line);
      if (column != null) {
        out.append(", column ").append(column);
      }
      out.append(')');
    }
    return out.toString();
  }
}
