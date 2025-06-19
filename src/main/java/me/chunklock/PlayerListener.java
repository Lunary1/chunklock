package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final UnlockGui unlockGui;
    
    // Thread-safe collections for better performance
    private final Map<UUID, Long> lastWarned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUnlockAttempt = new ConcurrentHashMap<>();
    
    // Track last border update per player to avoid excessive updates
    private final Map<UUID, Long> lastBorderUpdate = new ConcurrentHashMap<>();
    private static final long BORDER_UPDATE_COOLDOWN_MS = 5000L; // 5 seconds between border updates
    
    // FIX: Track if player is truly new (first time joining)
    private final Set<UUID> newPlayers = new HashSet<>();
    
    private final Random random = new Random();
    private static final long COOLDOWN_MS = 2000L;
    private static final long UNLOCK_COOLDOWN_MS = 1000L; // Rate limiting for unlock attempts
    private static final int MAX_SPAWN_ATTEMPTS = 100;
    private static final int MAX_SCORE_THRESHOLD = 25;

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
            
            // Clear any stale data
            lastWarned.remove(playerId);
            lastUnlockAttempt.remove(playerId);
            lastBorderUpdate.remove(playerId); // Clear border update tracking
            
            if (!playerDataManager.hasChunk(playerId)) {
                // FIX: Mark as new player and assign starting chunk
                newPlayers.add(playerId);
                assignStartingChunk(player);
            } else {
                try {
                    Location savedSpawn = playerDataManager.getChunkSpawn(playerId);
                    if (savedSpawn != null && isValidLocation(savedSpawn)) {
                        // FIX: Only teleport to center if this is a new player or if they're outside their assigned chunk
                        Chunk savedChunk = savedSpawn.getChunk();
                        Chunk currentChunk = player.getLocation().getChunk();
                        
                        // Check if player is in an unlocked chunk
                        chunkLockManager.initializeChunk(currentChunk, playerId);
                        boolean currentChunkUnlocked = !chunkLockManager.isLocked(currentChunk);
                        
                        if (newPlayers.contains(playerId) || !currentChunkUnlocked) {
                            // Only teleport to center if it's a new player or they're in a locked chunk
                            Location centerSpawn = getCenterLocationOfChunk(savedChunk);
                            player.teleport(centerSpawn);
                            player.setRespawnLocation(centerSpawn, true);
                            player.sendMessage("§aWelcome back! You've been returned to your starting chunk.");
                        } else {
                            // Player is in an unlocked chunk, just set respawn location but don't teleport
                            Location centerSpawn = getCenterLocationOfChunk(savedChunk);
                            player.setRespawnLocation(centerSpawn, true);
                            player.sendMessage("§aWelcome back! Your respawn point has been set to your starting chunk.");
                        }
                        
                        // Remove from new players set after first join handling
                        newPlayers.remove(playerId);
                    } else {
                        ChunklockPlugin.getInstance().getLogger().warning("Invalid saved spawn for player " + player.getName() + ", reassigning");
                        newPlayers.add(playerId);
                        assignStartingChunk(player);
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error handling returning player " + player.getName(), e);
                    newPlayers.add(playerId);
                    assignStartingChunk(player);
                }
            }
            
            // Update glass borders after a delay (allow other systems to initialize first)
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                if (player.isOnline()) {
                    try {
                        ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                        if (borderManager != null) {
                            borderManager.scheduleBorderUpdate(player);
                            lastBorderUpdate.put(playerId, System.currentTimeMillis());
                        }
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().warning("Error updating borders for joined player " + player.getName() + ": " + e.getMessage());
                    }
                }
            }, 60L); // 3 second delay
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in player join handling", e);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        UUID playerId = player.getUniqueId();
        
        try {
            // Clean up player-specific data to prevent memory leaks
            lastWarned.remove(playerId);
            lastUnlockAttempt.remove(playerId);
            lastBorderUpdate.remove(playerId); // Clean up border tracking
            // FIX: Don't remove from newPlayers here - let it be handled on next join
            
            // Notify TickTask to cleanup player data
            TickTask tickTask = ChunklockPlugin.getInstance().getTickTask();
            if (tickTask != null) {
                tickTask.removePlayer(playerId);
            }
            
            // Notify HologramManager to cleanup
            HologramManager hologramManager = ChunklockPlugin.getInstance().getHologramManager();
            if (hologramManager != null) {
                hologramManager.stopHologramDisplay(player);
            }
            
            // Clean up glass borders
            try {
                ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                if (borderManager != null) {
                    borderManager.removeBordersForPlayer(player);
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Error cleaning up borders for leaving player " + player.getName() + ": " + e.getMessage());
            }
            
            ChunklockPlugin.getInstance().getLogger().fine("Cleaned up data for leaving player: " + player.getName());
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error cleaning up data for leaving player " + player.getName(), e);
        }
    }

    /**
     * Rate limiting for unlock attempts
     */
    public boolean canAttemptUnlock(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastUnlockAttempt.get(player.getUniqueId());
        
        if (last != null && (now - last) < UNLOCK_COOLDOWN_MS) {
            return false;
        }
        
        lastUnlockAttempt.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Updates borders when player moves to a different chunk (rate limited)
     */
    private void updateBordersOnChunkChange(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUpdate = lastBorderUpdate.get(playerId);
        
        // Rate limit border updates to avoid performance issues
        if (lastUpdate != null && (now - lastUpdate) < BORDER_UPDATE_COOLDOWN_MS) {
            return;
        }
        
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            if (borderManager != null && borderManager.isAutoUpdateOnMovementEnabled()) {
                // Update borders asynchronously to avoid blocking player movement
                Bukkit.getScheduler().runTaskAsynchronously(ChunklockPlugin.getInstance(), () -> {
                    // Run the border calculation in async, then update blocks on main thread
                    Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                        if (player.isOnline()) {
                            borderManager.scheduleBorderUpdate(player);
                            lastBorderUpdate.put(playerId, System.currentTimeMillis());
                        }
                    });
                });
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Error updating borders on chunk change for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Calculates the exact center location of a chunk with improved error handling
     */
    private Location getCenterLocationOfChunk(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        
        World world = chunk.getWorld();
        if (world == null) {
            throw new IllegalStateException("Chunk world is null");
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Calculate exact center coordinates
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        
        // Get the highest solid block at center with better error handling
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
                "Error getting highest block at chunk center (" + centerX + "," + centerZ + "), using fallback Y", e);
            centerY = Math.max(world.getMinHeight() + 10, world.getSpawnLocation().getBlockY());
        }
        
        // Return center location with 0.5 offset for perfect centering
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    private void assignStartingChunk(Player player) {
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
                try {
                    setupPlayerSpawn(player, world.getSpawnLocation().getChunk());
                } catch (Exception e2) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error: Could not set up any spawn for " + player.getName(), e2);
                }
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Invalid state during player spawn setup: " + player.getName(), e);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Unexpected error setting up player spawn for " + player.getName(), e);
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
            } catch (IllegalArgumentException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid chunk coordinates in attempt " + attempt, e);
                continue;
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Chunk state error in attempt " + attempt, e);
                continue;
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

    private void setupPlayerSpawn(Player player, Chunk startChunk) throws IllegalArgumentException, IllegalStateException {
        if (startChunk == null) {
            throw new IllegalArgumentException("Starting chunk cannot be null");
        }
        
        if (startChunk.getWorld() == null) {
            throw new IllegalStateException("Starting chunk world is null");
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
                Long last = lastWarned.get(player.getUniqueId());

                if (last == null || (now - last) >= COOLDOWN_MS) {
                    try {
                        ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), toChunk);
                        player.sendMessage("§cThis chunk is locked!");
                        
                        String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
                        player.sendMessage("§7Difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score + " | Biome: " + biomeName);
                        lastWarned.put(player.getUniqueId(), now);
                        
                        // Rate limit unlock GUI opening
                        if (canAttemptUnlock(player)) {
                            unlockGui.open(player, toChunk);
                        }
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error showing chunk info to player", e);
                        player.sendMessage("§cThis chunk is locked!");
                    }
                }

                event.setCancelled(true);
            } else {
                // Player moved to an unlocked chunk, update borders if needed
                updateBordersOnChunkChange(player);
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

            // Optimization: only check if player moved to a different block
            if (from.getBlockX() == to.getBlockX() && 
                from.getBlockZ() == to.getBlockZ()) {
                return;
            }

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

    /**
     * Get current statistics for debugging
     */
    public Map<String, Object> getPlayerListenerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("playersWithWarningCooldown", lastWarned.size());
        stats.put("playersWithUnlockCooldown", lastUnlockAttempt.size());
        stats.put("playersWithBorderUpdateTracking", lastBorderUpdate.size());
        stats.put("newPlayersTracked", newPlayers.size());
        return stats;
    }
}