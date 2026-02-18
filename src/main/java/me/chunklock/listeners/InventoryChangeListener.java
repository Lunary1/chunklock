package me.chunklock.listeners;

import me.chunklock.ChunklockPlugin;
import me.chunklock.hologram.HologramService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for inventory changes that should trigger hologram progress updates.
 * ISSUE B FIX: Ensures holograms update when players gain/lose required items.
 */
public class InventoryChangeListener implements Listener {
    
    private final ChunklockPlugin plugin;
    private final HologramService hologramService;
    
    // Debounce mechanism to prevent excessive updates
    private final Map<UUID, Long> lastUpdateTimes = new HashMap<>();
    private static final long DEBOUNCE_DELAY_TICKS = 3L; // 3 ticks = ~150ms
    
    public InventoryChangeListener(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.hologramService = plugin.getHologramService();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        
        scheduleHologramUpdate(player, "item pickup");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        scheduleHologramUpdate(event.getPlayer(), "item drop");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Only trigger for player inventory and crafting interactions
        if (event.getInventory().getType() == InventoryType.PLAYER ||
            event.getInventory().getType() == InventoryType.CRAFTING ||
            event.getInventory().getType() == InventoryType.WORKBENCH) {
            scheduleHologramUpdate(player, "inventory click");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        
        // Only trigger for player inventory interactions
        if (event.getInventory().getType() == InventoryType.PLAYER ||
            event.getInventory().getType() == InventoryType.CRAFTING) {
            scheduleHologramUpdate(player, "inventory drag");
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Block breaking might give items
        scheduleHologramUpdate(event.getPlayer(), "block break");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Block placing consumes items
        scheduleHologramUpdate(event.getPlayer(), "block place");
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        // Consuming items (food, potions) changes inventory
        scheduleHologramUpdate(event.getPlayer(), "item consume");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastUpdateTimes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        lastUpdateTimes.remove(event.getPlayer().getUniqueId());
    }
    
    /**
     * Schedule a debounced hologram update for the player
     */
    private void scheduleHologramUpdate(Player player, String reason) {
        UUID playerId = player.getUniqueId();
        long currentTick = plugin.getServer().getCurrentTick();
        
        // Check if we recently scheduled an update for this player
        Long lastUpdate = lastUpdateTimes.get(playerId);
        if (lastUpdate != null && (currentTick - lastUpdate) < DEBOUNCE_DELAY_TICKS) {
            return; // Skip update, too recent
        }
        
        lastUpdateTimes.put(playerId, currentTick);
        
        // Schedule the update with debounce delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                if (player.isOnline() && hologramService != null) {
                    if (plugin.getLogger().isLoggable(Level.FINE)) {
                        plugin.getLogger().fine(
                            "Updating holograms for " + player.getName() + " due to " + reason);
                    }
                    hologramService.updateActiveHologramsForPlayer(player);
                }
            } finally {
                lastUpdateTimes.remove(playerId);
            }
        }, DEBOUNCE_DELAY_TICKS);
    }
}
