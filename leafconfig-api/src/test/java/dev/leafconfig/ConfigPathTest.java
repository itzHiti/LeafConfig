package dev.leafconfig;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfigPathTest {

  @Test
  void rendersKeysAndIndices() {
    ConfigPath path = ConfigPath.of("servers").index(2).child("host");
    assertThat(path.toString()).isEqualTo("servers[2].host");
    assertThat(path.segments()).containsExactly("servers", "[2]", "host");
  }

  @Test
  void rootRendersEmptyAndIsImmutable() {
    ConfigPath root = ConfigPath.root();
    ConfigPath child = root.child("a");
    assertThat(root.isRoot()).isTrue();
    assertThat(root.toString()).isEmpty();
    assertThat(child).isNotEqualTo(root);
    assertThat(child).isEqualTo(ConfigPath.of("a"));
    assertThat(child.hashCode()).isEqualTo(ConfigPath.of("a").hashCode());
  }

  @Test
  void diagnosticRenderingIncludesPathCodeAndLine() {
    ConfigDiagnostic diagnostic =
        ConfigDiagnostic.error(
                ConfigPath.of("database", "port"),
                DiagnosticCodes.OUT_OF_RANGE,
                "expected 1..65535")
            .at(14, null);
    assertThat(diagnostic.toString())
        .isEqualTo("database.port [OUT_OF_RANGE]: expected 1..65535 (line 14)");
    assertThat(ConfigDiagnostics.render("config.yml", java.util.List.of(diagnostic)))
        .startsWith("Invalid configuration: config.yml")
        .contains("  - database.port [OUT_OF_RANGE]");
  }
}
