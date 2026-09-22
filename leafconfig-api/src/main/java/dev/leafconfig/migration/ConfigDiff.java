package dev.leafconfig.migration;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.SequenceNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Semantic difference between two documents, as a flat list of leaf changes.
 *
 * <p>Mappings are compared key by key; scalars, nulls and sequences are leaves. Comments, key order
 * and scalar styles are not part of the comparison. Changes are listed in the order of the {@code
 * after} document, followed by removals in the order of the {@code before} document.
 *
 * @param changes unmodifiable changes; empty when both documents are semantically equal
 */
public record ConfigDiff(List<Change> changes) {

  /** Copies the list defensively. */
  public ConfigDiff {
    changes = List.copyOf(changes);
  }

  /** Kind of one change. */
  public enum Kind {
    /** Present only in the {@code after} document. */
    ADDED,
    /** Present only in the {@code before} document. */
    REMOVED,
    /** Present in both with different values. */
    CHANGED
  }

  /**
   * One leaf change.
   *
   * @param kind kind
   * @param path path of the leaf
   * @param before value before, {@code null} for {@link Kind#ADDED}
   * @param after value after, {@code null} for {@link Kind#REMOVED}
   */
  public record Change(Kind kind, ConfigPath path, ConfigNode before, ConfigNode after) {

    /** Validates that the sides match the kind. */
    public Change {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(path, "path");
      if ((kind == Kind.ADDED) != (before == null) || (kind == Kind.REMOVED) != (after == null)) {
        throw new IllegalArgumentException("sides do not match kind " + kind);
      }
    }

    /** Renders {@code + path: after}, {@code - path: before} or {@code ~ path: before -> after}. */
    @Override
    public String toString() {
      String name = path.isRoot() ? "(document)" : path.toString();
      return switch (kind) {
        case ADDED -> "+ " + name + ": " + render(after);
        case REMOVED -> "- " + name + ": " + render(before);
        case CHANGED -> "~ " + name + ": " + render(before) + " -> " + render(after);
      };
    }
  }

  /** Computes the difference between two documents. */
  public static ConfigDiff between(ConfigNode before, ConfigNode after) {
    List<Change> changes = new ArrayList<>();
    compare(ConfigPath.root(), before, after, changes);
    return new ConfigDiff(changes);
  }

  /** Returns {@code true} when nothing changed. */
  public boolean isEmpty() {
    return changes.isEmpty();
  }

  /** Renders one change per line, or {@code (no changes)}. */
  public String render() {
    if (changes.isEmpty()) {
      return "(no changes)";
    }
    StringBuilder out = new StringBuilder();
    for (Change change : changes) {
      if (!out.isEmpty()) {
        out.append('\n');
      }
      out.append(change);
    }
    return out.toString();
  }

  private static void compare(
      ConfigPath path, ConfigNode before, ConfigNode after, List<Change> out) {
    if (before instanceof MappingNode left && after instanceof MappingNode right) {
      Set<String> keys = new LinkedHashSet<>(right.entries().keySet());
      keys.addAll(left.entries().keySet());
      for (String key : keys) {
        ConfigNode l = left.entries().get(key);
        ConfigNode r = right.entries().get(key);
        ConfigPath child = path.child(key);
        if (l == null) {
          added(child, r, out);
        } else if (r == null) {
          removed(child, l, out);
        } else {
          compare(child, l, r, out);
        }
      }
      return;
    }
    if (before instanceof MappingNode left) {
      removed(path, left, out);
      added(path, after, out);
      return;
    }
    if (after instanceof MappingNode right) {
      removed(path, before, out);
      added(path, right, out);
      return;
    }
    if (!sameLeaf(before, after)) {
      out.add(new Change(Kind.CHANGED, path, before, after));
    }
  }

  private static void added(ConfigPath path, ConfigNode node, List<Change> out) {
    if (node instanceof MappingNode mapping && !mapping.entries().isEmpty()) {
      for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
        added(path.child(entry.getKey()), entry.getValue(), out);
      }
      return;
    }
    out.add(new Change(Kind.ADDED, path, null, node));
  }

  private static void removed(ConfigPath path, ConfigNode node, List<Change> out) {
    if (node instanceof MappingNode mapping && !mapping.entries().isEmpty()) {
      for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
        removed(path.child(entry.getKey()), entry.getValue(), out);
      }
      return;
    }
    out.add(new Change(Kind.REMOVED, path, node, null));
  }

  private static boolean sameLeaf(ConfigNode left, ConfigNode right) {
    if (left instanceof ScalarNode l && right instanceof ScalarNode r) {
      return l.tag() == r.tag() && l.value().equals(r.value());
    }
    if (left instanceof NullNode && right instanceof NullNode) {
      return true;
    }
    if (left instanceof SequenceNode l && right instanceof SequenceNode r) {
      if (l.elements().size() != r.elements().size()) {
        return false;
      }
      for (int i = 0; i < l.elements().size(); i++) {
        if (!same(l.elements().get(i), r.elements().get(i))) {
          return false;
        }
      }
      return true;
    }
    if (left instanceof MappingNode l && right instanceof MappingNode r) {
      return l.entries().isEmpty() && r.entries().isEmpty();
    }
    return false;
  }

  private static boolean same(ConfigNode left, ConfigNode right) {
    if (left instanceof MappingNode l && right instanceof MappingNode r) {
      if (!l.entries().keySet().equals(r.entries().keySet())) {
        return false;
      }
      for (Map.Entry<String, ConfigNode> entry : l.entries().entrySet()) {
        if (!same(entry.getValue(), r.entries().get(entry.getKey()))) {
          return false;
        }
      }
      return true;
    }
    return sameLeaf(left, right);
  }

  private static String render(ConfigNode node) {
    if (node instanceof ScalarNode scalar) {
      return scalar.tag() == dev.leafconfig.node.ScalarTag.STRING
          ? "\"" + scalar.value().replace("\"", "\\\"") + "\""
          : scalar.value();
    }
    if (node instanceof NullNode) {
      return "null";
    }
    if (node instanceof SequenceNode sequence) {
      StringBuilder out = new StringBuilder("[");
      for (ConfigNode element : sequence.elements()) {
        if (out.length() > 1) {
          out.append(", ");
        }
        out.append(render(element));
      }
      return out.append(']').toString();
    }
    MappingNode mapping = (MappingNode) node;
    StringBuilder out = new StringBuilder("{");
    for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
      if (out.length() > 1) {
        out.append(", ");
      }
      out.append(entry.getKey()).append(": ").append(render(entry.getValue()));
    }
    return out.append('}').toString();
  }
}
