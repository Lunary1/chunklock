package me.chunklock.economy.calculation;

import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.ChunkEvaluator;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

/**
 * Strategy interface for calculating chunk unlock costs.
 * Different implementations handle different economy types and AI integration.
 */
public interface CostCalculationStrategy {
    
    /**
     * Calculate the payment requirement for unlocking a chunk.
     * 
     * @param player The player attempting to unlock
     * @param chunk The chunk to unlock
     * @param biome The biome of the chunk
     * @param evaluation The chunk evaluation data
     * @return The payment requirement
     */
    EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome, 
                                               ChunkEvaluator.ChunkValueData evaluation);
}

