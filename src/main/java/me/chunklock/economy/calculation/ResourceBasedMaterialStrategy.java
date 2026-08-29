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
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
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
 *   <li>Select a deterministic best candidate (tier, abundance, and anti-repeat weighting)</li>
 *   <li>Calculate amount from tier, diminishing-return progression, and soft availability modifier</li>
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
    private static final int RECENT_SELECTION_MEMORY = 3;

    // Cost calculation runs on async threads (see AsyncCostCalculationService), so this
    // map and the deques inside it must both be safe for concurrent access.
    private final Map<UUID, Deque<Material>> recentSelections = new ConcurrentHashMap<>();

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

            List<OwnedChunkScanner.ResourceEntry> sortedCandidates = sortCandidates(obtainable);
            OwnedChunkScanner.ResourceEntry selected = selectMaterial(player, sortedCandidates, unlocked);

            // Convert ore blocks to their drop material for the payment requirement
            Material paymentMaterial = mapToDropMaterial(selected.material());

            // Calculate cost amount
            double progressionMultiplier = calculateProgressionMultiplier(player, evaluation.score);
            double tierMultiplier = OwnedChunkScanner.getTierCostMultiplier(selected.tier());
            double availabilityModifier = calculateAvailabilityModifier(selected, sortedCandidates);
            int amount = (int) Math.ceil(baseCost * tierMultiplier * progressionMultiplier * availabilityModifier);

            // Clamp
            amount = Math.max(minCost, Math.min(maxCost, amount));

            plugin.getLogger().fine("Resource-based cost for " + player.getName() + 
                ": " + amount + "x " + paymentMaterial + 
                " (tier " + selected.tier() + ", available: " + selected.count() + ", availability-mod: " + String.format("%.2f", availabilityModifier) + ")");

            List<ItemRequirement> requirements = new ArrayList<>();
            requirements.add(new VanillaItemRequirement(paymentMaterial, amount));
            return new EconomyManager.PaymentRequirement(requirements);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Resource scan failed for " + player.getName() + 
                ", falling back to biome-based", e);
            return fallbackToBiomeBased(player, biome, evaluation);
        }
    }

    private OwnedChunkScanner.ResourceEntry selectMaterial(Player player,
                                                           List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
                                                           int unlockedChunks) {
        UUID playerId = player.getUniqueId();
        Deque<Material> recent = recentSelections.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());

        return selectMaterial(sortedCandidates, unlockedChunks, recent);
    }

    /**
     * Choose the material to require, given the candidate list and the player's recent
     * selection history.
     *
     * <p>This method is a <strong>pure query</strong>: it does not modify {@code recent}.
     * That is deliberate and is the fix for issue #82. Selection used to record its own
     * result here, but {@code calculate} is reached from display paths - opening the unlock
     * GUI, rendering a hologram, and the async pre-calculation of adjacent chunks - so
     * recording on every call made merely <em>looking</em> at a chunk change its price.
     * With a memory of {@value #RECENT_SELECTION_MEMORY} against a four-candidate window,
     * the top materials rotated in a strict cycle.</p>
     *
     * <p>The anti-repeat variety this history provides is still applied, but the history is
     * now only advanced by {@link #recordUnlockSelection(UUID, Material)} when an unlock
     * actually completes.</p>
     */
    static OwnedChunkScanner.ResourceEntry selectMaterial(List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
                                                          int unlockedChunks,
                                                          Deque<Material> recent) {
        int windowSize = Math.min(4, sortedCandidates.size());
        List<OwnedChunkScanner.ResourceEntry> topCandidates = sortedCandidates.subList(0, windowSize);

        OwnedChunkScanner.ResourceEntry best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (OwnedChunkScanner.ResourceEntry candidate : topCandidates) {
            double score = computeSelectionScore(candidate, unlockedChunks, recent);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null) {
            best = topCandidates.get(unlockedChunks % windowSize);
        }

        return best;
    }

    /**
     * Record that a player actually unlocked a chunk paying the given material, so the next
     * chunk they price is nudged towards a different requirement.
     *
     * <p>Called only from the completed-unlock path. See {@link #selectMaterial} for why
     * display paths must not advance this history (#82).</p>
     */
    public void recordUnlockSelection(UUID playerId, Material material) {
        if (playerId == null || material == null) {
            return;
        }
        Deque<Material> recent = recentSelections.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());
        rememberSelection(recent, material);
    }

    /**
     * Record that a player paid to <em>reject</em> a material, so recalculating this chunk
     * produces a different requirement (#83 re-roll).
     *
     * <p>This is needed because {@link #selectMaterial} is deterministic - deliberately so,
     * since that is the #82 fix. Given the same candidates, progression and history it
     * returns the same material every time. Clearing a committed price and recalculating
     * therefore reproduces the price that was just cleared, and a player who paid for a
     * re-roll would see nothing change.</p>
     *
     * <p>Advancing the recent-selection history applies the same -350 anti-repeat penalty an
     * unlock does, which is what actually moves selection to a different candidate. It is
     * kept separate from {@link #recordUnlockSelection} because the two mean different
     * things - one is "paid for", the other "paid to avoid" - and because only completed
     * unlocks and explicit re-rolls may ever touch this history. Display paths must not, or
     * prices shift as players look at them.</p>
     *
     * @return true if the history was advanced
     */
    public boolean recordRejectedSelection(UUID playerId, Material material) {
        if (playerId == null || material == null) {
            return false;
        }
        Deque<Material> recent = recentSelections.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());
        rememberSelection(recent, material);
        return true;
    }

    private List<OwnedChunkScanner.ResourceEntry> sortCandidates(List<OwnedChunkScanner.ResourceEntry> candidates) {
        List<OwnedChunkScanner.ResourceEntry> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
            .comparingInt(OwnedChunkScanner.ResourceEntry::tier).reversed()
            .thenComparingInt(OwnedChunkScanner.ResourceEntry::count).reversed()
            .thenComparing(entry -> entry.material().name()));
        return sorted;
    }

    static double computeSelectionScore(OwnedChunkScanner.ResourceEntry entry, int unlockedChunks, Deque<Material> recent) {
        double score = (entry.tier() * 1000.0) + Math.min(entry.count(), 250);

        if (isWoodLike(entry.material()) && unlockedChunks >= 5) {
            score -= unlockedChunks >= 12 ? 220.0 : 140.0;
        }

        if (recent.contains(entry.material())) {
            score -= 350.0;
        }

        return score;
    }

    private double calculateAvailabilityModifier(OwnedChunkScanner.ResourceEntry selected,
                                                 List<OwnedChunkScanner.ResourceEntry> sortedCandidates) {
        int windowSize = Math.min(4, sortedCandidates.size());
        double averageCount = sortedCandidates.stream()
            .limit(windowSize)
            .mapToInt(OwnedChunkScanner.ResourceEntry::count)
            .average()
            .orElse(selected.count());

        return computeAvailabilityModifier(selected.count(), averageCount);
    }

    static double computeAvailabilityModifier(int selectedCount, double averageCount) {
        if (averageCount <= 0) {
            return 1.0;
        }
        double ratio = selectedCount / averageCount;
        double modifier = 1.0 + ((ratio - 1.0) * 0.15);
        return Math.max(0.9, Math.min(1.1, modifier));
    }

    static void rememberSelection(Deque<Material> recent, Material material) {
        recent.addLast(material);
        // pollFirst() returns null on an empty deque instead of throwing, so a concurrent
        // trim from another async calculation cannot fail here.
        while (recent.size() > RECENT_SELECTION_MEMORY && recent.pollFirst() != null) {
            // trimmed
        }
    }

    /**
     * Forget a player's recent-selection history. Called when a player quits so the
     * per-player map does not grow without bound on long-running servers.
     */
    public void invalidatePlayer(UUID playerId) {
        recentSelections.remove(playerId);
    }

    /**
     * Clear all remembered selections (used on reload).
     */
    public void clearRecentSelections() {
        recentSelections.clear();
    }

    static boolean isWoodLike(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD");
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
            case RAW_IRON_BLOCK -> Material.RAW_IRON;
            case RAW_GOLD_BLOCK -> Material.RAW_GOLD;
            default -> blockMaterial;
        };
    }

    private double calculateProgressionMultiplier(Player player, int score) {
        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = computeBaseProgressionMultiplier(unlocked, score);

        if (biomeRegistry.isTeamIntegrationActive()) {
            try {
                multiplier *= biomeRegistry.getTeamCostMultiplier(player);
            } catch (Exception e) {
                // Ignore
            }
        }

        return multiplier;
    }

    static double computeBaseProgressionMultiplier(int unlockedChunks, int score) {
        double progression = 1.0 + (Math.sqrt(Math.max(0, unlockedChunks)) / 5.0);
        double normalizedScore = Math.max(0.0, Math.min(1.0, score / 100.0));
        double scoreFactor = 1.0 + (normalizedScore * 0.6);
        return progression * scoreFactor;
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
