package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.Map;

/**
 * Configuration section for economy-related settings.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class EconomyConfig {
    
    private final FileConfiguration config;
    
    public EconomyConfig(FileConfiguration config) {
        this.config = config;
    }
    
    /**
     * Gets the economy type (materials or vault).
     * 
     * @return Economy type
     */
    public String getEconomyType() {
        return config.getString("economy.type", "materials");
    }
    
    /**
     * Checks if economy is enabled.
     * 
     * @return true if economy is enabled
     */
    public boolean isEconomyEnabled() {
        return !getEconomyType().equals("disabled");
    }
    
    /**
     * Gets the base cost for vault economy.
     * 
     * @return Base cost
     */
    public double getVaultBaseCost() {
        return config.getDouble("economy.vault.base-cost", 100.0);
    }
    
    /**
     * Gets the cost per unlocked chunk for vault economy.
     * 
     * @return Cost per unlocked chunk
     */
    public double getVaultCostPerUnlocked() {
        return config.getDouble("economy.vault.cost-per-unlocked", 25.0);
    }
    
    /**
     * Gets the difficulty multiplier for a specific difficulty.
     * 
     * @param difficulty The difficulty name
     * @return The multiplier
     */
    public double getDifficultyMultiplier(String difficulty) {
        return config.getDouble("economy.vault.difficulty-multipliers." + difficulty, 1.0);
    }
    
    /**
     * Gets the biome multiplier for a specific biome.
     * 
     * @param biome The biome name
     * @return The multiplier
     */
    public double getBiomeMultiplier(String biome) {
        return config.getDouble("economy.vault.biome-multipliers." + biome, 1.0);
    }
    
    /**
     * Gets all biome multipliers.
     * 
     * @return Map of biome names to multipliers
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBiomeMultipliers() {
        return (Map<String, Object>) config.getConfigurationSection("economy.vault.biome-multipliers")
                .getValues(false);
    }
    
    /**
     * Gets all difficulty multipliers.
     * 
     * @return Map of difficulty names to multipliers
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDifficultyMultipliers() {
        return (Map<String, Object>) config.getConfigurationSection("economy.vault.difficulty-multipliers")
                .getValues(false);
    }
    
    /**
     * Checks if progressive pricing is enabled.
     * 
     * @return true if progressive pricing is enabled
     */
    public boolean isProgressivePricingEnabled() {
        return config.getBoolean("economy.progressive-pricing", true);
    }
    
    /**
     * Gets the progressive pricing multiplier.
     * 
     * @return Progressive pricing multiplier
     */
    public double getProgressivePricingMultiplier() {
        return config.getDouble("economy.progressive-multiplier", 1.1);
    }
    
    /**
     * Validates the economy configuration section.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean valid = true;
        
        String economyType = getEconomyType();
        if (!economyType.equals("materials") && !economyType.equals("vault") && !economyType.equals("disabled")) {
            valid = false;
        }
        
        if (getVaultBaseCost() < 0) {
            valid = false;
        }
        
        if (getVaultCostPerUnlocked() < 0) {
            valid = false;
        }
        
        if (getProgressivePricingMultiplier() <= 0) {
            valid = false;
        }
        
        return valid;
    }
}