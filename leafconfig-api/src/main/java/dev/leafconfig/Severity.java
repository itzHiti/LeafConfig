package dev.leafconfig;

/** Diagnostic severity. Any {@link #ERROR} prevents a configuration from being published. */
public enum Severity {
  /** Informational; the configuration is still usable. */
  WARNING,
  /** The configuration cannot be used. */
  ERROR
}
