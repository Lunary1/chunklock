package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.WorldManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Enhanced starting chunk service that handles both regular worlds and newly created player worlds.
 * Provides optimized chunk assignment for different world scenarios.
 */
public class EnhancedStartingChunkService {
    
    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final WorldManager worldManager;
    private final Random random = new Random();
    
    // Configuration constants
    private static final int MAX_SPAWN_ATTEMPTS = 50; // Reduced for faster assignment
    private static final int MAX_SCORE_THRESHOLD = 30; // Slightly higher for new worlds
    private static final int SPAWN_SEARCH_RADIUS = 10; // Smaller radius for new worlds
    private static final int NEW_WORLD_DELAY_TICKS = 40; // 2 seconds delay for new worlds
    
    public EnhancedStartingChunkService(ChunkLockManager chunkLockManager, PlayerDataManager playerDataManager, WorldManager worldManager) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
        this.worldManager = worldManager;
    }
    
    /**
     * Assigns a starting chunk to a player, with special handling for new player worlds
     */
    public void assignStartingChunk(Player player) {
        if (player == null) {
            ChunklockPlugin.getInstance().getLogger().severe("Cannot assign starting chunk: null player");
            return;
        }
        
        World world = player.getWorld();
        if (world == null) {
            ChunklockPlugin.getInstance().getLogger().severe("Cannot assign starting chunk: player world is null");
            return;
        }
        
        // Check if this is a newly created player world
        boolean isNewPlayerWorld = worldManager.isPlayerWorld(world.getName()) && 
                                 worldManager.isRecentlyCreatedWorld(world.getName());
        
        if (isNewPlayerWorld) {
            // For new player worlds, delay chunk assignment to allow world generation
            ChunklockPlugin.getInstance().getLogger().info("Delaying starting chunk assignment for " + player.getName() + " in new world " + world.getName());
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    assignStartingChunkForNewWorld(player, world);
                }
            }.runTaskLater(ChunklockPlugin.getInstance(), NEW_WORLD_DELAY_TICKS);
        } else {
            // For existing worlds, use standard assignment
            assignStartingChunkStandard(player, world);
        }
    }
    
    /**
     * Assign starting chunk for a newly created player world
     */
    private void assignStartingChunkForNewWorld(Player player, World world) {
        try {
            ChunklockPlugin.getInstance().getLogger().info("Assigning starting chunk for " + player.getName() + " in new world " + world.getName());
            
            // For new worlds, start closer to spawn and use a simpler approach
            Chunk startingChunk = findSuitableChunkNearSpawn(player, world);
            
            if (startingChunk == null) {
                // If we still can't find one, just use the spawn chunk
                startingChunk = world.getSpawnLocation().getChunk();
                ChunklockPlugin.getInstance().getLogger().info("Using spawn chunk for " + player.getName() + " at " + startingChunk.getX() + "," + startingChunk.getZ());
            }
            
            setupPlayerSpawn(player, startingChunk);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Error assigning starting chunk for new world", e);
            fallbackSpawn(player, world);
        }
    }
    
    /**
     * Standard starting chunk assignment for existing worlds
     */
    private void assignStartingChunkStandard(Player player, World world) {
        try {
            Chunk bestChunk = findBestStartingChunk(player, world);
            
            if (bestChunk == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Could not find suitable starting chunk for " + player.getName() + ", using world spawn area");
                bestChunk = world.getSpawnLocation().getChunk();
            }
            
            setupPlayerSpawn(player, bestChunk);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Error in standard chunk assignment", e);
            fallbackSpawn(player, world);
        }
    }
    
    /**
     * Find a suitable chunk near spawn for new worlds
     */
    private Chunk findSuitableChunkNearSpawn(Player player, World world) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        
        // Search in a smaller area around spawn for new worlds
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            try {
                // Smaller search radius for new worlds
                int cx = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
                int cz = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;
                
                // Force chunk generation if needed
                if (!chunk.isLoaded()) {
                    chunk.load(true);
                }
                
                // Wait a bit for generation to complete
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ignored) {}
                
                // Evaluate chunk score
                try {
                    var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                    
                    if (evaluation.score <= MAX_SCORE_THRESHOLD && evaluation.score < bestScore) {
                        bestScore = evaluation.score;
                        bestChunk = chunk;
                        
                        // If we find a good chunk, use it
                        if (evaluation.score <= 15) {
                            ChunklockPlugin.getInstance().getLogger().info("Found good starting chunk for " + player.getName() + 
                                " at " + chunk.getX() + "," + chunk.getZ() + " with score " + evaluation.score);
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Skip this chunk if evaluation fails
                    continue;
                }
                
            } catch (Exception e) {
                // Skip this attempt if there's an error
                continue;
            }
        }
        
        if (bestChunk != null) {
            ChunklockPlugin.getInstance().getLogger().info("Selected starting chunk for " + player.getName() + 
                " at " + bestChunk.getX() + "," + bestChunk.getZ() + " with score " + bestScore);
        }
        
        return bestChunk;
    }
    
    /**
     * Original chunk finding logic for existing worlds
     */
    private Chunk findBestStartingChunk(Player player, World world) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        ChunklockPlugin.getInstance().getLogger().info("Searching for suitable starting chunk for " + player.getName() + 
            " (target score <= " + MAX_SCORE_THRESHOLD + ")");

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            try {
                // Search in a reasonable area around spawn
                int cx = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
                int cz = random.nextInt(SPAWN_SEARCH_RADIUS * 2 + 1) - SPAWN_SEARCH_RADIUS;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;

                // Evaluate chunk score
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                // Check if this chunk meets our criteria
                if (evaluation.score <= MAX_SCORE_THRESHOLD) {
                    validChunksFound++;
                    if (evaluation.score < bestScore) {
                        bestScore = evaluation.score;
                        bestChunk = chunk;
                    }
                    
                    // If we find a really good chunk, stop searching
                    if (evaluation.score <= 10) {
                        break;
                    }
                }
                
            } catch (Exception e) {
                // Skip problematic chunks
                continue;
            }
        }

        ChunklockPlugin.getInstance().getLogger().info("Starting chunk search complete for " + player.getName() + 
            ": found " + validChunksFound + " valid chunks" + 
            (bestChunk != null ? ", best score: " + bestScore : ", best score: none"));

        return bestChunk;
    }
    
    /**
     * Set up player spawn in the assigned chunk
     */
    private void setupPlayerSpawn(Player player, Chunk chunk) {
        try {
            UUID playerId = player.getUniqueId();
            
            // Unlock the starting chunk
            chunkLockManager.unlockChunk(chunk, playerId);
            ChunklockPlugin.getInstance().getLogger().info("Unlocked starting chunk for " + player.getName());
            
            // Find a safe spawn location within the chunk
            Location spawnLocation = findSafeSpawnInChunk(chunk);
            
            // Save the spawn location
            playerDataManager.setChunk(playerId, spawnLocation);
            
            // Teleport player to the spawn location
            player.teleport(spawnLocation);
            
            ChunklockPlugin.getInstance().getLogger().info("Successfully set up starting chunk for " + player.getName() + 
                " at chunk " + chunk.getX() + "," + chunk.getZ());
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Error setting up player spawn", e);
            throw e;
        }
    }
    
    /**
     * Find a safe spawn location within a chunk
     */
    private Location findSafeSpawnInChunk(Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() * 16 + 8; // Center of chunk
        int chunkZ = chunk.getZ() * 16 + 8;
        
        // Find the highest safe location
        int y = world.getHighestBlockYAt(chunkX, chunkZ) + 1;
        
        // Make sure it's not too high or too low
        y = Math.max(y, 64); // Minimum Y level
        y = Math.min(y, 200); // Maximum Y level
        
        return new Location(world, chunkX + 0.5, y, chunkZ + 0.5);
    }
    
    /**
     * Fallback spawn assignment
     */
    private void fallbackSpawn(Player player, World world) {
        try {
            Chunk spawnChunk = world.getSpawnLocation().getChunk();
            setupPlayerSpawn(player, spawnChunk);
            ChunklockPlugin.getInstance().getLogger().info("Used fallback spawn for " + player.getName());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Even fallback spawn failed for " + player.getName(), e);
        }
    }
}
