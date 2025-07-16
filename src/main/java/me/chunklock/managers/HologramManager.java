package me.chunklock.managers;

import me.chunklock.hologram.HologramService;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import java.util.Map;

/**
 * Legacy HologramManager that delegates to the new modular HologramService.
 * Maintains backward compatibility while using the new architecture.
 * 
 * @deprecated Use HologramService directly for new code
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class HologramManager {

    private final HologramService hologramService;

    public HologramManager(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        // Get WorldManager from plugin instance
        WorldManager worldManager = me.chunklock.ChunklockPlugin.getInstance().getWorldManager();
        
        this.hologramService = HologramService.create(
            chunkLockManager, 
            biomeUnlockRegistry, 
            worldManager
        );
    }

    /**
     * Starts hologram display task for a player.
     */
    public void startHologramDisplay(Player player) {
        hologramService.startHologramDisplay(player);
    }

    /**
     * Stops hologram display for a player.
     */
    public void stopHologramDisplay(Player player) {
        hologramService.stopHologramDisplay(player);
    }

    /**
     * Force immediate cleanup of holograms for a specific chunk.
     */
    public void forceCleanupChunk(Player player, Chunk chunk) {
        hologramService.forceCleanupChunk(player, chunk);
    }

    /**
     * Force refresh holograms for a specific player.
     */
    public void refreshHologramsForPlayer(Player player) {
        hologramService.refreshHologramsForPlayer(player);
    }

    /**
     * Cleanup all holograms and tasks.
     */
    public void cleanup() {
        hologramService.cleanup();
    }

    /**
     * Get hologram statistics for debugging.
     */
    public Map<String, Object> getHologramStats() {
        return hologramService.getStatistics();
    }
}