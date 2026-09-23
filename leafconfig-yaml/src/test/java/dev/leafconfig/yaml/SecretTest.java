package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.adapter.ConfigDecodeException;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.Pattern;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.annotation.Secret;
import dev.leafconfig.migration.MigrationPreview;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code @Secret} values never reach diagnostic messages or dry-run diffs. */
class SecretTest {

  private static final String PASSWORD = "hunter2-SECRET";
  private static final String TOKEN = "tok-SECRET";
  private static final String KEY = "key-SECRET";

  @TempDir Path base;

  /** Opaque value decoded by a user adapter that quotes its input in error messages. */
  record ApiKey(String value) {}

  static final class ApiKeyAdapter implements TypeAdapter<ApiKey> {
    @Override
    public ApiKey decode(ConfigNode node, DecodeContext context) {
      String text = ((ScalarNode) node).value();
      if (!text.startsWith("ak_")) {
        throw new ConfigDecodeException(
            DiagnosticCodes.INVALID_VALUE, "not an API key: '" + text + "'");
      }
      return new ApiKey(text);
    }

    @Override
    public ConfigNode encode(ApiKey value, EncodeContext context) {
      return ScalarNode.ofString(value.value());
    }
  }

  @ConfigFile("secret.yml")
  static final class Model {
    @Secret
    @Pattern("[a-z]+")
    private String password = "changeme";

    @Range(min = 1, max = 10)
    private int visible = 1;

    @Secret private Credentials credentials = new Credentials();

    private Map<String, Server> servers = new LinkedHashMap<>(Map.of("main", new Server()));

    @Secret private List<Integer> pins = List.of(1);

    @Secret private ApiKey apiKey = new ApiKey("ak_default");

    String password() {
      return password;
    }
  }

  static final class Credentials {
    @Range(min = 1, max = 10)
    private int level = 1;
  }

  static final class Server {
    private String host = "localhost";

    @Secret
    @Pattern("[a-z]+")
    private String token = "abc";
  }

  private ConfigManager.Builder builder() {
    return ConfigManager.builder(base)
        .adapter(ApiKey.class, new ApiKeyAdapter())
        .validator(
            Model.class,
            (config, context) ->
                context.error(
                    ConfigPath.of("password"), "custom validator quoting " + config.password()));
  }

  @Test
  void messagesAtSecretPathsAreRedactedButCodesPathsAndLinesStay() throws IOException {
    Files.writeString(
        base.resolve("secret.yml"),
        """
        password: %s
        visible: 99
        credentials:
          level: 55
        servers:
          main:
            host: h
            token: %s
        pins: [x]
        api-key: %s
        """
            .formatted(PASSWORD, TOKEN, KEY));
    try (ConfigManager manager = builder().build()) {
      assertThatThrownBy(() -> manager.load(Model.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e -> {
                ConfigLoadException failure = (ConfigLoadException) e;
                assertThat(failure.getMessage())
                    .doesNotContain(PASSWORD)
                    .doesNotContain(TOKEN)
                    .doesNotContain(KEY)
                    .doesNotContain("55")
                    // Non-secret diagnostics keep their text.
                    .contains("expected 1..10, got 99");
                List<ConfigDiagnostic> diagnostics = failure.diagnostics();
                assertThat(diagnostics)
                    .extracting(d -> d.path().toString(), ConfigDiagnostic::code)
                    .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                            "password", DiagnosticCodes.PATTERN_MISMATCH),
                        org.assertj.core.groups.Tuple.tuple(
                            "visible", DiagnosticCodes.OUT_OF_RANGE),
                        org.assertj.core.groups.Tuple.tuple(
                            "credentials.level", DiagnosticCodes.OUT_OF_RANGE),
                        org.assertj.core.groups.Tuple.tuple(
                            "servers.main.token", DiagnosticCodes.PATTERN_MISMATCH),
                        org.assertj.core.groups.Tuple.tuple(
                            "pins[0]", DiagnosticCodes.INVALID_VALUE),
                        org.assertj.core.groups.Tuple.tuple(
                            "api-key", DiagnosticCodes.INVALID_VALUE));
                assertThat(diagnostics)
                    .filteredOn(d -> !d.path().toString().equals("visible"))
                    .allSatisfy(
                        d -> {
                          assertThat(d.message()).contains("@Secret");
                          assertThat(d.line()).isNotNull();
                        });
              });
    }
  }

  @Test
  void programmaticValidatorMessagesAreRedactedToo() throws IOException {
    Files.writeString(base.resolve("secret.yml"), "password: abc\n");
    try (ConfigManager manager = builder().build()) {
      assertThatThrownBy(() -> manager.load(Model.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .singleElement()
                      .satisfies(
                          d -> {
                            assertThat(d.code()).isEqualTo(DiagnosticCodes.VALIDATION_FAILED);
                            assertThat(d.message()).doesNotContain("abc").contains("@Secret");
                          }));
    }
  }

  @Test
  void dryRunDiffHidesSecretValues() {
    try (ConfigManager manager =
        ConfigManager.builder(base).adapter(ApiKey.class, new ApiKeyAdapter()).build()) {
      MigrationPreview preview = manager.previewMigration(Model.class);
      String rendered = preview.diff().render();
      assertThat(rendered)
          .contains("+ password: \"***\"")
          .contains("+ credentials.level: \"***\"")
          .contains("+ servers.main.token: \"***\"")
          .contains("+ servers.main.host: \"localhost\"")
          .contains("+ pins: \"***\"")
          .contains("+ visible: 1")
          .doesNotContain("changeme")
          .doesNotContain("ak_default");
    }
  }

  @Test
  void secretValuesStillLoadAndAreWrittenNormally() throws IOException {
    try (ConfigManager manager =
        ConfigManager.builder(base).adapter(ApiKey.class, new ApiKeyAdapter()).build()) {
      assertThat(manager.load(Model.class).get().password()).isEqualTo("changeme");
    }
    assertThat(Files.readString(base.resolve("secret.yml")))
        .contains("password: changeme")
        .contains("api-key: ak_default");
  }
}
