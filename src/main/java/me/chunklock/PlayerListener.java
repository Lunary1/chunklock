package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final UnlockGui unlockGui;
    private final Map<UUID, Long> lastWarned = new HashMap<>();
    private final Random random = new Random();
    private static final long COOLDOWN_MS = 2000L;
    private static final int MAX_SPAWN_ATTEMPTS = 100; // Increased for better chunk finding
    private static final int MAX_SCORE_THRESHOLD = 25; // Maximum score for starting chunks

    public PlayerListener(ChunkLockManager chunkLockManager, PlayerProgressTracker progressTracker, 
                         PlayerDataManager playerDataManager, UnlockGui unlockGui) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
        this.unlockGui = unlockGui;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            
            if (player == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Player join event with null player");
                return;
            }

            UUID playerId = player.getUniqueId();
            
            if (!playerDataManager.hasChunk(playerId)) {
                assignStartingChunk(player);
            } else {
                try {
                    Location savedSpawn = playerDataManager.getChunkSpawn(playerId);
                    if (savedSpawn != null && isValidLocation(savedSpawn)) {
                        // Ensure they spawn at the center of their assigned chunk
                        Location centerSpawn = getCenterLocationOfChunk(savedSpawn.getChunk());
                        player.teleport(centerSpawn);
                        player.setRespawnLocation(centerSpawn, true);
                        player.sendMessage("§aWelcome back! You've been returned to your starting chunk.");
                    } else {
                        ChunklockPlugin.getInstance().getLogger().warning("Invalid saved spawn for player " + player.getName() + ", reassigning");
                        assignStartingChunk(player);
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error teleporting player " + player.getName() + " to saved spawn", e);
                    assignStartingChunk(player);
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in player join handling", e);
        }
    }

    /**
     * Calculates the exact center location of a chunk
     * X = chunkX * 16 + 8
     * Z = chunkZ * 16 + 8  
     * Y = highest solid block at that position
     */
    private Location getCenterLocationOfChunk(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            throw new IllegalArgumentException("Invalid chunk provided");
        }

        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Calculate exact center coordinates
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        
        // Get the highest solid block at center
        int centerY;
        try {
            centerY = world.getHighestBlockAt(centerX, centerZ).getY();
            // Add 1 to place player on top of the block, not inside it
            centerY += 1;
            
            // Ensure Y is within world bounds
            centerY = Math.max(world.getMinHeight() + 1, 
                      Math.min(centerY, world.getMaxHeight() - 2));
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error getting highest block at chunk center, using fallback Y", e);
            centerY = world.getSpawnLocation().getBlockY();
        }
        
        // Return center location with 0.5 offset for perfect centering
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    private void assignStartingChunk(Player player) {
        try {
            if (player == null || player.getWorld() == null) {
                ChunklockPlugin.getInstance().getLogger().severe("Cannot assign starting chunk: null player or world");
                return;
            }

            World world = player.getWorld();
            Chunk bestChunk = findBestStartingChunk(player, world);

            if (bestChunk == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Could not find suitable starting chunk for " + player.getName() + ", using world spawn area");
                bestChunk = world.getSpawnLocation().getChunk();
            }

            try {
                setupPlayerSpawn(player, bestChunk);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Error setting up player spawn for " + player.getName(), e);
                // Try fallback to world spawn chunk
                try {
                    setupPlayerSpawn(player, world.getSpawnLocation().getChunk());
                } catch (Exception e2) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error: Could not set up any spawn for " + player.getName(), e2);
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in assignStartingChunk for player " + (player != null ? player.getName() : "null"), e);
        }
    }

    /**
     * Finds the best starting chunk with score below threshold
     */
    private Chunk findBestStartingChunk(Player player, World world) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        ChunklockPlugin.getInstance().getLogger().info("Searching for suitable starting chunk for " + player.getName() + 
            " (target score <= " + MAX_SCORE_THRESHOLD + ")");

        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            try {
                // Search in a reasonable area around spawn (±20 chunks = ±320 blocks)
                int cx = random.nextInt(41) - 20; // -20 to +20 chunk range
                int cz = random.nextInt(41) - 20;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;

                // Evaluate chunk score
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                // Check if this chunk meets our criteria
                if (evaluation.score <= MAX_SCORE_THRESHOLD) {
                    validChunksFound++;
                    
                    // Additional safety check - ensure spawn location is safe
                    Location centerLocation = getCenterLocationOfChunk(chunk);
                    if (isSafeSpawnLocation(centerLocation)) {
                        
                        // Prefer the chunk with the lowest score
                        if (evaluation.score < bestScore) {
                            bestChunk = chunk;
                            bestScore = evaluation.score;
                            
                            ChunklockPlugin.getInstance().getLogger().fine("Found better starting chunk at " + 
                                cx + "," + cz + " with score " + evaluation.score + 
                                " (difficulty: " + evaluation.difficulty + ")");
                        }
                        
                        // If we found a really good chunk (score <= 10), use it immediately
                        if (evaluation.score <= 10) {
                            ChunklockPlugin.getInstance().getLogger().info("Found excellent starting chunk at " + 
                                cx + "," + cz + " with score " + evaluation.score);
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error in chunk evaluation attempt " + attempt, e);
                continue;
            }
        }

        ChunklockPlugin.getInstance().getLogger().info("Chunk search completed for " + player.getName() + 
            ": found " + validChunksFound + " valid chunks, selected chunk with score " + 
            (bestChunk != null ? bestScore : "none"));

        return bestChunk;
    }

    private void setupPlayerSpawn(Player player, Chunk startChunk) throws Exception {
        if (startChunk == null || startChunk.getWorld() == null) {
            throw new IllegalArgumentException("Invalid chunk provided");
        }

        // Get the exact center location of the chunk
        Location centerSpawn = getCenterLocationOfChunk(startChunk);
        
        // Unlock the starting chunk
        chunkLockManager.unlockChunk(startChunk);
        
        // Set player data and teleport to center
        playerDataManager.setChunk(player.getUniqueId(), centerSpawn);
        player.teleport(centerSpawn);
        player.setRespawnLocation(centerSpawn, true);
        
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

    private boolean isSafeSpawnLocation(Location location) {
        try {
            if (location == null || location.getWorld() == null) return false;
            
            World world = location.getWorld();
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
                   blockAbove != null && (blockAbove.getType().isAir() || !blockAbove.getType().isSolid()) &&
                   !blockBelow.isLiquid();
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                "Error checking spawn safety at " + location, e);
            return false;
        }
    }

    private boolean isValidLocation(Location location) {
        try {
            return location != null && 
                   location.getWorld() != null && 
                   location.getY() >= location.getWorld().getMinHeight() && 
                   location.getY() <= location.getWorld().getMaxHeight();
        } catch (Exception e) {
            return false;
        }
    }

    private void handleChunkChange(PlayerMoveEvent event, Player player, Chunk toChunk) {
        try {
            chunkLockManager.initializeChunk(toChunk, player.getUniqueId());
            
            if (chunkLockManager.isBypassing(player)) {
                return;
            }

            if (chunkLockManager.isLocked(toChunk)) {
                long now = System.currentTimeMillis();
                long last = lastWarned.getOrDefault(player.getUniqueId(), 0L);

                if (now - last >= COOLDOWN_MS) {
                    try {
                        ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), toChunk);
                        player.sendMessage("§cThis chunk is locked!");
                        
                        String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
                        player.sendMessage("§7Difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score + " | Biome: " + biomeName);
                        lastWarned.put(player.getUniqueId(), now);
                        
                        unlockGui.open(player, toChunk);
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error showing chunk info to player", e);
                        player.sendMessage("§cThis chunk is locked!");
                    }
                }

                event.setCancelled(true);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error handling chunk change for player " + player.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        try {
            if (event.isCancelled()) return;
            
            Player player = event.getPlayer();
            if (player == null) return;

            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (from == null || to == null) return;

            Chunk fromChunk = from.getChunk();
            Chunk toChunk = to.getChunk();

            if (fromChunk == null || toChunk == null) return;

            if (!fromChunk.equals(toChunk)) {
                handleChunkChange(event, player, toChunk);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error in player move event", e);
        }
    }
}