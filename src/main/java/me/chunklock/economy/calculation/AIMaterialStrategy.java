package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.ai.OpenAIChunkCostAgent;
import me.chunklock.config.modular.OpenAIConfig;
import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/**
 * AI-powered material cost calculation strategy using OpenAI.
 */
public class AIMaterialStrategy implements CostCalculationStrategy {
    
    private final ChunklockPlugin plugin;
    private final OpenAIChunkCostAgent openAIAgent;
    private final OpenAIConfig openAIConfig;
    private final TraditionalMaterialStrategy fallbackStrategy;
    
    public AIMaterialStrategy(ChunklockPlugin plugin, BiomeUnlockRegistry biomeRegistry,
                             PlayerProgressTracker progressTracker,
                             OpenAIChunkCostAgent openAIAgent, OpenAIConfig openAIConfig) {
        this.plugin = plugin;
        this.openAIAgent = openAIAgent;
        this.openAIConfig = openAIConfig;
        this.fallbackStrategy = new TraditionalMaterialStrategy(plugin, biomeRegistry, progressTracker);
    }
    
    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome, 
                                                      ChunkEvaluator.ChunkValueData evaluation) {
        if (chunk == null) {
            plugin.getLogger().warning("AI material calculation requires chunk context");
            return fallbackStrategy.calculate(player, chunk, biome, evaluation);
        }
        
        try {
            OpenAIChunkCostAgent.OpenAICostResult aiResult = 
                openAIAgent.calculateOptimizedCost(player, chunk);
            
            // Create payment requirement with AI-suggested material
            EconomyManager.PaymentRequirement requirement = 
                new EconomyManager.PaymentRequirement(aiResult.getMaterial(), aiResult.getAmount());
            
            // Send AI explanation if enabled
            if (aiResult.isAiProcessed() && !aiResult.getExplanation().isEmpty()) {
                boolean transparencyEnabled = openAIConfig != null ? 
                    openAIConfig.isTransparencyEnabled() : false;
                if (transparencyEnabled) {
                    player.sendMessage(Component.text("💡 ")
                        .color(NamedTextColor.AQUA)
                        .append(Component.text(aiResult.getExplanation())
                            .color(NamedTextColor.YELLOW))
                        .decoration(TextDecoration.ITALIC, false));
                }
            }
            
            return requirement;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AI material calculation failed, using fallback", e);
            return fallbackStrategy.calculate(player, chunk, biome, evaluation);
        }
    }
}

