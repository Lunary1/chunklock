package me.chunklock.economy.items;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chunklock.economy.items.providers.CustomItemProvider;

/**
 * Custom item requirement (from third-party plugins like MMOItems, Oraxen, etc.).
 */
public class CustomItemRequirement implements ItemRequirement {
    
    private final CustomItemProvider provider;
    private final String itemId;
    private final int amount;
    private final boolean isValid;
    
    public CustomItemRequirement(CustomItemProvider provider, String itemId, int amount) {
        this.provider = provider;
        this.itemId = itemId;
        this.amount = amount;
        this.isValid = provider.itemExists(itemId);
    }
    
    @Override
    public String getDisplayName() {
        if (!isValid) {
            return "[Missing: " + itemId + "]";
        }
        return provider.getItemDisplayName(itemId);
    }
    
    @Override
    public int getAmount() {
        return amount;
    }
    
    @Override
    public boolean hasInInventory(Player player) {
        if (!isValid) return false;
        return provider.playerHasItem(player, itemId, amount);
    }
    
    @Override
    public void consumeFromInventory(Player player) {
        if (!isValid) return;
        provider.consumeItem(player, itemId, amount);
    }
    
    @Override
    public ItemStack getRepresentativeStack() {
        if (!isValid) return null;
        return provider.getItemStack(itemId);
    }
    
    @Override
    public boolean isValid() {
        return isValid;
    }
    
    @Override
    public String getType() {
        return provider.getPluginName().toLowerCase();
    }
    
    public String getItemId() {
        return itemId;
    }
    
    public CustomItemProvider getProvider() {
        return provider;
    }
}
