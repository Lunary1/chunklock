package me.chunklock.economy.items;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import me.chunklock.economy.items.providers.CustomItemProvider;
import me.chunklock.economy.items.providers.CustomItemRegistry;

import java.util.*;

/**
 * Factory for creating ItemRequirement instances from config.
 */
public class ItemRequirementFactory {
    
    private final Plugin plugin;
    private final CustomItemRegistry customItemRegistry;
    
    public ItemRequirementFactory(Plugin plugin, CustomItemRegistry customItemRegistry) {
        this.plugin = plugin;
        this.customItemRegistry = customItemRegistry;
    }
    
    /**
     * Parse and build a list of ItemRequirements from a biome config section.
     * Config format:
     * {
     *   "vanilla": {
     *     "WHEAT": 8,
     *     "HAY_BLOCK": 2
     *   },
     *   "custom": [
     *     { "plugin": "mmoitems", "type": "MATERIAL", "item": "diamond_ingot", "amount": 3 },
     *     { "plugin": "oraxen", "item": "custom_sword", "amount": 1 }
     *   ]
     * }
     */
    public List<ItemRequirement> parseRequirements(String biomeName, ConfigurationSection biomeSection) {
        List<ItemRequirement> requirements = new ArrayList<>();
        
        if (biomeSection == null) {
            plugin.getLogger().warning("BiomeUnlockRegistry: biome section is null for " + biomeName);
            return requirements;
        }
        
        // Parse vanilla items
        if (biomeSection.isConfigurationSection("vanilla")) {
            ConfigurationSection vanillaSection = biomeSection.getConfigurationSection("vanilla");
            if (vanillaSection != null) {
                for (String itemName : vanillaSection.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(itemName.toUpperCase());
                        int amount = vanillaSection.getInt(itemName, 1);
                        if (amount > 0) {
                            requirements.add(new VanillaItemRequirement(material, amount));
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid vanilla material for " + biomeName + ": " + itemName);
                    }
                }
            }
        }
        
        // Parse custom items (list format)
        if (biomeSection.isList("custom")) {
            List<?> customList = biomeSection.getList("custom");
            if (customList != null) {
                for (Object obj : customList) {
                    if (obj instanceof ConfigurationSection customItem) {
                        parseCustomItem(biomeName, customItem, requirements);
                    } else if (obj instanceof Map<?, ?> customMap) {
                        parseCustomItemMap(biomeName, customMap, requirements);
                    }
                }
            }
        }
        
        if (requirements.isEmpty()) {
            plugin.getLogger().warning("BiomeUnlockRegistry: " + biomeName + " has no item requirements");
        }
        
        return requirements;
    }
    
    /**
     * Parse a custom item from config section.
     */
    private void parseCustomItem(String biomeName, ConfigurationSection section, List<ItemRequirement> requirements) {
        try {
            String providerName = section.getString("plugin", "").toLowerCase();
            if (providerName.isEmpty()) {
                plugin.getLogger().warning("Custom item in " + biomeName + " missing 'plugin' field");
                return;
            }
            
            CustomItemProvider provider = customItemRegistry.getProvider(providerName);
            if (provider == null || !provider.isAvailable()) {
                plugin.getLogger().warning("Custom item provider not available: " + providerName);
                return;
            }
            
            String itemId;
            if (providerName.equals("mmoitems")) {
                // MMOItems format: type.item
                String type = section.getString("type", "").toUpperCase();
                String itemName = section.getString("item", "").toLowerCase();
                if (type.isEmpty() || itemName.isEmpty()) {
                    plugin.getLogger().warning("MMOItems custom item in " + biomeName + " missing type or item");
                    return;
                }
                itemId = type + "." + itemName;
            } else {
                // Other plugins (Oraxen, etc): just use item ID
                itemId = section.getString("item", "");
                if (itemId.isEmpty()) {
                    plugin.getLogger().warning("Custom item in " + biomeName + " missing 'item' field for provider " + providerName);
                    return;
                }
            }
            
            int amount = section.getInt("amount", 1);
            if (amount <= 0) {
                plugin.getLogger().warning("Custom item " + itemId + " in " + biomeName + " has invalid amount: " + amount);
                return;
            }
            
            // Check if item exists - warn but don't fail if it doesn't
            if (!provider.itemExists(itemId)) {
                plugin.getLogger().warning("Custom item does not exist: " + providerName + ":" + itemId + " (in biome " + biomeName + ")");
                // Still add it, so admins can see the warning and fix their config
            }
            
            requirements.add(new CustomItemRequirement(provider, itemId, amount));
            plugin.getLogger().fine("Loaded custom item requirement: " + providerName + ":" + itemId + " (" + amount + "x) for " + biomeName);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing custom item in " + biomeName + ": " + e.getMessage());
        }
    }
    
    /**
     * Parse a custom item from a map (for YAML compatibility).
     */
    private void parseCustomItemMap(String biomeName, Map<?, ?> map, List<ItemRequirement> requirements) {
        try {
            Object providerObj = map.get("plugin");
            String providerName = providerObj instanceof String ? (String) providerObj : "";
            if (providerName.isEmpty()) {
                plugin.getLogger().warning("Custom item in " + biomeName + " missing 'plugin' field");
                return;
            }
            
            CustomItemProvider provider = customItemRegistry.getProvider(providerName.toLowerCase());
            if (provider == null || !provider.isAvailable()) {
                plugin.getLogger().warning("Custom item provider not available: " + providerName);
                return;
            }
            
            String itemId;
            if (providerName.toLowerCase().equals("mmoitems")) {
                Object typeObj = map.get("type");
                Object itemObj = map.get("item");
                String type = typeObj instanceof String ? ((String) typeObj).toUpperCase() : "";
                String itemName = itemObj instanceof String ? ((String) itemObj).toLowerCase() : "";
                if (type.isEmpty() || itemName.isEmpty()) {
                    plugin.getLogger().warning("MMOItems custom item in " + biomeName + " missing type or item");
                    return;
                }
                itemId = type + "." + itemName;
            } else {
                Object itemObj = map.get("item");
                itemId = itemObj instanceof String ? (String) itemObj : "";
                if (itemId.isEmpty()) {
                    plugin.getLogger().warning("Custom item in " + biomeName + " missing 'item' field");
                    return;
                }
            }
            
            int amount = 1;
            Object amountObj = map.get("amount");
            if (amountObj instanceof Integer) {
                amount = (Integer) amountObj;
            } else if (amountObj instanceof Number) {
                amount = ((Number) amountObj).intValue();
            }
            
            if (amount <= 0) {
                plugin.getLogger().warning("Custom item in " + biomeName + " has invalid amount: " + amount);
                return;
            }
            
            if (!provider.itemExists(itemId)) {
                plugin.getLogger().warning("Custom item does not exist: " + providerName + ":" + itemId + " (in biome " + biomeName + ")");
            }
            
            requirements.add(new CustomItemRequirement(provider, itemId, amount));
            
        } catch (Exception e) {
            plugin.getLogger().warning("Error parsing custom item map in " + biomeName + ": " + e.getMessage());
        }
    }
}
