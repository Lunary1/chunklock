package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handles asynchronous cost calculation for chunks to improve UI performance.
 * Pre-calculates costs for adjacent chunks after a chunk is unlocked.
 * Delegates caching to ChunkCostDatabase for unified cache management.
 */
public class AsyncCostCalculationService {
    
    private final ChunklockPlugin plugin;
    private final EconomyManager economyManager;
    private final ChunkLockManager chunkLockManager;
    private final ChunkCostDatabase costDatabase;
    
    public AsyncCostCalculationService(ChunklockPlugin plugin, EconomyManager economyManager, 
                                     ChunkLockManager chunkLockManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.chunkLockManager = chunkLockManager;
        this.costDatabase = plugin.getCostDatabase();
    }
    
    /**
     * Pre-calculate costs for adjacent chunks after a chunk is unlocked
     */
    public void preCalculateAdjacentChunks(Player player, Chunk unlockedChunk) {
        if (economyManager == null || costDatabase == null) return;
        
        plugin.getLogger().fine("Starting async cost pre-calculation for adjacent chunks of " + 
            unlockedChunk.getX() + "," + unlockedChunk.getZ() + " for player " + player.getName());
        
        // Run async to avoid blocking the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Calculate costs for 8 adjacent chunks
                    int[] xOffsets = {-1, -1, -1, 0, 0, 1, 1, 1};
                    int[] zOffsets = {-1, 0, 1, -1, 1, -1, 0, 1};
                    
                    for (int i = 0; i < 8; i++) {
                        int adjX = unlockedChunk.getX() + xOffsets[i];
                        int adjZ = unlockedChunk.getZ() + zOffsets[i];
                        
                        Chunk adjacentChunk = unlockedChunk.getWorld().getChunkAt(adjX, adjZ);
                        
                        // Skip if already unlocked
                        if (!chunkLockManager.isLocked(adjacentChunk)) {
                            continue;
                        }
                        
                        try {
                            // Calculate cost - caching is handled by ChunkCostDatabase via strategies
                            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), adjacentChunk);
                            var requirement = economyManager.calculateRequirement(player, adjacentChunk, evaluation.biome, evaluation);
                            
                            plugin.getLogger().fine("Pre-calculated cost for chunk " + adjX + "," + adjZ + 
                                " - " + (requirement.getType() == EconomyManager.EconomyType.VAULT ? 
                                "$" + requirement.getVaultCost() : 
                                requirement.getMaterialAmount() + "x " + me.chunklock.util.item.MaterialUtil.getMaterialName(requirement.getMaterial())));
                                
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to pre-calculate cost for chunk " + 
                                adjX + "," + adjZ, e);
                        }
                    }
                    
                    plugin.getLogger().fine("Completed async cost pre-calculation for player " + player.getName());
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in async cost calculation", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Calculate cost with cache check - delegates to ChunkCostDatabase for caching
     */
    public CompletableFuture<EconomyManager.PaymentRequirement> getCostAsync(Player player, Chunk chunk) {
        if (costDatabase == null) {
            // Fallback if database not available
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                    return economyManager.calculateRequirement(player, chunk, evaluation.biome, evaluation);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to calculate cost", e);
                    return new EconomyManager.PaymentRequirement(100.0); // Default fallback
                }
            });
        }
        
        // Check cache first via ChunkCostDatabase
        String configHash = costDatabase.generateConfigHash();
        CompletableFuture<EconomyManager.PaymentRequirement> cached = 
            costDatabase.getCachedCost(player, chunk, configHash);
        
        return cached.thenCompose(cachedResult -> {
            if (cachedResult != null) {
                return CompletableFuture.completedFuture(cachedResult);
            }
            
            // Not cached, calculate asynchronously
            return CompletableFuture.supplyAsync(() -> {
                try {
                    var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                    return economyManager.calculateRequirement(player, chunk, evaluation.biome, evaluation);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to calculate cost for chunk " + 
                        chunk.getX() + "," + chunk.getZ(), e);
                    return new EconomyManager.PaymentRequirement(100.0); // Default fallback
                }
            });
        });
    }
    
    public String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ();
    }
}
