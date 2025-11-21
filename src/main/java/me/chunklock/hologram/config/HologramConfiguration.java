package me.chunklock.hologram.config;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.HologramsConfig;

/**
 * Handles hologram-related configuration settings.
 * Provides typed access to configuration values with sensible defaults.
 */
public final class HologramConfiguration {

    private final HologramsConfig config;

    public HologramConfiguration(ChunklockPlugin plugin) {
        this.config = plugin.getConfigManager().getHologramsConfig();
    }

    /**
     * Checks if holograms are enabled in the configuration.
     */
    public boolean isEnabled() {
        return config != null ? config.isEnabled() : true;
    }

    /**
     * Gets the configured hologram provider.
     */
    public String getProvider() {
        return config != null ? config.getProvider() : "auto";
    }

    /**
     * Gets the hologram view distance.
     */
    public int getViewDistance() {
        return config != null ? config.getViewDistance() : 64;
    }

    /**
     * Gets the wall offset for hologram positioning.
     */
    public double getWallOffset() {
        return config != null ? config.getWallOffset() : 0.5;
    }

    /**
     * Gets the center offset for hologram positioning.
     */
    public double getCenterOffset() {
        return config != null ? config.getCenterOffset() : 8.0;
    }

    /**
     * Gets the ground clearance for hologram height.
     */
    public double getGroundClearance() {
        return config != null ? config.getGroundClearance() : 3.0;
    }

    /**
     * Gets the minimum height for holograms.
     */
    public int getMinHeight() {
        return config != null ? config.getMinHeight() : 64;
    }

    /**
     * Gets the hologram update interval in ticks.
     */
    public int getUpdateInterval() {
        return config != null ? config.getUpdateInterval() : 20;
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

    /**
     * Gets the debounce delay in ticks for rapid hologram updates.
     */
    public int getDebounceDelayTicks() {
        return config != null ? config.getDebounceDelayTicks() : 3;
    }

    /**
     * Gets the maximum number of active holograms per player for distance culling.
     */
    public int getMaxActiveHologramsPerPlayer() {
        return config != null ? config.getMaxActiveHologramsPerPlayer() : 100;
    }

    /**
     * Gets the maximum view distance for holograms in blocks.
     */
    public double getMaxViewDistance() {
        return config != null ? config.getMaxViewDistance() : 128.0;
    }

    /**
     * Gets the culling sweep period in ticks for distance updates.
     */
    public long getCullingSweepPeriod() {
        return config != null ? config.getCullingSweepPeriod() : 60L;
    }
}
