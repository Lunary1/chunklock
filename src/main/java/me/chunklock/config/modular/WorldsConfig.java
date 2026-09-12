package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for worlds.yml
 * Manages world configuration settings.
 */
public final class WorldsConfig {
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

    /**
     * Persists the world name and diameter chosen at setup (issue #103).
     *
     * <p>Setup previously wrote these to {@code config.yml} while startup read them from here, so
     * the diameter silently reverted to its default on every restart. The write and the read have
     * to address the same file.
     *
     * @return true when the values reached disk
     */
    public boolean saveWorldSettings(String worldName, int diameter) {
        config.set("world.name", worldName);
        config.set("world.diameter", diameter);
        return save();
    }

    /**
     * Writes the in-memory configuration back to {@code worlds.yml}.
     */
    public boolean save() {
        File file = new File(plugin.getDataFolder(), "worlds.yml");
        try {
            config.save(file);
            return true;
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Failed to save worlds.yml: " + e.getMessage());
            return false;
        }
    }
}

