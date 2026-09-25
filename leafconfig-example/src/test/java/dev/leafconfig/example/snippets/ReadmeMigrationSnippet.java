package dev.leafconfig.example.snippets;

import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.annotation.FormerlyKnownAs;
import dev.leafconfig.yaml.ConfigManager;
import java.nio.file.Path;

/**
 * Migration example from the README, compiled but never run. The documented block declares a class
 * and then builds a manager, so here the manager is a field initializer next to the class.
 */
@SuppressWarnings("ClassCanBeStatic") // the documentation shows a plain nested class
final class ReadmeMigrationSnippet {

  private final Path dir = Path.of("plugins", "Example");

  /** Section referenced by the documented class. */
  static final class Database {}

  // snippet-start: readme-migrations
  @ConfigFile("config.yml")
  @ConfigVersion(2)
  public final class MainConfig {
    @FormerlyKnownAs("motd")          // simple rename, handled without a step
    private String greeting = "Welcome!";
    private Database database = new Database();
  }

  ConfigManager manager = ConfigManager.builder(dir)
      .migrations(MainConfig.class, m -> m
          .from(1).to(2, doc -> {
            doc.rename("mysql.ip", "database.host");   // value and comments move along
            doc.remove("mysql");
          }))
      .build();
  // snippet-end: readme-migrations
}
