package me.chunklock.listeners;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.services.StartingChunkService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles teleportation between worlds - assigns new starting chunks when players
 * teleport to different worlds where ChunkLock is active
 */
public class TeleportListener implements Listener {
    
    private final WorldManager worldManager;
    private final PlayerDataManager playerDataManager;
    private final StartingChunkService startingChunkService;
    
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
            
            World fromWorld = from.getWorld();
            World toWorld = to.getWorld();
            
            // Check if player changed worlds
            if (fromWorld == null || toWorld == null || fromWorld.equals(toWorld)) {
                return;
            }
            
            // Check if destination world has ChunkLock enabled
            if (!worldManager.isWorldEnabled(toWorld)) {
                return;
            }
            
            // Check if auto-assignment is enabled
            if (!worldManager.isAutoAssignOnWorldChangeEnabled()) {
                return;
            }
            
            handleWorldChange(player, toWorld);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error handling world change teleport for player " + event.getPlayer().getName(), e);
        }
    }
    
    /**
     * Handle player changing to a ChunkLock-enabled world
     */
    private void handleWorldChange(Player player, World newWorld) {
        try {
            UUID playerId = player.getUniqueId();
            
            // Check if player already has a starting chunk in this world
            Location existingSpawn = playerDataManager.getChunkSpawn(playerId);
            if (existingSpawn != null && existingSpawn.getWorld().equals(newWorld)) {
                // Player already has spawn in this world, teleport there
                player.teleport(existingSpawn);
                player.sendMessage("§aReturned to your existing chunk in " + newWorld.getName());
                return;
            }
            
            // Assign new starting chunk in the new world
            startingChunkService.assignStartingChunk(player);
            
            ChunklockPlugin.getInstance().getLogger().info(
                "Assigned new starting chunk to " + player.getName() + " in world " + newWorld.getName());
                
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to handle world change for player " + player.getName(), e);
        }
    }
}