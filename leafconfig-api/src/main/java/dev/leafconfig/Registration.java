package dev.leafconfig;

/**
 * Handle to something registered with LeafConfig, such as a reload listener. Closing it undoes the
 * registration.
 *
 * <p>{@link #close()} is idempotent, thread-safe and throws no checked exception, so it can be used
 * in try-with-resources or stored and closed later. Implementations are provided by LeafConfig.
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

  /** Removes the registration. Further calls have no effect. */
  @Override
  void close();
}
