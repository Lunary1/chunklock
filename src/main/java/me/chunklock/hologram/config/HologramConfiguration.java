package me.chunklock.hologram.config;

import me.chunklock.ChunklockPlugin;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Handles hologram-related configuration settings.
 * Provides typed access to configuration values with sensible defaults.
 */
public final class HologramConfiguration {

    private final ConfigurationSection config;

    public HologramConfiguration(ChunklockPlugin plugin) {
        this.config = plugin.getConfig();
    }

    /**
     * Checks if holograms are enabled in the configuration.
     */
    public boolean isEnabled() {
        return config.getBoolean("holograms.enabled", true);
    }

    /**
     * Gets the configured hologram provider.
     */
    public String getProvider() {
        return config.getString("holograms.provider", "auto").toLowerCase();
    }

    /**
     * Gets the hologram view distance.
     */
    public int getViewDistance() {
        return config.getInt("holograms.view-distance", 64);
    }

    /**
     * Gets the wall offset for hologram positioning.
     */
    public double getWallOffset() {
        return config.getDouble("holograms.positioning.wall-offset", 0.5);
    }

    /**
     * Gets the center offset for hologram positioning.
     */
    public double getCenterOffset() {
        return config.getDouble("holograms.positioning.center-offset", 8.0);
    }

    /**
     * Gets the ground clearance for hologram height.
     */
    public double getGroundClearance() {
        return config.getDouble("holograms.positioning.ground-clearance", 2.5);
    }

    /**
     * Gets the minimum height for holograms.
     */
    public int getMinHeight() {
        return config.getInt("holograms.positioning.min-height", 64);
    }

    /**
     * Gets the hologram update interval in ticks.
     */
    public int getUpdateInterval() {
        return config.getInt("holograms.update-interval", 20);
    }

    /**
     * Checks if the specified provider is explicitly disabled.
     */
    public boolean isProviderDisabled() {
        return "none".equals(getProvider());
    }

    /**
     * Checks if auto-detection should be used for providers.
     */
    public boolean isAutoDetection() {
        return "auto".equals(getProvider());
    }
}
