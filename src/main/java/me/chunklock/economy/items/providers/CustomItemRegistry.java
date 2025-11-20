package me.chunklock.economy.items.providers;

import org.bukkit.plugin.Plugin;
import java.util.*;

/**
 * Registry for custom item providers.
 * Auto-detects and manages MMOItems, Oraxen, and other custom item plugins.
 */
public class CustomItemRegistry {
    
    private final Plugin plugin;
    private final Map<String, CustomItemProvider> providers = new HashMap<>();
    
    public CustomItemRegistry(Plugin plugin) {
        this.plugin = plugin;
        detectAndInitializeProviders();
    }
    
    /**
     * Auto-detect and initialize available custom item providers.
     */
    private void detectAndInitializeProviders() {
        plugin.getLogger().info("🔍 Scanning for custom item providers...");
        
        // Try MMOItems
        try {
            MMOItemsProvider mmoItems = new MMOItemsProvider(plugin);
            if (mmoItems.isAvailable()) {
                providers.put("mmoitems", mmoItems);
                plugin.getLogger().info("✅ MMOItems provider loaded");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("MMOItems provider not available: " + e.getMessage());
        }
        
        // Try Oraxen
        try {
            OraxenProvider oraxen = new OraxenProvider(plugin);
            if (oraxen.isAvailable()) {
                providers.put("oraxen", oraxen);
                plugin.getLogger().info("✅ Oraxen provider loaded");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("Oraxen provider not available: " + e.getMessage());
        }
        
        if (providers.isEmpty()) {
            plugin.getLogger().info("ℹ️ No custom item providers detected (this is OK - vanilla items only)");
        } else {
            plugin.getLogger().info("✅ Custom item providers ready: " + providers.keySet());
        }
    }
    
    /**
     * Get a provider by name (e.g., "mmoitems", "oraxen").
     */
    public CustomItemProvider getProvider(String providerName) {
        return providers.get(providerName.toLowerCase());
    }
    
    /**
     * Check if a provider is available.
     */
    public boolean isProviderAvailable(String providerName) {
        CustomItemProvider provider = getProvider(providerName);
        return provider != null && provider.isAvailable();
    }
    
    /**
     * Get all available providers.
     */
    public Collection<CustomItemProvider> getAllProviders() {
        return providers.values();
    }
    
    /**
     * Check if any custom item providers are available.
     */
    public boolean hasAnyProviders() {
        return !providers.isEmpty();
    }
}
