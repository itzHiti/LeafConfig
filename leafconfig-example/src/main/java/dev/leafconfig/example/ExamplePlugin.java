package dev.leafconfig.example;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ConfigLoadException;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.paper.LeafConfig;
import dev.leafconfig.yaml.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minimal plugin showing the whole LeafConfig lifecycle: build a manager for the plugin, load the
 * configuration during {@code onEnable()}, reload it on command while keeping the old snapshot on
 * failure, and close the manager on disable.
 */
public final class ExamplePlugin extends JavaPlugin {

  private ConfigManager manager;
  private ConfigHandle<MainConfig> config;

  @Override
  public void onEnable() {
    manager = LeafConfig.forPlugin(this).build();
    try {
      config = manager.load(MainConfig.class);
    } catch (ConfigLoadException e) {
      // The message already lists every diagnostic with path and line; the file was left untouched.
      getLogger().severe(e.getMessage());
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    config.onReload(current -> getLogger().info("Configuration reloaded"));
    MainConfig current = config.get();
    if (current.debug()) {
      getLogger().info("Loaded " + config.file() + " with max-players=" + current.maxPlayers());
    }
  }

  @Override
  public void onDisable() {
    if (manager != null) {
      manager.close();
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
      ReloadResult<MainConfig> result = config.reload();
      Component prefix = result.current().prefix();
      if (result.successful()) {
        sender.sendMessage(
            prefix.append(Component.text("Configuration reloaded", NamedTextColor.GREEN)));
      } else {
        sender.sendMessage(
            prefix.append(
                Component.text("Reload failed, previous configuration kept:", NamedTextColor.RED)));
        for (ConfigDiagnostic diagnostic : result.diagnostics()) {
          sender.sendMessage(Component.text("  " + diagnostic, NamedTextColor.RED));
        }
      }
      return true;
    }
    sender.sendMessage(
        config.get().prefix().append(Component.text("Usage: /" + label + " reload")));
    return true;
  }
}
