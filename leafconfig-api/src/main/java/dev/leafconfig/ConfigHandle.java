package dev.leafconfig;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Stable reference to one loaded configuration file.
 *
 * <p>Implementations are thread-safe. {@link #get()} never blocks and always returns the latest
 * successfully published snapshot; snapshots are never mutated after publication.
 *
 * @param <T> configuration type
 */
public interface ConfigHandle<T> {

  /** Returns the current snapshot. Never {@code null}. */
  T get();

  /** Returns the configuration type. */
  Class<T> type();

  /** Returns the resolved file path. */
  Path file();

  /**
   * Re-reads the file and publishes a new snapshot only if parsing, decoding, validation and any
   * required merge write all succeed. On failure the previous snapshot stays active. Concurrent
   * reloads of the same handle are serialized.
   */
  ReloadResult<T> reload();

  /**
   * Registers a listener invoked after every successful reload with the new snapshot. Listeners run
   * on the reloading thread. A listener that throws is reported as a {@link
   * DiagnosticCodes#LISTENER_FAILED} warning; it never rolls back the publication.
   */
  void onReload(Consumer<? super T> listener);
}
