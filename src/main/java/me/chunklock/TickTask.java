package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TickTask extends BukkitRunnable {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private int tickCounter = 0;
    
    // Performance optimizations
    private static final int PARTICLE_UPDATE_INTERVAL = 10; // Reduced frequency: every 0.5 seconds
    private static final int PARTICLES_PER_SIDE = 4; // Reduced particle density
    private static final double PARTICLE_HEIGHT_RANGE = 3.0; // Reduced height range
    private static final int MAX_BORDER_DISTANCE = 48; // Only show borders within 3 chunks
    private static final int CHUNK_CACHE_SIZE = 100; // Cache chunk evaluations
    
    // Caching system to avoid repeated chunk evaluations
    private final Map<String, ChunkEvaluator.ChunkValueData> chunkEvaluationCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();
    private static final long CACHE_DURATION = 30000; // 30 seconds cache
    
    // Track which chunks actually need borders
    private final Map<String, Set<String>> playerBorderChunks = new HashMap<>();

    public TickTask(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    @Override
    public void run() {
        tickCounter++;
        
        // Update particles less frequently
        if (tickCounter % PARTICLE_UPDATE_INTERVAL != 0) {
            return;
        }
        
        // Clean cache periodically
        if (tickCounter % 200 == 0) { // Every 10 seconds
            cleanCache();
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (chunkLockManager.isBypassing(player)) {
                continue;
            }
            
            try {
                updatePlayerEffectsOptimized(player);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Error updating effects for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Optimized version that only shows borders for chunks adjacent to unlocked chunks
     */
    private void updatePlayerEffectsOptimized(Player player) {
        Chunk playerChunk = player.getLocation().getChunk();
        String playerKey = player.getUniqueId().toString();
        
        // Find chunks that should show borders
        Set<String> borderChunks = findBorderChunks(player, playerChunk);
        playerBorderChunks.put(playerKey, borderChunks);
        
        // Only draw borders for identified chunks
        for (String chunkKey : borderChunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                try {
                    int x = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    Chunk chunk = player.getWorld().getChunkAt(x, z);
                    
                    // Double-check distance
                    if (getChunkDistance(playerChunk, chunk) <= 3) {
                        var eval = getCachedEvaluation(player, chunk);
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
     * Finds chunks that should show borders (locked chunks adjacent to unlocked chunks)
     */
    private Set<String> findBorderChunks(Player player, Chunk playerChunk) {
        Set<String> borderChunks = new HashSet<>();
        Set<String> checkedChunks = new HashSet<>();
        
        // Check chunks around player (3x3 grid)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
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
            }
        }
    }

    /**
     * Cached chunk evaluation to avoid repeated expensive operations
     */
    private ChunkEvaluator.ChunkValueData getCachedEvaluation(Player player, Chunk chunk) {
        String key = getChunkKey(chunk) + ":" + player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Check if we have a valid cached result
        if (chunkEvaluationCache.containsKey(key)) {
            Long timestamp = cacheTimestamps.get(key);
            if (timestamp != null && (now - timestamp) < CACHE_DURATION) {
                return chunkEvaluationCache.get(key);
            }
        }
        
        // Calculate and cache new result
        var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
        chunkEvaluationCache.put(key, evaluation);
        cacheTimestamps.put(key, now);
        
        return evaluation;
    }

    /**
     * Optimized border drawing with fewer particles and smarter placement
     */
    private void drawOptimizedChunkBorder(Player player, Chunk chunk, Difficulty difficulty, boolean canUnlock) {
        Location playerLoc = player.getLocation();
        
        // Calculate chunk center for distance check
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        double chunkCenterX = chunkX + 8;
        double chunkCenterZ = chunkZ + 8;
        
        // Skip if too far from player
        double distanceSquared = Math.pow(playerLoc.getX() - chunkCenterX, 2) + Math.pow(playerLoc.getZ() - chunkCenterZ, 2);
        if (distanceSquared > MAX_BORDER_DISTANCE * MAX_BORDER_DISTANCE) {
            return;
        }

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

        // Calculate Y range around player (much smaller range)
        int playerY = playerLoc.getBlockY();
        int minY = Math.max(chunk.getWorld().getMinHeight(), playerY - (int)PARTICLE_HEIGHT_RANGE);
        int maxY = Math.min(chunk.getWorld().getMaxHeight() - 1, playerY + (int)PARTICLE_HEIGHT_RANGE);

        // Smart animation offset
        double animationOffset = (tickCounter * 0.2) % 16;
        
        // Draw corners and midpoints only (much fewer particles)
        for (int y = minY; y <= maxY; y += 3) { // Skip every other Y level
            // Corner particles (more visible)
            spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ + 15, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ + 15, canUnlock);
            
            // Midpoint particles with animation
            spawnBorderParticle(player, particleType, color, chunkX + 8 + Math.sin(animationOffset) * 2, y, chunkZ, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 8 + Math.sin(animationOffset) * 2, y, chunkZ + 15, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ + 8 + Math.cos(animationOffset) * 2, canUnlock);
            spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ + 8 + Math.cos(animationOffset) * 2, canUnlock);
        }
    }

    private void spawnBorderParticle(Player player, Particle particleType, Color color, 
                                   double x, double y, double z, boolean canUnlock) {
        try {
            if (particleType == Particle.DUST && color != null) {
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, canUnlock ? 1.5f : 1.0f);
                player.spawnParticle(particleType, x, y, z, 1, 0, 0, 0, 0, dustOptions);
            } else {
                player.spawnParticle(particleType, x, y, z, canUnlock ? 2 : 1, 0.1, 0.1, 0.1, 0);
            }
        } catch (Exception e) {
            // Silently ignore particle errors
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

    private void cleanCache() {
        long now = System.currentTimeMillis();
        chunkEvaluationCache.entrySet().removeIf(entry -> {
            Long timestamp = cacheTimestamps.get(entry.getKey());
            return timestamp == null || (now - timestamp) > CACHE_DURATION;
        });
        cacheTimestamps.entrySet().removeIf(entry -> 
            (now - entry.getValue()) > CACHE_DURATION);
        
        // Limit cache size
        if (chunkEvaluationCache.size() > CHUNK_CACHE_SIZE) {
            chunkEvaluationCache.clear();
            cacheTimestamps.clear();
        }
    }
}