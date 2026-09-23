package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.Severity;
import dev.leafconfig.node.SourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ordered, mutable list of diagnostics collected during one load. Not thread-safe.
 *
 * <p>Every diagnostic passes through this class, so it is also where {@code @Secret} redaction
 * happens: whoever produced the message, an adapter, a validator or the framework, its text is
 * replaced when the path is secret.
 */
public final class DiagnosticCollector {

  /** Message used instead of the original text at secret paths. */
  public static final String REDACTED = "details hidden because the key is marked @Secret";

  private final List<ConfigDiagnostic> diagnostics = new ArrayList<>();
  private final Predicate<ConfigPath> secret;
  private int errors;

  /** Creates a collector without secret paths. */
  public DiagnosticCollector() {
    this(path -> false);
  }

  /** Creates a collector that redacts messages at paths matching {@code secret}. */
  public DiagnosticCollector(Predicate<ConfigPath> secret) {
    this.secret = secret;
  }

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
    String text = secret.test(path) ? REDACTED : message;
    diagnostics.add(new ConfigDiagnostic(severity, path, code, text, line, column));
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
