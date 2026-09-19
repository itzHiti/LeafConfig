package dev.leafconfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Initial load of a configuration failed. The file on disk was left untouched and no configuration
 * instance was published.
 */
public final class ConfigLoadException extends ConfigException {

  private static final long serialVersionUID = 1L;

  private final transient Path file;

  /** Creates the exception for the given file. */
  public ConfigLoadException(Path file, List<ConfigDiagnostic> diagnostics) {
    super(ConfigDiagnostics.render(file.toString(), diagnostics), diagnostics);
    this.file = file;
  }

  /** Returns the file that failed to load. */
  public Path file() {
    return file;
  }
}
