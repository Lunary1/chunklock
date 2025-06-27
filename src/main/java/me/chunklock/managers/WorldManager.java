package me.chunklock.managers;

import me.chunklock.ChunklockPlugin;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Manages world-specific settings for ChunkLock
 */
public class WorldManager {
    
    private final ChunklockPlugin plugin;
    private final FileConfiguration config;
    
    public WorldManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        initializeConfig();
    }
    
    private void initializeConfig() {
        // Set defaults if not present
        if (!config.contains("worlds.enabled-worlds")) {
            config.set("worlds.enabled-worlds", List.of("world"));
        }
        if (!config.contains("worlds.auto-assign-on-world-change")) {
            config.set("worlds.auto-assign-on-world-change", true);
        }
        plugin.saveConfig();
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
     */
    public boolean isWorldEnabled(String worldName) {
        if (worldName == null) return false;
        List<String> enabledWorlds = config.getStringList("worlds.enabled-worlds");
        return enabledWorlds.contains(worldName);
    }
    
    /**
     * Check if auto-assignment on world change is enabled
     */
    public boolean isAutoAssignOnWorldChangeEnabled() {
        return config.getBoolean("worlds.auto-assign-on-world-change", true);
    }
    
    /**
     * Get list of enabled worlds
     */
    public List<String> getEnabledWorlds() {
        return config.getStringList("worlds.enabled-worlds");
    }
}