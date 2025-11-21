package me.chunklock.config.modular;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration handler for block-values.yml
 * Manages block values and biome weights for chunk scoring.
 */
public class BlockValuesConfig {
    private final Plugin plugin;
    private FileConfiguration config;
    private final Map<String, Integer> thresholds = new HashMap<>();
    private final Map<String, Integer> biomeWeights = new HashMap<>();
    private final Map<String, Integer> blockWeights = new HashMap<>();

    public BlockValuesConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "block-values.yml");
        if (!file.exists()) {
            plugin.saveResource("block-values.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        loadThresholds();
        loadBiomeWeights();
        loadBlockWeights();
    }

    private void loadThresholds() {
        thresholds.clear();
        if (config.isConfigurationSection("thresholds")) {
            thresholds.put("easy", config.getInt("thresholds.easy", 30));
            thresholds.put("normal", config.getInt("thresholds.normal", 50));
            thresholds.put("hard", config.getInt("thresholds.hard", 80));
        } else {
            thresholds.put("easy", 30);
            thresholds.put("normal", 50);
            thresholds.put("hard", 80);
        }
    }

    private void loadBiomeWeights() {
        biomeWeights.clear();
        if (config.isConfigurationSection("biomes")) {
            for (String key : config.getConfigurationSection("biomes").getKeys(false)) {
                biomeWeights.put(key, config.getInt("biomes." + key, 8));
            }
        }
    }

    private void loadBlockWeights() {
        blockWeights.clear();
        if (config.isConfigurationSection("blocks")) {
            for (String key : config.getConfigurationSection("blocks").getKeys(false)) {
                blockWeights.put(key, config.getInt("blocks." + key, 1));
            }
        }
    }

    public int getThreshold(String difficulty) {
        return thresholds.getOrDefault(difficulty, 50);
    }

    public Map<String, Integer> getBiomeWeights() {
        return new HashMap<>(biomeWeights);
    }

    public Map<String, Integer> getBlockWeights() {
        return new HashMap<>(blockWeights);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public ConfigurationSection getThresholdsSection() {
        return config.getConfigurationSection("thresholds");
    }

    public ConfigurationSection getBiomesSection() {
        return config.getConfigurationSection("biomes");
    }

    public ConfigurationSection getBlocksSection() {
        return config.getConfigurationSection("blocks");
    }
}

