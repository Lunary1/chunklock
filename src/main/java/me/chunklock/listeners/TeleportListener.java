package me.chunklock.listeners;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.HologramManager;
import me.chunklock.services.StartingChunkService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;
import java.util.logging.Level;
import java.util.Set;
import java.util.HashSet;

/**
 * Handles teleportation between worlds - assigns new starting chunks when players
 * teleport to different worlds where ChunkLock is active
 * 
 * FIXED: Now properly restarts holograms when teleporting back to enabled worlds
 */
public class TeleportListener implements Listener {
    
    private final WorldManager worldManager;
    private final PlayerDataManager playerDataManager;
    private final StartingChunkService startingChunkService;
    
    // Track players to prevent infinite recursion
    private final Set<UUID> processingPlayers = new HashSet<>();
    
    public TeleportListener(WorldManager worldManager, 
                           PlayerDataManager playerDataManager,
                           StartingChunkService startingChunkService) {
        this.worldManager = worldManager;
        this.playerDataManager = playerDataManager;
        this.startingChunkService = startingChunkService;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }
        
        try {
            Player player = event.getPlayer();
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (player == null || from == null || to == null) {
                return;
            }
            
            UUID playerId = player.getUniqueId();
            
            // Prevent infinite recursion - skip if we're already processing this player
            if (processingPlayers.contains(playerId)) {
                return;
            }
            
            World fromWorld = from.getWorld();
            World toWorld = to.getWorld();
            
            // Check if player changed worlds
            if (fromWorld == null || toWorld == null || fromWorld.equals(toWorld)) {
                return;
            }
            
            // FIXED: Handle both directions of world changes
            boolean fromEnabled = worldManager.isWorldEnabled(fromWorld);
            boolean toEnabled = worldManager.isWorldEnabled(toWorld);
            
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " teleporting from " + fromWorld.getName() + " (enabled: " + fromEnabled + ") to " + 
                toWorld.getName() + " (enabled: " + toEnabled + ") via " + event.getCause());
            
            // Case 1: Player teleporting TO an enabled world (regardless of where they came from)
            if (toEnabled) {
                handleTeleportToEnabledWorld(player, toWorld);
            }
            
            // Case 2: Only override teleport destination for automatic world changes (not commands/plugins)
            // FIXED: Don't override intentional teleports like /mv tp
            if (fromEnabled && toEnabled && worldManager.isAutoAssignOnWorldChangeEnabled()) {
                // Only handle automatic world changes, not intentional teleports
                if (isAutomaticWorldChange(event)) {
                    handleWorldChange(player, toWorld);
                }
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error handling world change teleport for player " + event.getPlayer().getName(), e);
        }
    }
    
    /**
     * FIXED: Determines if a teleport is an automatic world change vs intentional teleport
     * Automatic: portals, commands that don't specify location, falling into void
     * Intentional: /tp, /mv tp, plugin teleports with specific coordinates
     */
    private boolean isAutomaticWorldChange(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        
        // These are considered automatic world changes that should trigger chunk assignment
        switch (cause) {
            case NETHER_PORTAL:
            case END_PORTAL:
            case END_GATEWAY:
            case CHORUS_FRUIT:
                return true;
                
            case PLUGIN:
            case COMMAND:
                // For plugins/commands, check if destination seems intentional
                // If the teleport destination is close to spawn, it might be automatic
                // If it's to a specific coordinate, it's likely intentional
                Location to = event.getTo();
                World toWorld = to.getWorld();
                if (toWorld != null) {
                    Location worldSpawn = toWorld.getSpawnLocation();
                    double distanceFromSpawn = to.distance(worldSpawn);
                    
                    // If teleporting near world spawn (within 100 blocks), consider it automatic
                    // If teleporting to specific coordinates far from spawn, consider it intentional
                    return distanceFromSpawn < 100;
                }
                return false;
                
            case UNKNOWN:
                // Unknown cause - be conservative and don't override
                return false;
                
            default:
                // Most other causes (SPECTATE, ENDER_PEARL, etc.) are intentional
                return false;
        }
    }
    
    private void handleTeleportToEnabledWorld(Player player, World enabledWorld) {
        try {
            ChunklockPlugin.getInstance().getLogger().fine("Handling teleport to enabled world for " + player.getName());
            
            // Restart holograms with a short delay to ensure the teleport is complete
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                try {
                    if (player.isOnline() && player.getWorld().equals(enabledWorld)) {
                        HologramManager hologramManager = ChunklockPlugin.getInstance().getHologramManager();
                        if (hologramManager != null) {
                            // Stop any existing hologram task (cleanup)
                            hologramManager.stopHologramDisplay(player);
                            
                            // Start fresh hologram display
                            hologramManager.startHologramDisplay(player);
                            
                            ChunklockPlugin.getInstance().getLogger().fine(
                                "Restarted holograms for " + player.getName() + " after teleporting to enabled world " + enabledWorld.getName());
                        }
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                        "Error restarting holograms after teleport for " + player.getName(), e);
                }
            }, 20L); // 1 second delay to ensure teleport is complete
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error in handleTeleportToEnabledWorld for " + player.getName(), e);
        }
    }
    
    /**
     * Handle player changing to a ChunkLock-enabled world (original functionality)
     */
    private void handleWorldChange(Player player, World newWorld) {
        try {
            UUID playerId = player.getUniqueId();
            
            // Add to processing set to prevent recursion
            processingPlayers.add(playerId);
            
            // Check if player already has a starting chunk in this world
            Location existingSpawn = playerDataManager.getChunkSpawn(playerId);
            if (existingSpawn != null && existingSpawn.getWorld().equals(newWorld)) {
                // Player already has spawn in this world, teleport there SAFELY
                // Use scheduler to prevent recursion in the teleport event
                Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                    try {
                        if (player.isOnline()) {
                            Location centerSpawn = me.chunklock.util.ChunkUtils.getChunkCenter(existingSpawn.getChunk());
                            player.teleport(centerSpawn);
                            player.setRespawnLocation(centerSpawn, true);
                            player.sendMessage("§aReturned to your starting chunk in " + newWorld.getName());
                        }
                    } finally {
                        processingPlayers.remove(playerId);
                    }
                }, 1L);
                return;
            }
            
            // Player doesn't have a starting chunk in this world, assign one
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                try {
                    if (player.isOnline()) {
                        startingChunkService.assignStartingChunk(player);
                        player.sendMessage("§aWelcome to " + newWorld.getName() + "! You've been assigned a starting chunk.");
                    }
                } finally {
                    processingPlayers.remove(playerId);
                }
            }, 1L);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error handling world change for " + player.getName(), e);
            processingPlayers.remove(player.getUniqueId());
        }
    }
}