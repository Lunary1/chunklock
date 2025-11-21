package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.ChunkValueRegistry;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service to compute and cache base chunk values.
 * Base values are computed by scanning all blocks in a chunk using configurable block→value mapping.
 * Values are computed once and stored permanently.
 */
public class ChunkBaseValueService {

    private final ChunklockPlugin plugin;
    private final ChunkLockManager chunkLockManager;
    private final ChunkValueRegistry chunkValueRegistry;
    private final boolean asyncMode;

    /**
     * Get the ChunkLockManager instance (for accessing base values).
     */
    public ChunkLockManager getChunkLockManager() {
        return chunkLockManager;
    }

    public ChunkBaseValueService(ChunklockPlugin plugin, ChunkLockManager chunkLockManager, 
                                 ChunkValueRegistry chunkValueRegistry, boolean asyncMode) {
        this.plugin = plugin;
        this.chunkLockManager = chunkLockManager;
        this.chunkValueRegistry = chunkValueRegistry;
        this.asyncMode = asyncMode;
    }

    /**
     * Get or compute the base value for a chunk.
     * If the value is already stored, returns it immediately.
     * Otherwise, computes it (sync or async based on config) and stores it.
     * 
     * @param chunk The chunk to get the base value for
     * @return CompletableFuture that completes with the base value
     */
    public CompletableFuture<Double> getOrComputeBaseValue(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return CompletableFuture.completedFuture(0.0);
        }

        // Check if value is already stored
        double existingValue = chunkLockManager.getBaseValue(chunk);
        if (existingValue > 0.0) {
            return CompletableFuture.completedFuture(existingValue);
        }

        // Need to compute the value
        if (asyncMode) {
            return computeBaseValueAsync(chunk);
        } else {
            double value = calculateBaseValue(chunk);
            chunkLockManager.setBaseValue(chunk, value);
            chunkLockManager.saveAll();
            return CompletableFuture.completedFuture(value);
        }
    }

    /**
     * Calculate the base value for a chunk by scanning all blocks.
     * This method scans the entire chunk (all Y levels) to compute a comprehensive value.
     * 
     * @param chunk The chunk to calculate the base value for
     * @return The computed base value
     */
    public double calculateBaseValue(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return 0.0;
        }

        double totalValue = 0.0;
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Scan all blocks in the chunk
        // For performance, we can sample blocks (every N blocks) or scan all
        // Using a sampling approach for better performance
        int sampleRate = 2; // Sample every 2 blocks (8x8xheight instead of 16x16xheight)
        
        for (int x = 0; x < 16; x += sampleRate) {
            for (int z = 0; z < 16; z += sampleRate) {
                for (int y = minY; y < maxY; y += sampleRate) {
                    try {
                        Block block = chunk.getBlock(x, y, z);
                        if (block != null && block.getType() != null) {
                            Material material = block.getType();
                            if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR) {
                                int blockWeight = chunkValueRegistry.getBlockWeight(material);
                                totalValue += blockWeight;
                            }
                        }
                    } catch (Exception e) {
                        // Skip invalid coordinates
                        plugin.getLogger().log(Level.FINE, "Error scanning block at " + x + "," + y + "," + z + " in chunk", e);
                    }
                }
            }
        }

        // Normalize the value (divide by number of samples to get average, then scale)
        // This ensures consistent values regardless of sample rate
        int samples = (16 / sampleRate) * (16 / sampleRate) * ((maxY - minY) / sampleRate);
        if (samples > 0) {
            totalValue = totalValue / samples * (16 * 16 * (maxY - minY));
        }

        return totalValue;
    }

    /**
     * Compute base value asynchronously and store it.
     * 
     * @param chunk The chunk to compute the value for
     * @return CompletableFuture that completes with the computed value
     */
    private CompletableFuture<Double> computeBaseValueAsync(Chunk chunk) {
        CompletableFuture<Double> future = new CompletableFuture<>();

        // Run computation on async thread
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    double value = calculateBaseValue(chunk);
                    
                    // Save on main thread
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            chunkLockManager.setBaseValue(chunk, value);
                            chunkLockManager.saveAll();
                            future.complete(value);
                        }
                    }.runTask(plugin);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error computing base value for chunk " + 
                        chunk.getX() + "," + chunk.getZ(), e);
                    future.complete(0.0);
                }
            }
        }.runTaskAsynchronously(plugin);

        return future;
    }

    /**
     * Force recompute the base value for a chunk (useful for migration or updates).
     * 
     * @param chunk The chunk to recompute
     * @return CompletableFuture that completes with the new value
     */
    public CompletableFuture<Double> recomputeBaseValue(Chunk chunk) {
        if (asyncMode) {
            return computeBaseValueAsync(chunk);
        } else {
            double value = calculateBaseValue(chunk);
            chunkLockManager.setBaseValue(chunk, value);
            chunkLockManager.saveAll();
            return CompletableFuture.completedFuture(value);
        }
    }
}

