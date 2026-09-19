package dev.leafconfig.yaml.internal.decode;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.validation.ValidationContext;

/** {@link ValidationContext} backed by a {@link DiagnosticCollector}. */
public final class CollectingValidationContext implements ValidationContext {

  private final DiagnosticCollector collector;

  /** Creates a context reporting into {@code collector}. */
  public CollectingValidationContext(DiagnosticCollector collector) {
    this.collector = collector;
  }

  @Override
  public void error(ConfigPath path, String code, String message) {
    collector.error(path, code, message, null);
  }

  @Override
  public void warning(ConfigPath path, String code, String message) {
    collector.warning(path, code, message, null);
  }
}
