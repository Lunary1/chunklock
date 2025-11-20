package me.chunklock.economy.items.providers;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * MMOItems custom item provider.
 * Requires MMOItems plugin to be installed.
 */
public class MMOItemsProvider implements CustomItemProvider {
    
    private final Plugin plugin;
    private Object mmoItemsAPI;
    private boolean available = false;
    
    public MMOItemsProvider(Plugin plugin) {
        this.plugin = plugin;
        this.available = initializeAPI();
    }
    
    private boolean initializeAPI() {
        try {
            // Check if MMOItems is installed
            Plugin mmoItems = plugin.getServer().getPluginManager().getPlugin("MMOItems");
            if (mmoItems == null || !mmoItems.isEnabled()) {
                plugin.getLogger().fine("MMOItems plugin not found or not enabled - custom items disabled");
                return false;
            }
            
            // Try to get MMOItems API
            Class<?> apiClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            java.lang.reflect.Method getAPI = apiClass.getMethod("plugin");
            mmoItemsAPI = getAPI.invoke(null);
            
            plugin.getLogger().info("✅ MMOItems provider initialized successfully");
            return true;
        } catch (Exception e) {
            plugin.getLogger().fine("Failed to initialize MMOItems provider: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getPluginName() {
        return "MMOItems";
    }
    
    @Override
    public boolean itemExists(String itemId) {
        if (!available) return false;
        
        try {
            // Parse itemId: should be in format "TYPE.item_name" (e.g., "MATERIAL.diamond_ingot")
            String[] parts = itemId.split("\\.", 2);
            if (parts.length != 2) {
                plugin.getLogger().warning("Invalid MMOItems format: " + itemId + " (expected TYPE.item_name)");
                return false;
            }
            
            String type = parts[0].toUpperCase();
            String name = parts[1].toLowerCase();
            
            // Use reflection to check if item exists
            Class<?> itemTypeClass = Class.forName("net.Indyuce.mmoitems.ItemType");
            Object itemType = itemTypeClass.getField(type).get(null);
            
            if (mmoItemsAPI == null) return false;
            
            Class<?> mmoitemsClass = mmoItemsAPI.getClass();
            java.lang.reflect.Method getItemMethod = mmoitemsClass.getMethod("getItem", itemTypeClass, String.class);
            Object item = getItemMethod.invoke(mmoItemsAPI, itemType, name);
            
            return item != null;
        } catch (Exception e) {
            plugin.getLogger().fine("Error checking MMOItems existence for " + itemId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getItemDisplayName(String itemId) {
        try {
            String[] parts = itemId.split("\\.", 2);
            if (parts.length != 2) return itemId;
            
            String type = parts[0].toUpperCase();
            String name = parts[1].toLowerCase();
            
            Class<?> itemTypeClass = Class.forName("net.Indyuce.mmoitems.ItemType");
            Object itemType = itemTypeClass.getField(type).get(null);
            
            if (mmoItemsAPI == null) return itemId;
            
            Class<?> mmoitemsClass = mmoItemsAPI.getClass();
            java.lang.reflect.Method getItemMethod = mmoitemsClass.getMethod("getItem", itemTypeClass, String.class);
            Object item = getItemMethod.invoke(mmoItemsAPI, itemType, name);
            
            if (item != null) {
                java.lang.reflect.Method getDisplayName = item.getClass().getMethod("getDisplayName");
                return (String) getDisplayName.invoke(item);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error getting MMOItems display name for " + itemId + ": " + e.getMessage());
        }
        
        // Fallback: format the name nicely
        String[] parts = itemId.split("\\.", 2);
        if (parts.length == 2) {
            return parts[1].replace("_", " ");
        }
        return itemId;
    }
    
    @Override
    public ItemStack getItemStack(String itemId) {
        try {
            String[] parts = itemId.split("\\.", 2);
            if (parts.length != 2) return null;
            
            String type = parts[0].toUpperCase();
            String name = parts[1].toLowerCase();
            
            Class<?> itemTypeClass = Class.forName("net.Indyuce.mmoitems.ItemType");
            Object itemType = itemTypeClass.getField(type).get(null);
            
            if (mmoItemsAPI == null) return null;
            
            Class<?> mmoitemsClass = mmoItemsAPI.getClass();
            java.lang.reflect.Method getItemMethod = mmoitemsClass.getMethod("getItem", itemTypeClass, String.class);
            Object item = getItemMethod.invoke(mmoItemsAPI, itemType, name);
            
            if (item != null) {
                java.lang.reflect.Method buildItem = item.getClass().getMethod("newBuilder", int.class);
                Object builder = buildItem.invoke(item, 1);
                java.lang.reflect.Method build = builder.getClass().getMethod("build");
                return (ItemStack) build.invoke(builder);
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Error getting MMOItems stack for " + itemId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public boolean playerHasItem(Player player, String itemId, int amount) {
        ItemStack stack = getItemStack(itemId);
        if (stack == null) return false;
        
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSameItem(item, stack)) {
                count += item.getAmount();
                if (count >= amount) return true;
            }
        }
        return count >= amount;
    }
    
    @Override
    public void consumeItem(Player player, String itemId, int amount) {
        ItemStack stack = getItemStack(itemId);
        if (stack == null) return;
        
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isSameItem(item, stack)) {
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
     * Compare two ItemStacks for MMOItems (accounting for NBT data).
     */
    private boolean isSameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        
        // For MMOItems, compare NBT tags
        try {
            return a.getItemMeta() != null && a.getItemMeta().equals(b.getItemMeta());
        } catch (Exception e) {
            return false;
        }
    }
}
