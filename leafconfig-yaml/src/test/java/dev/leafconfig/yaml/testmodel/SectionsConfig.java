package dev.leafconfig.yaml.testmodel;

import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Model with configuration sections inside a map and a list, used to prove that fields added to a
 * section class reach every existing entry.
 */
@ConfigFile("sections.yml")
public final class SectionsConfig {

  @Comment("Backend servers by name")
  private Map<String, Server> servers = new LinkedHashMap<>(Map.of("main", new Server()));

  private List<Reward> rewards = List.of(new Reward());

  public Map<String, Server> servers() {
    return servers;
  }

  public List<Reward> rewards() {
    return rewards;
  }

  /** Map value section. */
  public static final class Server {
    @Comment("Host name")
    private String host = "localhost";

    private int port = 25565;

    @Comment("Added in a later release")
    private boolean enabled = true;

    private Limits limits = new Limits();

    public String host() {
      return host;
    }

    public int port() {
      return port;
    }

    public boolean enabled() {
      return enabled;
    }

    public Limits limits() {
      return limits;
    }
  }

  /** Section nested inside a map value. */
  public static final class Limits {
    private int players = 10;

    private int queue = 5;

    public int players() {
      return players;
    }

    public int queue() {
      return queue;
    }
  }

  /** List element section. */
  public static final class Reward {
    private String item = "stone";

    private int amount = 1;

    public String item() {
      return item;
    }

    public int amount() {
      return amount;
    }
  }
}
