package me.chunklock.listeners;

import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.logging.Level;

/**
 * Handles border-specific events with world-aware filtering.
 * Only processes border interactions in worlds where ChunkLock is enabled.
 */
public class BorderListener implements Listener {
    private final ChunkBorderManager borderManager;

    public BorderListener(ChunkBorderManager borderManager) {
        this.borderManager = borderManager;
    }

    /**
     * Helper method to check if world is enabled for ChunkLock borders
     */
    private boolean isWorldEnabled(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            return worldManager.isWorldEnabled(player.getWorld());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not check world status for border interaction: " + e.getMessage());
            return false; // Err on the side of caution - no border processing if can't verify
        }
    }

    /**
     * Handles right-clicking on border blocks to open unlock GUI
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // NEW: Early world check - skip border processing in disabled worlds
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " interacted in disabled world " + player.getWorld().getName() + " - skipping border processing");
            return; // Skip border interactions in disabled worlds
        }
        
        // Check if border system is enabled
        if (!borderManager.isEnabled()) {
            return; // Border system disabled globally
        }
        
        try {
            // Delegate to border manager for actual processing
            borderManager.handlePlayerInteract(event);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error handling border interaction for player " + player.getName(), e);
        }
    }

    /**
     * Handles player join - updates borders in enabled worlds only
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // NEW: Check if player joined in an enabled world
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine("Player " + player.getName() + 
                " joined in disabled world " + player.getWorld().getName() + " - skipping border initialization");
            return; // Skip border setup in disabled worlds
        }
        
        // Check if border system is enabled
        if (!borderManager.isEnabled()) {
            return; // Border system disabled globally
        }
        
        try {
            // Delegate to border manager for actual processing
            borderManager.handlePlayerJoin(event);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error handling border setup for joining player " + player.getName(), e);
        }
    }

    /**
     * Handles player quit - cleanup borders regardless of world
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Always clean up borders on quit regardless of world
        // This prevents memory leaks and stale border data
        try {
            borderManager.handlePlayerQuit(event);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error cleaning up borders for leaving player " + event.getPlayer().getName(), e);
        }
    }
}