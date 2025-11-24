package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.EconomyConfig;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.util.world.BiomeUtil;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

/**
 * Base class for vault calculation strategies with shared multiplier logic.
 */
public abstract class BaseVaultStrategy implements CostCalculationStrategy {
    
    protected final ChunklockPlugin plugin;
    protected final EconomyConfig economyConfig;
    protected final BiomeUnlockRegistry biomeRegistry;
    protected final PlayerProgressTracker progressTracker;
    protected final double baseCost;
    protected final double costPerUnlocked;
    
    public BaseVaultStrategy(ChunklockPlugin plugin, EconomyConfig economyConfig,
                            BiomeUnlockRegistry biomeRegistry, PlayerProgressTracker progressTracker,
                            double baseCost, double costPerUnlocked) {
        this.plugin = plugin;
        this.economyConfig = economyConfig;
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
        this.baseCost = baseCost;
        this.costPerUnlocked = costPerUnlocked;
    }
    
    /**
     * Apply multipliers to a base cost value.
     * This is shared between AI and traditional vault calculations.
     * 
     * @param baseCost The base cost before multipliers
     * @param player The player
     * @param evaluation The chunk evaluation
     * @param biome The biome
     * @param aiReducedPower Whether to use reduced power for multipliers (AI mode)
     * @return The final cost after all multipliers
     */
    protected double applyMultipliers(double baseCost, Player player, 
                                     ChunkEvaluator.ChunkValueData evaluation, Biome biome,
                                     boolean aiReducedPower) {
        double cost = baseCost;
        
        // Add progressive cost based on unlocked chunks
        int unlockedCount = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double progressiveMultiplier = aiReducedPower ? 0.5 : 1.0;
        cost += unlockedCount * (costPerUnlocked * progressiveMultiplier);
        
        // Apply difficulty multiplier
        double difficultyMultiplier = economyConfig != null ? 
            economyConfig.getDifficultyMultiplier(evaluation.difficulty.name()) : 1.0;
        double difficultyPower = aiReducedPower ? 0.7 : 1.0;
        cost *= Math.pow(difficultyMultiplier, difficultyPower);
        
        // Apply biome multiplier
        double biomeMultiplier = economyConfig != null ? 
            economyConfig.getBiomeMultiplier(BiomeUtil.getBiomeName(biome)) : 1.0;
        double biomePower = aiReducedPower ? 0.7 : 1.0;
        cost *= Math.pow(biomeMultiplier, biomePower);
        
        // Apply team multiplier if available
        if (biomeRegistry.isTeamIntegrationActive()) {
            double teamMultiplier = biomeRegistry.getTeamCostMultiplier(player);
            cost *= teamMultiplier;
        }
        
        // Ensure minimum cost
        double minCost = aiReducedPower ? 10.0 : 1.0;
        cost = Math.max(cost, minCost);
        
        return cost;
    }
}

