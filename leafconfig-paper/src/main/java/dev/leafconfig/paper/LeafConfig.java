package dev.leafconfig.paper;

import dev.leafconfig.yaml.ConfigManager;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for Paper plugins.
 *
 * <p>{@link #forPlugin(JavaPlugin)} returns a {@link ConfigManager.Builder} rooted in the plugin's
 * data folder with the {@link PaperAdapters Paper adapters} registered at module precedence, so a
 * plugin may still override any of them with {@link ConfigManager.Builder#adapter}. Nothing is
 * registered with the server: no commands, listeners or tasks. Call {@link ConfigManager#close()}
 * from {@code onDisable()} so reload listeners and cached metadata are released with the plugin.
 *
 * <p>Design note: the original API sketch showed an explicit {@code registerPaperAdapters()}
 * step. It is omitted because there is no supported configuration in which a Paper plugin would
 * want the Paper adapters absent; user adapters already take precedence over them.
 */
public final class LeafConfig {

  private LeafConfig() {}

  /**
   * Starts a manager builder for {@code plugin}, using its data folder as base directory.
   *
   * <p>Loading during {@code onEnable()} is synchronous file I/O on the server thread; keep initial
   * configurations small or load them before the server starts ticking.
   */
  public static ConfigManager.Builder forPlugin(JavaPlugin plugin) {
    Objects.requireNonNull(plugin, "plugin");
    ConfigManager.Builder builder = ConfigManager.builder(plugin.getDataFolder().toPath());
    PaperAdapters.register(builder);
    return builder;
  }
}
