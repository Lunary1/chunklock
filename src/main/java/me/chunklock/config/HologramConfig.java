package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

/**
 * Configuration section for hologram-related settings.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class HologramConfig {
    
    private final FileConfiguration config;
    
    public HologramConfig(FileConfiguration config) {
        this.config = config;
    }
    
    /**
     * Checks if holograms are enabled.
     * 
     * @return true if holograms are enabled
     */
    public boolean isHologramsEnabled() {
        return config.getBoolean("holograms.enabled", true);
    }
    
    /**
     * Gets the preferred hologram provider.
     * 
     * @return Preferred provider name
     */
    public String getPreferredProvider() {
        return config.getString("holograms.provider", "auto");
    }
    
    /**
     * Gets the hologram visibility range.
     * 
     * @return Visibility range in blocks
     */
    public double getVisibilityRange() {
        return config.getDouble("holograms.visibility-range", 32.0);
    }
    
    /**
     * Gets the hologram update interval in ticks.
     * 
     * @return Update interval in ticks
     */
    public int getUpdateInterval() {
        return config.getInt("holograms.update-interval", 20);
    }
    
    /**
     * Gets the default hologram lines.
     * 
     * @return List of hologram lines
     */
    public List<String> getDefaultLines() {
        return config.getStringList("holograms.default-lines");
    }
    
    /**
     * Checks if hologram optimization is enabled.
     * 
     * @return true if optimization is enabled
     */
    public boolean isOptimizationEnabled() {
        return config.getBoolean("holograms.optimization.enabled", true);
    }
    
    /**
     * Gets the maximum holograms per chunk.
     * 
     * @return Maximum holograms per chunk
     */
    public int getMaxHologramsPerChunk() {
        return config.getInt("holograms.optimization.max-per-chunk", 5);
    }
    
    /**
     * Checks if dynamic view distance is enabled.
     * 
     * @return true if dynamic view distance is enabled
     */
    public boolean isDynamicViewDistanceEnabled() {
        return config.getBoolean("holograms.optimization.dynamic-view-distance", true);
    }
    
    /**
     * Validates the hologram configuration section.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean valid = true;
        
        if (getVisibilityRange() <= 0) {
            valid = false;
        }
        
        if (getUpdateInterval() <= 0) {
            valid = false;
        }
        
        if (getMaxHologramsPerChunk() <= 0) {
            valid = false;
        }
        
        return valid;
    }
}