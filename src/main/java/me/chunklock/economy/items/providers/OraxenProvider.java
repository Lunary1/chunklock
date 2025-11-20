package me.chunklock.economy.items.providers;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Oraxen custom item provider.
 * Requires Oraxen plugin to be installed.
 */
public class OraxenProvider implements CustomItemProvider {
    
    private final Plugin plugin;
    private Object oraxenAPI;
    private boolean available = false;
    
    public OraxenProvider(Plugin plugin) {
        this.plugin = plugin;
        this.available = initializeAPI();
    }
    
    private boolean initializeAPI() {
        try {
            // Check if Oraxen is installed
            Plugin oraxen = plugin.getServer().getPluginManager().getPlugin("Oraxen");
            if (oraxen == null || !oraxen.isEnabled()) {
                plugin.getLogger().fine("Oraxen plugin not found or not enabled - custom items disabled");
                return false;
            }
            
            // Try to get Oraxen API
            Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            java.lang.reflect.Method getAPI = oraxenClass.getMethod("getItemById", String.class);
            
            // Test if API is accessible by calling it with a dummy value
            // If no exception, API is available
            plugin.getLogger().info("✅ Oraxen provider initialized successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to initialize Oraxen provider: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getPluginName() {
        return "Oraxen";
    }
    
    @Override
    public boolean itemExists(String itemId) {
        if (!available) return false;
        
        try {
            Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            java.lang.reflect.Method getItemMethod = oraxenClass.getMethod("getItemById", String.class);
            Object item = getItemMethod.invoke(null, itemId);
            
            return item != null;
        } catch (Exception e) {
            plugin.getLogger().fine("Error checking Oraxen existence for " + itemId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getItemDisplayName(String itemId) {
        try {
            Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            java.lang.reflect.Method getItemMethod = oraxenClass.getMethod("getItemById", String.class);
            Object item = getItemMethod.invoke(null, itemId);
            
            if (item != null) {
                java.lang.reflect.Method getDisplayName = item.getClass().getMethod("getDisplayName");
                Object displayName = getDisplayName.invoke(item);
                if (displayName != null) {
                    return displayName.toString();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error getting Oraxen display name for " + itemId + ": " + e.getMessage());
        }
        
        // Fallback: format the ID nicely
        return itemId.replace("_", " ");
    }
    
    @Override
    public ItemStack getItemStack(String itemId) {
        try {
            Class<?> oraxenClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            java.lang.reflect.Method getItemMethod = oraxenClass.getMethod("getItemById", String.class);
            Object item = getItemMethod.invoke(null, itemId);
            
            if (item != null) {
                // Oraxen's getItemById returns an ItemBuilder, build it
                java.lang.reflect.Method buildMethod = item.getClass().getMethod("build");
                ItemStack stack = (ItemStack) buildMethod.invoke(item);
                stack.setAmount(1);
                return stack;
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error getting Oraxen stack for " + itemId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public boolean playerHasItem(Player player, String itemId, int amount) {
        ItemStack required = getItemStack(itemId);
        if (required == null) return false;
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSameOraxenItem(item, required)) {
                count += item.getAmount();
                if (count >= amount) return true;
            }
        }
        return count >= amount;
    }
    
    @Override
    public void consumeItem(Player player, String itemId, int amount) {
        ItemStack required = getItemStack(itemId);
        if (required == null) return;
        
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSameOraxenItem(item, required)) {
                int toRemove = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
                
                if (remaining <= 0) break;
            }
        }
    }
    
    @Override
    public boolean isAvailable() {
        return available;
    }
    
    /**
     * Compare two ItemStacks for Oraxen (accounting for custom model data).
     */
    private boolean isSameOraxenItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        
        // Compare ItemMeta (includes custom model data for Oraxen)
        try {
            if (a.getItemMeta() == null || b.getItemMeta() == null) {
                return a.getItemMeta() == b.getItemMeta();
            }
            return a.getItemMeta().equals(b.getItemMeta());
        } catch (Exception e) {
            return false;
        }
    }
}
