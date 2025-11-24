package me.chunklock.economy;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.EconomyConfig;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts material costs to equivalent vault costs.
 * Supports configurable material values from economy.yml
 */
public class MaterialVaultConverter {
    
    private final ChunklockPlugin plugin;
    private final EconomyConfig economyConfig;
    private final Map<Material, Double> materialValues;
    
    public MaterialVaultConverter(ChunklockPlugin plugin, EconomyConfig economyConfig) {
        this.plugin = plugin;
        this.economyConfig = economyConfig;
        this.materialValues = new HashMap<>();
        loadMaterialValues();
    }
    
    /**
     * Load material values from config or use defaults
     */
    private void loadMaterialValues() {
        // Try to load from config
        if (economyConfig != null && economyConfig.getRawConfig() != null) {
            var config = economyConfig.getRawConfig();
            if (config.isConfigurationSection("material-values")) {
                var section = config.getConfigurationSection("material-values");
                for (String key : section.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(key.toUpperCase());
                        double value = section.getDouble(key);
                        materialValues.put(material, value);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material in material-values: " + key);
                    }
                }
            }
        }
        
        // Add defaults for common materials if not in config
        addDefaultIfMissing(Material.DIAMOND, 50.0);
        addDefaultIfMissing(Material.EMERALD, 45.0);
        addDefaultIfMissing(Material.GOLD_INGOT, 25.0);
        addDefaultIfMissing(Material.IRON_INGOT, 10.0);
        addDefaultIfMissing(Material.COPPER_INGOT, 5.0);
        addDefaultIfMissing(Material.COAL, 2.0);
        addDefaultIfMissing(Material.COBBLESTONE, 0.5);
    }
    
    private void addDefaultIfMissing(Material material, double value) {
        materialValues.putIfAbsent(material, value);
    }
    
    /**
     * Convert material cost to equivalent vault cost
     * 
     * @param material The material
     * @param amount The amount
     * @return The equivalent vault cost
     */
    public double convertToVaultCost(Material material, int amount) {
        double baseValue = materialValues.getOrDefault(material, 5.0); // Default $5 per item
        return baseValue * amount;
    }
    
    /**
     * Reload material values from config
     */
    public void reload() {
        materialValues.clear();
        loadMaterialValues();
    }
}

