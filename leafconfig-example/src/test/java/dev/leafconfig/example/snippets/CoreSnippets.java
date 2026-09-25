package dev.leafconfig.example.snippets;

import dev.leafconfig.ConfigHandle;
import dev.leafconfig.example.MainConfig;
import dev.leafconfig.yaml.ConfigManager;
import java.nio.file.Path;

/** Plain-Java loading code shown in the README, compiled but never run. */
final class CoreSnippets {

  /** Locale file class used by the multi-file snippet. */
  static final class Messages {
    private String welcome = "Welcome!";
  }

  void loadWithoutPaper(Path dataDirectory) {
    // snippet-start: readme-load-plain
    ConfigManager manager = ConfigManager.builder(dataDirectory).build();
    ConfigHandle<MainConfig> config = manager.load(MainConfig.class);
    // snippet-end: readme-load-plain
  }

  void loadSeveralFiles(ConfigManager manager) {
    // snippet-start: readme-multi-file
    ConfigHandle<Messages> en = manager.load(Messages.class, "messages/en.yml");
    ConfigHandle<Messages> ru = manager.load(Messages.class, "messages/ru.yml");
    // snippet-end: readme-multi-file
  }
}
