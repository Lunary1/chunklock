package me.chunklock.managers;

import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.services.PlayerStore;
import me.chunklock.ChunklockPlugin;

import java.util.UUID;

public class PlayerDataManager {

    private final PlayerStore playerDatabase;

    public PlayerDataManager(JavaPlugin plugin) {
        
        // Get PlayerDatabase from plugin instance
        if (plugin instanceof ChunklockPlugin) {
            this.playerDatabase = ((ChunklockPlugin) plugin).getPlayerDatabase();
        } else {
            throw new IllegalStateException("PlayerDataManager requires ChunklockPlugin instance");
        }
    }

    public boolean hasChunk(UUID uuid) {
        Location spawn = playerDatabase.getSpawnLocation(uuid);
        return spawn != null;
    }

    public void setChunk(UUID uuid, Location location) {
        playerDatabase.setSpawnLocation(uuid, location);
    }

    public Location getChunkSpawn(UUID uuid) {
        return playerDatabase.getSpawnLocation(uuid);
    }
}
