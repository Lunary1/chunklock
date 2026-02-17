package me.chunklock.managers;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.services.PlayerDatabase;
import me.chunklock.ChunklockPlugin;

import java.util.UUID;

public class PlayerDataManager {

    private final PlayerDatabase playerDatabase;

    public PlayerDataManager(JavaPlugin plugin) {
        
        // Get PlayerDatabase from plugin instance
        if (plugin instanceof ChunklockPlugin) {
            this.playerDatabase = ((ChunklockPlugin) plugin).getPlayerDatabase();
        } else {
            throw new IllegalStateException("PlayerDataManager requires ChunklockPlugin instance");
        }
    }

    /**
     * Check if player has a chunk assigned.
     * This method checks the database directly without requiring the world to be loaded,
     * preventing false negatives after server restart when worlds load asynchronously.
     */
    public boolean hasChunk(UUID uuid) {
        // Check if spawn data exists in database (doesn't require world to be loaded)
        return playerDatabase.hasSpawnData(uuid);
    }
    
    /**
     * Check if player has a chunk AND the world is currently loaded.
     * Use this when you need an actual Location object.
     */
    public boolean hasChunkWithLoadedWorld(UUID uuid) {
        Location spawn = playerDatabase.getSpawnLocation(uuid);
        return spawn != null;
    }

    public void setChunk(UUID uuid, Location location) {
        playerDatabase.setSpawnLocation(uuid, location);
    }

    public Location getChunkSpawn(UUID uuid) {
        return playerDatabase.getSpawnLocation(uuid);
    }
    
    /**
     * Get the underlying PlayerDatabase instance.
     * Used for accessing world name without requiring world to be loaded.
     */
    public PlayerDatabase getPlayerDatabase() {
        return playerDatabase;
    }
}
