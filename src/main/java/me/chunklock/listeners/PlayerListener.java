package me.chunklock.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import me.chunklock.util.ChunkUtils;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.ui.UnlockGui;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.services.StartingChunkService;
import me.chunklock.managers.HologramManager;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import me.chunklock.services.StartingChunkService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final UnlockGui unlockGui;
    private me.chunklock.border.BorderRefreshService borderRefreshService;
    
    // Thread-safe collections for better performance
    private final Map<UUID, Long> lastWarned = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUnlockAttempt = new ConcurrentHashMap<>();
    
    // Track last border update per player to avoid excessive updates
    private final Map<UUID, Long> lastBorderUpdate = new ConcurrentHashMap<>();
    private static final long BORDER_UPDATE_COOLDOWN_MS = 5000L; // 5 seconds between border updates
    
    // FIX: Track if player is truly new (first time joining)
    private final Set<UUID> newPlayers = new HashSet<>();
    
    private static final long COOLDOWN_MS = 2000L;
    private static final long UNLOCK_COOLDOWN_MS = 1000L; // Rate limiting for unlock attempts

    private final StartingChunkService startingChunkService;

    public PlayerListener(ChunkLockManager chunkLockManager, PlayerProgressTracker progressTracker, 
                        PlayerDataManager playerDataManager, UnlockGui unlockGui) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
        this.unlockGui = unlockGui;
        this.startingChunkService = new StartingChunkService(chunkLockManager, playerDataManager);
    }

    public void setBorderRefreshService(me.chunklock.border.BorderRefreshService service) {
        this.borderRefreshService = service;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void handlePlayerJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            
            if (player == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Player join event with null player");
                return;
            }

            // NEW: Check if player is in an enabled world
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            if (!worldManager.isWorldEnabled(player.getWorld())) {
                ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                    " joined in disabled world " + player.getWorld().getName() + " - skipping ChunkLock processing");
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
                startingChunkService.assignStartingChunk(player);
            } else {
                try {
                    Location savedSpawn = playerDataManager.getChunkSpawn(playerId);
                    if (savedSpawn != null && startingChunkService.isValidLocation(savedSpawn)) {
                        // NEW: Check if saved spawn is in an enabled world
                        if (!worldManager.isWorldEnabled(savedSpawn.getWorld())) {
                            ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
                                " has saved spawn in disabled world " + savedSpawn.getWorld().getName() + 
                                " - reassigning to current world");
                            newPlayers.add(playerId);
                            startingChunkService.assignStartingChunk(player);
                            return;
                        }
                        
                        // FIX: Only teleport to center if this is a new player or if they're outside their assigned chunk
                        Chunk savedChunk = savedSpawn.getChunk();
                        Chunk currentChunk = player.getLocation().getChunk();
                        
                        // Check if player is in an unlocked chunk
                        chunkLockManager.initializeChunk(currentChunk, playerId);
                        boolean currentChunkUnlocked = !chunkLockManager.isLocked(currentChunk);
                        
                        if (newPlayers.contains(playerId) || !currentChunkUnlocked) {
                            // Only teleport to center if it's a new player or they're in a locked chunk
                            Location centerSpawn = ChunkUtils.getChunkCenter(savedChunk);
                            player.teleport(centerSpawn);
                            player.setRespawnLocation(centerSpawn, true);
                            player.sendMessage("§aWelcome back! You've been returned to your starting chunk.");
                        } else {
                            // Player is in an unlocked chunk, just set respawn location but don't teleport
                            Location centerSpawn = ChunkUtils.getChunkCenter(savedChunk);
                            player.setRespawnLocation(centerSpawn, true);
                            player.sendMessage("§aWelcome back! Your respawn point has been set to your starting chunk.");
                        }
                        
                        // Remove from new players set after first join handling
                        newPlayers.remove(playerId);
                    } else {
                        ChunklockPlugin.getInstance().getLogger().warning("Invalid saved spawn for player " + player.getName() + ", reassigning");
                        newPlayers.add(playerId);
                        startingChunkService.assignStartingChunk(player);
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error handling returning player " + player.getName(), e);
                    newPlayers.add(playerId);
                    startingChunkService.assignStartingChunk(player);
                }
            }
            
            // Update glass borders after a delay (allow other systems to initialize first)
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                if (player.isOnline() && worldManager.isWorldEnabled(player.getWorld())) {
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
    public void handlePlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        
        UUID playerId = player.getUniqueId();
        
        try {
            // Clean up player-specific data to prevent memory leaks (regardless of world)
            lastWarned.remove(playerId);
            lastUnlockAttempt.remove(playerId);
            lastBorderUpdate.remove(playerId); // Clean up border tracking
            newPlayers.remove(playerId); // Clean up new player tracking
            
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
        // NEW: Check if player is in enabled world before updating borders
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            if (!worldManager.isWorldEnabled(player.getWorld())) {
                return; // Skip border updates in disabled worlds
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for border update: " + e.getMessage());
            return;
        }
        
        if (borderRefreshService != null) {
            borderRefreshService.refreshBordersOnMove(player, lastBorderUpdate);
        }
    }

    private void handleChunkChange(PlayerMoveEvent event, Player player, Chunk toChunk) {
        try {
            // NEW: Check if player is in enabled world before processing chunk change
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            if (!worldManager.isWorldEnabled(player.getWorld())) {
                ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                    " moved in disabled world " + player.getWorld().getName() + " - skipping chunk change processing");
                return; // Skip chunk change processing in disabled worlds
            }
            
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

            // NEW: Early world check - exit immediately if not in enabled world
            try {
                WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
                if (!worldManager.isWorldEnabled(player.getWorld())) {
                    // Player is in disabled world - completely skip all ChunkLock processing
                    return;
                }
            } catch (Exception e) {
                // If we can't check world status, err on the side of caution and skip processing
                ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for player movement: " + e.getMessage());
                return;
            }

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
        
        // NEW: Add world-related statistics
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            stats.put("enabledWorlds", worldManager.getEnabledWorlds());
            stats.put("autoAssignOnWorldChange", worldManager.isAutoAssignOnWorldChangeEnabled());
            
            // Count players in enabled vs disabled worlds
            int playersInEnabledWorlds = 0;
            int playersInDisabledWorlds = 0;
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (worldManager.isWorldEnabled(player.getWorld())) {
                    playersInEnabledWorlds++;
                } else {
                    playersInDisabledWorlds++;
                }
            }
            
            stats.put("playersInEnabledWorlds", playersInEnabledWorlds);
            stats.put("playersInDisabledWorlds", playersInDisabledWorlds);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not get world statistics: " + e.getMessage());
        }
        
        return stats;
    }
}