package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.yaml.testmodel.VersionedConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One configuration class backing several files through {@code load(Class, String)}. */
class MultiFileTest {

  @TempDir Path base;

  /** Locale file without a default file name. */
  static final class Messages {
    @Comment("Shown on join")
    @NotBlank
    private String welcome = "Welcome!";

    private String bye = "Bye!";

    String welcome() {
      return welcome;
    }
  }

  @ConfigFile("other.yml")
  static final class Other {
    private int value = 1;
  }

  @Test
  void oneClassBacksIndependentFiles() throws IOException {
    Files.createDirectories(base.resolve("messages"));
    Files.writeString(base.resolve("messages/ru.yml"), "welcome: Привет!\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<Messages> en = manager.load(Messages.class, "messages/en.yml");
      ConfigHandle<Messages> ru = manager.load(Messages.class, "messages/ru.yml");

      assertThat(en).isNotSameAs(ru);
      assertThat(en.get().welcome()).isEqualTo("Welcome!");
      assertThat(ru.get().welcome()).isEqualTo("Привет!");
      assertThat(en.file()).isEqualTo(base.resolve("messages/en.yml").toAbsolutePath());
      assertThat(Files.readString(base.resolve("messages/en.yml")))
          .isEqualTo("# Shown on join\nwelcome: Welcome!\nbye: Bye!\n");
      assertThat(Files.readString(base.resolve("messages/ru.yml")))
          .isEqualTo("# Shown on join\nwelcome: Привет!\nbye: Bye!\n");

      // Reloading one file never touches the other handle.
      Files.writeString(base.resolve("messages/ru.yml"), "welcome: '  '\n");
      ReloadResult<Messages> failed = ru.reload();
      assertThat(failed.successful()).isFalse();
      assertThat(failed.diagnostics())
          .extracting(ConfigDiagnostic::code)
          .containsExactly(DiagnosticCodes.BLANK);
      assertThat(ru.get().welcome()).isEqualTo("Привет!");
      assertThat(en.reload().successful()).isTrue();
    }
  }

  @Test
  void sameFileReturnsSameHandleWhateverTheSpelling() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<Messages> first = manager.load(Messages.class, "messages/en.yml");
      assertThat(manager.load(Messages.class, "messages/en.yml")).isSameAs(first);
      assertThat(manager.load(Messages.class, "messages/../messages/./en.yml")).isSameAs(first);
    }
  }

  @Test
  void oneFileCannotBackTwoTypes() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(Other.class);
      assertThatThrownBy(() -> manager.load(Messages.class, "other.yml"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("already loaded as")
          .hasMessageContaining(Other.class.getName());
    }
  }

  @Test
  void explicitNameOverridesConfigFileAndDefaultNeedsTheAnnotation() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(Other.class, "copies/other-2.yml");
      assertThat(base.resolve("copies/other-2.yml")).exists();
      assertThat(base.resolve("other.yml")).doesNotExist();

      assertThatThrownBy(() -> manager.load(Messages.class))
          .isInstanceOf(ConfigModelException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigModelException) e).diagnostics())
                      .singleElement()
                      .satisfies(
                          d -> {
                            assertThat(d.code()).isEqualTo(DiagnosticCodes.INVALID_MODEL);
                            assertThat(d.message()).contains("@ConfigFile");
                          }));
      assertThatThrownBy(() -> manager.previewMigration(Messages.class))
          .isInstanceOf(ConfigModelException.class);
    }
  }

  @Test
  void fileNamesAreCheckedLikeConfigFile() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(Messages.class, "../escape.yml"))
          .isInstanceOf(ConfigLoadException.class)
          .hasMessageContaining(DiagnosticCodes.UNSAFE_PATH);
      assertThatThrownBy(() -> manager.load(Messages.class, " "))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(base.getParent().resolve("escape.yml")).doesNotExist();
  }

  @Test
  void migrationsAndPreviewApplyToEveryFileOfTheType() throws IOException {
    Files.writeString(base.resolve("a.yml"), "motd: A\n");
    Files.writeString(base.resolve("b.yml"), "config-version: 3\ngreeting: B\n");
    try (ConfigManager manager =
        ConfigManager.builder(base)
            .migrations(VersionedConfig.class, VersionedConfig::migrations)
            .build()) {
      MigrationPreview preview = manager.previewMigration(VersionedConfig.class, "a.yml");
      assertThat(preview.storedVersion()).isEqualTo(1);
      assertThat(preview.wouldWrite()).isTrue();
      assertThat(Files.readString(base.resolve("a.yml"))).isEqualTo("motd: A\n");

      assertThat(manager.load(VersionedConfig.class, "a.yml").get().greeting()).isEqualTo("A");
      assertThat(manager.load(VersionedConfig.class, "b.yml").get().greeting()).isEqualTo("B");
      assertThat(base.resolve("a.yml.bak")).exists();
      assertThat(base.resolve("b.yml.bak")).doesNotExist();
    }
  }

  @Test
  void closeClosesEveryHandle() {
    ConfigManager manager = ConfigManager.builder(base).build();
    ConfigHandle<Messages> en = manager.load(Messages.class, "en.yml");
    ConfigHandle<Messages> de = manager.load(Messages.class, "de.yml");
    manager.close();
    assertThatThrownBy(en::reload).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(de::reload).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> manager.load(Messages.class, "fr.yml"))
        .isInstanceOf(IllegalStateException.class);
  }
}
