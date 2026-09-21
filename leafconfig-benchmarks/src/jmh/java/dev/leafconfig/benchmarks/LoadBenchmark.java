package dev.leafconfig.benchmarks;

import dev.leafconfig.ConfigHandle;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.Range;
import dev.leafconfig.yaml.ConfigManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Baseline load and reload costs. {@code entries} controls the size of the {@code servers} map and
 * therefore the number of YAML keys (roughly {@code 3 * entries + 6}).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class LoadBenchmark {

  /** Root configuration used by every benchmark. */
  @ConfigFile("bench.yml")
  public static final class BenchConfig {
    private boolean debug = false;

    @Range(min = 1, max = 200)
    private int maxPlayers = 100;

    private String motd = "Welcome";
    private Duration timeout = Duration.ofSeconds(30);
    private List<String> worlds = List.of("world", "nether");
    private Map<String, Server> servers = new LinkedHashMap<>();

    /** Nested section repeated {@code entries} times. */
    public static final class Server {
      private String host = "localhost";

      @Range(min = 1, max = 65535)
      private int port = 25565;

      private boolean enabled = true;
    }
  }

  @Param({"1", "30", "300"})
  public int entries;

  private Path directory;
  private Path file;
  private ConfigManager manager;
  private ConfigHandle<BenchConfig> handle;
  private String fullDocument;
  private String reducedDocument;

  @Setup(Level.Trial)
  public void setUp() throws IOException {
    directory = Files.createTempDirectory("leafconfig-bench");
    file = directory.resolve("bench.yml");
    StringBuilder yaml =
        new StringBuilder(
            "debug: true\nmax-players: 50\nmotd: Hi\ntimeout: 1m\nworlds:\n  - a\n  - b\nservers:\n");
    for (int i = 0; i < entries; i++) {
      yaml.append("  s")
          .append(i)
          .append(":\n    host: h")
          .append(i)
          .append("\n    port: ")
          .append(1000 + i)
          .append("\n    enabled: false\n");
    }
    fullDocument = yaml.toString();
    // Same document without the top-level scalars: every load must merge them back and write.
    reducedDocument = fullDocument.substring(fullDocument.indexOf("worlds:"));
    Files.writeString(file, fullDocument);
    manager = ConfigManager.builder(directory).build();
    handle = manager.load(BenchConfig.class);
  }

  @TearDown(Level.Trial)
  public void tearDown() throws IOException {
    manager.close();
    Files.deleteIfExists(file);
    Files.deleteIfExists(directory);
  }

  /** Fresh manager: schema discovery plus one load of an unchanged file. */
  @Benchmark
  public BenchConfig coldDiscoveryAndLoad() {
    try (ConfigManager fresh = ConfigManager.builder(directory).build()) {
      return fresh.load(BenchConfig.class).get();
    }
  }

  /** Reload of an unchanged file through a warm handle: parse, decode, validate, no write. */
  @Benchmark
  public BenchConfig noopReload() {
    return handle.reload().current();
  }

  /** Reload of a file missing several keys: parse, decode, validate, merge and atomic write. */
  @Benchmark
  public BenchConfig mergeAndWriteReload() throws IOException {
    Files.writeString(file, reducedDocument);
    return handle.reload().current();
  }
}
