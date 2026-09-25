package dev.leafconfig.example.snippets;

import dev.leafconfig.ConfigDiagnostic;
import dev.leafconfig.ConfigHandle;
import dev.leafconfig.ReloadResult;
import dev.leafconfig.example.MainConfig;
import dev.leafconfig.paper.LeafConfig;
import dev.leafconfig.yaml.ConfigManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper code shown in the README and docs, compiled but never run. The text between the snippet
 * markers must stay identical to the documentation; {@code DocumentationSnippetsTest} checks it.
 * This directory is excluded from Spotless so the documented formatting survives.
 */
final class PaperSnippets extends JavaPlugin {

  private ConfigHandle<MainConfig> config;
  private final Cache cache = new Cache();

  /** Stand-in for whatever a plugin rebuilds after a reload. */
  static final class Cache {
    void rebuild(MainConfig config) {}
  }

  void loadInPlugin() {
    // snippet-start: readme-load-paper
    ConfigManager manager = LeafConfig.forPlugin(this).build();
    ConfigHandle<MainConfig> config = manager.load(MainConfig.class);
    MainConfig current = config.get();
    // snippet-end: readme-load-paper
  }

  void createManager() {
    // snippet-start: paper-integration-manager
    ConfigManager manager = LeafConfig.forPlugin(this).build();
    // snippet-end: paper-integration-manager
  }

  void reload() {
    // snippet-start: readme-reload
    ReloadResult<MainConfig> result = config.reload();
    if (!result.successful()) {
      // result.current() is still the previous, valid snapshot
      for (ConfigDiagnostic d : result.diagnostics()) {
        getLogger().warning(d.toString());
      }
    }
    config.onReload(fresh -> cache.rebuild(fresh));
    // snippet-end: readme-reload
  }
}
