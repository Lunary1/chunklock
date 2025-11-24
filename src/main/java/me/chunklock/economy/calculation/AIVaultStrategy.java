package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.ai.OpenAIChunkCostAgent;
import me.chunklock.config.modular.EconomyConfig;
import me.chunklock.config.modular.OpenAIConfig;
import me.chunklock.economy.EconomyManager;
import me.chunklock.economy.MaterialVaultConverter;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.services.ChunkCostDatabase;
import me.chunklock.util.item.MaterialUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * AI-powered vault cost calculation strategy using OpenAI.
 */
public class AIVaultStrategy extends BaseVaultStrategy {
    
    private final OpenAIChunkCostAgent openAIAgent;
    private final OpenAIConfig openAIConfig;
    private final MaterialVaultConverter materialConverter;
    
    public AIVaultStrategy(ChunklockPlugin plugin, EconomyConfig economyConfig,
                         BiomeUnlockRegistry biomeRegistry, PlayerProgressTracker progressTracker,
                         double baseCost, double costPerUnlocked,
                         OpenAIChunkCostAgent openAIAgent, OpenAIConfig openAIConfig,
                         MaterialVaultConverter materialConverter) {
        super(plugin, economyConfig, biomeRegistry, progressTracker, baseCost, costPerUnlocked);
        this.openAIAgent = openAIAgent;
        this.openAIConfig = openAIConfig;
        this.materialConverter = materialConverter;
    }
    
    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome, 
                                                      ChunkEvaluator.ChunkValueData evaluation) {
        if (chunk == null) {
            plugin.getLogger().warning("AI vault calculation requires chunk context");
            return fallbackToTraditional(player, chunk, biome, evaluation);
        }
        
        try {
            // Check cache first
            ChunkCostDatabase costDatabase = plugin.getCostDatabase();
            String configHash = costDatabase.generateConfigHash();
            
            try {
                // Use timeout to prevent blocking (2 seconds max for cache lookup)
                EconomyManager.PaymentRequirement cachedResult = 
                    costDatabase.getCachedCost(player, chunk, configHash)
                        .get(2, TimeUnit.SECONDS);
                if (cachedResult != null) {
                    plugin.getLogger().fine("Using cached AI cost for player " + player.getName());
                    return cachedResult;
                }
            } catch (TimeoutException e) {
                plugin.getLogger().fine("Cache lookup timed out, proceeding with calculation");
            } catch (Exception e) {
                plugin.getLogger().fine("Cache miss for AI calculation: " + e.getMessage());
            }
            
            // Calculate using AI
            plugin.getLogger().fine("Calculating AI-optimized vault cost for player " + player.getName());
            
            OpenAIChunkCostAgent.OpenAICostResult aiResult = 
                openAIAgent.calculateOptimizedCost(player, chunk);
            
            plugin.getLogger().fine("OpenAI result: Material=" + MaterialUtil.getMaterialName(aiResult.getMaterial()) + 
                ", Amount=" + aiResult.getAmount() + ", AI Processed=" + aiResult.isAiProcessed());
            
            // Convert material cost to vault cost
            double baseVaultCost = materialConverter.convertToVaultCost(
                aiResult.getMaterial(), aiResult.getAmount());
            
            plugin.getLogger().fine("Converted to vault: $" + baseVaultCost);
            
            // Apply multipliers with reduced power (AI already accounts for difficulty/biome)
            double cost = applyMultipliers(baseVaultCost, player, evaluation, biome, true);
            
            plugin.getLogger().fine("Final AI-optimized vault cost: $" + cost);
            
            // Send AI explanation if enabled
            if (aiResult.isAiProcessed() && !aiResult.getExplanation().isEmpty()) {
                boolean transparencyEnabled = openAIConfig != null ? 
                    openAIConfig.isTransparencyEnabled() : false;
                if (transparencyEnabled) {
                    player.sendMessage(Component.text("💡 ")
                        .color(NamedTextColor.AQUA)
                        .append(Component.text("AI Cost Analysis: " + aiResult.getExplanation())
                            .color(NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            EconomyManager.PaymentRequirement result = new EconomyManager.PaymentRequirement(cost);
            
            // Store in cache
            try {
                costDatabase.storeCost(player, chunk, result, 
                    me.chunklock.util.world.BiomeUtil.getBiomeName(biome), 
                    evaluation.difficulty.name(), evaluation.score, 
                    aiResult.isAiProcessed(), aiResult.getExplanation(), configHash);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to store AI cost in cache: " + e.getMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AI vault calculation failed, falling back to traditional", e);
            return fallbackToTraditional(player, chunk, biome, evaluation);
        }
    }
    
    private EconomyManager.PaymentRequirement fallbackToTraditional(Player player, Chunk chunk, 
                                                                    Biome biome, 
                                                                    ChunkEvaluator.ChunkValueData evaluation) {
        TraditionalVaultStrategy fallback = new TraditionalVaultStrategy(
            plugin, economyConfig, biomeRegistry, progressTracker, baseCost, costPerUnlocked);
        return fallback.calculate(player, chunk, biome, evaluation);
    }
}

