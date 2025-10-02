package me.chunklock.hologram.listeners;

import me.chunklock.ChunklockPlugin;
import me.chunklock.hologram.HologramServiceEnhanced;
import me.chunklock.managers.WorldManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listens for world changes and player movement to manage hologram WorldReady states
 * and trigger hologram refreshes at appropriate times.
 */
public class HologramWorldListener implements Listener {
    
    private final HologramServiceEnhanced hologramService;
    private final WorldManager worldManager;
    
    public HologramWorldListener(HologramServiceEnhanced hologramService, WorldManager worldManager) {
        this.hologramService = hologramService;
        this.worldManager = worldManager;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if player joined in an enabled world
        if (worldManager.isWorldEnabled(player.getWorld())) {
            // Register the player for their current world
            hologramService.registerPlayerWorldTransition(player, player.getWorld().getName());
            
            ChunklockPlugin.getInstance().getLogger().fine("Hologram: Player " + player.getName() + 
                " joined in enabled world " + player.getWorld().getName());
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Clear any existing hologram state
        hologramService.despawnPlayerHolograms(player);
        
        // Check if new world is enabled
        if (worldManager.isWorldEnabled(player.getWorld())) {
            // Register for world transition tracking
            hologramService.registerPlayerWorldTransition(player, player.getWorld().getName());
            
            ChunklockPlugin.getInstance().getLogger().fine("Hologram: Player " + player.getName() + 
                " changed to enabled world " + player.getWorld().getName());
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        
        // Check if teleporting between worlds
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            // Clear existing hologram state
            hologramService.despawnPlayerHolograms(player);
            
            // Check if destination world is enabled
            if (worldManager.isWorldEnabled(event.getTo().getWorld())) {
                // Register for world transition tracking
                hologramService.registerPlayerWorldTransition(player, event.getTo().getWorld().getName());
                
                ChunklockPlugin.getInstance().getLogger().fine("Hologram: Player " + player.getName() + 
                    " teleported to enabled world " + event.getTo().getWorld().getName());
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process significant movement (changed block)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && 
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Only process in enabled worlds
        if (!worldManager.isWorldEnabled(player.getWorld())) {
            return;
        }
        
        // Update active holograms based on new position
        hologramService.updateActiveHologramsForPlayer(player);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up all hologram state for this player
        hologramService.despawnPlayerHolograms(player);
        
        ChunklockPlugin.getInstance().getLogger().fine("Hologram: Cleaned up state for " + player.getName());
    }
}
