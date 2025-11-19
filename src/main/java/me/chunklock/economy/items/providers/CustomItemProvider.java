package me.chunklock.economy.items.providers;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Interface for custom item provider plugins (MMOItems, Oraxen, etc.).
 * Implementations must handle plugin-specific item lookup and inventory management.
 */
public interface CustomItemProvider {
    
    /**
     * Get the name of the plugin (e.g., "MMOItems", "Oraxen").
     */
    String getPluginName();
    
    /**
     * Check if a custom item exists in this plugin.
     * @param itemId The item identifier (e.g., "MATERIAL.diamond_ingot" for MMOItems or "custom_sword" for Oraxen)
     */
    boolean itemExists(String itemId);
    
    /**
     * Get the display name for a custom item.
     * @param itemId The item identifier
     */
    String getItemDisplayName(String itemId);
    
    /**
     * Get an ItemStack representing this custom item (for GUI display, etc.).
     * @param itemId The item identifier
     */
    ItemStack getItemStack(String itemId);
    
    /**
     * Check if player has at least the specified amount of a custom item.
     * @param player The player
     * @param itemId The item identifier
     * @param amount The amount needed
     */
    boolean playerHasItem(Player player, String itemId, int amount);
    
    /**
     * Remove the specified amount of a custom item from player inventory.
     * Assumes playerHasItem() was true.
     * @param player The player
     * @param itemId The item identifier
     * @param amount The amount to remove
     */
    void consumeItem(Player player, String itemId, int amount);
    
    /**
     * Check if this provider is available (plugin is installed and API loaded).
     */
    boolean isAvailable();
}
