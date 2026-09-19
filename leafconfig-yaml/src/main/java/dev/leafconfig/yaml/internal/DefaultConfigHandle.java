package dev.leafconfig.yaml.internal;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Handle with a volatile snapshot, a reload lock that serializes concurrent reloads, and listeners
 * that run only after a successful publication.
 */
public final class DefaultConfigHandle<T> implements ConfigHandle<T> {

  private final Class<T> type;
  private final Path file;
  private final ConfigSchema schema;
  private final ConfigLoader loader;
  private final ReentrantLock reloadLock = new ReentrantLock();
  private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();
  private volatile T current;
  private volatile boolean closed;

  /** Creates a handle around an already loaded snapshot. */
  public DefaultConfigHandle(
      Class<T> type, Path file, ConfigSchema schema, ConfigLoader loader, T initial) {
    this.type = type;
    this.file = file;
    this.schema = schema;
    this.loader = loader;
    this.current = Objects.requireNonNull(initial, "initial");
  }

  @Override
  public T get() {
    return current;
  }

  @Override
  public Class<T> type() {
    return type;
  }

  @Override
  public Path file() {
    return file;
  }

  @Override
  public ReloadResult<T> reload() {
    if (closed) {
      throw new IllegalStateException("configuration manager is closed");
    }
    reloadLock.lock();
    try {
      ConfigLoader.Outcome<T> outcome;
      try {
        outcome = loader.load(schema, type, file);
      } catch (LoadFailure failure) {
        return ReloadResult.failure(current, failure.diagnostics());
      }
      T snapshot = outcome.instance();
      current = snapshot;
      List<ConfigDiagnostic> diagnostics = new ArrayList<>(outcome.warnings());
      for (Consumer<? super T> listener : listeners) {
        try {
          listener.accept(snapshot);
        } catch (RuntimeException e) {
          diagnostics.add(
              ConfigDiagnostic.warning(
                  ConfigPath.root(),
                  DiagnosticCodes.LISTENER_FAILED,
                  "reload listener " + listener.getClass().getName() + " threw " + e));
        }
      }
      return ReloadResult.success(snapshot, diagnostics);
    } finally {
      reloadLock.unlock();
    }
  }

  @Override
  public void onReload(Consumer<? super T> listener) {
    listeners.add(Objects.requireNonNull(listener, "listener"));
  }

  /** Marks the handle closed and drops listeners. Idempotent. */
  public void close() {
    closed = true;
    listeners.clear();
  }
}
