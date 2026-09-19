package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.yaml.testmodel.GoldenConfig;
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
        "unindented-sequences");
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
