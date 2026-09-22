package dev.leafconfig.yaml.internal.yaml;

import dev.leafconfig.migration.ConfigDocument;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * {@link ConfigDocument} over a SnakeYAML mapping. Every operation edits the tree in place so that
 * comments, ordering and unknown keys survive; the tree is rendered by {@link YamlDocument}.
 */
public final class YamlConfigDocument implements ConfigDocument {

  private final MappingNode root;
  private final YamlLimits limits;

  /** Wraps the root mapping of {@code document}. */
  public YamlConfigDocument(YamlDocument document, YamlLimits limits) {
    this.root = document.root();
    this.limits = limits;
  }

  @Override
  public Optional<ConfigNode> get(String path) {
    NodeTuple tuple = tuple(path);
    if (tuple == null) {
      return Optional.empty();
    }
    return Optional.of(
        new NodeConverter(limits, new DiagnosticCollector())
            .toConfigNode(tuple.getValueNode(), dev.leafconfig.ConfigPath.of(split(path)), 0));
  }

  @Override
  public boolean contains(String path) {
    return tuple(path) != null;
  }

  @Override
  public void set(String path, Object value) {
    String[] segments = split(path);
    Node valueNode = NodeConverter.toYaml(toNode(value));
    MappingNode parent = parent(segments, true);
    String key = segments[segments.length - 1];
    int index = indexOf(parent, key);
    if (index < 0) {
      parent.getValue().add(new NodeTuple(keyNode(key), valueNode));
      unflow(parent);
      return;
    }
    NodeTuple existing = parent.getValue().get(index);
    Node old = existing.getValueNode();
    // Comments after the value on the same line belong to the value node; keep them.
    valueNode.setInLineComments(old.getInLineComments());
    valueNode.setEndComments(old.getEndComments());
    parent.getValue().set(index, new NodeTuple(existing.getKeyNode(), valueNode));
  }

  @Override
  public boolean setIfMissing(String path, Object value) {
    if (contains(path)) {
      return false;
    }
    set(path, value);
    return true;
  }

  @Override
  public boolean remove(String path) {
    String[] segments = split(path);
    MappingNode parent = parent(segments, false);
    if (parent == null) {
      return false;
    }
    int index = indexOf(parent, segments[segments.length - 1]);
    if (index < 0) {
      return false;
    }
    parent.getValue().remove(index);
    return true;
  }

  @Override
  @SuppressWarnings("ReferenceEquality") // the entry stays in place only inside the same mapping
  public boolean rename(String from, String to) {
    String[] source = split(from);
    String[] target = split(to);
    MappingNode sourceParent = parent(source, false);
    if (sourceParent == null) {
      return false;
    }
    int sourceIndex = indexOf(sourceParent, source[source.length - 1]);
    if (sourceIndex < 0) {
      return false;
    }
    if (tuple(to) != null) {
      throw new IllegalArgumentException(
          "cannot rename '" + from + "': '" + to + "' already exists");
    }
    NodeTuple tuple = sourceParent.getValue().get(sourceIndex);
    NodeTuple renamed =
        new NodeTuple(
            renamedKey(tuple.getKeyNode(), target[target.length - 1]), tuple.getValueNode());
    MappingNode targetParent = parent(target, true);
    if (targetParent == sourceParent) {
      sourceParent.getValue().set(sourceIndex, renamed);
    } else {
      sourceParent.getValue().remove(sourceIndex);
      targetParent.getValue().add(renamed);
      unflow(targetParent);
    }
    return true;
  }

  @Override
  public ConfigNode root() {
    return new NodeConverter(limits, new DiagnosticCollector())
        .toConfigNode(root, dev.leafconfig.ConfigPath.root(), 0);
  }

  /**
   * Renames the entry {@code from} to {@code to} inside {@code mapping}, keeping value, comments
   * and position. Used for {@code @FormerlyKnownAs}.
   */
  public static void renameInPlace(MappingNode mapping, String from, String to) {
    int index = indexOf(mapping, from);
    NodeTuple tuple = mapping.getValue().get(index);
    mapping
        .getValue()
        .set(index, new NodeTuple(renamedKey(tuple.getKeyNode(), to), tuple.getValueNode()));
  }

  /** Returns the index of {@code key} in {@code mapping}, or {@code -1}. */
  public static int indexOf(MappingNode mapping, String key) {
    List<NodeTuple> tuples = mapping.getValue();
    for (int i = 0; i < tuples.size(); i++) {
      if (tuples.get(i).getKeyNode() instanceof org.snakeyaml.engine.v2.nodes.ScalarNode scalar
          && scalar.getValue().equals(key)) {
        return i;
      }
    }
    return -1;
  }

  /** Creates a plain string key node. */
  public static org.snakeyaml.engine.v2.nodes.ScalarNode keyNode(String key) {
    return new org.snakeyaml.engine.v2.nodes.ScalarNode(Tag.STR, key, ScalarStyle.PLAIN);
  }

  private static org.snakeyaml.engine.v2.nodes.ScalarNode renamedKey(Node old, String key) {
    org.snakeyaml.engine.v2.nodes.ScalarNode renamed = keyNode(key);
    renamed.setBlockComments(old.getBlockComments());
    renamed.setInLineComments(old.getInLineComments());
    renamed.setEndComments(old.getEndComments());
    return renamed;
  }

  private NodeTuple tuple(String path) {
    String[] segments = split(path);
    MappingNode parent = parent(segments, false);
    if (parent == null) {
      return null;
    }
    int index = indexOf(parent, segments[segments.length - 1]);
    return index < 0 ? null : parent.getValue().get(index);
  }

  /**
   * Returns the mapping that holds the last segment, creating missing intermediate mappings when
   * {@code create} is set. Without {@code create}, a missing or non-mapping intermediate segment
   * yields {@code null}, so reads simply report absence.
   *
   * @throws IllegalArgumentException when {@code create} is set and an intermediate segment is not
   *     a mapping
   */
  private MappingNode parent(String[] segments, boolean create) {
    MappingNode current = root;
    for (int i = 0; i < segments.length - 1; i++) {
      int index = indexOf(current, segments[i]);
      if (index < 0) {
        if (!create) {
          return null;
        }
        MappingNode child = new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        current.getValue().add(new NodeTuple(keyNode(segments[i]), child));
        unflow(current);
        current = child;
        continue;
      }
      Node value = current.getValue().get(index).getValueNode();
      if (!(value instanceof MappingNode mapping)) {
        if (!create) {
          return null;
        }
        throw new IllegalArgumentException(
            "'" + String.join(".", List.of(segments).subList(0, i + 1)) + "' is not a mapping");
      }
      current = mapping;
    }
    return current;
  }

  /** An empty {@code {}} that receives entries must render as a block mapping. */
  private static void unflow(MappingNode mapping) {
    if (mapping.getFlowStyle() == FlowStyle.FLOW && !mapping.getValue().isEmpty()) {
      mapping.setFlowStyle(FlowStyle.BLOCK);
    }
  }

  static String[] split(String path) {
    if (path == null || path.isEmpty()) {
      throw new IllegalArgumentException("path must not be empty");
    }
    String[] segments = path.split("\\.", -1);
    for (String segment : segments) {
      if (segment.isEmpty() || !segment.equals(segment.strip())) {
        throw new IllegalArgumentException("invalid path '" + path + "': empty or padded segment");
      }
    }
    return segments;
  }

  private static ConfigNode toNode(Object value) {
    if (value == null) {
      return new NullNode(null);
    }
    if (value instanceof ConfigNode node) {
      return node;
    }
    if (value instanceof String text) {
      return ScalarNode.ofString(text);
    }
    if (value instanceof Boolean flag) {
      return ScalarNode.ofBoolean(flag);
    }
    if (value instanceof Integer || value instanceof Long) {
      return ScalarNode.ofInteger(((Number) value).longValue());
    }
    if (value instanceof BigInteger big) {
      return ScalarNode.ofInteger(big);
    }
    if (value instanceof Float || value instanceof Double) {
      return ScalarNode.ofFloat(((Number) value).doubleValue());
    }
    if (value instanceof BigDecimal decimal) {
      return ScalarNode.ofDecimal(decimal);
    }
    throw new IllegalArgumentException(
        "unsupported value type "
            + value.getClass().getName()
            + "; pass a ConfigNode, String, Boolean, Integer, Long, BigInteger, Float, Double,"
            + " BigDecimal or null");
  }
}
