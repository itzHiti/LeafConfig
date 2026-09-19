package dev.leafconfig.yaml.internal.yaml;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarTag;
import dev.leafconfig.node.SourceLocation;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Converts between SnakeYAML nodes and the backend-neutral {@link ConfigNode} model while enforcing
 * the safety rules: no anchors or aliases, only core-schema tags, scalar
 * keys only, and resource limits.
 */
final class NodeConverter {

  private static final Set<Tag> SCALAR_TAGS =
      Set.of(Tag.STR, Tag.INT, Tag.FLOAT, Tag.BOOL, Tag.NULL);

  private final YamlLimits limits;
  private final DiagnosticCollector collector;

  NodeConverter(YamlLimits limits, DiagnosticCollector collector) {
    this.limits = limits;
    this.collector = collector;
  }

  ConfigNode toConfigNode(Node node, ConfigPath path, int depth) {
    SourceLocation source = location(node);
    if (node.getAnchor().isPresent()) {
      collector.error(
          path, DiagnosticCodes.ALIAS_UNSUPPORTED, "anchors and aliases are not supported", source);
    }
    if (depth > limits.maxDepth()) {
      collector.error(
          path,
          DiagnosticCodes.LIMIT_EXCEEDED,
          "nesting deeper than " + limits.maxDepth() + " levels",
          source);
      return new dev.leafconfig.node.NullNode(source);
    }
    if (node instanceof ScalarNode scalar) {
      return scalar(scalar, path, source);
    }
    if (node instanceof SequenceNode sequence) {
      checkTag(node, Tag.SEQ, path, source);
      List<Node> items = sequence.getValue();
      checkSize(items.size(), path, source);
      List<ConfigNode> elements = new ArrayList<>(items.size());
      for (int i = 0; i < items.size(); i++) {
        elements.add(toConfigNode(items.get(i), path.index(i), depth + 1));
      }
      return new dev.leafconfig.node.SequenceNode(elements, source);
    }
    if (node instanceof MappingNode mapping) {
      checkTag(node, Tag.MAP, path, source);
      List<NodeTuple> tuples = mapping.getValue();
      checkSize(tuples.size(), path, source);
      Map<String, ConfigNode> entries = new LinkedHashMap<>();
      for (NodeTuple tuple : tuples) {
        if (!(tuple.getKeyNode() instanceof ScalarNode keyNode)) {
          collector.error(
              path,
              DiagnosticCodes.TYPE_MISMATCH,
              "complex mapping keys are not supported",
              location(tuple.getKeyNode()));
          continue;
        }
        String key = keyNode.getValue();
        ConfigNode value = toConfigNode(tuple.getValueNode(), path.child(key), depth + 1);
        if (entries.put(key, value) != null) {
          collector.error(
              path.child(key),
              DiagnosticCodes.DUPLICATE_KEY,
              "duplicate key '" + key + "'",
              location(keyNode));
        }
      }
      return new dev.leafconfig.node.MappingNode(entries, source);
    }
    collector.error(
        path, DiagnosticCodes.TAG_UNSUPPORTED, "unsupported node " + node.getNodeType(), source);
    return new dev.leafconfig.node.NullNode(source);
  }

  private ConfigNode scalar(ScalarNode scalar, ConfigPath path, SourceLocation source) {
    Tag tag = scalar.getTag();
    if (!SCALAR_TAGS.contains(tag)) {
      collector.error(
          path, DiagnosticCodes.TAG_UNSUPPORTED, "unsupported YAML tag " + tag.getValue(), source);
      return new dev.leafconfig.node.NullNode(source);
    }
    String value = scalar.getValue();
    if (value.length() > limits.maxScalarLength()) {
      collector.error(
          path,
          DiagnosticCodes.LIMIT_EXCEEDED,
          "scalar longer than " + limits.maxScalarLength() + " characters",
          source);
      return new dev.leafconfig.node.NullNode(source);
    }
    if (tag.equals(Tag.NULL)) {
      return new dev.leafconfig.node.NullNode(source);
    }
    ScalarTag kind;
    if (tag.equals(Tag.INT)) {
      kind = ScalarTag.INTEGER;
    } else if (tag.equals(Tag.FLOAT)) {
      kind = ScalarTag.FLOAT;
    } else if (tag.equals(Tag.BOOL)) {
      kind = ScalarTag.BOOLEAN;
    } else {
      kind = ScalarTag.STRING;
    }
    return new dev.leafconfig.node.ScalarNode(value, kind, source);
  }

  private void checkTag(Node node, Tag expected, ConfigPath path, SourceLocation source) {
    if (!expected.equals(node.getTag())) {
      collector.error(
          path,
          DiagnosticCodes.TAG_UNSUPPORTED,
          "unsupported YAML tag " + node.getTag().getValue(),
          source);
    }
  }

  private void checkSize(int size, ConfigPath path, SourceLocation source) {
    if (size > limits.maxCollectionSize()) {
      collector.error(
          path,
          DiagnosticCodes.LIMIT_EXCEEDED,
          "collection larger than " + limits.maxCollectionSize() + " entries",
          source);
    }
  }

  static SourceLocation location(Node node) {
    return node.getStartMark()
        .map(mark -> new SourceLocation(mark.getLine() + 1, mark.getColumn() + 1))
        .orElse(null);
  }

  /** Builds a SnakeYAML node for rendering; block style for non-empty collections. */
  static Node toYaml(ConfigNode node) {
    if (node instanceof dev.leafconfig.node.ScalarNode scalar) {
      Tag tag =
          switch (scalar.tag()) {
            case STRING -> Tag.STR;
            case INTEGER -> Tag.INT;
            case FLOAT -> Tag.FLOAT;
            case BOOLEAN -> Tag.BOOL;
          };
      ScalarStyle style =
          scalar.tag() == ScalarTag.STRING && scalar.value().indexOf('\n') >= 0
              ? ScalarStyle.LITERAL
              : ScalarStyle.PLAIN;
      return new ScalarNode(tag, scalar.value(), style);
    }
    if (node instanceof dev.leafconfig.node.NullNode) {
      return new ScalarNode(Tag.NULL, "null", ScalarStyle.PLAIN);
    }
    if (node instanceof dev.leafconfig.node.SequenceNode sequence) {
      List<Node> items = new ArrayList<>(sequence.elements().size());
      for (ConfigNode element : sequence.elements()) {
        items.add(toYaml(element));
      }
      return new SequenceNode(Tag.SEQ, items, items.isEmpty() ? FlowStyle.FLOW : FlowStyle.BLOCK);
    }
    dev.leafconfig.node.MappingNode mapping = (dev.leafconfig.node.MappingNode) node;
    List<NodeTuple> tuples = new ArrayList<>(mapping.entries().size());
    for (Map.Entry<String, ConfigNode> entry : mapping.entries().entrySet()) {
      tuples.add(
          new NodeTuple(
              new ScalarNode(Tag.STR, entry.getKey(), ScalarStyle.PLAIN),
              toYaml(entry.getValue())));
    }
    return new MappingNode(Tag.MAP, tuples, tuples.isEmpty() ? FlowStyle.FLOW : FlowStyle.BLOCK);
  }
}
