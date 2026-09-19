package dev.leafconfig.yaml.testmodel;

import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Range;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Model shared by the golden-file tests. Field order defines generated key order. */
@ConfigFile("golden.yml")
@Comment({"Golden fixture configuration", "Edit values freely; comments are preserved"})
public final class GoldenConfig {

  @Comment("Enable debug output")
  private boolean debug = false;

  @Comment({"Maximum number of players", "Must be between 1 and 200"})
  @Range(min = 1, max = 200)
  private int maxPlayers = 100;

  @Comment("Greeting shown on join")
  private String motd = "Welcome!";

  private List<String> worlds = List.of("world", "world_nether");

  private Set<Mode> modes = new LinkedHashSet<>(List.of(Mode.SURVIVAL, Mode.CREATIVE));

  private Map<String, Integer> limits = new LinkedHashMap<>(Map.of("default", 10));

  @Comment("Database settings")
  private Database database = new Database();

  private String nullable = null;

  private Duration timeout = Duration.ofSeconds(90);

  private UUID owner = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

  private double ratio = 0.75;

  private BigDecimal price = new BigDecimal("19.99");

  public boolean debug() {
    return debug;
  }

  public int maxPlayers() {
    return maxPlayers;
  }

  public String motd() {
    return motd;
  }

  public List<String> worlds() {
    return worlds;
  }

  public Set<Mode> modes() {
    return modes;
  }

  public Map<String, Integer> limits() {
    return limits;
  }

  public Database database() {
    return database;
  }

  public String nullable() {
    return nullable;
  }

  public Duration timeout() {
    return timeout;
  }

  public UUID owner() {
    return owner;
  }

  public double ratio() {
    return ratio;
  }

  public BigDecimal price() {
    return price;
  }

  /** Game modes for the enum adapter. */
  public enum Mode {
    SURVIVAL,
    CREATIVE,
    ADVENTURE
  }

  /** Nested section. */
  public static final class Database {

    @Comment("Host name of the database server")
    @NotBlank
    private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 5432;

    private List<String> tags = List.of();

    private Map<String, String> options = new LinkedHashMap<>();

    public String host() {
      return host;
    }

    public int port() {
      return port;
    }

    public List<String> tags() {
      return tags;
    }

    public Map<String, String> options() {
      return options;
    }
  }
}
