package dev.leafconfig.yaml.internal.yaml;

import dev.leafconfig.yaml.internal.codec.ObjectAdapter;
import dev.leafconfig.yaml.internal.decode.Encoder;
import dev.leafconfig.yaml.internal.schema.ConfigProperty;
import dev.leafconfig.yaml.internal.schema.ConfigSchema;
import dev.leafconfig.yaml.internal.schema.ObjectSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Inserts missing schema keys and schema comments into an existing document without touching
 * anything the administrator wrote.
 *
 * <p>Rules: unknown keys are never removed, existing keys are never reordered or restyled, a schema
 * comment is added only to keys that have no leading comment, and a new key is placed directly
 * after its nearest preceding schema neighbour that exists in the document.
 */
public final class DocumentMerger {

  private final Encoder encoder;

  /** Creates a merger that encodes default values with {@code encoder}. */
  public DocumentMerger(Encoder encoder) {
    this.encoder = encoder;
  }

  /**
   * Merges defaults into {@code document}.
   *
   * @return {@code true} when the document changed and must be written
   */
  public boolean merge(YamlDocument document, ConfigSchema schema, Object defaults) {
    boolean changed = mergeObject(document.root(), schema.root(), defaults);
    List<String> header = schema.root().comments();
    List<NodeTuple> tuples = document.root().getValue();
    if (document.isGenerated() && !header.isEmpty() && !tuples.isEmpty()) {
      Node firstKey = tuples.get(0).getKeyNode();
      List<CommentLine> lines = new ArrayList<>(commentLines(header));
      List<CommentLine> own = firstKey.getBlockComments();
      if (own != null && !own.isEmpty()) {
        lines.add(new CommentLine(Optional.empty(), Optional.empty(), "", CommentType.BLANK_LINE));
        lines.addAll(own);
      }
      firstKey.setBlockComments(lines);
      changed = true;
    }
    return changed;
  }

  private boolean mergeObject(MappingNode mapping, ObjectSchema schema, Object defaults) {
    boolean changed = false;
    boolean wasEmpty = mapping.getValue().isEmpty();
    List<NodeTuple> tuples = mapping.getValue();
    List<ConfigProperty> properties = schema.properties();
    for (int i = 0; i < properties.size(); i++) {
      ConfigProperty property = properties.get(i);
      int index = indexOf(tuples, property.key());
      if (index >= 0) {
        NodeTuple tuple = tuples.get(index);
        Node keyNode = tuple.getKeyNode();
        List<CommentLine> existing = keyNode.getBlockComments();
        if (!property.comments().isEmpty() && (existing == null || existing.isEmpty())) {
          keyNode.setBlockComments(commentLines(property.comments()));
          changed = true;
        }
        if (property.adapter() instanceof ObjectAdapter nestedAdapter
            && tuple.getValueNode() instanceof MappingNode nested) {
          Object nestedDefaults = property.accessor().get(defaults);
          if (nestedDefaults == null) {
            nestedDefaults = nestedAdapter.schema().instantiate();
          }
          changed |= mergeObject(nested, nestedAdapter.schema(), nestedDefaults);
        }
        continue;
      }
      Object defaultValue = property.accessor().get(defaults);
      Node value;
      if (property.adapter() instanceof ObjectAdapter nestedAdapter && defaultValue != null) {
        // Build nested sections through the merger so their schema comments are written too.
        MappingNode nested = new MappingNode(Tag.MAP, new ArrayList<>(), FlowStyle.BLOCK);
        mergeObject(nested, nestedAdapter.schema(), defaultValue);
        if (nested.getValue().isEmpty()) {
          nested.setFlowStyle(FlowStyle.FLOW);
        }
        value = nested;
      } else {
        value = NodeConverter.toYaml(encoder.encodeProperty(property, defaultValue));
      }
      ScalarNode key = new ScalarNode(Tag.STR, property.key(), ScalarStyle.PLAIN);
      if (!property.comments().isEmpty()) {
        key.setBlockComments(commentLines(property.comments()));
      }
      tuples.add(insertionIndex(tuples, properties, i), new NodeTuple(key, value));
      changed = true;
    }
    if (wasEmpty && !tuples.isEmpty() && mapping.getFlowStyle() == FlowStyle.FLOW) {
      mapping.setFlowStyle(FlowStyle.BLOCK);
    }
    return changed;
  }

  /**
   * After the nearest preceding schema neighbour present in the document; otherwise directly before
   * the nearest following one. The only exception: a first key that carries comments keeps its
   * position, because file header comments are attached to it and must stay on top. Without any
   * present neighbour the key is appended.
   */
  private static int insertionIndex(
      List<NodeTuple> tuples, List<ConfigProperty> properties, int propertyIndex) {
    for (int j = propertyIndex - 1; j >= 0; j--) {
      int index = indexOf(tuples, properties.get(j).key());
      if (index >= 0) {
        return index + 1;
      }
    }
    for (int j = propertyIndex + 1; j < properties.size(); j++) {
      int index = indexOf(tuples, properties.get(j).key());
      if (index >= 0) {
        List<CommentLine> comments = tuples.get(index).getKeyNode().getBlockComments();
        boolean firstWithComments = index == 0 && comments != null && !comments.isEmpty();
        return firstWithComments ? 1 : index;
      }
    }
    return tuples.size();
  }

  private static int indexOf(List<NodeTuple> tuples, String key) {
    for (int i = 0; i < tuples.size(); i++) {
      if (tuples.get(i).getKeyNode() instanceof ScalarNode scalar
          && scalar.getValue().equals(key)) {
        return i;
      }
    }
    return -1;
  }

  private static List<CommentLine> commentLines(List<String> comments) {
    List<CommentLine> lines = new ArrayList<>(comments.size());
    for (String comment : comments) {
      String text = comment.isEmpty() ? "" : " " + comment;
      lines.add(new CommentLine(Optional.empty(), Optional.empty(), text, CommentType.BLOCK));
    }
    return lines;
  }
}
