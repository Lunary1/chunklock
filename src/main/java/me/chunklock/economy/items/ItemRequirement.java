package me.chunklock.economy.items;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Abstraction for item requirements - supports both vanilla and custom items.
 * Implementations should handle checking inventory and consuming items.
 */
public interface ItemRequirement {
    
    /**
     * Get display name for this requirement (used in UI/messages).
     * Example: "Wheat", "Diamond Ingot", "Custom Sword"
     */
    String getDisplayName();
    
    /**
     * Get the amount required.
     */
    int getAmount();
    
    /**
     * Check if player has at least this many of the required item.
     */
    boolean hasInInventory(Player player);
    
    /**
     * Remove this many items from player inventory.
     * Assumes hasInInventory() was true.
     */
    void consumeFromInventory(Player player);
    
    /**
     * Get a representative ItemStack (for GUI display, null if custom item not found).
     */
    ItemStack getRepresentativeStack();
    
    /**
     * Check if this requirement is valid (e.g., item exists if custom).
     */
    boolean isValid();
    
    /**
     * Get type: "vanilla" or name of custom plugin (e.g., "mmoitems", "oraxen").
     */
    String getType();
}
