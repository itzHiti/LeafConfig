package dev.leafconfig.migration;

import dev.leafconfig.node.ConfigNode;
import java.util.Optional;

/**
 * Mutable view of one configuration document handed to a {@link Migration}.
 *
 * <p>Paths are dotted key paths such as {@code database.host}; every segment names a mapping key.
 * Sequence elements cannot be addressed. Comments, ordering and unknown keys of the underlying
 * document are preserved by every operation; a renamed entry keeps its comments and position.
 *
 * <p>Values passed to {@link #set} and {@link #setIfMissing} are either {@link ConfigNode}
 * instances or plain values: {@code String}, {@code Boolean}, {@code Integer}, {@code Long}, {@code
 * java.math.BigInteger}, {@code Float}, {@code Double}, {@code java.math.BigDecimal}, or {@code
 * null}. Other types are rejected with {@link IllegalArgumentException}; encode them through the
 * node factories on {@link dev.leafconfig.node.ScalarNode} or build a {@link
 * dev.leafconfig.node.MappingNode} explicitly.
 *
 * <p>Implementations are not thread-safe and are only valid during the migration step that received
 * them.
 */
public interface ConfigDocument {

  /**
   * Returns the value at {@code path}, or empty when any segment is missing or an intermediate
   * segment is not a mapping.
   */
  Optional<ConfigNode> get(String path);

  /**
   * Returns {@code true} when {@code path} exists, even if its value is YAML {@code null}. A path
   * through a non-mapping value does not exist.
   */
  boolean contains(String path);

  /**
   * Sets {@code path} to {@code value}, creating intermediate mappings and replacing any existing
   * value. An existing entry keeps its comments and position.
   *
   * @throws IllegalArgumentException when an intermediate segment exists but is not a mapping, or
   *     the value type is unsupported
   */
  void set(String path, Object value);

  /**
   * Sets {@code path} to {@code value} only when {@code path} does not exist.
   *
   * @return {@code true} when the value was set
   */
  boolean setIfMissing(String path, Object value);

  /**
   * Removes the entry at {@code path} together with its comments.
   *
   * @return {@code true} when an entry was removed
   */
  boolean remove(String path);

  /**
   * Moves the entry at {@code from} to {@code to}, keeping its value and comments. Within one
   * mapping the entry also keeps its position; across mappings it is appended to the target
   * mapping, whose missing parents are created. The source mapping is left in place even if it
   * becomes empty; call {@link #remove} to drop it.
   *
   * @return {@code false} when {@code from} does not exist
   * @throws IllegalArgumentException when {@code to} already exists, lies inside {@code from}, or a
   *     parent of {@code to} is not a mapping
   */
  boolean rename(String from, String to);

  /** Returns an immutable snapshot of the whole document. */
  ConfigNode root();
}
