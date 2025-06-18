package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player interactions with chunk borders
 */
public class ChunkBorderInteractListener implements Listener {
    
    private final ChunkBorderManager borderManager;
    private final UnlockGui unlockGui;
    private final ChunkLockManager chunkLockManager;
    
    // Cooldown to prevent spam
    private final Map<UUID, Long> interactionCooldown = new HashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 second cooldown
    
    public ChunkBorderInteractListener(ChunkBorderManager borderManager, UnlockGui unlockGui, 
                                     ChunkLockManager chunkLockManager) {
        this.borderManager = borderManager;
        this.unlockGui = unlockGui;
        this.chunkLockManager = chunkLockManager;
    }
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        
        Player player = event.getPlayer();
        
        // Check if this is a border block
        if (!borderManager.isBorderBlock(clickedBlock)) {
            return;
        }
        
        // Check cooldown
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastInteraction = interactionCooldown.get(playerId);
        
        if (lastInteraction != null && (now - lastInteraction) < COOLDOWN_MS) {
            return;
        }
        
        interactionCooldown.put(playerId, now);
        
        // Cancel the normal interaction
        event.setCancelled(true);
        
        // Find which chunk this border is protecting
        Chunk targetChunk = borderManager.getBorderChunk(clickedBlock, player);
        
        if (targetChunk == null) {
            player.sendMessage("§cError: Could not determine which chunk this border protects.");
            return;
        }
        
        // Check if chunk is actually locked (safety check)
        if (!chunkLockManager.isLocked(targetChunk)) {
            player.sendMessage("§cThis chunk is already unlocked!");
            // Update borders since state seems inconsistent
            borderManager.updateBordersForPlayer(player);
            return;
        }
        
        // Play interaction sound
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_HIT, 0.5f, 1.0f);
        
        // Open the unlock GUI
        unlockGui.open(player, targetChunk);
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Update borders after a short delay to ensure everything is loaded
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    borderManager.updateBordersForPlayer(player);
                }
            }
        }.runTaskLater(ChunklockPlugin.getInstance(), 20L); // 1 second delay
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Clean up cooldown
        interactionCooldown.remove(playerId);
        
        // Remove player's borders
        borderManager.removeAllBordersForPlayer(playerId);
    }
    
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        
        // Update borders when player changes worlds
        borderManager.updateBordersForPlayer(player);
    }
    
    /**
     * Clean up on disable
     */
    public void cleanup() {
        interactionCooldown.clear();
    }
}