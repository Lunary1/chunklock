package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Asynchronously scans chunks around a player to initialize them without
 * blocking the main server thread. Once complete, a synchronous task refreshes
 * holograms/borders for the player.
 */
public class ChunkSearchTask implements Runnable {
    private final Player player;
    private final ChunkLockManager chunkLockManager;
    private final int radius;

    public ChunkSearchTask(Player player, ChunkLockManager chunkLockManager, int radius) {
        this.player = player;
        this.chunkLockManager = chunkLockManager;
        this.radius = radius;
    }

    /**
     * Starts the asynchronous search.
     */
    public void start() {
        // Capture player's current location on main thread
        Location start = player.getLocation();
        Bukkit.getScheduler().runTaskAsynchronously(ChunklockPlugin.getInstance(), () -> runSearch(start));
    }

    @Override
    public void run() {
        // Not used directly. Call start() instead.
        start();
    }

    private void runSearch(Location start) {
        World world = start.getWorld();
        if (world == null) {
            return;
        }
        int baseX = start.getChunk().getX();
        int baseZ = start.getChunk().getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                try {
                    Chunk chunk = world.getChunkAt(baseX + dx, baseZ + dz);
                    chunkLockManager.initializeChunk(chunk, player.getUniqueId());
                } catch (Exception ignored) {
                }
            }
        }

        // Sync back to main thread for visual updates
        Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
            try {
                HologramManager hm = ChunklockPlugin.getInstance().getHologramManager();
                if (hm != null) {
                    hm.refreshHologramsForPlayer(player);
                }
            } catch (Exception ignored) {
            }
        });
    }
}
