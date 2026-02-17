package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.economy.items.VanillaItemRequirement;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Traditional material cost calculation strategy (no AI).
 */
public class TraditionalMaterialStrategy implements CostCalculationStrategy {
    
    private final BiomeUnlockRegistry biomeRegistry;
    private final PlayerProgressTracker progressTracker;
    
    public TraditionalMaterialStrategy(ChunklockPlugin plugin, BiomeUnlockRegistry biomeRegistry,
                                      PlayerProgressTracker progressTracker) {
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
    }
    
    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome, 
                                                      ChunkEvaluator.ChunkValueData evaluation) {
        // Get all requirements from BiomeUnlockRegistry
        List<ItemRequirement> requirements = biomeRegistry.getRequirementsForBiome(biome);
        if (requirements.isEmpty()) {
            // Fallback to legacy calculation
            BiomeUnlockRegistry.UnlockRequirement legacyRequirement = 
                biomeRegistry.calculateRequirement(player, biome, evaluation.score);
            return new EconomyManager.PaymentRequirement(
                legacyRequirement.material(), legacyRequirement.amount());
        }
        
        // Apply multiplier and keep a single requirement (legacy behavior)
        double multiplier = calculateMultiplier(player, evaluation.score);
        List<ItemRequirement> adjustedRequirements = new ArrayList<>();
        for (ItemRequirement req : requirements) {
            if (req instanceof VanillaItemRequirement vanillaReq) {
                int adjustedAmount = (int) Math.ceil(vanillaReq.getAmount() * multiplier);
                adjustedRequirements.add(new VanillaItemRequirement(vanillaReq.getMaterial(), adjustedAmount));
            } else {
                // For custom items, keep original amount
                adjustedRequirements.add(req);
            }
        }

        ItemRequirement selectedRequirement = selectPrimaryRequirement(adjustedRequirements);
        return new EconomyManager.PaymentRequirement(List.of(selectedRequirement));
    }

    private ItemRequirement selectPrimaryRequirement(List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            if (req instanceof VanillaItemRequirement) {
                return req;
            }
        }
        return requirements.get(0);
    }
    
    private double calculateMultiplier(Player player, int score) {
        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = 1.0 + unlocked / 10.0 + score / 50.0;
        
        if (biomeRegistry.isTeamIntegrationActive()) {
            try {
                multiplier *= biomeRegistry.getTeamCostMultiplier(player);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return multiplier;
    }
}

