package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.yaml.testmodel.GoldenConfig;
import dev.leafconfig.yaml.testmodel.SectionsConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden-file tests for generation and non-destructive merge. Each case directory under {@code
 * src/test/resources/golden} holds an optional {@code input.yml} and the {@code expected.yml} the
 * file must contain after one load. Run with {@code -Dleafconfig.golden.record=true} to rewrite the
 * expected files, then review the diff by hand before committing.
 */
class GoldenFileTest {

  private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");
  private static final boolean RECORD = Boolean.getBoolean("leafconfig.golden.record");

  @TempDir Path base;

  static Stream<String> cases() {
    return Stream.of(
        "first-generation",
        "add-defaults",
        "preserve-everything",
        "schema-comment-insertion",
        "nested-partial",
        "empty-collections-and-null",
        "unicode-crlf",
        "four-space-indent",
        "unindented-sequences",
        "comments-only");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void fileMatchesGolden(String name) throws IOException {
    Path caseDir = GOLDEN_DIR.resolve(name);
    Path input = caseDir.resolve("input.yml");
    Path file = base.resolve("golden.yml");
    if (Files.exists(input)) {
      Files.write(file, Files.readAllBytes(input));
    }

    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(GoldenConfig.class);
    }

    byte[] actual = Files.readAllBytes(file);
    Path expected = caseDir.resolve("expected.yml");
    if (RECORD) {
      Files.createDirectories(caseDir);
      Files.write(expected, actual);
    }
    assertThat(new String(actual, StandardCharsets.UTF_8))
        .isEqualTo(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));
  }

  /**
   * Fields added to a section class reach every existing map value and list element. Entries get
   * missing keys only, never schema comments, exactly like freshly generated entries.
   */
  @Test
  void sectionsInsideCollectionsReceiveMissingKeys() throws IOException {
    Path caseDir = GOLDEN_DIR.resolve("sections-in-collections");
    Path file = base.resolve("sections.yml");
    Files.write(file, Files.readAllBytes(caseDir.resolve("input.yml")));
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      SectionsConfig config = manager.load(SectionsConfig.class).get();
      assertThat(config.servers().get("lobby").limits().players()).isEqualTo(50);
      assertThat(config.rewards().get(1).amount()).isEqualTo(3);
    }
    byte[] actual = Files.readAllBytes(file);
    Path expected = caseDir.resolve("expected.yml");
    if (RECORD) {
      Files.write(expected, actual);
    }
    assertThat(new String(actual, StandardCharsets.UTF_8))
        .isEqualTo(new String(Files.readAllBytes(expected), StandardCharsets.UTF_8));

    // Second load: complete entries are not touched again.
    byte[] merged = Files.readAllBytes(file);
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(SectionsConfig.class);
    }
    assertThat(Files.readAllBytes(file)).isEqualTo(merged);
  }

  @Test
  void malformedFileIsLeftUntouched() throws IOException {
    Path file = base.resolve("golden.yml");
    String malformed = "debug: [unclosed\nmax-players: 5\n";
    Files.writeString(file, malformed);
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(GoldenConfig.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertCode(((ConfigLoadException) e).diagnostics(), DiagnosticCodes.YAML_SYNTAX));
    }
    assertThat(Files.readString(file)).isEqualTo(malformed);
  }

  @Test
  void duplicateKeysAreRejectedAndFileUntouched() throws IOException {
    Path file = base.resolve("golden.yml");
    String duplicate = "debug: true\ndebug: false\n";
    Files.writeString(file, duplicate);
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(GoldenConfig.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertCode(
                      ((ConfigLoadException) e).diagnostics(), DiagnosticCodes.DUPLICATE_KEY));
    }
    assertThat(Files.readString(file)).isEqualTo(duplicate);
  }

  @Test
  void anchorsAndCustomTagsAreRejected() throws IOException {
    Path file = base.resolve("golden.yml");
    Files.writeString(file, "motd: &m hello\nnullable: *m\nworlds: !!set {a: null}\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(GoldenConfig.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e -> {
                List<ConfigDiagnostic> diagnostics = ((ConfigLoadException) e).diagnostics();
                assertCode(diagnostics, DiagnosticCodes.ALIAS_UNSUPPORTED);
                assertCode(diagnostics, DiagnosticCodes.TAG_UNSUPPORTED);
              });
    }
  }

  private static void assertCode(List<ConfigDiagnostic> diagnostics, String code) {
    assertThat(diagnostics).extracting(ConfigDiagnostic::code).contains(code);
  }
}
