package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigModelException;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Pattern;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.annotation.Required;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code Optional<T>}: YAML null and missing-key semantics, constraints and model rules. */
class OptionalTest {

  @TempDir Path base;

  enum Mode {
    FAST,
    SAFE
  }

  @ConfigFile("optional.yml")
  static final class Model {
    private Optional<String> motd = Optional.empty();

    @Range(min = 1, max = 100)
    private Optional<Integer> limit = Optional.of(10);

    private Optional<Duration> timeout = Optional.of(Duration.ofSeconds(30));

    @Pattern("[a-z]+")
    @NotBlank
    private Optional<String> code = Optional.empty();

    private Optional<Mode> mode = Optional.empty();

    // A null default is exposed as Optional.empty(), never as null.
    private Optional<String> unset = null;
  }

  private Model load() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      return manager.load(Model.class).get();
    }
  }

  private Path file() {
    return base.resolve("optional.yml");
  }

  @Test
  void generationWritesEmptyAsNullAndPresentValuesNormally() throws IOException {
    Model model = load();
    assertThat(Files.readString(file()))
        .isEqualTo(
            """
            motd: null
            limit: 10
            timeout: 30s
            code: null
            mode: null
            unset: null
            """);
    assertThat(model.motd).isEmpty();
    assertThat(model.limit).contains(10);
    assertThat(model.unset).isNotNull().isEmpty();
  }

  @Test
  void yamlNullIsEmptyValuesArePresentAndMissingKeysGetDefaults() throws IOException {
    Files.writeString(file(), "motd: Hello\nlimit: null\nmode: safe\nunset: x\n");
    Model model = load();
    assertThat(model.motd).contains("Hello");
    assertThat(model.limit).isEmpty();
    assertThat(model.mode).contains(Mode.SAFE);
    assertThat(model.unset).contains("x");
    assertThat(model.timeout).contains(Duration.ofSeconds(30));
    assertThat(model.code).isEmpty();
    // Missing keys are inserted next to their schema neighbours; existing values are untouched.
    assertThat(Files.readString(file()))
        .isEqualTo(
            """
            motd: Hello
            limit: null
            timeout: 30s
            code: null
            mode: safe
            unset: x
            """);
  }

  @Test
  void constraintsApplyToThePresentValueOnly() throws IOException {
    Files.writeString(file(), "limit: 500\ncode: ABC\ntimeout: soon\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(Model.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .extracting(d -> d.path().toString(), ConfigDiagnostic::code)
                      .containsExactlyInAnyOrder(
                          org.assertj.core.groups.Tuple.tuple(
                              "limit", DiagnosticCodes.OUT_OF_RANGE),
                          org.assertj.core.groups.Tuple.tuple(
                              "code", DiagnosticCodes.PATTERN_MISMATCH),
                          org.assertj.core.groups.Tuple.tuple(
                              "timeout", DiagnosticCodes.INVALID_VALUE)));
    }
    // Empty values pass every constraint.
    Files.writeString(file(), "limit: null\ncode: null\n");
    assertThat(load().limit).isEmpty();
  }

  @ConfigFile("bad.yml")
  static final class RequiredOptional {
    @Required private Optional<String> value = Optional.empty();
  }

  @ConfigFile("bad.yml")
  static final class OptionalInList {
    private List<Optional<String>> values = List.of();
  }

  @ConfigFile("bad.yml")
  static final class OptionalOfList {
    private Optional<List<String>> values = Optional.empty();
  }

  @ConfigFile("bad.yml")
  static final class OptionalOfSection {
    private Optional<Section> section = Optional.empty();

    static final class Section {
      private int a = 1;
    }
  }

  @ConfigFile("bad.yml")
  static final class RangeOnOptionalString {
    @Range(min = 1, max = 2)
    private Optional<String> value = Optional.empty();
  }

  @Test
  void modelRulesAreEnforced() {
    assertModelError(RequiredOptional.class, "@Required contradicts Optional");
    assertModelError(OptionalInList.class, "only supported as the declared type of a field");
    assertModelError(OptionalOfList.class, "scalar-like values only");
    assertModelError(OptionalOfSection.class, "scalar-like values only");
    assertModelError(RangeOnOptionalString.class, "found java.lang.String");
  }

  private void assertModelError(Class<?> type, String messagePart) {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(type))
          .isInstanceOf(ConfigModelException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigModelException) e).diagnostics())
                      .anySatisfy(d -> assertThat(d.message()).contains(messagePart)));
    }
  }
}
