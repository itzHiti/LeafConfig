package dev.leafconfig.adapter;

import dev.leafconfig.node.ConfigNode;

/**
 * Converts between a Java value and a backend-neutral {@link ConfigNode}.
 *
 * <p>Adapters must be stateless or thread-safe, must not log, and must not catch fatal JVM errors.
 * Null handling is done by the framework: {@link #decode} is never called with a {@link
 * dev.leafconfig.node.NullNode} and {@link #encode} is never called with {@code null}.
 *
 * @param <T> Java type
 */
public interface TypeAdapter<T> {

  /**
   * Decodes a node.
   *
   * @throws ConfigDecodeException when the node cannot represent a {@code T}; the framework
   *     attaches the path and source position
   */
  T decode(ConfigNode node, DecodeContext context);

  /** Encodes a non-null value. */
  ConfigNode encode(T value, EncodeContext context);
}
