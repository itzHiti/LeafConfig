package dev.leafconfig.yaml.internal.yaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.DumpSettings;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.StreamDataWriter;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.api.lowlevel.Serialize;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.FlowStyle;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.emitter.Emitter;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.Tag;

/**
 * Proof for the YAML layer: the chosen node API must re-render comments, quoted and
 * block scalars, unknown keys and key order before any merge architecture is committed.
 *
 * <p>Known limitation found by this proof: line breaks inside folded scalars ({@code >}) are not
 * preserved; the emitter re-folds the text. The scalar value itself is unchanged.
 */
class SnakeYamlRoundTripProofTest {

  private static final String INPUT =
      """
      # Header comment line one
      # Header comment line two
      debug: false
      # Maximum players allowed
      max-players: 100 # inline comment
      unknown-key: keep me
      quoted: "0042"
      single: 'yes'
      block: |
        line one
        line two
      folded: >-
        folded text continues
      database:
        # host of the database
        host: localhost
        port: 5432
      list:
        - a
        - "b"
        - c: 1
          d: 2
      empty-map: {}
      empty-list: []
      nothing: null
      кириллица: значение
      # trailing comment
      """;

  @Test
  void reRendersDocumentByteForByte() {
    Node root = compose(INPUT);
    assertThat(render(root)).isEqualTo(INPUT);
  }

  @Test
  void insertingCommentedKeyKeepsEverythingElse() {
    MappingNode root = (MappingNode) compose(INPUT);
    ScalarNode key = new ScalarNode(Tag.STR, "new-key", ScalarStyle.PLAIN);
    key.setBlockComments(
        List.of(
            new CommentLine(
                Optional.empty(), Optional.empty(), " Added by schema", CommentType.BLOCK)));
    ScalarNode value = new ScalarNode(Tag.INT, "7", ScalarStyle.PLAIN);
    int index = indexOf(root, "max-players") + 1;
    root.getValue().add(index, new NodeTuple(key, value));

    String expected =
        INPUT.replace(
            "max-players: 100 # inline comment\n",
            "max-players: 100 # inline comment\n# Added by schema\nnew-key: 7\n");
    assertThat(render(root)).isEqualTo(expected);
  }

  private static int indexOf(MappingNode mapping, String key) {
    List<NodeTuple> tuples = mapping.getValue();
    for (int i = 0; i < tuples.size(); i++) {
      if (((ScalarNode) tuples.get(i).getKeyNode()).getValue().equals(key)) {
        return i;
      }
    }
    throw new AssertionError("missing " + key);
  }

  private static Node compose(String yaml) {
    LoadSettings settings = LoadSettings.builder().setParseComments(true).build();
    return new Compose(settings).composeString(yaml).orElseThrow();
  }

  private static String render(Node root) {
    DumpSettings settings =
        DumpSettings.builder()
            .setDumpComments(true)
            .setDefaultFlowStyle(FlowStyle.BLOCK)
            .setIndent(2)
            .setIndicatorIndent(2)
            .setIndentWithIndicator(true)
            .setSplitLines(false)
            .build();
    StringWriter out = new StringWriter();
    StreamDataWriter writer =
        new StreamDataWriter() {
          @Override
          public void write(String str) {
            out.write(str);
          }

          @Override
          public void write(String str, int off, int len) {
            out.write(str, off, len);
          }
        };
    Emitter emitter = new Emitter(settings, writer);
    for (Event event : new Serialize(settings).serializeOne(root)) {
      emitter.emit(event);
    }
    return out.toString();
  }
}
