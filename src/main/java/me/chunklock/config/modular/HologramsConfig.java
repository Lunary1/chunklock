package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for holograms.yml
 * Manages hologram display settings.
 */
public class HologramsConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public HologramsConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "holograms.yml");
        if (!file.exists()) {
            plugin.saveResource("holograms.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public String getProvider() {
        return config.getString("provider", "auto").toLowerCase();
    }

    public int getViewDistance() {
        return config.getInt("view-distance", 64);
    }

    public double getWallOffset() {
        return config.getDouble("positioning.wall-offset", 0.5);
    }

    public double getCenterOffset() {
        return config.getDouble("positioning.center-offset", 8.0);
    }

    public double getGroundClearance() {
        return config.getDouble("positioning.ground-clearance", 3.0);
    }

    public int getMinHeight() {
        return config.getInt("positioning.min-height", 64);
    }

    public int getUpdateInterval() {
        return config.getInt("update-interval", 20);
    }

    public int getDebounceDelayTicks() {
        return config.getInt("performance.debounce-delay-ticks", 3);
    }

    public int getMaxActiveHologramsPerPlayer() {
        return config.getInt("performance.max-active-per-player", 100);
    }

    public double getMaxViewDistance() {
        return config.getDouble("performance.max-view-distance", 128.0);
    }

    public long getCullingSweepPeriod() {
        return config.getLong("performance.culling-sweep-period", 60L);
    }

    public int getScanRange() {
        return config.getInt("performance.scan-range", 3);
    }

    public int getMaxHologramsPerPlayer() {
        return config.getInt("performance.max-holograms-per-player", 16);
    }

    public int getCleanupInterval() {
        return config.getInt("performance.cleanup-interval", 100);
    }

    public boolean isWallFacing() {
        return config.getBoolean("display.wall-facing", true);
    }

    public boolean isFixedBillboard() {
        return config.getBoolean("display.fixed-billboard", true);
    }

    public boolean isShowDistance() {
        return config.getBoolean("display.show-distance", true);
    }

    public boolean isDebugLogging() {
        return config.getBoolean("debug-logging", false);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

