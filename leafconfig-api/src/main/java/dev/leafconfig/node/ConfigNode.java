package dev.leafconfig.node;

/**
 * Backend-neutral document value handed to {@link dev.leafconfig.adapter.TypeAdapter adapters}.
 *
 * <p>Nodes are immutable. The YAML backend converts its own syntax tree into this model for
 * decoding and converts adapter output back for rendering, so adapters never see YAML classes.
 */
public sealed interface ConfigNode permits ScalarNode, SequenceNode, MappingNode, NullNode {

  /** Position in the source document, or {@code null} for nodes created in memory. */
  SourceLocation source();
}
