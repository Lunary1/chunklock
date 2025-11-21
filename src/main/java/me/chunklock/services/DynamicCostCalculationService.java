package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.DynamicCostsConfig;
import me.chunklock.economy.EconomyManager;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.util.chunk.ChunkNeighborUtils;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Unified cost calculation service for dynamic chunk unlock costs.
 * Provides consistent cost calculation across GUI, Holograms, and backend systems.
 * 
 * This service calculates costs using a multiplier system based on neighbor chunk values.
 */
public class DynamicCostCalculationService {

    private final ChunklockPlugin plugin;
    private final ChunkBaseValueService baseValueService;
    private final ChunkEvaluator chunkEvaluator;
    private final DynamicCostsConfig config;

    public DynamicCostCalculationService(ChunklockPlugin plugin,
                                        ChunkBaseValueService baseValueService,
                                        ChunkLockManager chunkLockManager,
                                        ChunkEvaluator chunkEvaluator,
                                        DynamicCostsConfig config) {
        this.plugin = plugin;
        this.baseValueService = baseValueService;
        this.chunkEvaluator = chunkEvaluator;
        this.config = config;
    }

    /**
     * Get the base value for a chunk.
     * 
     * @param chunk The chunk to get the base value for
     * @return CompletableFuture that completes with the base value
     */
    public CompletableFuture<Double> getBaseValue(Chunk chunk) {
        return baseValueService.getOrComputeBaseValue(chunk);
    }

    /**
     * Get the multiplier for a chunk based on its neighbors.
     * 
     * @param chunk The chunk to calculate the multiplier for
     * @return CompletableFuture that completes with the multiplier
     */
    public CompletableFuture<Double> getMultiplier(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            return CompletableFuture.completedFuture(1.0);
        }

        // Get base value for this chunk
        CompletableFuture<Double> chunkValueFuture = getBaseValue(chunk);

        // Get neighbors
        List<Chunk> neighbors = ChunkNeighborUtils.getCardinalNeighbors(chunk);
        
        if (neighbors.isEmpty()) {
            // No neighbors available, return default multiplier
            return chunkValueFuture.thenApply(value -> 1.0);
        }

        // Get base values for all neighbors
        @SuppressWarnings("unchecked")
        CompletableFuture<Double>[] neighborFutures = neighbors.stream()
            .map(this::getBaseValue)
            .toArray(CompletableFuture[]::new);

        // Wait for all values to be computed
        return CompletableFuture.allOf(chunkValueFuture, CompletableFuture.allOf(neighborFutures))
            .thenApply(v -> {
                try {
                    double chunkValue = chunkValueFuture.join();
                    
                    // Calculate average neighbor value
                    double neighborSum = 0.0;
                    int validNeighbors = 0;
                    
                    for (CompletableFuture<Double> neighborFuture : neighborFutures) {
                        double neighborValue = neighborFuture.join();
                        if (neighborValue > 0.0) {
                            neighborSum += neighborValue;
                            validNeighbors++;
                        }
                    }
                    
                    if (validNeighbors == 0) {
                        // No valid neighbors, return default multiplier
                        return 1.0;
                    }
                    
                    double avgNeighborValue = neighborSum / validNeighbors;
                    
                    // Avoid division by zero
                    if (avgNeighborValue == 0.0) {
                        return 1.0;
                    }
                    
                    // Calculate multiplier: chunkValue / avgNeighborValue
                    double multiplier = chunkValue / avgNeighborValue;
                    
                    // Apply clamp
                    double minMultiplier = config.getMultiplierMin();
                    double maxMultiplier = config.getMultiplierMax();
                    multiplier = Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));
                    
                    return multiplier;
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error calculating multiplier for chunk " + 
                        chunk.getX() + "," + chunk.getZ(), e);
                    return 1.0;
                }
            });
    }

    /**
     * Get the final cost for unlocking a chunk.
     * This is the unified method used by GUI, Holograms, and backend.
     * 
     * @param chunk The chunk to calculate the cost for
     * @param player The player attempting to unlock
     * @return CompletableFuture that completes with the PaymentRequirement
     */
    public CompletableFuture<EconomyManager.PaymentRequirement> getFinalCost(Chunk chunk, Player player) {
        if (chunk == null || player == null) {
            return CompletableFuture.completedFuture(
                new EconomyManager.PaymentRequirement(Material.DIRT, 1));
        }

        // Get chunk evaluation for biome
        ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
        Biome biome = evaluation.biome;

        // Get base cost template for this biome
        List<DynamicCostsConfig.CostTemplateItem> template = config.getBaseCostTemplate(biome);
        
        if (template.isEmpty()) {
            // Fallback to default template
            template = config.getDefaultCostTemplate();
        }

        if (template.isEmpty()) {
            // Ultimate fallback
            return CompletableFuture.completedFuture(
                new EconomyManager.PaymentRequirement(Material.DIRT, 1));
        }

        // Make template final for use in lambda
        final List<DynamicCostsConfig.CostTemplateItem> finalTemplate = template;

        // Get multiplier
        CompletableFuture<Double> multiplierFuture = getMultiplier(chunk);

        return multiplierFuture.thenApply(multiplier -> {
            // Apply multiplier to each item in the template
            // For now, we'll use the first item in the template and apply the multiplier
            // In the future, this could be extended to support multiple items
            DynamicCostsConfig.CostTemplateItem firstItem = finalTemplate.get(0);
            int baseAmount = firstItem.getAmount();
            
            // Calculate final amount with multiplier
            double finalAmountDouble = baseAmount * multiplier;
            
            // Apply rounding
            int finalAmount = applyRounding(finalAmountDouble);
            
            // Ensure minimum of 1
            finalAmount = Math.max(1, finalAmount);
            
            return new EconomyManager.PaymentRequirement(firstItem.getMaterial(), finalAmount);
        });
    }

    /**
     * Apply rounding to a value based on configuration.
     * 
     * @param value The value to round
     * @return The rounded value as an integer
     */
    private int applyRounding(double value) {
        String mode = config.getRoundingMode();
        
        switch (mode) {
            case "floor":
                return (int) Math.floor(value);
            case "ceil":
                return (int) Math.ceil(value);
            case "round":
            default:
                return (int) Math.round(value);
        }
    }

    /**
     * Non-blocking synchronous version of getFinalCost.
     * This version will NOT block - it only works if base values are already computed.
     * If base values need to be computed, it returns a fallback and schedules async computation.
     * 
     * @param chunk The chunk to calculate the cost for
     * @param player The player attempting to unlock
     * @return The PaymentRequirement (fallback if computation needed)
     */
    public EconomyManager.PaymentRequirement getFinalCostSync(Chunk chunk, Player player) {
        if (chunk == null || player == null) {
            return new EconomyManager.PaymentRequirement(Material.DIRT, 1);
        }

        try {
            // Get ChunkLockManager from baseValueService
            ChunkLockManager chunkLockManager = baseValueService.getChunkLockManager();
            
            // Check if base value is already computed (non-blocking check)
            double chunkBaseValue = chunkLockManager.getBaseValue(chunk);
            
            // If base value is 0, it might not be computed yet - use fallback and schedule async computation
            if (chunkBaseValue == 0.0) {
                // Schedule async computation for future use (don't wait for it)
                getFinalCost(chunk, player).thenAccept(result -> {
                    // Cache is updated automatically by ChunkBaseValueService
                });
                
                // Return fallback using base template without multiplier
                ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
                Biome biome = evaluation.biome;
                List<DynamicCostsConfig.CostTemplateItem> template = config.getBaseCostTemplate(biome);
                if (template.isEmpty()) {
                    template = config.getDefaultCostTemplate();
                }
                if (!template.isEmpty()) {
                    DynamicCostsConfig.CostTemplateItem firstItem = template.get(0);
                    return new EconomyManager.PaymentRequirement(firstItem.getMaterial(), firstItem.getAmount());
                }
                return new EconomyManager.PaymentRequirement(Material.DIRT, 1);
            }

            // Base value exists - check if we can compute multiplier synchronously
            ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(player.getUniqueId(), chunk);
            Biome biome = evaluation.biome;
            List<DynamicCostsConfig.CostTemplateItem> template = config.getBaseCostTemplate(biome);
            if (template.isEmpty()) {
                template = config.getDefaultCostTemplate();
            }
            if (template.isEmpty()) {
                return new EconomyManager.PaymentRequirement(Material.DIRT, 1);
            }

            // Check if neighbors have base values (quick non-blocking check)
            List<Chunk> neighbors = ChunkNeighborUtils.getCardinalNeighbors(chunk);
            boolean allNeighborsReady = true;
            for (Chunk neighbor : neighbors) {
                if (chunkLockManager.getBaseValue(neighbor) == 0.0) {
                    allNeighborsReady = false;
                    break;
                }
            }

            if (allNeighborsReady && !neighbors.isEmpty()) {
                // All neighbors ready - compute multiplier synchronously
                double neighborSum = 0.0;
                int validNeighbors = 0;
                for (Chunk neighbor : neighbors) {
                    double neighborValue = chunkLockManager.getBaseValue(neighbor);
                    if (neighborValue > 0.0) {
                        neighborSum += neighborValue;
                        validNeighbors++;
                    }
                }
                
                if (validNeighbors > 0) {
                    double avgNeighborValue = neighborSum / validNeighbors;
                    if (avgNeighborValue > 0.0) {
                        double multiplier = chunkBaseValue / avgNeighborValue;
                        double minMultiplier = config.getMultiplierMin();
                        double maxMultiplier = config.getMultiplierMax();
                        multiplier = Math.max(minMultiplier, Math.min(maxMultiplier, multiplier));
                        
                        DynamicCostsConfig.CostTemplateItem firstItem = template.get(0);
                        int baseAmount = firstItem.getAmount();
                        double finalAmountDouble = baseAmount * multiplier;
                        int finalAmount = applyRounding(finalAmountDouble);
                        finalAmount = Math.max(1, finalAmount);
                        
                        return new EconomyManager.PaymentRequirement(firstItem.getMaterial(), finalAmount);
                    }
                }
            }

            // Neighbors not ready - use base template without multiplier, schedule async update
            getFinalCost(chunk, player).thenAccept(result -> {
                // Future updates will use the computed value
            });
            
            DynamicCostsConfig.CostTemplateItem firstItem = template.get(0);
            return new EconomyManager.PaymentRequirement(firstItem.getMaterial(), firstItem.getAmount());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting final cost synchronously", e);
            return new EconomyManager.PaymentRequirement(Material.DIRT, 1);
        }
    }
}

