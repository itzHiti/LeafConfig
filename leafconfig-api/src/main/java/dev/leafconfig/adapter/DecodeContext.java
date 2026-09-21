package dev.leafconfig.adapter;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.node.ConfigNode;
import java.lang.reflect.Type;

/**
 * Framework services available while decoding one node.
 *
 * <p>Composite adapters (collections, maps, nested objects) delegate to {@link #decodeChild} so
 * that failures inside elements are aggregated instead of aborting the whole load.
 */
public interface DecodeContext {

  /** Logical path of the node being decoded. */
  ConfigPath path();

  /**
   * Decodes a child node with the adapter resolved for {@code type}.
   *
   * <p>A {@code null} node value (YAML null) or a decode failure is recorded as an error diagnostic
   * and {@code null} is returned; the caller should keep going so all errors are reported. Because
   * the whole load fails when any error was recorded, the {@code null} placeholder never reaches
   * user code.
   *
   * @param segment path segment for diagnostics, a key or {@code [index]}
   * @param type target type, generic information included
   * @param node child node
   * @return decoded value or {@code null} on failure
   */
  Object decodeChild(String segment, Type type, ConfigNode node);

  /**
   * Decodes the element at {@code index} of a sequence; equivalent to {@link #decodeChild} with the
   * {@code [index]} path segment used by {@link ConfigPath#index(int)}.
   */
  default Object decodeElement(int index, Type type, ConfigNode node) {
    return decodeChild("[" + index + "]", type, node);
  }
}
