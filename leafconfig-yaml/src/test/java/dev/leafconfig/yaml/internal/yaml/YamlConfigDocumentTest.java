package dev.leafconfig.yaml.internal.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.yaml.YamlLimits;
import dev.leafconfig.yaml.internal.decode.DiagnosticCollector;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class YamlConfigDocumentTest {

  private static final String TEXT =
      """
      # header
      a: 1
      section:
        # about x
        x: old # inline
        y: [1, 2]
      empty: {}
      """;

  private YamlDocument document;
  private YamlConfigDocument editable;

  private void parse(String text) {
    DiagnosticCollector collector = new DiagnosticCollector();
    document = YamlDocument.parse(text, YamlLimits.DEFAULT, collector);
    assertThat(collector.hasErrors()).isFalse();
    editable = new YamlConfigDocument(document, YamlLimits.DEFAULT);
  }

  @Test
  void getAndContainsFollowDottedPaths() {
    parse(TEXT);
    assertThat(editable.contains("section.x")).isTrue();
    assertThat(editable.contains("section.z")).isFalse();
    assertThat(editable.contains("a.b")).isFalse();
    assertThat(editable.get("section.x"))
        .get()
        .extracting(node -> ((ScalarNode) node).value())
        .isEqualTo("old");
    assertThat(editable.get("missing")).isEmpty();
    assertThat(editable.root()).isInstanceOf(MappingNode.class);
  }

  @Test
  void setReplacesInPlaceKeepingInlineCommentsAndCreatesParents() {
    parse(TEXT);
    editable.set("section.x", "new");
    editable.set("section.deeper.flag", true);
    editable.set("empty.count", 3L);
    editable.set("price", new BigDecimal("1.50"));
    editable.set("nothing", null);
    editable.set("ratio", 0.5);
    assertThat(editable.setIfMissing("a", 99)).isFalse();
    assertThat(editable.setIfMissing("b", 2)).isTrue();
    assertThat(document.render())
        .isEqualTo(
            """
            # header
            a: 1
            section:
              # about x
              x: new # inline
              y: [1, 2]
              deeper:
                flag: true
            empty:
              count: 3
            price: 1.50
            nothing: null
            ratio: 0.5
            b: 2
            """);
  }

  @Test
  void removeDropsEntryWithComments() {
    parse(TEXT);
    assertThat(editable.remove("section.x")).isTrue();
    assertThat(editable.remove("section.x")).isFalse();
    assertThat(editable.remove("nope.deeper")).isFalse();
    assertThat(document.render()).doesNotContain("about x").doesNotContain("old");
  }

  @Test
  void renameKeepsPositionAndCommentsInsideAMappingAndMovesAcrossMappings() {
    parse(TEXT);
    assertThat(editable.rename("section.x", "section.renamed")).isTrue();
    assertThat(editable.rename("section.y", "other.list")).isTrue();
    assertThat(editable.rename("ghost", "anything")).isFalse();
    assertThat(document.render())
        .isEqualTo(
            """
            # header
            a: 1
            section:
              # about x
              renamed: old # inline
            empty: {}
            other:
              list: [1, 2]
            """);
    assertThatThrownBy(() -> editable.rename("a", "section.renamed"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  void invalidPathsAndValuesAreRejected() {
    parse(TEXT);
    assertThatThrownBy(() -> editable.get("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editable.get("a..b")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editable.get(" a")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> editable.set("a.b", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not a mapping");
    assertThatThrownBy(() -> editable.set("k", new Object()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported value type");
    assertThat(editable.get("a"))
        .contains(
            new ScalarNode(
                "1",
                dev.leafconfig.node.ScalarTag.INTEGER,
                editable.get("a").orElseThrow().source()));
    editable.set("n", new NullNode(null));
    editable.set("m", MappingNode.of(Map.of("k", ScalarNode.ofString("v"))));
    assertThat(editable.get("m.k")).contains(ScalarNode.ofString("v"));
  }
}
