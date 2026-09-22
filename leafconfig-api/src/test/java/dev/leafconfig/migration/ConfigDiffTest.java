package dev.leafconfig.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.MappingNode;
import dev.leafconfig.node.NullNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.node.SequenceNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigDiffTest {

  private static MappingNode mapping(Object... keysAndValues) {
    Map<String, ConfigNode> entries = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      entries.put((String) keysAndValues[i], (ConfigNode) keysAndValues[i + 1]);
    }
    return MappingNode.of(entries);
  }

  @Test
  void reportsLeafChangesInAfterOrderThenRemovals() {
    MappingNode before =
        mapping(
            "a", ScalarNode.ofInteger(1),
            "gone", ScalarNode.ofString("x"),
            "nested", mapping("k", ScalarNode.ofBoolean(true), "old", new NullNode(null)),
            "list", new SequenceNode(List.of(ScalarNode.ofInteger(1)), null));
    MappingNode after =
        mapping(
            "a", ScalarNode.ofInteger(2),
            "nested", mapping("k", ScalarNode.ofBoolean(true), "fresh", mapping()),
            "list",
                new SequenceNode(List.of(ScalarNode.ofInteger(1), ScalarNode.ofInteger(2)), null),
            "added", mapping("deep", ScalarNode.ofString("say \"hi\"")));
    ConfigDiff diff = ConfigDiff.between(before, after);
    assertThat(diff.render())
        .isEqualTo(
            """
            ~ a: 1 -> 2
            + nested.fresh: {}
            - nested.old: null
            ~ list: [1] -> [1, 2]
            + added.deep: "say \\"hi\\""
            - gone: "x"\
            """);
    assertThat(diff.changes().get(0).path()).isEqualTo(ConfigPath.of("a"));
  }

  @Test
  void equalDocumentsHaveNoChanges() {
    MappingNode left = mapping("a", mapping("b", ScalarNode.ofString("1")));
    MappingNode right = mapping("a", mapping("b", ScalarNode.ofString("1")));
    assertThat(ConfigDiff.between(left, right).isEmpty()).isTrue();
    assertThat(ConfigDiff.between(left, right).render()).isEqualTo("(no changes)");
    // Same text, different tag: the value is semantically different.
    assertThat(
            ConfigDiff.between(
                    mapping("a", ScalarNode.ofString("1")), mapping("a", ScalarNode.ofInteger(1)))
                .changes())
        .hasSize(1);
  }

  @Test
  void kindChangesBetweenLeafAndMappingAreReportedAsRemoveAndAdd() {
    MappingNode before = mapping("a", ScalarNode.ofInteger(1));
    MappingNode after = mapping("a", mapping("b", ScalarNode.ofInteger(2)));
    assertThat(ConfigDiff.between(before, after).render())
        .isEqualTo(
            """
            - a: 1
            + a.b: 2\
            """);
    assertThat(ConfigDiff.between(after, before).render())
        .isEqualTo(
            """
            - a.b: 2
            + a: 1\
            """);
  }

  @Test
  void changeValidatesItsSides() {
    assertThatThrownBy(
            () ->
                new ConfigDiff.Change(
                    ConfigDiff.Kind.ADDED, ConfigPath.of("a"), ScalarNode.ofInteger(1), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new ConfigDiff.Change(
                    ConfigDiff.Kind.REMOVED, ConfigPath.root(), ScalarNode.ofInteger(1), null)
                .toString())
        .isEqualTo("- (document): 1");
  }
}
