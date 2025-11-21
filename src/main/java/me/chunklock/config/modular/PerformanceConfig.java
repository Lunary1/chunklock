package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for performance.yml
 * Manages performance tuning settings.
 */
public class PerformanceConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public PerformanceConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "performance.yml");
        if (!file.exists()) {
            plugin.saveResource("performance.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public int getBorderUpdateDelay() {
        return config.getInt("border-update-delay", 2);
    }

    public int getMaxBorderUpdatesPerTick() {
        return config.getInt("max-border-updates-per-tick", 10);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

