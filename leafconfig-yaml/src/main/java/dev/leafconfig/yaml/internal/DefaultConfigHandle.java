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

  /** Instance and the warnings of the load that produced it, published together. */
  private record Snapshot<T>(T value, List<ConfigDiagnostic> warnings) {}

  private final Class<T> type;
  private final Path file;
  private final ConfigSchema schema;
  private final ConfigLoader loader;
  private final ReentrantLock reloadLock = new ReentrantLock();
  private final List<Consumer<? super T>> listeners = new CopyOnWriteArrayList<>();
  private volatile Snapshot<T> current;
  private volatile boolean closed;

  /** Creates a handle around an already loaded snapshot. */
  public DefaultConfigHandle(
      Class<T> type,
      Path file,
      ConfigSchema schema,
      ConfigLoader loader,
      ConfigLoader.Outcome<T> initial) {
    this.type = type;
    this.file = file;
    this.schema = schema;
    this.loader = loader;
    this.current = snapshot(initial);
  }

  private static <T> Snapshot<T> snapshot(ConfigLoader.Outcome<T> outcome) {
    return new Snapshot<>(
        Objects.requireNonNull(outcome.instance(), "instance"), List.copyOf(outcome.warnings()));
  }

  @Override
  public T get() {
    return current.value();
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
  public List<ConfigDiagnostic> warnings() {
    return current.warnings();
  }

  @Override
  public ReloadResult<T> reload() {
    reloadLock.lock();
    try {
      // Checked under the lock so a close() that acquired the lock first is always observed.
      if (closed) {
        throw new IllegalStateException("configuration handle is closed");
      }
      ConfigLoader.Outcome<T> outcome;
      try {
        outcome = loader.load(schema, type, file);
      } catch (LoadFailure failure) {
        return ReloadResult.failure(current.value(), failure.diagnostics());
      }
      Snapshot<T> snapshot = snapshot(outcome);
      current = snapshot;
      List<ConfigDiagnostic> diagnostics = new ArrayList<>(snapshot.warnings());
      for (Consumer<? super T> listener : listeners) {
        try {
          listener.accept(snapshot.value());
        } catch (RuntimeException e) {
          diagnostics.add(
              ConfigDiagnostic.warning(
                  ConfigPath.root(),
                  DiagnosticCodes.LISTENER_FAILED,
                  "reload listener " + listener.getClass().getName() + " threw " + e));
        }
      }
      return ReloadResult.success(snapshot.value(), diagnostics);
    } finally {
      reloadLock.unlock();
    }
  }

  @Override
  public void onReload(Consumer<? super T> listener) {
    listeners.add(Objects.requireNonNull(listener, "listener"));
  }

  /** Marks the handle closed and drops listeners after any in-flight reload. Idempotent. */
  public void close() {
    reloadLock.lock();
    try {
      closed = true;
      listeners.clear();
    } finally {
      reloadLock.unlock();
    }
  }
}
