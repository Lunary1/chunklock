package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TickTask extends BukkitRunnable {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private int tickCounter = 0;
    
    // Performance optimizations - reduced frequencies
    private static final int PARTICLE_UPDATE_INTERVAL = 20; // Every 1 second (was 10)
    private static final int CHUNK_SCAN_INTERVAL = 40; // Every 2 seconds for chunk updates
    private static final int CACHE_CLEANUP_INTERVAL = 600; // Every 30 seconds (was 200)
    
    private static final int PARTICLES_PER_SIDE = 3; // Reduced from 4
    private static final double PARTICLE_HEIGHT_RANGE = 2.0; // Reduced from 3.0
    private static final int MAX_BORDER_DISTANCE = 40; // Reduced from 48
    private static final int CHUNK_CACHE_SIZE = 150; // Reduced from 100
    private static final double FOV_DOT_THRESHOLD = -0.5; // 120-degree FOV
    
    // Thread-safe collections
    private final Map<String, ChunkEvaluator.ChunkValueData> chunkEvaluationCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 45000; // 45 seconds (was 30)
    
    // Thread-safe player tracking
    private final Map<String, Set<String>> playerBorderChunks = new ConcurrentHashMap<>();
    private final Map<String, Long> lastChunkScan = new ConcurrentHashMap<>();
    
    // Performance metrics
    private final AtomicLong particlesSpawned = new AtomicLong();
    private final AtomicLong chunksEvaluated = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();

    public TickTask(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    @Override
    public void run() {
        tickCounter++;
        
        try {
            // Update particles less frequently
            if (tickCounter % PARTICLE_UPDATE_INTERVAL != 0) {
                return;
            }
            
            // Clean cache periodically
            if (tickCounter % CACHE_CLEANUP_INTERVAL == 0) {
                cleanCache();
                logPerformanceMetrics();
            }

            // Process all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player == null || !player.isOnline()) {
                    continue;
                }
                
                if (chunkLockManager.isBypassing(player)) {
                    continue;
                }
                
                try {
                    updatePlayerEffectsOptimized(player);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().warning(
                        "Error updating effects for " + player.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error in TickTask main loop: " + e.getMessage());
        }
    }

    /**
     * Optimized version that only shows borders for chunks adjacent to unlocked chunks
     */
    private void updatePlayerEffectsOptimized(Player player) {
        String playerKey = player.getUniqueId().toString();
        long now = System.currentTimeMillis();
        
        // Only rescan chunks periodically per player
        Long lastScan = lastChunkScan.get(playerKey);
        if (lastScan == null || (now - lastScan) >= (CHUNK_SCAN_INTERVAL * 50)) { // Convert ticks to ms
            Chunk playerChunk = player.getLocation().getChunk();
            Set<String> borderChunks = findBorderChunks(player, playerChunk);
            playerBorderChunks.put(playerKey, borderChunks);
            lastChunkScan.put(playerKey, now);
        }
        
        // Get cached border chunks for this player
        Set<String> borderChunks = playerBorderChunks.get(playerKey);
        if (borderChunks == null) {
            return;
        }
        
        // Only draw borders for identified chunks with view culling
        for (String chunkKey : borderChunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    Chunk chunk = player.getWorld().getChunkAt(x, z);
                    
                    // Distance and view culling
                    if (shouldShowParticles(player, chunk)) {
                        var eval = getCachedEvaluation(chunk);
                        boolean canUnlock = biomeUnlockRegistry.hasRequiredItems(player, eval.biome, eval.score);
                        drawOptimizedChunkBorder(player, chunk, eval.difficulty, canUnlock);
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid chunk coordinates
                }
            }
        }
    }

    /**
     * Determines if particles should be shown for this chunk
     */
    private boolean shouldShowParticles(Player player, Chunk chunk) {
        Location playerLoc = player.getLocation();
        
        // Distance check first (cheapest)
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        double chunkCenterX = chunkX + 8;
        double chunkCenterZ = chunkZ + 8;
        
        double distanceSquared = Math.pow(playerLoc.getX() - chunkCenterX, 2) + 
                                Math.pow(playerLoc.getZ() - chunkCenterZ, 2);
        
        if (distanceSquared > MAX_BORDER_DISTANCE * MAX_BORDER_DISTANCE) {
            return false;
        }
        
        // Simple FOV check (only if within distance)
        Vector toChunk = new Vector(chunkCenterX - playerLoc.getX(), 0, chunkCenterZ - playerLoc.getZ());
        Vector playerDirection = playerLoc.getDirection();
        
        // Normalize and check dot product for FOV
        toChunk.normalize();
        return toChunk.dot(playerDirection) > FOV_DOT_THRESHOLD;
    }

    /**
     * Finds chunks that should show borders (locked chunks adjacent to unlocked chunks)
     */
    private Set<String> findBorderChunks(Player player, Chunk playerChunk) {
        Set<String> borderChunks = new HashSet<>();
        Set<String> checkedChunks = new HashSet<>();
        
        // Check chunks around player (3x3 grid)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                try {
                    Chunk centerChunk = player.getWorld().getChunkAt(
                        playerChunk.getX() + dx, 
                        playerChunk.getZ() + dz
                    );
                    
                    String centerKey = getChunkKey(centerChunk);
                    if (checkedChunks.contains(centerKey)) continue;
                    checkedChunks.add(centerKey);
                    
                    chunkLockManager.initializeChunk(centerChunk, player.getUniqueId());
                    
                    // If this chunk is unlocked, check its neighbors for locked chunks
                    if (!chunkLockManager.isLocked(centerChunk)) {
                        findAdjacentLockedChunks(player, centerChunk, borderChunks);
                    }
                } catch (Exception e) {
                    // Skip chunks that can't be loaded
                    continue;
                }
            }
        }
        
        return borderChunks;
    }

    /**
     * Finds locked chunks adjacent to an unlocked chunk
     */
    private void findAdjacentLockedChunks(Player player, Chunk unlockedChunk, Set<String> borderChunks) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // Skip center chunk
                
                try {
                    Chunk adjacentChunk = player.getWorld().getChunkAt(
                        unlockedChunk.getX() + dx,
                        unlockedChunk.getZ() + dz
                    );
                    
                    // Check distance limit
                    if (getChunkDistance(player.getLocation().getChunk(), adjacentChunk) > 3) {
                        continue;
                    }
                    
                    chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                    
                    if (chunkLockManager.isLocked(adjacentChunk)) {
                        borderChunks.add(getChunkKey(adjacentChunk));
                    }
                } catch (Exception e) {
                    // Skip chunks that can't be loaded
                    continue;
                }
            }
        }
    }

    /**
     * Improved cached chunk evaluation - no longer player-specific
     */
    private ChunkEvaluator.ChunkValueData getCachedEvaluation(Chunk chunk) {
        String key = getChunkKey(chunk);
        long now = System.currentTimeMillis();
        
        // Check if we have a valid cached result
        if (chunkEvaluationCache.containsKey(key)) {
            Long timestamp = cacheTimestamps.get(key);
            if (timestamp != null && (now - timestamp) < CACHE_DURATION) {
                cacheHits.incrementAndGet();
                return chunkEvaluationCache.get(key);
            }
        }
        
        // Calculate and cache new result (use null for player ID since we removed player-specific caching)
        var evaluation = chunkLockManager.evaluateChunk(null, chunk);
        chunkEvaluationCache.put(key, evaluation);
        cacheTimestamps.put(key, now);
        chunksEvaluated.incrementAndGet();
        
        return evaluation;
    }

    /**
     * Optimized border drawing with fewer particles and smarter placement
     */
    private void drawOptimizedChunkBorder(Player player, Chunk chunk, Difficulty difficulty, boolean canUnlock) {
        // Determine particle type and color
        Particle particleType;
        Color color = null;
        
        if (canUnlock) {
            particleType = Particle.HAPPY_VILLAGER;
        } else {
            particleType = Particle.DUST;
            color = switch (difficulty) {
                case EASY -> Color.GREEN;
                case NORMAL -> Color.YELLOW;
                case HARD -> Color.RED;
                case IMPOSSIBLE -> Color.PURPLE;
            };
        }

        // Calculate Y range around player (smaller range)
        Location playerLoc = player.getLocation();
        int playerY = playerLoc.getBlockY();
        int minY = Math.max(chunk.getWorld().getMinHeight(), playerY - (int)PARTICLE_HEIGHT_RANGE);
        int maxY = Math.min(chunk.getWorld().getMaxHeight() - 1, playerY + (int)PARTICLE_HEIGHT_RANGE);

        // Smart animation offset
        double animationOffset = (tickCounter * 0.15) % 16; // Slower animation
        
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        
        // Draw fewer particles with better spacing
        for (int y = minY; y <= maxY; y += 4) { // Larger Y spacing
            // Corner particles (most visible)
            spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ + 15, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ + 15, canUnlock);
            
            // Fewer animated midpoint particles
            if (y % 8 == 0) { // Only every other Y level gets midpoints
                spawnBorderParticle(player, particleType, color, 
                    chunkX + 8 + Math.sin(animationOffset) * 1.5, y, chunkZ, canUnlock);
                spawnBorderParticle(player, particleType, color, 
                    chunkX + 8 + Math.sin(animationOffset) * 1.5, y, chunkZ + 15, canUnlock);
                spawnBorderParticle(player, particleType, color, 
                    chunkX, y, chunkZ + 8 + Math.cos(animationOffset) * 1.5, canUnlock);
                spawnBorderParticle(player, particleType, color, 
                    chunkX + 15, y, chunkZ + 8 + Math.cos(animationOffset) * 1.5, canUnlock);
            }
        }
    }

    private void spawnBorderParticle(Player player, Particle particleType, Color color, 
                                   double x, double y, double z, boolean canUnlock) {
        try {
            if (particleType == Particle.DUST && color != null) {
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, canUnlock ? 1.3f : 0.8f);
                player.spawnParticle(particleType, x, y, z, 1, 0, 0, 0, 0, dustOptions);
            } else {
                player.spawnParticle(particleType, x, y, z, canUnlock ? 2 : 1, 0.1, 0.1, 0.1, 0);
            }
            particlesSpawned.incrementAndGet();
        } catch (Exception e) {
            // Silently ignore particle errors to prevent spam
        }
    }

    /**
     * Utility methods
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private int getChunkDistance(Chunk chunk1, Chunk chunk2) {
        return Math.abs(chunk1.getX() - chunk2.getX()) + Math.abs(chunk1.getZ() - chunk2.getZ());
    }

    /**
     * Improved cache cleanup with better performance
     */
    private void cleanCache() {
        long now = System.currentTimeMillis();
        int initialSize = chunkEvaluationCache.size();
        
        // Remove expired entries
        chunkEvaluationCache.entrySet().removeIf(entry -> {
            Long timestamp = cacheTimestamps.get(entry.getKey());
            return timestamp == null || (now - timestamp) > CACHE_DURATION;
        });
        
        cacheTimestamps.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > CACHE_DURATION);
        
        // Limit cache size more aggressively if needed
        if (chunkEvaluationCache.size() > CHUNK_CACHE_SIZE) {
            // Remove oldest entries first
            chunkEvaluationCache.entrySet().stream()
                .sorted((e1, e2) -> {
                    Long t1 = cacheTimestamps.get(e1.getKey());
                    Long t2 = cacheTimestamps.get(e2.getKey());
                    if (t1 == null) return -1;
                    if (t2 == null) return 1;
                    return t1.compareTo(t2);
                })
                .limit(chunkEvaluationCache.size() - CHUNK_CACHE_SIZE + 20) // Remove extra
                .forEach(entry -> {
                    chunkEvaluationCache.remove(entry.getKey());
                    cacheTimestamps.remove(entry.getKey());
                });
        }
        
        int removedEntries = initialSize - chunkEvaluationCache.size();
        if (removedEntries > 0) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Cache cleanup: removed " + removedEntries + " entries, " + 
                chunkEvaluationCache.size() + " remaining");
        }
    }

    /**
     * Clean up data for a specific player (called when player leaves)
     */
    public void removePlayer(UUID playerId) {
        String playerKey = playerId.toString();
        playerBorderChunks.remove(playerKey);
        lastChunkScan.remove(playerKey);
        
        ChunklockPlugin.getInstance().getLogger().fine(
            "Cleaned up TickTask data for player: " + playerId);
    }

    /**
     * Performance metrics logging
     */
    private void logPerformanceMetrics() {
        long particles = particlesSpawned.getAndSet(0);
        long evaluations = chunksEvaluated.getAndSet(0);
        long hits = cacheHits.getAndSet(0);
        
        if (particles > 0 || evaluations > 0) {
            ChunklockPlugin.getInstance().getLogger().info(String.format(
                "TickTask Performance (30s): %d particles, %d evaluations, %d cache hits, %d cached chunks",
                particles, evaluations, hits, chunkEvaluationCache.size()
            ));
        }
    }

    /**
     * Get current cache statistics
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", chunkEvaluationCache.size());
        stats.put("playerBorderChunks", playerBorderChunks.size());
        stats.put("particlesSpawned", particlesSpawned.get());
        stats.put("chunksEvaluated", chunksEvaluated.get());
        stats.put("cacheHits", cacheHits.get());
        return stats;
    }
}