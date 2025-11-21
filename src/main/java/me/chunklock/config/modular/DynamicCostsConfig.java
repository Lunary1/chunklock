package me.chunklock.config.modular;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration handler for dynamic-costs.yml
 * Manages dynamic chunk unlock cost system settings.
 */
public class DynamicCostsConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public DynamicCostsConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "dynamic-costs.yml");
        if (!file.exists()) {
            plugin.saveResource("dynamic-costs.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", false);
    }

    public double getMultiplierMin() {
        return config.getDouble("multiplier.min", 0.5);
    }

    public double getMultiplierMax() {
        return config.getDouble("multiplier.max", 2.0);
    }

    public String getRoundingMode() {
        return config.getString("rounding.mode", "round").toLowerCase();
    }

    public String getScanningMode() {
        return config.getString("scanning.mode", "async").toLowerCase();
    }

    public String getBlockValueMapping() {
        return config.getString("scanning.block-value-mapping", "use-registry").toLowerCase();
    }

    /**
     * Get base cost template for a specific biome, or default if not found.
     * First checks root-level biome definitions, then falls back to base-cost-templates section.
     * 
     * @param biome The biome to get the template for
     * @return List of cost items (material, amount pairs)
     */
    public List<CostTemplateItem> getBaseCostTemplate(Biome biome) {
        // Try biome-specific definition at root level first (unified config)
        if (biome != null) {
            String biomeKey = biome.getKey().getKey().toUpperCase();
            ConfigurationSection biomeSection = config.getConfigurationSection(biomeKey);
            if (biomeSection != null) {
                // Check if this is a biome definition (has material keys) or a nested structure
                // Skip known config keys
                if (!biomeKey.equals("ENABLED") && !biomeKey.equals("MULTIPLIER") && 
                    !biomeKey.equals("ROUNDING") && !biomeKey.equals("SCANNING") &&
                    !biomeKey.equals("BASE-COST-TEMPLATES")) {
                    List<CostTemplateItem> items = parseCostTemplate(biomeSection);
                    if (!items.isEmpty()) {
                        return items;
                    }
                }
            }
            
            // Try legacy base-cost-templates section
            ConfigurationSection templateSection = config.getConfigurationSection("base-cost-templates." + biomeKey);
            if (templateSection != null) {
                return parseCostTemplate(templateSection);
            }
        }

        // Fall back to default template in base-cost-templates section
        ConfigurationSection defaultSection = config.getConfigurationSection("base-cost-templates.default");
        if (defaultSection != null) {
            return parseCostTemplate(defaultSection);
        }

        // Ultimate fallback: return empty list
        return new ArrayList<>();
    }
    
    /**
     * Get the root configuration section for biome definitions.
     * This allows BiomeUnlockRegistry to read biome costs directly from dynamic-costs.yml.
     * 
     * @return The root configuration section
     */
    public ConfigurationSection getRootSection() {
        return config;
    }
    
    /**
     * Get a biome section from the root configuration.
     * 
     * @param biomeKey The biome key (e.g., "FOREST", "PLAINS")
     * @return The configuration section for the biome, or null if not found
     */
    public ConfigurationSection getBiomeSection(String biomeKey) {
        // Skip known config keys
        if (biomeKey.equalsIgnoreCase("enabled") || biomeKey.equalsIgnoreCase("multiplier") ||
            biomeKey.equalsIgnoreCase("rounding") || biomeKey.equalsIgnoreCase("scanning") ||
            biomeKey.equalsIgnoreCase("base-cost-templates")) {
            return null;
        }
        return config.getConfigurationSection(biomeKey);
    }

    /**
     * Get the default base cost template.
     * 
     * @return List of cost items
     */
    public List<CostTemplateItem> getDefaultCostTemplate() {
        ConfigurationSection defaultSection = config.getConfigurationSection("base-cost-templates.default");
        if (defaultSection != null) {
            return parseCostTemplate(defaultSection);
        }
        return new ArrayList<>();
    }

    /**
     * Parse a cost template from a configuration section.
     * 
     * @param section The configuration section containing the template
     * @return List of cost items
     */
    private List<CostTemplateItem> parseCostTemplate(ConfigurationSection section) {
        List<CostTemplateItem> items = new ArrayList<>();
        
        if (section.isList("items")) {
            // List format: [material: amount, ...]
            List<Map<?, ?>> itemList = section.getMapList("items");
            for (Map<?, ?> itemMap : itemList) {
                String materialStr = (String) itemMap.get("material");
                Object amountObj = itemMap.get("amount");
                
                if (materialStr != null && amountObj != null) {
                    try {
                        Material material = Material.valueOf(materialStr.toUpperCase());
                        int amount = amountObj instanceof Number ? 
                            ((Number) amountObj).intValue() : 
                            Integer.parseInt(amountObj.toString());
                        items.add(new CostTemplateItem(material, amount));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material in cost template: " + materialStr);
                    }
                }
            }
        } else {
            // Map format: material: amount
            for (String key : section.getKeys(false)) {
                if (!key.equals("items")) {
                    try {
                        Material material = Material.valueOf(key.toUpperCase());
                        int amount = section.getInt(key);
                        items.add(new CostTemplateItem(material, amount));
                    } catch (IllegalArgumentException e) {
                        // Skip invalid materials
                    }
                }
            }
        }
        
        return items;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    /**
     * Represents a single item in a cost template.
     */
    public static class CostTemplateItem {
        private final Material material;
        private final int amount;

        public CostTemplateItem(Material material, int amount) {
            this.material = material;
            this.amount = amount;
        }

        public Material getMaterial() {
            return material;
        }

        public int getAmount() {
            return amount;
        }
    }
}

