package dev.leafconfig.node;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered mapping from string keys to nodes.
 *
 * @param entries unmodifiable, insertion-ordered entries
 * @param source position, or {@code null} for in-memory nodes
 */
public record MappingNode(Map<String, ConfigNode> entries, SourceLocation source)
    implements ConfigNode {

  /** Copies the entries defensively while preserving their order. */
  public MappingNode {
    entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  /** Creates an in-memory mapping. */
  public static MappingNode of(Map<String, ConfigNode> entries) {
    return new MappingNode(entries, null);
  }
}
