package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Handles asynchronous cost calculation for chunks to improve UI performance.
 * Pre-calculates costs for adjacent chunks after a chunk is unlocked.
 */
public class AsyncCostCalculationService {
    
    private final ChunklockPlugin plugin;
    private final EconomyManager economyManager;
    private final ChunkLockManager chunkLockManager;
    
    // Cache for pre-calculated costs: playerId -> chunkKey -> cost
    private final Map<UUID, Map<String, CachedCost>> costCache = new ConcurrentHashMap<>();
    
    public AsyncCostCalculationService(ChunklockPlugin plugin, EconomyManager economyManager, 
                                     ChunkLockManager chunkLockManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.chunkLockManager = chunkLockManager;
    }
    
    /**
     * Represents a cached cost calculation result
     */
    public static class CachedCost {
        private final EconomyManager.PaymentRequirement requirement;
        private final long calculatedAt;
        private final boolean aiProcessed;
        
        public CachedCost(EconomyManager.PaymentRequirement requirement, boolean aiProcessed) {
            this.requirement = requirement;
            this.calculatedAt = System.currentTimeMillis();
            this.aiProcessed = aiProcessed;
        }
        
        public EconomyManager.PaymentRequirement getRequirement() { return requirement; }
        public boolean isAiProcessed() { return aiProcessed; }
        public boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - calculatedAt > maxAgeMs;
        }
    }
    
    /**
     * Pre-calculate costs for adjacent chunks after a chunk is unlocked
     */
    public void preCalculateAdjacentChunks(Player player, Chunk unlockedChunk) {
        if (economyManager == null) return;
        
        plugin.getLogger().info("Starting async cost pre-calculation for adjacent chunks of " + 
            unlockedChunk.getX() + "," + unlockedChunk.getZ() + " for player " + player.getName());
        
        // Run async to avoid blocking the main thread
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    UUID playerId = player.getUniqueId();
                    Map<String, CachedCost> playerCache = costCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                    
                    // Calculate costs for 8 adjacent chunks
                    int[] xOffsets = {-1, -1, -1, 0, 0, 1, 1, 1};
                    int[] zOffsets = {-1, 0, 1, -1, 1, -1, 0, 1};
                    
                    for (int i = 0; i < 8; i++) {
                        int adjX = unlockedChunk.getX() + xOffsets[i];
                        int adjZ = unlockedChunk.getZ() + zOffsets[i];
                        
                        Chunk adjacentChunk = unlockedChunk.getWorld().getChunkAt(adjX, adjZ);
                        String chunkKey = getChunkKey(adjacentChunk);
                        
                        // Skip if already unlocked
                        if (!chunkLockManager.isLocked(adjacentChunk)) {
                            continue;
                        }
                        
                        try {
                            // Calculate cost using AI-enhanced method
                            var evaluation = chunkLockManager.evaluateChunk(playerId, adjacentChunk);
                            var requirement = economyManager.calculateRequirement(player, adjacentChunk, evaluation.biome, evaluation);
                            
                            // Cache the result
                            boolean aiProcessed = economyManager.getCurrentType() == EconomyManager.EconomyType.VAULT || 
                                                economyManager.isAiCostingEnabled();
                            CachedCost cachedCost = new CachedCost(requirement, aiProcessed);
                            playerCache.put(chunkKey, cachedCost);
                            
                            plugin.getLogger().fine("Pre-calculated cost for chunk " + adjX + "," + adjZ + 
                                " - " + (requirement.getType() == EconomyManager.EconomyType.VAULT ? 
                                "$" + requirement.getVaultCost() : 
                                requirement.getMaterialAmount() + "x " + me.chunklock.util.item.MaterialUtil.getMaterialName(requirement.getMaterial())));
                                
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Failed to pre-calculate cost for chunk " + 
                                adjX + "," + adjZ, e);
                        }
                    }
                    
                    plugin.getLogger().info("Completed async cost pre-calculation for player " + player.getName());
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in async cost calculation", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
    
    /**
     * Get cached cost if available, otherwise return null
     */
    public CachedCost getCachedCost(Player player, Chunk chunk) {
        Map<String, CachedCost> playerCache = costCache.get(player.getUniqueId());
        if (playerCache == null) return null;
        
        String chunkKey = getChunkKey(chunk);
        CachedCost cached = playerCache.get(chunkKey);
        
        // Check if cache is expired (5 minutes)
        if (cached != null && cached.isExpired(5 * 60 * 1000)) {
            playerCache.remove(chunkKey);
            return null;
        }
        
        return cached;
    }
    
    /**
     * Calculate cost with cache check - returns immediately if cached, otherwise calculates async
     */
    public CompletableFuture<EconomyManager.PaymentRequirement> getCostAsync(Player player, Chunk chunk) {
        CachedCost cached = getCachedCost(player, chunk);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached.getRequirement());
        }
        
        // Not cached, calculate asynchronously
        return CompletableFuture.supplyAsync(() -> {
            try {
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                var requirement = economyManager.calculateRequirement(player, chunk, evaluation.biome, evaluation);
                
                // Cache the result
                UUID playerId = player.getUniqueId();
                Map<String, CachedCost> playerCache = costCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>());
                boolean aiProcessed = economyManager.getCurrentType() == EconomyManager.EconomyType.VAULT || 
                                    economyManager.isAiCostingEnabled();
                playerCache.put(getChunkKey(chunk), new CachedCost(requirement, aiProcessed));
                
                return requirement;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to calculate cost for chunk " + 
                    chunk.getX() + "," + chunk.getZ(), e);
                
                // Fallback to basic calculation
                try {
                    var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                    return economyManager.calculateRequirement(player, evaluation.biome, evaluation);
                } catch (Exception fallbackError) {
                    plugin.getLogger().log(Level.SEVERE, "Fallback cost calculation also failed", fallbackError);
                    // Return a default cost
                    return new EconomyManager.PaymentRequirement(100.0); // $100 default
                }
            }
        });
    }
    
    /**
     * Clear cache for a player
     */
    public void clearPlayerCache(UUID playerId) {
        costCache.remove(playerId);
    }
    
    /**
     * Clear expired cache entries
     */
    public void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        long maxAge = 10 * 60 * 1000; // 10 minutes
        
        costCache.values().forEach(playerCache -> {
            playerCache.entrySet().removeIf(entry -> entry.getValue().isExpired(maxAge));
        });
        
        // Remove empty player caches
        costCache.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
    
    public String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ();
    }
    
    /**
     * Public getter for cost cache (used by EconomyManager for direct cache access)
     */
    public Map<UUID, Map<String, CachedCost>> getCostCache() {
        return costCache;
    }
}
