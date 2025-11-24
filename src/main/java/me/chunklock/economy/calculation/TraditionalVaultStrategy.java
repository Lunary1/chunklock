package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.EconomyConfig;
import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.services.ChunkCostDatabase;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * Traditional vault cost calculation strategy (no AI).
 */
public class TraditionalVaultStrategy extends BaseVaultStrategy {
    
    public TraditionalVaultStrategy(ChunklockPlugin plugin, EconomyConfig economyConfig,
                                   BiomeUnlockRegistry biomeRegistry, PlayerProgressTracker progressTracker,
                                   double baseCost, double costPerUnlocked) {
        super(plugin, economyConfig, biomeRegistry, progressTracker, baseCost, costPerUnlocked);
    }
    
    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome, 
                                                      ChunkEvaluator.ChunkValueData evaluation) {
        plugin.getLogger().fine("Using traditional vault cost calculation");
        
        // Start with base cost
        double cost = baseCost;
        
        // Apply all multipliers (aiReducedPower = false for traditional)
        cost = applyMultipliers(cost, player, evaluation, biome, false);
        
        plugin.getLogger().fine("Final traditional vault cost: $" + cost);
        
        EconomyManager.PaymentRequirement result = new EconomyManager.PaymentRequirement(cost);
        
        // Store in cache if available
        if (chunk != null) {
            try {
                ChunkCostDatabase costDatabase = plugin.getCostDatabase();
                String configHash = costDatabase.generateConfigHash();
                
                costDatabase.storeCost(player, chunk, result, 
                    me.chunklock.util.world.BiomeUtil.getBiomeName(biome), 
                    evaluation.difficulty.name(), evaluation.score, 
                    false, "", configHash);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to store traditional cost in cache", e);
            }
        }
        
        return result;
    }
}

