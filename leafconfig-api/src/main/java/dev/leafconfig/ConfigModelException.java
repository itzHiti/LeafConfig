package dev.leafconfig;

import java.util.List;

/**
 * The Java configuration type cannot be used: unsupported field, duplicate key, invalid annotation
 * placement, or a recursive object graph. This is a programming error, not a user-data error.
 */
public final class ConfigModelException extends ConfigException {

  private static final long serialVersionUID = 1L;

  /** Creates the exception for the given type. */
  public ConfigModelException(Class<?> type, List<ConfigDiagnostic> diagnostics) {
    super(ConfigDiagnostics.render(type.getName(), diagnostics), diagnostics);
  }
}
