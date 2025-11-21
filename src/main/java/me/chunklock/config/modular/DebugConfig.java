package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for debug.yml
 * Manages debug and logging options.
 */
public class DebugConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public DebugConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "debug.yml");
        if (!file.exists()) {
            plugin.saveResource("debug.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", false);
    }

    public boolean isBordersDebug() {
        return config.getBoolean("borders", false);
    }

    public boolean isUnlockGuiDebug() {
        return config.getBoolean("unlock-gui", false);
    }

    public boolean isChunkFindingDebug() {
        return config.getBoolean("chunk-finding", false);
    }

    public boolean isPerformanceDebug() {
        return config.getBoolean("performance", false);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

