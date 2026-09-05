package me.chunklock.economy.calculation;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import me.chunklock.economy.items.VanillaItemRequirement;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.services.ChunkProfileStore;
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
 *   <li>Rank the <em>target</em> chunk's stored profile by distinctiveness (#86)</li>
 *   <li>Fall back to scanning the player's owned chunks when that is unavailable</li>
 *   <li>Select a deterministic best candidate (tier, abundance, and anti-repeat weighting)</li>
 *   <li>Calculate amount from tier, diminishing-return progression, and soft availability modifier</li>
 *   <li>Fall back to biome-based calculation if no resources found</li>
 * </ol>
 *
 * <h3>The #69 obtainability ladder (#86)</h3>
 *
 * <p>A price should describe the chunk being bought - the forest costs wood, the mountain
 * costs stone - but never at the cost of naming something the player cannot get. That is what
 * {@code resource-scan} mode exists to prevent: #69 was players on a treeless plains chunk
 * being told to pay oak logs. So target-chunk candidates are tried in order and each rung
 * falls through to the next:</p>
 *
 * <ol>
 *   <li>the target chunk's most distinctive material, if within the progression tier cap</li>
 *   <li>down the target chunk's <em>own</em> ranked list, so the price still describes it</li>
 *   <li>the player's owned chunks - today's behaviour, and the rung that guarantees
 *       obtainability</li>
 *   <li>biome-based, when nothing has been scanned at all</li>
 * </ol>
 *
 * <p>Rung 3 is load-bearing rather than a safety net, and it is also what warm-up reuses:
 * until enough chunks are profiled for the baseline to mean anything
 * ({@link TargetChunkCandidateSource#MIN_BASELINE_CHUNKS}), pricing simply stays on it.</p>
 *
 * @see OwnedChunkScanner
 * @see TargetChunkCandidateSource
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

    /**
     * How many of the top-sorted candidates selection actually chooses between.
     *
     * <p>Named because two separate things depend on it: the anti-repeat weighting, and
     * whether a re-roll has anywhere to go. See {@link #countSelectableCandidates}.</p>
     */
    private static final int SELECTION_WINDOW = 4;

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

    /**
     * The profile store, or null when pricing must fall back to owned-chunk behaviour.
     *
     * <p>Resolved per call rather than injected because this strategy is constructed fresh on
     * every economy reload, and {@link EconomyManager#selectCalculationStrategy} can run
     * before the store exists. Null is an ordinary answer here, not a failure: it means
     * "profiles unavailable", which is rung 3 of the ladder.</p>
     */
    private ChunkProfileStore profileStore() {
        try {
            return plugin.getChunkProfileStore();
        } catch (Exception e) {
            // Never let a missing service break pricing - fall back rather than fail.
            return null;
        }
    }

    public void setBaseCost(int baseCost) { this.baseCost = baseCost; }
    public void setMaxCost(int maxCost) { this.maxCost = maxCost; }
    public void setMinCost(int minCost) { this.minCost = minCost; }

    @Override
    public EconomyManager.PaymentRequirement calculate(Player player, Chunk chunk, Biome biome,
                                                       ChunkEvaluator.ChunkValueData evaluation) {
        try {
            // Determine max tier based on player progression
            int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
            int maxTier = getMaxTierForProgression(unlocked);

            // Rungs 1-2 of the ladder: price from the target chunk's own contents when it has
            // been profiled and the baseline is warm. Reads the chunk's coordinates here, on
            // the calling thread, because calculate() runs async (AsyncCostCalculationService)
            // and touching Bukkit objects off the main thread is what #78 was.
            List<TargetChunkCandidateSource.ScoredCandidate> scoredTarget = targetChunkScored(chunk, maxTier);
            boolean fromTargetChunk = !scoredTarget.isEmpty();

            List<OwnedChunkScanner.ResourceEntry> sortedCandidates = new ArrayList<>(scoredTarget.size());
            for (TargetChunkCandidateSource.ScoredCandidate candidate : scoredTarget) {
                sortedCandidates.add(candidate.toResourceEntry());
            }

            // Rung 3: the player's owned chunks. Guarantees obtainability (#69), and is also
            // where warm-up sits until enough chunks are profiled.
            if (!fromTargetChunk) {
                List<OwnedChunkScanner.ResourceEntry> resources = scanner.scanPlayerResources(player);

                if (resources.isEmpty()) {
                    plugin.getLogger().fine("Resource scan empty for " + player.getName() + ", falling back to biome-based");
                    return fallbackToBiomeBased(player, biome, evaluation);
                }

                // Filter to obtainable materials (within progression tier cap)
                List<OwnedChunkScanner.ResourceEntry> obtainable = resources.stream()
                    .filter(r -> r.tier() <= maxTier)
                    .toList();

                if (obtainable.isEmpty()) {
                    plugin.getLogger().fine("No obtainable resources (max tier " + maxTier + ") for " + player.getName() + ", falling back to biome-based");
                    return fallbackToBiomeBased(player, biome, evaluation);
                }

                sortedCandidates = sortCandidates(obtainable);
            }

            OwnedChunkScanner.ResourceEntry selected =
                selectMaterial(player, sortedCandidates, unlocked, fromTargetChunk);

            // Convert ore blocks to their drop material for the payment requirement
            Material paymentMaterial = mapToDropMaterial(selected.material());

            // Calculate cost amount
            double progressionMultiplier = calculateProgressionMultiplier(player, evaluation.score);
            double tierMultiplier = OwnedChunkScanner.getTierCostMultiplier(selected.tier());
            // Each pool is modulated by the quantity it actually ranks by: distinctiveness for
            // the target chunk, held count for owned chunks. See computeDistinctivenessModifier.
            double availabilityModifier =
                selectAmountModifier(fromTargetChunk, selected, scoredTarget, sortedCandidates);
            int amount = (int) Math.ceil(baseCost * tierMultiplier * progressionMultiplier * availabilityModifier);

            // Clamp
            amount = Math.max(minCost, Math.min(maxCost, amount));

            plugin.getLogger().fine("Resource-based cost for " + player.getName() +
                ": " + amount + "x " + paymentMaterial +
                " (source: " + (fromTargetChunk ? "target-chunk" : "owned-chunks") +
                ", tier " + selected.tier() + ", available: " + selected.count() +
                ", " + (fromTargetChunk ? "distinctiveness-mod: " : "availability-mod: ")
                + String.format("%.2f", availabilityModifier) + ")");

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
     * Rungs 1-2: the target chunk's own materials, ranked by distinctiveness and capped to
     * what the player's progression can obtain.
     *
     * <p>Returns empty whenever target-chunk pricing cannot honestly answer - the store is
     * unavailable, the baseline is still warming up, the chunk has never been profiled, or
     * everything in it is above the tier cap. Every one of those is an ordinary outcome
     * meaning "use the owned-chunk rung", not an error.</p>
     *
     * <p><strong>Ordering matters for cost, not just correctness.</strong> The warm-up gate is
     * checked before the profile read, because {@code getBaselineChunkCount()} is one cheap
     * aggregate whereas {@code getProfile} is a per-chunk lookup - and on a MySQL server both
     * are network round trips. Before warm-up completes the answer is "fall back" regardless
     * of what the profile holds, so reading it first would be a query per priced chunk whose
     * result is thrown away.</p>
     *
     * <p>Walking down the ranked list is rung 2: the head material may be above the tier cap
     * on a chunk full of diamond, and the next-most-distinctive obtainable material still
     * describes the chunk. Only when none of them qualifies does pricing leave the chunk
     * behind.</p>
     *
     * <p>Candidates keep their distinctiveness rather than being converted straight to
     * {@code ResourceEntry}, because amount calculation needs the score ranking used and that
     * type has nowhere to carry it. See {@link #computeDistinctivenessModifier}.</p>
     *
     * @param chunk the chunk being priced; its coordinates are read on the calling thread
     * @return ranked obtainable candidates, or empty to fall through to the owned-chunk rung
     */
    private List<TargetChunkCandidateSource.ScoredCandidate> targetChunkScored(Chunk chunk, int maxTier) {
        ChunkProfileStore store = profileStore();
        if (store == null || chunk == null) {
            return List.of();
        }

        try {
            if (!TargetChunkCandidateSource.isBaselineReady(store.getBaselineChunkCount())) {
                return List.of();
            }

            // Bukkit reads stay on the calling thread (#78).
            String worldName = chunk.getWorld().getName();
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();

            List<ChunkProfileStore.ProfileEntry> profile = store.getProfile(worldName, chunkX, chunkZ);
            if (profile.isEmpty()) {
                return List.of();
            }

            return rankObtainableScored(profile, store.getBaseline(), maxTier);
        } catch (Exception e) {
            // Pricing must never fail because profiles are unreadable - fall back instead.
            plugin.getLogger().log(Level.FINE, "Target-chunk profile unavailable, using owned chunks", e);
            return List.of();
        }
    }

    /**
     * Rank a chunk's profile and keep only what the player's progression can obtain.
     *
     * <p>Rung 2 of the ladder, as a pure function so it can be tested without a server: the
     * ordering is {@link TargetChunkCandidateSource}'s distinctiveness ranking, and the tier
     * filter is applied <em>after</em> ranking so removing an unobtainable head material
     * promotes the next-most-distinctive one rather than reshuffling by abundance.</p>
     *
     * <p>Returning empty means every material in the chunk is above the cap, which is the
     * signal to fall through to owned chunks (#69).</p>
     */
    static List<OwnedChunkScanner.ResourceEntry> rankObtainable(
            List<ChunkProfileStore.ProfileEntry> profile,
            Map<Material, Double> baseline,
            int maxTier) {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            rankObtainableScored(profile, baseline, maxTier);

        List<OwnedChunkScanner.ResourceEntry> obtainable = new ArrayList<>(ranked.size());
        for (TargetChunkCandidateSource.ScoredCandidate candidate : ranked) {
            obtainable.add(candidate.toResourceEntry());
        }
        return obtainable;
    }

    /** As {@link #rankObtainable}, keeping the distinctiveness score amount calculation needs. */
    static List<TargetChunkCandidateSource.ScoredCandidate> rankObtainableScored(
            List<ChunkProfileStore.ProfileEntry> profile,
            Map<Material, Double> baseline,
            int maxTier) {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(profile, baseline);

        List<TargetChunkCandidateSource.ScoredCandidate> obtainable = new ArrayList<>(ranked.size());
        for (TargetChunkCandidateSource.ScoredCandidate candidate : ranked) {
            if (candidate.tier() <= maxTier) {
                obtainable.add(candidate);
            }
        }
        return obtainable;
    }

    private OwnedChunkScanner.ResourceEntry selectMaterial(Player player,
                                                           List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
                                                           int unlockedChunks,
                                                           boolean fromTargetChunk) {
        UUID playerId = player.getUniqueId();
        Deque<Material> recent = recentSelections.computeIfAbsent(playerId, id -> new ConcurrentLinkedDeque<>());

        return selectForPool(fromTargetChunk, sortedCandidates, unlockedChunks, recent);
    }

    /**
     * Route selection to the strategy that matches the pool the candidates came from.
     *
     * <p>Extracted so the <em>routing</em> is testable and not just the two strategies it
     * routes between. Reverting this branch leaves both strategies individually correct while
     * pricing silently uses the wrong one - which is precisely how the tier-first selection
     * survived task 4 unnoticed until play-testing.</p>
     */
    static OwnedChunkScanner.ResourceEntry selectForPool(boolean fromTargetChunk,
                                                         List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
                                                         int unlockedChunks,
                                                         Deque<Material> recent) {
        return fromTargetChunk
            ? selectFromTargetChunk(sortedCandidates, recent)
            : selectMaterial(sortedCandidates, unlockedChunks, recent);
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
     * With a memory of {@value #RECENT_SELECTION_MEMORY} against a {@value #SELECTION_WINDOW}-candidate window,
     * the top materials rotated in a strict cycle.</p>
     *
     * <p>The anti-repeat variety this history provides is still applied, but the history is
     * now only advanced by {@link #recordUnlockSelection(UUID, Material)} when an unlock
     * actually completes.</p>
     */
    static OwnedChunkScanner.ResourceEntry selectMaterial(List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
                                                          int unlockedChunks,
                                                          Deque<Material> recent) {
        int windowSize = Math.min(SELECTION_WINDOW, sortedCandidates.size());
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
     * Choose from a target chunk's candidates, honouring the distinctiveness ranking.
     *
     * <p><strong>Why this cannot reuse {@link #selectMaterial}.</strong> That method scores by
     * {@code tier * 1000 + count}, which is consistent on the owned-chunk path because the list
     * it receives is already sorted by tier then count - re-scoring agrees with the ordering.
     *
     * <p>The target-chunk list is sorted by <em>distinctiveness</em>, so tier-first scoring
     * discards the ranking {@link TargetChunkCandidateSource} just computed. The effect was not
     * subtle: stone is tier 4 against dirt and clay at tier 1, so once a player's progression
     * lifted the cap to tier 4 the stone column outscored every characteristic material and
     * <strong>#86's original bug returned through selection</strong> - the metric was right and
     * the pick ignored it. Before the cap lifts, the same scoring collapses every chunk to
     * whichever of dirt/gravel/clay/log ranks highest by tier, which is the rotation reported
     * in the September 5 play-test.
     *
     * <p>So selection here takes the ranking as given. Tier keeps both of its real jobs -
     * capping what a player can be asked for, and setting the amount via
     * {@link OwnedChunkScanner#getTierCostMultiplier} - but it no longer decides <em>which</em>
     * material describes the chunk. Owner's call, September 5.
     *
     * <p>Anti-repeat still applies, so a re-roll walks down the chunk's own ranked list rather
     * than repeating the head material. Wood is not penalised here: that rule exists to stop
     * the owned-chunk pool leaning on logs forever, whereas on this path wood only wins when
     * the chunk is genuinely wooded.
     *
     * @param sortedCandidates candidates in distinctiveness order, highest first
     */
    static OwnedChunkScanner.ResourceEntry selectFromTargetChunk(
            List<OwnedChunkScanner.ResourceEntry> sortedCandidates,
            Deque<Material> recent) {
        int windowSize = Math.min(SELECTION_WINDOW, sortedCandidates.size());
        List<OwnedChunkScanner.ResourceEntry> topCandidates = sortedCandidates.subList(0, windowSize);

        // The list is already in preference order, so the first candidate not recently used is
        // the answer. Walking it this way is what makes a re-roll move to the chunk's
        // next-most-distinctive material rather than to its highest tier.
        for (OwnedChunkScanner.ResourceEntry candidate : topCandidates) {
            if (!recent.contains(candidate.material())) {
                return candidate;
            }
        }

        // Everything in the window was recently used - the chunk is out of variety, so the
        // most distinctive material stands rather than falling back to an arbitrary pick.
        return topCandidates.get(0);
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

    /**
     * How many distinct materials this player could actually be asked for right now.
     *
     * <p>Used to decide whether a re-roll can change anything (#83). Selection only ever
     * considers the top {@value #SELECTION_WINDOW} candidates, so "has an alternative" means
     * <em>the window</em> holds more than one entry, not the raw scan. A player sitting on a
     * hundred kinds of stone still has nothing to re-roll into if progression caps them to
     * one obtainable tier.</p>
     *
     * <p>The filtering here deliberately mirrors {@link #calculate} - same source, same tier
     * cap, same window - because a count that disagreed with what selection actually does
     * would grey out a usable button, or charge for a re-roll that cannot move. Returns 0
     * when the scan is empty, which is the biome-based fallback path rather than a
     * resource-scan selection at all.</p>
     *
     * <p><strong>Under #86 this counts the target chunk's own materials</strong>, because a
     * re-roll walks down that chunk's ranked list rather than widening to the player's
     * territory. Widening would keep the button alive more often but the re-rolled price
     * would stop describing the chunk, which is the exact thing #86 exists to fix - better to
     * grey the button out honestly. Expect it to fire considerably more often than it does
     * today: a sparse chunk may genuinely offer only one obtainable material. That is correct
     * behaviour, not a regression.</p>
     *
     * @param chunk the chunk whose re-roll is being offered, or null to count owned chunks
     */
    public int countSelectableCandidates(Player player, Chunk chunk) {
        try {
            int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
            int maxTier = getMaxTierForProgression(unlocked);

            List<TargetChunkCandidateSource.ScoredCandidate> targetCandidates = targetChunkScored(chunk, maxTier);
            if (!targetCandidates.isEmpty()) {
                return Math.min(SELECTION_WINDOW, targetCandidates.size());
            }

            List<OwnedChunkScanner.ResourceEntry> resources = scanner.scanPlayerResources(player);
            if (resources.isEmpty()) {
                return 0;
            }

            long obtainable = resources.stream()
                .filter(r -> r.tier() <= maxTier)
                .count();

            return (int) Math.min(SELECTION_WINDOW, obtainable);
        } catch (Exception e) {
            // A failure here must not block the button. Reporting "more than one" leaves the
            // re-roll offered, which is the behaviour that existed before this check.
            plugin.getLogger().log(Level.FINE,
                "Failed to count re-roll candidates for " + player.getName(), e);
            return SELECTION_WINDOW;
        }
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

    /**
     * Locate the selected material among the ranked candidates and modulate by its
     * distinctiveness relative to the same selection window.
     *
     * <p>Matching is by material rather than by index because selection may pick any entry in
     * the window, not just the head. A material that cannot be found - which should not happen,
     * since selection chooses from this very list - yields a neutral modifier rather than
     * guessing.</p>
     */
    static double calculateDistinctivenessModifier(OwnedChunkScanner.ResourceEntry selected,
                                                   List<TargetChunkCandidateSource.ScoredCandidate> ranked) {
        double selectedScore = -1.0;
        for (TargetChunkCandidateSource.ScoredCandidate candidate : ranked) {
            if (candidate.material() == selected.material()) {
                selectedScore = candidate.distinctiveness();
                break;
            }
        }
        if (selectedScore < 0) {
            return 1.0;
        }

        int windowSize = Math.min(SELECTION_WINDOW, ranked.size());
        double averageScore = ranked.stream()
            .limit(windowSize)
            .mapToDouble(TargetChunkCandidateSource.ScoredCandidate::distinctiveness)
            .average()
            .orElse(selectedScore);

        return computeDistinctivenessModifier(selectedScore, averageScore);
    }

    /**
     * Pick the amount modifier for whichever pool the price came from.
     *
     * <p>Extracted from {@code calculate()} so the <em>routing</em> is testable, not just the
     * two modifiers it routes between. Without this, reverting the target-chunk path to the
     * count-based modifier leaves every unit test passing - the functions are each still
     * correct in isolation, and nothing pins which one pricing actually uses.</p>
     *
     * @param fromTargetChunk whether the price came from rungs 1-2 rather than owned chunks
     */
    static double selectAmountModifier(boolean fromTargetChunk,
                                       OwnedChunkScanner.ResourceEntry selected,
                                       List<TargetChunkCandidateSource.ScoredCandidate> scoredTarget,
                                       List<OwnedChunkScanner.ResourceEntry> sortedCandidates) {
        if (fromTargetChunk) {
            return calculateDistinctivenessModifier(selected, scoredTarget);
        }
        int windowSize = Math.min(SELECTION_WINDOW, sortedCandidates.size());
        double averageCount = sortedCandidates.stream()
            .limit(windowSize)
            .mapToInt(OwnedChunkScanner.ResourceEntry::count)
            .average()
            .orElse(selected.count());
        return computeAvailabilityModifier(selected.count(), averageCount);
    }

    /**
     * The amount modifier for the target-chunk rungs, expressed in distinctiveness (#86 task 5).
     *
     * <p><strong>Why this cannot reuse {@link #calculateAvailabilityModifier}.</strong> That
     * method compares the selected material's {@code count} against the window average, which
     * is meaningful for owned chunks - {@code count} there is how much the player holds, and
     * the list is ordered by tier then count, so the comparison is like-for-like.
     *
     * <p>On this path both halves of that break. {@code count} is raw blocks in the chunk, and
     * the list is ordered by <em>distinctiveness</em>. Comparing a raw count against a window
     * ranked by a different quantity inverts the intent: a rare-but-distinctive material - the
     * exact thing #86 exists to surface - sits far below a window average that stone and
     * deepslate dominate, and would be priced <em>cheaper</em> for being notable.
     *
     * <p>So the modifier is computed over the quantity this path actually ranks by. A material
     * well above its world baseline is what makes the chunk worth buying, and costs slightly
     * more; an ordinary one costs slightly less. The envelope is deliberately the same bounded
     * ±10% as the owned-chunk path - this adjusts a price, it does not set one.
     */
    static double computeDistinctivenessModifier(double selectedDistinctiveness,
                                                 double averageDistinctiveness) {
        if (averageDistinctiveness <= 0 || selectedDistinctiveness <= 0) {
            return 1.0;
        }
        double ratio = selectedDistinctiveness / averageDistinctiveness;
        double modifier = 1.0 + ((ratio - 1.0) * 0.15);
        return Math.max(0.9, Math.min(1.1, modifier));
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
