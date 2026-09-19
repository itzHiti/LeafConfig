package dev.leafconfig.node;

/**
 * Explicit YAML {@code null} (including {@code ~} and an empty value).
 *
 * @param source position, or {@code null} for in-memory nodes
 */
public record NullNode(SourceLocation source) implements ConfigNode {

  private static final NullNode INSTANCE = new NullNode(null);

  /** Returns an in-memory null node. */
  public static NullNode instance() {
    return INSTANCE;
  }
}
