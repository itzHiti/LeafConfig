package dev.leafconfig.example.snippets;

import dev.leafconfig.ConfigPath;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.yaml.ConfigManager;
import java.nio.file.Path;

/** Programmatic validator from docs/annotations.md, compiled but never run. */
final class ValidatorSnippet {

  /** Configuration with the two related values the documented rule compares. */
  @ConfigFile("config.yml")
  static final class MainConfig {
    private int minPlayers = 1;
    private int maxPlayers = 100;

    int minPlayers() {
      return minPlayers;
    }

    int maxPlayers() {
      return maxPlayers;
    }
  }

  void register(Path dir) {
    // snippet-start: annotations-validator
    ConfigManager.builder(dir)
        .validator(MainConfig.class, (config, ctx) -> {
          if (config.maxPlayers() < config.minPlayers()) {
            ctx.error(ConfigPath.of("max-players"), "must be at least min-players");
          }
        })
        .build();
    // snippet-end: annotations-validator
  }
}
