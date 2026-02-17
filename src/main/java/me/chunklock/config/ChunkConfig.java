package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

/**
 * Configuration section for chunk-related settings.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class ChunkConfig {
    
    private final FileConfiguration config;
    
    public ChunkConfig(FileConfiguration config) {
        this.config = config;
    }
    
    /**
     * Gets the maximum number of chunks a player can unlock.
     * 
     * @return Maximum chunks per player
     */
    public int getMaxChunksPerPlayer() {
        return config.getInt("chunk.max-chunks-per-player", 100);
    }
    
    /**
     * Gets the starting chunk radius for new players.
     * 
     * @return Starting radius in chunks
     */
    public int getStartingRadius() {
        return config.getInt("chunk.starting-radius", 1);
    }
    
    /**
     * Checks if adjacent chunk requirement is enabled.
     * 
     * @return true if adjacent chunks must be unlocked first
     */
    public boolean isAdjacentRequirementEnabled() {
        return config.getBoolean("chunk.require-adjacent", true);
    }
    
    /**
     * Gets the list of worlds where chunk locking is enabled.
     * 
     * @return List of world names
     */
    public List<String> getEnabledWorlds() {
        return config.getStringList("chunk.enabled-worlds");
    }
    
    /**
     * Checks if automatic border generation is enabled.
     * 
     * @return true if borders should be automatically generated
     */
    public boolean isAutoBorderEnabled() {
        return config.getBoolean("chunk.auto-border", true);
    }
    
    /**
     * Gets the border update interval in ticks.
     * 
     * @return Update interval in ticks
     */
    public int getBorderUpdateInterval() {
        return config.getInt("chunk.border-update-interval", 20);
    }
    
    /**
     * Checks if chunk evaluation caching is enabled.
     * 
     * @return true if caching is enabled
     */
    public boolean isCachingEnabled() {
        return config.getBoolean("chunk.enable-caching", true);
    }
    
    /**
     * Gets the cache TTL in milliseconds.
     * 
     * @return Cache TTL in milliseconds
     */
    public long getCacheTTL() {
        return config.getLong("chunk.cache-ttl", 300000); // 5 minutes default
    }
    
    /**
     * Validates the chunk configuration section.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean valid = true;
        
        if (getMaxChunksPerPlayer() <= 0) {
            valid = false;
        }
        
        if (getStartingRadius() < 0) {
            valid = false;
        }
        
        if (getBorderUpdateInterval() <= 0) {
            valid = false;
        }
        
        return valid;
    }
}