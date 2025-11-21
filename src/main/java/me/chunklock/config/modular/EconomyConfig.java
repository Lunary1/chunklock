package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration handler for economy.yml
 * Manages economy and payment system settings.
 */
public class EconomyConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public EconomyConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "economy.yml");
        if (!file.exists()) {
            plugin.saveResource("economy.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getEconomyType() {
        return config.getString("type", "materials");
    }

    public double getVaultBaseCost() {
        return config.getDouble("vault.base-cost", 100.0);
    }

    public double getVaultCostPerUnlocked() {
        return config.getDouble("vault.cost-per-unlocked", 25.0);
    }

    public double getDifficultyMultiplier(String difficulty) {
        return config.getDouble("vault.difficulty-multipliers." + difficulty, 1.0);
    }

    public double getBiomeMultiplier(String biome) {
        return config.getDouble("vault.biome-multipliers." + biome, 1.0);
    }

    public Map<String, Object> getBiomeMultipliers() {
        if (config.isConfigurationSection("vault.biome-multipliers")) {
            return new HashMap<>(config.getConfigurationSection("vault.biome-multipliers").getValues(false));
        }
        return new HashMap<>();
    }

    public Map<String, Object> getDifficultyMultipliers() {
        if (config.isConfigurationSection("vault.difficulty-multipliers")) {
            return new HashMap<>(config.getConfigurationSection("vault.difficulty-multipliers").getValues(false));
        }
        return new HashMap<>();
    }

    public boolean isMaterialsEnabled() {
        return config.getBoolean("materials.enabled", true);
    }

    public boolean isVaultFallbackEnabled() {
        return config.getBoolean("materials.vault-fallback", false);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

