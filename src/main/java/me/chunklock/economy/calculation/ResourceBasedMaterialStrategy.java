package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import me.chunklock.economy.items.VanillaItemRequirement;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Material cost calculation strategy based on resources actually available
 * in the player's owned chunks.
 *
 * <p>Instead of requiring biome-specific materials that may not exist in the
 * player's territory (e.g., oak_log in a treeless Plains chunk), this strategy
 * scans the player's owned chunks and selects materials the player can actually
 * gather.</p>
 *
 * <h3>Algorithm:</h3>
 * <ol>
 *   <li>Scan all player-owned chunks for harvestable resources</li>
 *   <li>Select the best available material (highest tier that meets abundance threshold)</li>
 *   <li>Calculate required amount based on tier cost multiplier and progression</li>
 *   <li>Fall back to biome-based calculation if no resources found</li>
 * </ol>
 *
 * @see OwnedChunkScanner
 */
public class ResourceBasedMaterialStrategy implements CostCalculationStrategy {

    private final ChunklockPlugin plugin;
    private final OwnedChunkScanner scanner;
    private final BiomeUnlockRegistry biomeRegistry;
    private final PlayerProgressTracker progressTracker;

    // Config
    private int baseCost = 16;
    private int maxCost = 128;
    private int minCost = 1;

    public ResourceBasedMaterialStrategy(ChunklockPlugin plugin,
                                         OwnedChunkScanner scanner,
                                         BiomeUnlockRegistry biomeRegistry,
                                         PlayerProgressTracker progressTracker) {
        this.plugin = plugin;
        this.scanner = scanner;
        this.biomeRegistry = biomeRegistry;
        this.progressTracker = progressTracker;
    }

    public void setBaseCost(int baseCost) { this.baseCost = baseCost; }
    public void setMaxCost(int maxCost) { this.maxCost = maxCost; }
    public void setMinCost(int minCost) { this.minCost = minCost; }

    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome,
                                                       ChunkEvaluator.ChunkValueData evaluation) {
        try {
            List<OwnedChunkScanner.ResourceEntry> resources = scanner.scanPlayerResources(player);

            if (resources.isEmpty()) {
                plugin.getLogger().fine("Resource scan empty for " + player.getName() + ", falling back to biome-based");
                return fallbackToBiomeBased(player, biome, evaluation);
            }

            // Determine max tier based on player progression
            int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
            int maxTier = getMaxTierForProgression(unlocked);

            // Filter to obtainable materials (within progression tier cap)
            List<OwnedChunkScanner.ResourceEntry> obtainable = resources.stream()
                .filter(r -> r.tier() <= maxTier)
                .toList();

            if (obtainable.isEmpty()) {
                plugin.getLogger().fine("No obtainable resources (max tier " + maxTier + ") for " + player.getName() + ", falling back to biome-based");
                return fallbackToBiomeBased(player, biome, evaluation);
            }

            // Pick the best obtainable material: prefer highest tier within the cap
            // Resources already sorted highest-tier-first
            OwnedChunkScanner.ResourceEntry selected = obtainable.get(0);

            // Convert ore blocks to their drop material for the payment requirement
            Material paymentMaterial = mapToDropMaterial(selected.material());

            // Calculate cost amount
            double progressionMultiplier = calculateProgressionMultiplier(player, evaluation.score);
            double tierMultiplier = OwnedChunkScanner.getTierCostMultiplier(selected.tier());
            int amount = (int) Math.ceil(baseCost * tierMultiplier * progressionMultiplier);

            // Clamp
            amount = Math.max(minCost, Math.min(maxCost, amount));

            // Ensure we don't ask for more than a reasonable fraction of what's available
            // Don't ask for more than 25% of available resources
            int maxFromAvailable = Math.max(minCost, selected.count() / 4);
            amount = Math.min(amount, maxFromAvailable);

            plugin.getLogger().fine("Resource-based cost for " + player.getName() + 
                ": " + amount + "x " + paymentMaterial + 
                " (tier " + selected.tier() + ", available: " + selected.count() + ")");

            List<ItemRequirement> requirements = new ArrayList<>();
            requirements.add(new VanillaItemRequirement(paymentMaterial, amount));
            return new EconomyManager.PaymentRequirement(requirements);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Resource scan failed for " + player.getName() + 
                ", falling back to biome-based", e);
            return fallbackToBiomeBased(player, biome, evaluation);
        }
    }

    /**
     * Determine the maximum resource tier a player can reasonably obtain based on
     * how many chunks they have unlocked (proxy for progression / tool availability).
     *
     * <ul>
     *   <li>0-2 chunks  → Tier 1-3 (dirt, wood, crops — hand/wooden tools)</li>
     *   <li>3-7 chunks  → Tier 1-4 (+ stone, coal, copper — stone tools)</li>
     *   <li>8-14 chunks → Tier 1-5 (+ iron, gold, redstone — iron tools)</li>
     *   <li>15+ chunks  → Tier 1-6 (+ diamond, emerald — full progression)</li>
     * </ul>
     */
    private int getMaxTierForProgression(int unlockedChunks) {
        if (unlockedChunks >= 15) return 6;
        if (unlockedChunks >= 8)  return 5;
        if (unlockedChunks >= 3)  return 4;
        return 3; // New players: only common blocks, wood, and crops
    }

    /**
     * Select the best resource for payment. Prefers higher tier materials
     * that have sufficient abundance.
     */
    private OwnedChunkScanner.ResourceEntry selectBestResource(List<OwnedChunkScanner.ResourceEntry> resources) {
        // Resources are already sorted highest-tier-first, most-abundant-first within tier
        // Just return the first one (highest tier with sufficient abundance)
        return resources.get(0);
    }

    /**
     * Map ore/block materials to their player-obtainable drop material.
     * For example, IRON_ORE -> IRON_ORE (player mines it with silk touch or gets raw iron).
     * We keep it as the block material so players know what to mine.
     */
    private Material mapToDropMaterial(Material blockMaterial) {
        // Map ore blocks to their raw/drop forms that players actually collect
        return switch (blockMaterial) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> Material.COAL;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.RAW_IRON;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.RAW_GOLD;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.RAW_COPPER;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> Material.EMERALD;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> Material.LAPIS_LAZULI;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE;
            case RAW_COPPER_BLOCK -> Material.RAW_COPPER;
            default -> blockMaterial;
        };
    }

    private double calculateProgressionMultiplier(Player player, int score) {
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

    /**
     * Fallback to biome-based calculation when resource scan is unavailable.
     */
    private EconomyManager.PaymentRequirement fallbackToBiomeBased(Player player, Biome biome,
                                                                    ChunkEvaluator.ChunkValueData evaluation) {
        List<ItemRequirement> requirements = biomeRegistry.getRequirementsForBiome(biome);
        if (requirements.isEmpty()) {
            BiomeUnlockRegistry.UnlockRequirement legacy =
                    biomeRegistry.calculateRequirement(player, biome, evaluation.score);
            return new EconomyManager.PaymentRequirement(legacy.material(), legacy.amount());
        }

        double multiplier = calculateProgressionMultiplier(player, evaluation.score);
        List<ItemRequirement> adjusted = new ArrayList<>();
        for (ItemRequirement req : requirements) {
            if (req instanceof VanillaItemRequirement vanillaReq) {
                int adjustedAmount = (int) Math.ceil(vanillaReq.getAmount() * multiplier);
                adjusted.add(new VanillaItemRequirement(vanillaReq.getMaterial(), adjustedAmount));
            } else {
                adjusted.add(req);
            }
        }
        ItemRequirement selectedRequirement = selectPrimaryRequirement(adjusted);
        return new EconomyManager.PaymentRequirement(java.util.List.of(selectedRequirement));
    }

    private ItemRequirement selectPrimaryRequirement(List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            if (req instanceof VanillaItemRequirement) {
                return req;
            }
        }
        return requirements.get(0);
    }
}
