package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.util.chunk.ChunkUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Service responsible for assigning starting chunks to new players.
 * Handles chunk evaluation, finding suitable spawn locations, and player setup.
 * NOW WITH PERFORMANCE OPTIMIZATION using pre-allocated chunks.
 */
public class StartingChunkService {
    
    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final Random random = new Random();
    private ChunkPreAllocationService preAllocationService; // NEW: Pre-allocation service
    
    // Debug configuration
    private boolean debugLogging;
    
    // Configuration constants
    private static final int MAX_SPAWN_ATTEMPTS = 100;
    private static final int MAX_SCORE_THRESHOLD = 25;
    private static final int SPAWN_SEARCH_RADIUS = 20; // chunks around spawn
    private static final int FAST_SEARCH_LIMIT = 20; // Limit attempts for fast search
    private static final int EXCELLENT_SCORE_THRESHOLD = 10; // Stop searching if we find this good a score
    
    public StartingChunkService(ChunkLockManager chunkLockManager, PlayerDataManager playerDataManager) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
        loadDebugConfiguration();
    }
    
    /**
     * Load debug configuration from config.yml
     */
    private void loadDebugConfiguration() {
        // Use modular debug config
        me.chunklock.config.modular.DebugConfig debugConfig = ChunklockPlugin.getInstance().getConfigManager().getDebugConfig();
        boolean masterDebug = debugConfig != null ? debugConfig.isEnabled() : false;
        this.debugLogging = masterDebug && (debugConfig != null ? debugConfig.isChunkFindingDebug() : false);
    }
    
    /**
     * Reload debug configuration (called during plugin reload)
     */
    public void reloadConfiguration() {
        loadDebugConfiguration();
    }
    
    /**
     * Set the pre-allocation service (called during plugin initialization)
     */
    public void setPreAllocationService(ChunkPreAllocationService preAllocationService) {
        this.preAllocationService = preAllocationService;
    }
    
    /**
     * Assigns a starting chunk to a new player.
     * Finds the best available chunk, unlocks it, and sets up the player spawn.
     */
    public void assignStartingChunk(Player player) {
        try {
            if (player == null) {
                ChunklockPlugin.getInstance().getLogger().severe("Cannot assign starting chunk: null player");
                return;
            }
            
            World world = player.getWorld();
            if (world == null) {
                ChunklockPlugin.getInstance().getLogger().severe("Cannot assign starting chunk: player world is null");
                return;
            }

            Chunk bestChunk = findBestStartingChunk(player, world);

            if (bestChunk == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Could not find suitable starting chunk for " + player.getName() + ", using world spawn area");
                bestChunk = world.getSpawnLocation().getChunk();
            }

            try {
                setupPlayerSpawn(player, bestChunk);
            } catch (IllegalArgumentException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Invalid arguments for player spawn setup: " + player.getName(), e);
                // Try fallback to world spawn chunk
                fallbackSpawn(player, world);
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Invalid state during player spawn setup: " + player.getName(), e);
                fallbackSpawn(player, world);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Unexpected error setting up player spawn for " + player.getName(), e);
                fallbackSpawn(player, world);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in assignStartingChunk for player " + (player != null ? player.getName() : "null"), e);
        }
    }
    
    /**
     * Finds the best starting chunk with score below threshold - OPTIMIZED VERSION.
     * Uses pre-allocated chunks first, then falls back to real-time search if needed.
     */
    private Chunk findBestStartingChunk(Player player, World world) {
        long startTime = System.currentTimeMillis();
        
        // OPTIMIZATION 1: Try pre-allocated chunks first (instant!)
        if (preAllocationService != null && preAllocationService.hasAvailableChunks()) {
            Chunk preAllocatedChunk = preAllocationService.getPreAllocatedChunk();
            if (preAllocatedChunk != null) {
                long searchTime = System.currentTimeMillis() - startTime;
                if (debugLogging) {
                    ChunklockPlugin.getInstance().getLogger().info("Found pre-allocated starting chunk for " + player.getName() + 
                        " at " + preAllocatedChunk.getX() + "," + preAllocatedChunk.getZ() + " (took " + searchTime + "ms - INSTANT!)");
                }
                return preAllocatedChunk;
            }
        }
        
        // FALLBACK: Real-time search (optimized)
        if (debugLogging) {
            ChunklockPlugin.getInstance().getLogger().info("No pre-allocated chunks available, performing optimized search for " + player.getName());
        }
        
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        if (debugLogging) {
            ChunklockPlugin.getInstance().getLogger().info("Searching for suitable starting chunk for " + player.getName() + 
                " (target score <= " + MAX_SCORE_THRESHOLD + ")");
        }

        // OPTIMIZATION 2: Use faster search with fewer attempts
        int searchAttempts = Math.min(FAST_SEARCH_LIMIT, MAX_SPAWN_ATTEMPTS);
        
        for (int attempt = 0; attempt < searchAttempts; attempt++) {
            try {
                // Search in a reasonable area around spawn
                int cx = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS; // -20 to +20 chunk range
                int cz = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;

                // OPTIMIZATION 3: Quick water check first (fastest check)
                if (!ChunklockPlugin.getInstance().getChunkEvaluator().isChunkSuitableForSpawning(chunk)) {
                    ChunklockPlugin.getInstance().getLogger().fine("Skipping water-dominated chunk at " + chunk.getX() + "," + chunk.getZ());
                    continue;
                }

                // OPTIMIZATION 4: Use cached evaluation for better performance
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                // Check if this chunk meets our criteria
                if (evaluation.score <= MAX_SCORE_THRESHOLD) {
                    validChunksFound++;
                    if (evaluation.score < bestScore) {
                        bestScore = evaluation.score;
                        bestChunk = chunk;
                        
                        ChunklockPlugin.getInstance().getLogger().fine("Found better starting chunk at " + chunk.getX() + "," + chunk.getZ() + 
                            " with score " + evaluation.score + " (attempt " + (attempt + 1) + ")");
                    }
                    
                    // OPTIMIZATION 5: Stop early if we find an excellent chunk
                    if (evaluation.score <= EXCELLENT_SCORE_THRESHOLD) {
                        ChunklockPlugin.getInstance().getLogger().info("Found excellent starting chunk for " + player.getName() + 
                            " at " + chunk.getX() + "," + chunk.getZ() + " with score " + evaluation.score + " after " + (attempt + 1) + " attempts");
                        break;
                    }
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error evaluating chunk for player " + player.getName(), e);
                continue;
            }
        }

        long searchTime = System.currentTimeMillis() - startTime;
        if (debugLogging) {
            ChunklockPlugin.getInstance().getLogger().info("Starting chunk search complete for " + player.getName() + 
                ": found " + validChunksFound + " valid chunks, best score: " + (bestChunk != null ? bestScore : "none") + 
                " (took " + searchTime + "ms)");
        }

        return bestChunk;
    }
    
    /**
     * Sets up player spawn at the given chunk.
     */
    private void setupPlayerSpawn(Player player, Chunk startChunk) throws IllegalArgumentException, IllegalStateException {
        if (startChunk == null) {
            throw new IllegalArgumentException("Starting chunk cannot be null");
        }
        
        if (startChunk.getWorld() == null) {
            throw new IllegalStateException("Starting chunk world is null");
        }

        // Get the exact center location of the chunk
        Location centerSpawn = ChunkUtils.getChunkCenter(startChunk);
        
        // Unlock the starting chunk - assign ownership to the individual player, not team leader
        // This ensures solo players (not in teams) still own their starting chunk
        UUID playerId = player.getUniqueId();
        chunkLockManager.unlockChunk(startChunk, playerId);
        
        // Set player data and teleport to center
        playerDataManager.setChunk(player.getUniqueId(), centerSpawn);
        player.teleport(centerSpawn);
        player.setRespawnLocation(centerSpawn, true);
        
        // Send player information about their new chunk
        sendChunkInfoToPlayer(player, startChunk, centerSpawn);
    }
    
    /**
     * Sends chunk information to the player.
     */
    private void sendChunkInfoToPlayer(Player player, Chunk startChunk, Location centerSpawn) {
        try {
            ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), startChunk);
            player.sendMessage("§aYou have been assigned a starting chunk at " + startChunk.getX() + ", " + startChunk.getZ());
            player.sendMessage("§7Spawning at center coordinates: " + (int)centerSpawn.getX() + ", " + (int)centerSpawn.getZ());
            
            String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
            player.sendMessage("§7Chunk difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score + " | Biome: " + biomeName);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error sending chunk info to player", e);
            player.sendMessage("§aYou have been assigned a starting chunk at " + startChunk.getX() + ", " + startChunk.getZ());
        }
    }
    
    /**
     * Fallback spawn setup when normal assignment fails.
     */
    private void fallbackSpawn(Player player, World world) {
        try {
            setupPlayerSpawn(player, world.getSpawnLocation().getChunk());
        } catch (Exception e2) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error: Could not set up any spawn for " + player.getName(), e2);
        }
    }
    
    /**
     * Check if a location is safe for player spawn.
     */
    public boolean isSafeSpawnLocation(Location location) {
        try {
            if (location == null) return false;
            
            World world = location.getWorld();
            if (world == null) return false;
            
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            
            // Check if Y is within reasonable bounds
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 10) return false;
            
            var blockAt = world.getBlockAt(x, y, z);
            var blockBelow = world.getBlockAt(x, y - 1, z);
            var blockAbove = world.getBlockAt(x, y + 1, z);
            
            // Check for solid ground, air space to stand, and air above head
            return blockBelow != null && blockBelow.getType().isSolid() && 
                   blockAt != null && (blockAt.getType().isAir() || !blockAt.getType().isSolid()) && 
                   blockAbove != null && (blockAbove.getType().isAir() || !blockAbove.getType().isSolid());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if a location is valid (not null, world exists, etc.)
     */
    public boolean isValidLocation(Location location) {
        return location != null && 
               location.getWorld() != null && 
               location.getChunk() != null;
    }
}