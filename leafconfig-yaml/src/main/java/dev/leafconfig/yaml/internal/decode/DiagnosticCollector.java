package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.Severity;
import dev.leafconfig.node.SourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Ordered, mutable list of diagnostics collected during one load. Not thread-safe. */
public final class DiagnosticCollector {

  private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
  private int errors;

  /** Records an error at {@code path}, with position when {@code source} is known. */
  public void error(ConfigPath path, String code, String message, SourceLocation source) {
    add(Severity.ERROR, path, code, message, source);
  }

  /** Records a warning at {@code path}. */
  public void warning(ConfigPath path, String code, String message, SourceLocation source) {
    add(Severity.WARNING, path, code, message, source);
  }

  private void add(
      Severity severity, ConfigPath path, String code, String message, SourceLocation source) {
    Integer line = source == null ? null : source.line();
    Integer column = source == null ? null : source.column();
    diagnostics.add(new ConfigDiagnostic(severity, path, code, message, line, column));
    if (severity == Severity.ERROR) {
      errors++;
    }
  }

  /**
   * Returns {@code true} when an error was recorded at {@code path} or below it, meaning the value
   * there was not decoded and still holds its Java default.
   */
  public boolean hasErrorUnder(ConfigPath path) {
    for (ConfigDiagnostic diagnostic : diagnostics) {
      if (diagnostic.isError() && startsWith(diagnostic.path(), path)) {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWith(ConfigPath path, ConfigPath prefix) {
    return path.segments().size() >= prefix.segments().size()
        && path.segments().subList(0, prefix.segments().size()).equals(prefix.segments());
  }

  /** Returns {@code true} when at least one error was recorded. */
  public boolean hasErrors() {
    return errors > 0;
  }

  /** Returns the number of errors recorded so far. */
  public int errorCount() {
    return errors;
  }

  /** Returns a snapshot of all diagnostics in recording order. */
  public List<ConfigDiagnostic> diagnostics() {
    return List.copyOf(diagnostics);
  }
}
