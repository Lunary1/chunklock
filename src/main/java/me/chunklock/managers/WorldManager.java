package me.chunklock.managers;

import me.chunklock.ChunklockPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Simplified WorldManager that handles world validation for ChunkLock.
 * Manages world-specific settings and validates which worlds have ChunkLock enabled.
 * Only supports the single dedicated ChunkLock world system.
 */
public class WorldManager {
    
    private final ChunklockPlugin plugin;
    
    public WorldManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
        initializeConfig();
    }
    
    private void initializeConfig() {
        // Basic world configuration - no per-player worlds needed
    }
    
    /**
     * Check if ChunkLock is enabled in this world
     */
    public boolean isWorldEnabled(World world) {
        if (world == null) return false;
        return isWorldEnabled(world.getName());
    }
    
    /**
     * Check if ChunkLock is enabled in this world by name
     * Only enables ChunkLock in the single dedicated ChunkLock world
     */
    public boolean isWorldEnabled(String worldName) {
        if (worldName == null) return false;
        
        // Only enable in single world system
        boolean result = isSingleChunklockWorld(worldName);
        
        if (plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().fine("World enabled check: '" + worldName + "' -> " + result);
        }
        
        return result;
    }
    
    /**
     * Check if auto-assignment on world change is enabled
     * Always disabled for single world system
     */
    public boolean isAutoAssignOnWorldChangeEnabled() {
        return false; // Disabled for single world system
    }
    
    /**
     * Get list of enabled worlds (only the single ChunkLock world)
     */
    public List<String> getEnabledWorlds() {
        List<String> enabled = new ArrayList<>();
        
        // Include the single ChunkLock world if it exists
        try {
            me.chunklock.managers.SingleWorldManager singleWorldManager = plugin.getSingleWorldManager();
            String singleWorldName = singleWorldManager.getChunklockWorldName();
            if (Bukkit.getWorld(singleWorldName) != null) {
                enabled.add(singleWorldName);
            }
        } catch (Exception e) {
            // Fallback to configuration directly if SingleWorldManager not available
            String configuredWorldName = plugin.getConfig().getString("worlds.world.name", "chunklock_world");
            if (Bukkit.getWorld(configuredWorldName) != null) {
                enabled.add(configuredWorldName);
            }
        }
        
        return enabled;
    }
    
    /**
     * Check if a world is the single dedicated ChunkLock world
     */
    public boolean isSingleChunklockWorld(String worldName) {
        if (worldName == null) return false;
        
        String configuredWorldName;
        String source;
        
        // Get the configured single world name from the single world manager
        try {
            me.chunklock.managers.SingleWorldManager singleWorldManager = plugin.getSingleWorldManager();
            configuredWorldName = singleWorldManager.getChunklockWorldName();
            source = "SingleWorldManager";
        } catch (Exception e) {
            // Fallback to configuration directly if SingleWorldManager not available
            configuredWorldName = plugin.getConfig().getString("worlds.world.name", "chunklock_world");
            source = "config fallback";
        }
        
        boolean result = worldName.equals(configuredWorldName);
        
        if (plugin.getLogger().isLoggable(Level.FINE)) {
            plugin.getLogger().fine("Single world check: '" + worldName + "' vs configured '" + 
                configuredWorldName + "' (from " + source + ") -> " + result);
        }
        
        return result;
    }
    
    /**
     * Check if a world name represents a player world (always false for single world system)
     */
    public boolean isPlayerWorld(String worldName) {
        return false; // No per-player worlds in single world system
    }
    
    /**
     * Check if a world was recently created (not needed for single world system)
     */
    public boolean isRecentlyCreatedWorld(String worldName) {
        return false; // Not needed for single world system
    }
    
    /**
     * Handle player leaving (not needed for single world system)
     */
    public void onPlayerQuit(Player player) {
        // No cleanup needed for single world system
    }
    
    /**
     * Cleanup method for plugin disable
     */
    public void cleanup() {
        // Nothing to cleanup for simplified version
    }
}