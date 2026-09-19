package dev.leafconfig.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ConfigPath;
import dev.leafconfig.DiagnosticCodes;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.adapter.DecodeContext;
import dev.leafconfig.adapter.EncodeContext;
import dev.leafconfig.adapter.TypeAdapter;
import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Pattern;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.annotation.Required;
import dev.leafconfig.node.ConfigNode;
import dev.leafconfig.node.ScalarNode;
import dev.leafconfig.yaml.testmodel.GoldenConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {

  @TempDir Path base;

  @ConfigFile("validated.yml")
  static final class Validated {
    @Required private String name = null;

    @NotBlank private String title = "t";

    @Range(min = 1, max = 65535)
    private int port = 25565;

    @Pattern("[a-z]+")
    private String code = "abc";

    private Integer optional = 5;

    private int primitive = 1;

    private List<Server> servers = List.of();

    static final class Server {
      @Range(min = 1, max = 100)
      private int weight = 1;
    }
  }

  @Test
  void generatesFileFromDefaultsAndReturnsInstance() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<GoldenConfig> handle = manager.load(GoldenConfig.class);
      assertThat(handle.file()).isEqualTo(base.resolve("golden.yml"));
      assertThat(handle.type()).isEqualTo(GoldenConfig.class);
      assertThat(handle.get().maxPlayers()).isEqualTo(100);
      assertThat(handle.get().database().host()).isEqualTo("localhost");
      assertThat(Files.exists(handle.file())).isTrue();
      assertThat(manager.load(GoldenConfig.class)).isSameAs(handle);
    }
  }

  @Test
  void existingValuesWinAndCollectionsAreUnmodifiable() throws IOException {
    Files.writeString(
        base.resolve("golden.yml"),
        "max-players: 7\nworlds: [a, b]\nmodes: [creative]\nlimits: {x: 1}\ndatabase:\n  port: 1\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      GoldenConfig config = manager.load(GoldenConfig.class).get();
      assertThat(config.maxPlayers()).isEqualTo(7);
      assertThat(config.worlds()).containsExactly("a", "b");
      assertThat(config.modes()).containsExactly(GoldenConfig.Mode.CREATIVE);
      assertThat(config.limits()).containsEntry("x", 1);
      assertThat(config.database().port()).isEqualTo(1);
      assertThat(config.database().host()).isEqualTo("localhost");
      assertThatThrownBy(() -> config.worlds().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void aggregatesAllErrorsWithPathsAndLinesAndLeavesFileUntouched() throws IOException {
    String yaml =
        """
        title: "   "
        port: 70000
        code: ABC
        optional: null
        primitive: null
        servers:
          - weight: 5
          - weight: 500
        """;
    Path file = base.resolve("validated.yml");
    Files.writeString(file, yaml);
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigLoadException e =
          (ConfigLoadException)
              assertThatThrownBy(() -> manager.load(Validated.class))
                  .isInstanceOf(ConfigLoadException.class)
                  .actual();
      assertThat(e.file()).isEqualTo(file);
      assertThat(e.diagnostics())
          .extracting(d -> d.path().toString() + " " + d.code() + " " + d.line())
          .containsExactlyInAnyOrder(
              "name MISSING_REQUIRED 1",
              "primitive NULL_NOT_ALLOWED 5",
              "title BLANK 1",
              "port OUT_OF_RANGE 2",
              "code PATTERN_MISMATCH 3",
              "servers[1].weight OUT_OF_RANGE 8");
      assertThat(e.getMessage())
          .contains("Invalid configuration: ")
          .contains("port [OUT_OF_RANGE]: expected 1..65535, got 70000 (line 2, column 7)");
    }
    assertThat(Files.readString(file)).isEqualTo(yaml);
  }

  @Test
  void typeMismatchesAreReportedPerKey() throws IOException {
    Files.writeString(
        base.resolve("validated.yml"), "name: n\nport: [1]\ncode: {a: b}\nservers: nope\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(Validated.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .extracting(d -> d.path().toString() + " " + d.code())
                      .containsExactly(
                          "port TYPE_MISMATCH", "code TYPE_MISMATCH", "servers TYPE_MISMATCH"));
    }
  }

  @Test
  void customValidatorRunsAfterDecoding() throws IOException {
    Files.writeString(base.resolve("validated.yml"), "name: n\n");
    try (ConfigManager manager =
        ConfigManager.builder(base)
            .validator(
                Validated.class,
                (config, context) -> {
                  if (config.port == 25565) {
                    context.error(ConfigPath.of("port"), "default port is not allowed");
                  }
                  context.warning(ConfigPath.root(), "NOTE", "checked");
                })
            .build()) {
      assertThatThrownBy(() -> manager.load(Validated.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .extracting(ConfigDiagnostic::code)
                      .containsExactly(DiagnosticCodes.VALIDATION_FAILED, "NOTE"));
    }
  }

  @Test
  void unchangedDocumentIsNotRewritten() throws IOException {
    Path file = base.resolve("golden.yml");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(GoldenConfig.class);
    }
    byte[] generated = Files.readAllBytes(file);
    FileTime old = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
    Files.setLastModifiedTime(file, old);
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(GoldenConfig.class).reload();
    }
    assertThat(Files.getLastModifiedTime(file)).isEqualTo(old);
    assertThat(Files.readAllBytes(file)).isEqualTo(generated);
  }

  @Test
  void reloadPublishesNewSnapshotOnlyOnSuccess() throws IOException {
    Path file = base.resolve("golden.yml");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      ConfigHandle<GoldenConfig> handle = manager.load(GoldenConfig.class);
      GoldenConfig first = handle.get();
      List<GoldenConfig> seen = new ArrayList<>();
      handle.onReload(seen::add);
      handle.onReload(
          config -> {
            throw new IllegalStateException("listener boom");
          });

      Files.writeString(file, "max-players: 42\n");
      ReloadResult<GoldenConfig> ok = handle.reload();
      assertThat(ok.successful()).isTrue();
      assertThat(ok.current().maxPlayers()).isEqualTo(42);
      assertThat(handle.get()).isSameAs(ok.current()).isNotSameAs(first);
      assertThat(first.maxPlayers()).isEqualTo(100);
      assertThat(seen).containsExactly(ok.current());
      assertThat(ok.diagnostics())
          .singleElement()
          .satisfies(d -> assertThat(d.code()).isEqualTo(DiagnosticCodes.LISTENER_FAILED));

      String broken = "max-players: 999\n";
      Files.writeString(file, broken);
      ReloadResult<GoldenConfig> failed = handle.reload();
      assertThat(failed.successful()).isFalse();
      assertThat(failed.current()).isSameAs(ok.current());
      assertThat(handle.get()).isSameAs(ok.current());
      assertThat(failed.diagnostics())
          .extracting(ConfigDiagnostic::code)
          .containsExactly(DiagnosticCodes.OUT_OF_RANGE);
      assertThat(Files.readString(file)).isEqualTo(broken);
      assertThat(seen).hasSize(1);
    }
  }

  @Test
  void concurrentReloadsAreSerialized() throws Exception {
    AtomicInteger inFlight = new AtomicInteger();
    List<String> overlaps = new CopyOnWriteArrayList<>();
    try (ConfigManager manager =
        ConfigManager.builder(base)
            .validator(
                GoldenConfig.class,
                (config, context) -> {
                  if (inFlight.incrementAndGet() != 1) {
                    overlaps.add("overlap");
                  }
                  inFlight.decrementAndGet();
                })
            .build()) {
      ConfigHandle<GoldenConfig> handle = manager.load(GoldenConfig.class);
      int threads = 8;
      CyclicBarrier barrier = new CyclicBarrier(threads);
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      try {
        List<Future<ReloadResult<GoldenConfig>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
          futures.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    return handle.reload();
                  }));
        }
        for (Future<ReloadResult<GoldenConfig>> future : futures) {
          assertThat(future.get(30, TimeUnit.SECONDS).successful()).isTrue();
        }
      } finally {
        pool.shutdownNow();
      }
      assertThat(overlaps).isEmpty();
    }
  }

  @Test
  void closeIsIdempotentAndBlocksReload() {
    ConfigManager manager = ConfigManager.builder(base).build();
    ConfigHandle<GoldenConfig> handle = manager.load(GoldenConfig.class);
    manager.close();
    manager.close();
    assertThat(handle.get()).isNotNull();
    assertThatThrownBy(handle::reload).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> manager.load(GoldenConfig.class))
        .isInstanceOf(IllegalStateException.class);
  }

  @ConfigFile("../escape.yml")
  static final class Escaping {
    private int a = 1;
  }

  @Test
  void unsafeFileNamesAreRejected() {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThatThrownBy(() -> manager.load(Escaping.class))
          .isInstanceOf(ConfigLoadException.class)
          .satisfies(
              e ->
                  assertThat(((ConfigLoadException) e).diagnostics())
                      .extracting(ConfigDiagnostic::code)
                      .containsExactly(DiagnosticCodes.UNSAFE_PATH));
    }
    assertThat(Files.exists(base.getParent().resolve("escape.yml"))).isFalse();
  }

  @Test
  void limitsAreEnforced() throws IOException {
    Path file = base.resolve("golden.yml");
    Files.writeString(file, "motd: " + "x".repeat(200) + "\n");
    try (ConfigManager manager =
        ConfigManager.builder(base).limits(new YamlLimits(100, 64, 1000, 1000)).build()) {
      assertCode(manager, DiagnosticCodes.LIMIT_EXCEEDED);
    }
    try (ConfigManager manager =
        ConfigManager.builder(base).limits(new YamlLimits(1_000_000, 64, 1000, 10)).build()) {
      assertCode(manager, DiagnosticCodes.LIMIT_EXCEEDED);
    }
    Files.writeString(file, "limits:\n  a: {b: {c: 1}}\n");
    try (ConfigManager manager =
        ConfigManager.builder(base).limits(new YamlLimits(1_000_000, 2, 1000, 1000)).build()) {
      assertCode(manager, DiagnosticCodes.LIMIT_EXCEEDED);
    }
    Files.writeString(file, "- a\n");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertCode(manager, DiagnosticCodes.TYPE_MISMATCH);
    }
    Files.write(file, new byte[] {(byte) 0xC3, (byte) 0x28});
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertCode(manager, DiagnosticCodes.YAML_SYNTAX);
    }
  }

  @Test
  void emptyFileIsFilledWithDefaults() throws IOException {
    Path file = base.resolve("golden.yml");
    Files.writeString(file, "");
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      assertThat(manager.load(GoldenConfig.class).get().maxPlayers()).isEqualTo(100);
    }
    assertThat(Files.readString(file)).startsWith("# Golden fixture configuration");
  }

  private static final TypeAdapter<String> SHOUTING =
      new TypeAdapter<>() {
        @Override
        public String decode(ConfigNode node, DecodeContext context) {
          return ((ScalarNode) node).value().toUpperCase(java.util.Locale.ROOT);
        }

        @Override
        public ConfigNode encode(String value, EncodeContext context) {
          return ScalarNode.ofString(value);
        }
      };

  @Test
  void userAdaptersOverrideBuiltinsAndConflictsFail() throws IOException {
    Files.writeString(base.resolve("golden.yml"), "motd: hi\n");
    try (ConfigManager manager =
        ConfigManager.builder(base).adapter(String.class, SHOUTING).build()) {
      assertThat(manager.load(GoldenConfig.class).get().motd()).isEqualTo("HI");
    }
    assertThatThrownBy(
            () ->
                ConfigManager.builder(base)
                    .adapter(String.class, SHOUTING)
                    .adapter(String.class, SHOUTING))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("conflicting user adapters for java.lang.String");
    ConfigManager.builder(base)
        .adapter(String.class, SHOUTING)
        .moduleAdapter(String.class, SHOUTING)
        .build()
        .close();
  }

  private static void assertCode(ConfigManager manager, String code) {
    assertThatThrownBy(() -> manager.load(GoldenConfig.class))
        .isInstanceOf(ConfigLoadException.class)
        .satisfies(
            e ->
                assertThat(((ConfigLoadException) e).diagnostics())
                    .extracting(ConfigDiagnostic::code)
                    .contains(code));
  }

  @ConfigFile("commented.yml")
  @Comment("Header")
  static final class Commented {
    @Comment("first")
    private int a = 1;
  }

  @Test
  void headerAndKeyCommentsAreSeparatedByBlankLine() throws IOException {
    try (ConfigManager manager = ConfigManager.builder(base).build()) {
      manager.load(Commented.class);
    }
    assertThat(Files.readString(base.resolve("commented.yml")))
        .isEqualTo("# Header\n\n# first\na: 1\n");
  }
}
