package dev.leafconfig.example;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leafconfig.paper.PaperAdapters;
import dev.leafconfig.yaml.ConfigManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the generated file shown in the README: the example configuration rendered from defaults
 * must equal {@code src/test/resources/expected-config.yml} byte for byte.
 */
class MainConfigTest {

  @TempDir Path dataFolder;

  @Test
  void generatesDocumentedConfig() throws IOException {
    ConfigManager.Builder builder = ConfigManager.builder(dataFolder);
    PaperAdapters.register(builder);
    try (ConfigManager manager = builder.build()) {
      MainConfig config = manager.load(MainConfig.class).get();
      assertThat(config.maxPlayers()).isEqualTo(100);
      assertThat(config.database().port()).isEqualTo(5432);
    }
    String expected;
    try (var in = getClass().getResourceAsStream("/expected-config.yml")) {
      expected = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    assertThat(Files.readString(dataFolder.resolve("config.yml"))).isEqualTo(expected);
  }
}
