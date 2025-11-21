package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for openai.yml
 * Manages OpenAI integration settings.
 */
public class OpenAIConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public OpenAIConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "openai.yml");
        if (!file.exists()) {
            plugin.saveResource("openai.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", false);
    }

    public String getApiKey() {
        return config.getString("api-key", "");
    }

    public String getModel() {
        return config.getString("model", "gpt-4o-mini");
    }

    public int getMaxTokens() {
        return config.getInt("max-tokens", 300);
    }

    public double getTemperature() {
        return config.getDouble("temperature", 0.3);
    }

    public boolean isTransparencyEnabled() {
        return config.getBoolean("transparency", false);
    }

    public boolean isFallbackOnError() {
        return config.getBoolean("fallback-on-error", true);
    }

    public int getCacheDurationMinutes() {
        return config.getInt("cache-duration-minutes", 5);
    }

    public int getRequestTimeoutSeconds() {
        return config.getInt("request-timeout-seconds", 10);
    }

    public double getMinMultiplier() {
        return config.getDouble("cost-bounds.min-multiplier", 0.3);
    }

    public double getMaxMultiplier() {
        return config.getDouble("cost-bounds.max-multiplier", 3.0);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

