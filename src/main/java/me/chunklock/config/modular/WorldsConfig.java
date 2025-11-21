package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for worlds.yml
 * Manages world configuration settings.
 */
public class WorldsConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public WorldsConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        if (!file.exists()) {
            plugin.saveResource("worlds.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getWorldName() {
        return config.getString("world.name", "chunklock_world");
    }

    public int getWorldDiameter() {
        return config.getInt("world.diameter", 30000);
    }

    public int getMinDistanceBetweenClaims() {
        return config.getInt("claims.min-distance-between-claims", 2);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

