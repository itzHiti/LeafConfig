package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.annotation.FormerlyKnownAs;
import dev.leafconfig.migration.BackupPolicy;
import dev.leafconfig.migration.ConfigDiff;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.migration.Migrations;
import dev.leafconfig.yaml.testmodel.VersionedConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Migration transaction, renames, backups and dry runs. Golden cases live under {@code
 * src/test/resources/golden/migration}; record them with {@code -Dleafconfig.golden.record=true}.
 */
class MigrationTest {

  private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden", "migration");
  private static final boolean RECORD = Boolean.getBoolean("leafconfig.golden.record");

  private static final String V1 =
      """
      # Administrator header, must stay on top
      debug: true
      # Old database section
      mysql:
        ip: 10.0.0.1 # primary
        port: 3306
      motd: "Hi there"
      custom-flag: keep-me
      """;

  @TempDir Path base;

  private ConfigManager.Builder builder() {
    return ConfigManager.builder(base)
        .migrations(VersionedConfig.class, VersionedConfig::migrations);
  }

  private Path file() {
    return base.resolve("versioned.yml");
  }

  static Stream<String> goldenCases() {
    return Stream.of("upgrade-v1-to-v3", "upgrade-v2-to-v3", "rename-in-place", "first-generation");
  }

  @ParameterizedTest
  @MethodSource("goldenCases")
  void fileMatchesGolden(String name) throws IOException {
    Path caseDir = GOLDEN_DIR.resolve(name);
    Path input = caseDir.resolve("input.yml");
    if (Files.exists(input)) {
      Files.write(file(), Files.readAllBytes(input));
    }
    try (ConfigManager manager = builder().build()) {
      manager.load(VersionedConfig.class);
    }
    byte[] actual = Files.readAllBytes(file());
    Path expected = caseDir.resolve("expected.yml");
    if (RECORD) {
      Files.createDirectories(caseDir);
      Files.write(expected, actual);
    }
    assertThat(new String(actual, StandardCharsets.UTF_8))
        .isEqualTo(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));
  }

  @Test
  void upgradeMigratesValuesWritesBackupAndIsIdempotent() throws IOException {
    Files.writeString(file(), V1);
    try (ConfigManager manager = builder().build()) {
      ConfigHandle<VersionedConfig> handle = manager.load(VersionedConfig.class);
      VersionedConfig config = handle.get();
      assertThat(config.debug()).isTrue();
      assertThat(config.greeting()).isEqualTo("Hi there");
      assertThat(config.database().host()).isEqualTo("10.0.0.1");
      assertThat(config.database().port()).isEqualTo(3306);
      assertThat(config.database().poolSize()).isEqualTo(10);
      assertThat(handle.warnings())
          .extracting(ConfigDiagnostic::code)
          .containsExactly(DiagnosticCodes.VERSION_ASSUMED);
      assertThat(Files.readString(base.resolve("versioned.yml.bak"))).isEqualTo(V1);
      assertThat(Files.readString(file()))
          .contains("config-version: 3")
          .contains("custom-flag: keep-me");

      // Second load: nothing to migrate, nothing to write, no warning.
      byte[] migrated = Files.readAllBytes(file());
      FileTime old = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
      Files.setLastModifiedTime(file(), old);
      ReloadResult<VersionedConfig> result = handle.reload();
      assertThat(result.successful()).isTrue();
      assertThat(result.diagnostics()).isEmpty();
      assertThat(handle.warnings()).isEmpty();
      assertThat(Files.getLastModifiedTime(file())).isEqualTo(old);
      assertThat(Files.readAllBytes(file())).isEqualTo(migrated);
    }
  }

  @Test
  void backupPolicyNoneWritesNoBackup() throws IOException {
    Files.writeString(file(), V1);
    try (ConfigManager manager = builder().backupPolicy(BackupPolicy.NONE).build()) {
      manager.load(VersionedConfig.class);
    }
    assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
  }

  @Test
  void onlyStampingTheVersionWritesNoBackup() throws IOException {
    Files.writeString(file(), "config-version: 3\ndebug: true\n");
    try (ConfigManager manager = builder().build()) {
      manager.load(VersionedConfig.class);
    }
    assertThat(Files.readString(file()))
        .startsWith("config-version: 3\n")
        .contains("debug: true\n");
    assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
  }

  @Test
  void newerFileIsRejectedAndLeftUntouched() throws IOException {
    String text = "config-version: 4\ndebug: true\n";
    Files.writeString(file(), text);
    assertFailsWith(builder(), DiagnosticCodes.VERSION_TOO_NEW, text);
  }

  @Test
  void missingStepIsReported() throws IOException {
    Files.writeString(file(), V1);
    ConfigManager.Builder partial =
        ConfigManager.builder(base)
            .migrations(
                VersionedConfig.class,
                m -> m.from(2).to(3, d -> d.setIfMissing("database.pool-size", 10)));
    assertFailsWith(partial, DiagnosticCodes.MIGRATION_MISSING, V1);
  }

  @Test
  void failingStepLeavesFileAndSnapshotUntouched() throws IOException {
    Files.writeString(file(), "config-version: 3\ndebug: true\n");
    try (ConfigManager manager =
        ConfigManager.builder(base)
            .migrations(
                VersionedConfig.class,
                m ->
                    m.from(1)
                        .to(2, d -> d.rename("mysql.ip", "database.host"))
                        .from(2)
                        .to(
                            3,
                            d -> {
                              d.set("database.host", "partially-migrated");
                              throw new IllegalStateException("boom");
                            }))
            .build()) {
      ConfigHandle<VersionedConfig> handle = manager.load(VersionedConfig.class);
      VersionedConfig before = handle.get();

      String v2 = "config-version: 2\ndebug: false\ndatabase:\n  host: db\n";
      Files.writeString(file(), v2);
      ReloadResult<VersionedConfig> result = handle.reload();
      assertThat(result.successful()).isFalse();
      assertThat(result.current()).isSameAs(before);
      assertThat(result.diagnostics())
          .anySatisfy(
              d -> {
                assertThat(d.code()).isEqualTo(DiagnosticCodes.MIGRATION_FAILED);
                assertThat(d.path()).isEqualTo(ConfigPath.of(ConfigVersion.KEY));
                assertThat(d.message()).contains("2 to 3").contains("boom");
              });
      assertThat(Files.readString(file())).isEqualTo(v2);
      assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
    }
  }

  @Test
  void invalidVersionScalarIsReported() throws IOException {
    for (String text : List.of("config-version: two\n", "config-version: 0\n")) {
      Files.writeString(file(), text);
      assertFailsWith(builder(), DiagnosticCodes.INVALID_VALUE, text);
    }
    String huge = "config-version: 99999999999999999999\n";
    Files.writeString(file(), huge);
    assertFailsWith(builder(), DiagnosticCodes.VERSION_TOO_NEW, huge);
    // Quoted digits and integral floats are accepted like every other integer.
    Files.writeString(file(), "config-version: \"3\"\ndebug: true\n");
    try (ConfigManager manager = builder().build()) {
      assertThat(manager.load(VersionedConfig.class).warnings()).isEmpty();
    }
  }

  @Test
  void migratedDocumentStillGoesThroughValidation() throws IOException {
    Files.writeString(file(), "config-version: 2\ndatabase:\n  host: h\n  port: 70000\n");
    try (ConfigManager manager = builder().build()) {
      assertThatThrownBy(() -> manager.load(VersionedConfig.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .extracting(ConfigDiagnostic::code)
                      .containsExactly(DiagnosticCodes.OUT_OF_RANGE));
    }
    assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
  }

  @Test
  void renameConflictsAreRejected() throws IOException {
    String both = "config-version: 3\nmotd: a\ngreeting: b\n";
    Files.writeString(file(), both);
    assertFailsWith(builder(), DiagnosticCodes.RENAME_CONFLICT, both);
  }

  @ConfigFile("aliases.yml")
  static final class TwoFormerKeys {
    @FormerlyKnownAs({"old-name", "older-name"})
    private String name = "n";
  }

  @Test
  void severalFormerKeysPresentIsAConflictAndSingleOneIsRenamed() throws IOException {
    Path file = base.resolve("aliases.yml");
    Files.writeString(file, "old-name: a\nolder-name: b\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(TwoFormerKeys.class))
          .isInstanceOf(ConfigLoadException.class)
          .hasMessageContaining(DiagnosticCodes.RENAME_CONFLICT);
    }
    Files.writeString(file, "older-name: b\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThat(manager.load(TwoFormerKeys.class).get().name).isEqualTo("b");
    }
    assertThat(Files.readString(file)).isEqualTo("name: b\n");
    // Renames are user data changes, so a backup is kept even without @ConfigVersion.
    assertThat(Files.readString(base.resolve("aliases.yml.bak"))).isEqualTo("older-name: b\n");
  }

  @Test
  void previewReportsChangesWithoutWriting() throws IOException {
    Files.writeString(file(), V1);
    try (ConfigManager manager = builder().build()) {
      MigrationPreview preview = manager.previewMigration(VersionedConfig.class);
      assertThat(preview.successful()).isTrue();
      assertThat(preview.storedVersion()).isEqualTo(1);
      assertThat(preview.targetVersion()).isEqualTo(3);
      assertThat(preview.wouldWrite()).isTrue();
      assertThat(preview.diagnostics())
          .extracting(ConfigDiagnostic::code)
          .containsExactly(DiagnosticCodes.VERSION_ASSUMED);
      assertThat(preview.diff().render())
          .isEqualTo(
              """
              + config-version: 3
              + greeting: "Hi there"
              + database.host: "10.0.0.1"
              + database.port: 3306
              + database.pool-size: 10
              + timeout: "30s"
              - mysql.ip: "10.0.0.1"
              - mysql.port: 3306
              - motd: "Hi there"\
              """);
      assertThat(preview.diff().changes())
          .filteredOn(c -> c.kind() == ConfigDiff.Kind.REMOVED)
          .hasSize(3);
    }
    assertThat(Files.readString(file())).isEqualTo(V1);
    assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
  }

  @Test
  void previewOfCurrentFileHasNoChangesAndOfMissingFileShowsGeneration() throws IOException {
    try (ConfigManager manager = builder().build()) {
      MigrationPreview generation = manager.previewMigration(VersionedConfig.class);
      assertThat(generation.storedVersion()).isZero();
      assertThat(generation.wouldWrite()).isTrue();
      assertThat(generation.diff().changes())
          .allMatch(c -> c.kind() == ConfigDiff.Kind.ADDED)
          .extracting(c -> c.path().toString())
          .contains("config-version", "database.pool-size");
      assertThat(file()).doesNotExist();

      manager.load(VersionedConfig.class);
      MigrationPreview current = manager.previewMigration(VersionedConfig.class);
      assertThat(current.wouldWrite()).isFalse();
      assertThat(current.diff().isEmpty()).isTrue();
      assertThat(current.diff().render()).isEqualTo("(no changes)");
      assertThat(current.storedVersion()).isEqualTo(3);
    }
  }

  @Test
  void previewOfFailingLoadCarriesErrors() throws IOException {
    Files.writeString(file(), "config-version: 4\n");
    try (ConfigManager manager = builder().build()) {
      MigrationPreview preview = manager.previewMigration(VersionedConfig.class);
      assertThat(preview.successful()).isFalse();
      assertThat(preview.wouldWrite()).isFalse();
      assertThat(preview.storedVersion()).isEqualTo(4);
      assertThat(preview.diagnostics())
          .extracting(ConfigDiagnostic::code)
          .containsExactly(DiagnosticCodes.VERSION_TOO_NEW);
    }
  }

  @ConfigFile("plain.yml")
  static final class Unversioned {
    private int value = 1;
  }

  @ConfigFile("zero.yml")
  @ConfigVersion(0)
  static final class ZeroVersion {
    private int value = 1;
  }

  @ConfigFile("reserved.yml")
  @ConfigVersion(1)
  static final class ReservedKey {
    @dev.leafconfig.annotation.Key("config-version")
    private int value = 1;
  }

  @ConfigFile("former.yml")
  static final class BadFormerKey {
    @FormerlyKnownAs("a.b")
    private int value = 1;
  }

  @ConfigFile("clash.yml")
  static final class FormerKeyClash {
    private int value = 1;

    @FormerlyKnownAs("value")
    private int other = 2;
  }

  @Test
  void modelErrorsAreReported() {
    assertModelError(
        ConfigManager.builder(base).migrations(Unversioned.class, m -> m.from(1).to(2, d -> {})),
        Unversioned.class,
        "no @ConfigVersion");
    assertModelError(
        ConfigManager.builder(base)
            .migrations(VersionedConfig.class, m -> m.from(3).to(4, d -> {})),
        VersionedConfig.class,
        "targets version 4");
    assertModelError(ConfigManager.builder(base), ZeroVersion.class, "at least 1");
    assertModelError(ConfigManager.builder(base), ReservedKey.class, "reserved");
    assertModelError(ConfigManager.builder(base), BadFormerKey.class, "invalid former key");
    assertModelError(ConfigManager.builder(base), FormerKeyClash.class, "clashes");
  }

  @Test
  void migrationsMustBeSequentialAndUnique() {
    Migrations migrations = new Migrations();
    assertThatThrownBy(() -> migrations.from(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> migrations.from(1).to(3, d -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequential");
    migrations.from(1).to(2, d -> {});
    assertThatThrownBy(() -> migrations.from(1).to(2, d -> {}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");
    assertThat(migrations.highestTarget()).isEqualTo(2);
    assertThatThrownBy(
            () ->
                ConfigManager.builder(base)
                    .migrations(Unversioned.class, m -> {})
                    .migrations(Unversioned.class, m -> {}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private void assertFailsWith(ConfigManager.Builder builder, String code, String untouched)
      throws IOException {
    try (ConfigManager manager = builder.build()) {
      assertThatThrownBy(() -> manager.load(VersionedConfig.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .filteredOn(ConfigDiagnostic::isError)
                      .extracting(ConfigDiagnostic::code)
                      .containsExactly(code));
    }
    assertThat(Files.readString(file())).isEqualTo(untouched);
    assertThat(base.resolve("versioned.yml.bak")).doesNotExist();
  }

  private static void assertModelError(
      ConfigManager.Builder builder, Class<?> type, String messagePart) {
    try (ConfigManager manager = builder.build()) {
      assertThatThrownBy(() -> manager.load(type))
          .isInstanceOf(ConfigModelException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigModelException) e).diagnostics())
                      .anySatisfy(
                          d -> {
                            assertThat(d.code()).isEqualTo(DiagnosticCodes.INVALID_MODEL);
                            assertThat(d.message()).contains(messagePart);
                          }));
    }
  }
}
