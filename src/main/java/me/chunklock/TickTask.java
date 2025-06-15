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
    
    // Performance optimizations with full border visibility
    private static final int PARTICLE_UPDATE_INTERVAL = 25; // Slightly slower for more particles
    private static final int CHUNK_SCAN_INTERVAL = 40; // Every 2 seconds for chunk updates
    private static final int CACHE_CLEANUP_INTERVAL = 600; // Every 30 seconds
    
    // Full border configuration
    private static final double PARTICLE_SPACING = 1.8; // Space between border particles
    private static final int VERTICAL_LAYERS = 1; // Number of vertical layers
    private static final double BORDER_THICKNESS = 0.25; // Slight thickness for visibility
    private static final int MAX_BORDER_DISTANCE = 48; // Max distance to show borders
    private static final int CHUNK_CACHE_SIZE = 120; // Reduced cache size
    private static final double FOV_DOT_THRESHOLD = -0.6; // 130-degree FOV
    
    // Enhanced visibility settings
    private static final boolean USE_ENHANCED_CORNERS = true;
    private static final boolean USE_GROUND_MARKERS = true;
    private static final boolean USE_ANIMATION_EFFECTS = true;
    
    // Thread-safe collections
    private final Map<String, ChunkEvaluator.ChunkValueData> chunkEvaluationCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 45000; // 45 seconds
    
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
            // Update particles at configured interval
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
     * Optimized version that shows full borders for chunks adjacent to unlocked chunks
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
        
        // Draw full borders for identified chunks with view culling
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
                        drawFullChunkBorder(player, chunk, eval.difficulty, canUnlock);
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
     * Draws complete, visible chunk borders with full perimeter coverage
     */
    private void drawFullChunkBorder(Player player, Chunk chunk, Difficulty difficulty, boolean canUnlock) {
        // Determine particle type and color - red for locked, green for unlockable
        Particle particleType = getParticleType(canUnlock);
        Color color = getParticleColor(canUnlock);

        // Calculate Y levels around player for vertical coverage
        Location playerLoc = player.getLocation();
        int playerY = playerLoc.getBlockY();
        int baseY = Math.max(chunk.getWorld().getMinHeight(), playerY - 2);
        int topY = Math.min(chunk.getWorld().getMaxHeight() - 1, playerY + 3);

        // Animation offset for dynamic effects
        double animationOffset = USE_ANIMATION_EFFECTS ? (tickCounter * 0.15) % (Math.PI * 2) : 0;
        
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;
        
        // Draw full border on multiple Y levels
        for (int yLevel = 0; yLevel < VERTICAL_LAYERS; yLevel++) {
            int y = baseY + (yLevel * 2); // Space layers by 2 blocks
            if (y > topY) break;
            
            // FIX: Draw sides without overlapping corners
            // North side (exclude corners to avoid overlap)
            drawBorderSide(player, particleType, color, canUnlock, animationOffset,
                          chunkX + 1, chunkX + 14, y, chunkZ, true); // Start at +1, end at +14
            
            // South side (exclude corners to avoid overlap)
            drawBorderSide(player, particleType, color, canUnlock, animationOffset,
                          chunkX + 1, chunkX + 14, y, chunkZ + 15, true); // Start at +1, end at +14
            
            // West side (include full length since we excluded corners from north/south)
            drawBorderSide(player, particleType, color, canUnlock, animationOffset,
                          chunkX, chunkZ, chunkZ + 15, y, false);
            
            // East side (include full length since we excluded corners from north/south)
            drawBorderSide(player, particleType, color, canUnlock, animationOffset,
                          chunkX + 15, chunkZ, chunkZ + 15, y, false);
        }
        
        // Add enhanced corner markers for better visibility (drawn separately to avoid conflicts)
        if (USE_ENHANCED_CORNERS) {
            drawEnhancedCorners(player, particleType, color, canUnlock, chunkX, chunkZ, baseY, topY);
        }
        
        // Add ground markers every few ticks for performance
        if (USE_GROUND_MARKERS && tickCounter % 15 == 0) {
            drawGroundMarkers(player, particleType, color, canUnlock, chunkX, chunkZ);
        }
    }

    /**
     * Draws one side of the chunk border with proper spacing
     */
    private void drawBorderSide(Player player, Particle particleType, Color color, boolean canUnlock, 
                               double animationOffset, int fixedCoord, int startCoord, int endCoord, 
                               int y, boolean isHorizontal) {
        double totalLength = Math.abs(endCoord - startCoord);
        int particleCount = (int) Math.ceil(totalLength / PARTICLE_SPACING);
        
        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            double position = startCoord + (progress * (endCoord - startCoord));
            
            // Add slight animation wave effect
            double waveOffset = USE_ANIMATION_EFFECTS ? 
                Math.sin(animationOffset + progress * Math.PI * 2) * 0.15 : 0;
            
            double x, z;
            if (isHorizontal) {
                x = position + waveOffset;
                z = fixedCoord;
            } else {
                x = fixedCoord;
                z = position + waveOffset;
            }
            
            // Spawn particles with slight thickness for better visibility
            spawnBorderParticleWithThickness(player, particleType, color, x, y, z, canUnlock);
        }
    }

    /**
     * Enhanced corner markers for maximum visibility
     */
    private void drawEnhancedCorners(Player player, Particle particleType, Color color, boolean canUnlock,
                                    int chunkX, int chunkZ, int baseY, int topY) {
        int[][] corners = {
            {chunkX, chunkZ},           // Northwest
            {chunkX + 15, chunkZ},      // Northeast  
            {chunkX, chunkZ + 15},      // Southwest
            {chunkX + 15, chunkZ + 15}  // Southeast
        };
        
        for (int[] corner : corners) {
            // FIX: Only draw corner particles every few Y levels to reduce density
            for (int y = baseY; y <= topY; y += 2) { // Every 2 blocks instead of every block
                double x = corner[0] + 0.5;
                double z = corner[1] + 0.5;
                
                if (canUnlock) {
                    // Bright unlockable corners with multiple effects
                    player.spawnParticle(Particle.HAPPY_VILLAGER, x, y, z, 1, 0.1, 0.1, 0.1, 0); // Reduced count
                    if (y % 4 == 0) { // Only every 4th Y level for enchant particles
                        player.spawnParticle(Particle.ENCHANT, x, y, z, 2, 0.2, 0.2, 0.2, 1);
                        particlesSpawned.addAndGet(3);
                    } else {
                        particlesSpawned.incrementAndGet();
                    }
                } else {
                    // Enhanced locked corners - always red but reduced density
                    spawnBorderParticleWithThickness(player, particleType, color, x, y, z, false);
                }
            }
        }
    }

    /**
     * Ground markers for better chunk identification
     */
    private void drawGroundMarkers(Player player, Particle particleType, Color color, boolean canUnlock,
                                  int chunkX, int chunkZ) {
        World world = player.getWorld();
        
        // Corner ground markers
        int[] cornerX = {chunkX, chunkX + 15};
        int[] cornerZ = {chunkZ, chunkZ + 15};
        
        for (int x : cornerX) {
            for (int z : cornerZ) {
                try {
                    int groundY = world.getHighestBlockYAt(x, z) + 1;
                    
                    if (canUnlock) {
                        // Bright green ground markers for unlockable chunks
                        player.spawnParticle(Particle.DUST, x + 0.5, groundY, z + 0.5, 2, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(Color.LIME, 3.0f));
                        player.spawnParticle(Particle.HAPPY_VILLAGER, x + 0.5, groundY, z + 0.5, 1, 0.1, 0.1, 0.1, 0);
                        particlesSpawned.addAndGet(3);
                    } else {
                        // Red ground markers for locked chunks
                        player.spawnParticle(Particle.DUST, x + 0.5, groundY, z + 0.5, 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.RED, 2.5f));
                        particlesSpawned.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Skip if can't get ground level
                }
            }
        }
    }

    /**
     * Enhanced particle spawning with thickness for better visibility
     */
    private void spawnBorderParticleWithThickness(Player player, Particle particleType, Color color, 
                                                double x, double y, double z, boolean canUnlock) {
        try {
            if (particleType == Particle.DUST && color != null) {
                // Enhanced dust particles with larger size
                float particleSize = canUnlock ? 2.8f : 2.2f;
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, particleSize);
                
                // Main particle
                player.spawnParticle(particleType, x, y, z, 1, 0, 0, 0, 0, dustOptions);
                
                // Add thickness by spawning nearby particles for better visibility
                if (BORDER_THICKNESS > 0) {
                    player.spawnParticle(particleType, x + BORDER_THICKNESS, y, z, 1, 0, 0, 0, 0, dustOptions);
                    player.spawnParticle(particleType, x, y, z + BORDER_THICKNESS, 1, 0, 0, 0, 0, dustOptions);
                    particlesSpawned.addAndGet(3);
                } else {
                    particlesSpawned.incrementAndGet();
                }
                
            } else if (particleType == Particle.HAPPY_VILLAGER) {
                // Enhanced unlockable particles
                int count = canUnlock ? 3 : 2;
                player.spawnParticle(particleType, x, y, z, count, 0.2, 0.2, 0.2, 0);
                
                // Add sparkle effect for unlockable chunks
                if (canUnlock && Math.random() < 0.4) {
                    player.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(Color.YELLOW, 2.2f));
                    particlesSpawned.addAndGet(count + 1);
                } else {
                    particlesSpawned.addAndGet(count);
                }
                
            } else {
                // Other particle types (FLAME, LAVA, WITCH)
                int particleCount = canUnlock ? 2 : 1;
                double spread = canUnlock ? 0.2 : 0.1;
                player.spawnParticle(particleType, x, y, z, particleCount, spread, spread, spread, 0);
                
                // Add thickness for non-dust particles
                if (BORDER_THICKNESS > 0) {
                    player.spawnParticle(particleType, x + BORDER_THICKNESS, y, z, 1, 0.1, 0.1, 0.1, 0);
                    particlesSpawned.addAndGet(particleCount + 1);
                } else {
                    particlesSpawned.addAndGet(particleCount);
                }
            }
        } catch (Exception e) {
            // Silently ignore particle errors
        }
    }

    /**
     * Helper method to get particle type based on unlock status only
     */
    private Particle getParticleType(boolean canUnlock) {
        if (canUnlock) {
            return Particle.HAPPY_VILLAGER;
        } else {
            return Particle.DUST; // Always use DUST for locked chunks
        }
    }

    /**
     * Helper method to get particle color - red for locked, green for unlockable
     */
    private Color getParticleColor(boolean canUnlock) {
        if (canUnlock) {
            return Color.LIME; // Bright green for unlockable
        } else {
            return Color.RED; // Always red for locked chunks
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
     * Improved cached chunk evaluation
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
        
        // Calculate and cache new result
        var evaluation = chunkLockManager.evaluateChunk(null, chunk);
        chunkEvaluationCache.put(key, evaluation);
        cacheTimestamps.put(key, now);
        chunksEvaluated.incrementAndGet();
        
        return evaluation;
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
        
        // Limit cache size if needed
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
                .limit(chunkEvaluationCache.size() - CHUNK_CACHE_SIZE + 20)
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