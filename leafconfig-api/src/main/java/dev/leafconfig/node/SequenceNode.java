package dev.leafconfig.node;

import java.util.List;

/**
 * Ordered sequence of nodes.
 *
 * @param elements unmodifiable element list
 * @param source position, or {@code null} for in-memory nodes
 */
public record SequenceNode(List<ConfigNode> elements, SourceLocation source) implements ConfigNode {

  /** Copies the element list defensively. */
  public SequenceNode {
    elements = List.copyOf(elements);
  }

  /** Creates an in-memory sequence. */
  public static SequenceNode of(List<ConfigNode> elements) {
    return new SequenceNode(elements, null);
  }
}
