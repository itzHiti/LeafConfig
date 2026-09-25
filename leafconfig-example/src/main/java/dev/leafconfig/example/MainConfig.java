package dev.leafconfig.example;

import dev.leafconfig.annotation.Comment;
import dev.leafconfig.annotation.ConfigFile;
import dev.leafconfig.annotation.ConfigVersion;
import dev.leafconfig.annotation.NotBlank;
import dev.leafconfig.annotation.Range;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * Configuration of the example plugin; every field is persisted to {@code config.yml}. The version
 * is stored as {@code config-version}; bump it and register a migration on the manager when a key
 * moves.
 */
// snippet-start: readme-main-config
@ConfigFile("config.yml")
@ConfigVersion(1)
@Comment({"LeafConfig example plugin", "Edit and run /leafconfigexample reload"})
public final class MainConfig {

  @Comment("Enable verbose diagnostic messages")
  private boolean debug = false;

  @Comment("Maximum number of players allowed to use the reward")
  @Range(min = 1, max = 200)
  private int maxPlayers = 100;

  @Comment("Prefix shown before every message, MiniMessage format")
  private Component prefix =
      MiniMessage.miniMessage().deserialize("<gray>[<green>Leaf</green>]</gray> ");

  @Comment("Item handed out by the reward")
  private Material rewardItem = Material.GOLDEN_APPLE;

  @Comment("How long a player has to wait between rewards")
  private Duration cooldown = Duration.ofMinutes(5);

  @Comment({"Sound played on reward, for example entity.player.levelup", "null disables the sound"})
  private Sound rewardSound = null;

  private List<String> enabledWorlds = List.of("world");

  @Comment("Database connection settings")
  private Database database = new Database();

  // snippet-skip: // accessors omitted
  public boolean debug() {
    return debug;
  }

  public int maxPlayers() {
    return maxPlayers;
  }

  public Component prefix() {
    return prefix;
  }

  public Material rewardItem() {
    return rewardItem;
  }

  public Duration cooldown() {
    return cooldown;
  }

  public Sound rewardSound() {
    return rewardSound;
  }

  public List<String> enabledWorlds() {
    return enabledWorlds;
  }

  public Database database() {
    return database;
  }

  // snippet-skip-end

  /** Nested section rendered as {@code database:}. */
  public static final class Database {

    @NotBlank private String host = "localhost";

    @Range(min = 1, max = 65535)
    private int port = 5432;

    // snippet-skip:

    public String host() {
      return host;
    }

    public int port() {
      return port;
    }
    // snippet-skip-end
  }
}
// snippet-end: readme-main-config
