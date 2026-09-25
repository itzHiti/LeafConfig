package dev.leafconfig.example.snippets;

import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.annotation.FormerlyKnownAs;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.yaml.ConfigManager;
import java.nio.file.Path;
import java.util.logging.Logger;

/** Code from docs/migrations.md, compiled but never run. */
final class MigrationDocSnippets {

  private final Path dir = Path.of("plugins", "Example");
  private final Logger logger = Logger.getLogger("Example");

  /** Stand-in for the documented version 3 configuration. */
  @ConfigFile("config.yml")
  @ConfigVersion(3)
  static final class MainConfig {
    // snippet-start: migrations-formerly-known-as
    @FormerlyKnownAs("motd")
    private String greeting = "Welcome!";
    // snippet-end: migrations-formerly-known-as
  }

  // snippet-start: migrations-register
  ConfigManager manager = ConfigManager.builder(dir)
      .migrations(MainConfig.class, m -> m
          .from(1).to(2, doc -> {
            doc.rename("mysql.ip", "database.host");
            doc.rename("mysql.port", "database.port");
            doc.remove("mysql");
          })
          .from(2).to(3, doc -> doc.setIfMissing("database.pool-size", 10)))
      .build();
  // snippet-end: migrations-register

  void preview() {
    // snippet-start: migrations-preview
    MigrationPreview preview = manager.previewMigration(MainConfig.class);
    if (!preview.successful()) {
      preview.diagnostics().forEach(d -> logger.warning(d.toString()));
    }
    logger.info("Would change:\n" + preview.diff().render());
    // snippet-end: migrations-preview
  }
}
