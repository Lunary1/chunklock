package me.chunklock.config.modular;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for biome-unlocks.yml
 * Manages biome-specific unlock requirements.
 */
public class BiomeUnlocksConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public BiomeUnlocksConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "biome-unlocks.yml");
        if (!file.exists()) {
            plugin.saveResource("biome-unlocks.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public ConfigurationSection getBiomeSection(String biome) {
        return config.getConfigurationSection(biome);
    }

    public ConfigurationSection getRootSection() {
        return config;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

