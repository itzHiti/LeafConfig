package dev.leafconfig.example;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    getLogger().info("LeafConfig example enabled");
  }
}
