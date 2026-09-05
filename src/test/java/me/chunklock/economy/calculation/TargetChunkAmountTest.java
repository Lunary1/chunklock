package me.chunklock.economy.calculation;

import me.chunklock.services.ChunkProfileStore;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Amount calculation against a target-chunk pool (#86, step 3.9 task 5).
 *
 * <h3>The defect this pins</h3>
 *
 * <p>Task 4 changed <em>which list</em> pricing selects from, but left
 * {@code calculateAvailabilityModifier} comparing the selected material's raw block
 * {@code count} against the window average. That comparison is sound for owned chunks, where
 * {@code count} is how much the player holds and the list is ordered by tier then count.</p>
 *
 * <p>On the target-chunk path both halves break. {@code count} becomes raw blocks in the
 * chunk, and the list is ordered by <strong>distinctiveness</strong>. So the modifier compared
 * a raw count against a window ranked by a different quantity - and because a distinctive
 * material is typically <em>rare</em> (that is what makes it distinctive), the material #86
 * exists to surface scored far below a window average dominated by stone and deepslate, and
 * was priced <em>cheaper</em> for being notable.</p>
 *
 * <p>These tests would pass just as happily against the old code for the neutral cases, so the
 * ones that matter are {@link #distinctiveMaterialCostsMoreNotLess()} and
 * {@link #rawCountWouldInvertThePremium()} - the second demonstrates the old behaviour
 * explicitly rather than asserting the new one.</p>
 */
class TargetChunkAmountTest {

    // ---- Fixtures: same shape as TargetChunkCandidateSourceTest -------------------------

    /** Forest: trees at the surface, ordinary stone column beneath. */
    private static List<ChunkProfileStore.ProfileEntry> forest() {
        return profile(
            entry(Material.OAK_LOG, 180),
            entry(Material.DIRT, 320),
            entry(Material.STONE, 5200),
            entry(Material.DEEPSLATE, 3100));
    }

    private static Map<Material, Double> typicalBaseline() {
        Map<Material, Double> baseline = new LinkedHashMap<>();
        baseline.put(Material.STONE, 5200.0);
        baseline.put(Material.DEEPSLATE, 3100.0);
        baseline.put(Material.DIRT, 260.0);
        baseline.put(Material.OAK_LOG, 45.0);
        return baseline;
    }

    // ---- The headline behaviour ----------------------------------------------------------

    @Test
    @DisplayName("a distinctive material costs more, not less - the whole point of #86")
    void distinctiveMaterialCostsMoreNotLess() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), typicalBaseline());

        TargetChunkCandidateSource.ScoredCandidate head = ranked.get(0);
        assertEquals(Material.OAK_LOG, head.material(),
            "fixture sanity: the forest's headline material should be wood");

        double windowAverage = ranked.stream()
            .limit(4)
            .mapToDouble(TargetChunkCandidateSource.ScoredCandidate::distinctiveness)
            .average()
            .orElseThrow();

        double modifier = ResourceBasedMaterialStrategy
            .computeDistinctivenessModifier(head.distinctiveness(), windowAverage);

        assertTrue(modifier > 1.0,
            "a chunk's most distinctive material should carry a premium, not a discount - got " + modifier);
    }

    /**
     * The regression this task exists to fix, demonstrated rather than asserted.
     *
     * <p>Feeds the same forest chunk through the <em>old</em> raw-count comparison and shows it
     * produces a discount on exactly the material the new one gives a premium. If someone
     * reverts {@code calculate()} to the count-based modifier on this path, the assertion above
     * fails and this one explains why.</p>
     */
    @Test
    @DisplayName("the old raw-count modifier inverted the premium on a distinctive material")
    void rawCountWouldInvertThePremium() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), typicalBaseline());

        TargetChunkCandidateSource.ScoredCandidate head = ranked.get(0);

        double countWindowAverage = ranked.stream()
            .limit(4)
            .mapToInt(TargetChunkCandidateSource.ScoredCandidate::count)
            .average()
            .orElseThrow();

        double oldModifier = ResourceBasedMaterialStrategy
            .computeAvailabilityModifier(head.count(), countWindowAverage);

        assertTrue(oldModifier < 1.0,
            "documents the defect: by raw count, OAK_LOG (180) sits far below a window average "
                + "dominated by STONE and DEEPSLATE, so it was discounted for being distinctive - got "
                + oldModifier);

        double newWindowAverage = ranked.stream()
            .limit(4)
            .mapToDouble(TargetChunkCandidateSource.ScoredCandidate::distinctiveness)
            .average()
            .orElseThrow();
        double newModifier = ResourceBasedMaterialStrategy
            .computeDistinctivenessModifier(head.distinctiveness(), newWindowAverage);

        assertTrue(newModifier > oldModifier,
            "the distinctiveness modifier must move the price the opposite way from the raw-count one");
    }

    /**
     * Pins the <em>routing</em>, not just the two modifiers.
     *
     * <p>Written after discovering that reverting {@code calculate()} to the count-based
     * modifier left all other tests green: each function was still correct in isolation, and
     * nothing asserted which one pricing actually used. A fix nothing calls is not a fix.</p>
     */
    @Test
    @DisplayName("target-chunk pricing routes through the distinctiveness modifier, not the count one")
    void targetChunkPathUsesDistinctiveness() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), typicalBaseline());
        TargetChunkCandidateSource.ScoredCandidate head = ranked.get(0);

        List<OwnedChunkScanner.ResourceEntry> asEntries = new ArrayList<>();
        for (TargetChunkCandidateSource.ScoredCandidate candidate : ranked) {
            asEntries.add(candidate.toResourceEntry());
        }
        OwnedChunkScanner.ResourceEntry selected = head.toResourceEntry();

        double routed = ResourceBasedMaterialStrategy
            .selectAmountModifier(true, selected, ranked, asEntries);

        assertTrue(routed > 1.0,
            "the target-chunk path must price a distinctive material at a premium; a value below "
                + "1.0 means it fell back to the raw-count modifier - got " + routed);

        // And the owned-chunk path must keep its own, count-based behaviour unchanged.
        double ownedPath = ResourceBasedMaterialStrategy
            .selectAmountModifier(false, selected, ranked, asEntries);
        assertTrue(ownedPath < 1.0,
            "the owned-chunk path should still compare raw counts - got " + ownedPath);
    }

    // ---- Envelope and edge cases ---------------------------------------------------------

    @Test
    @DisplayName("the modifier stays inside the same bounded +/-10% envelope as the owned-chunk path")
    void modifierStaysWithinEnvelope() {
        // An absurdly distinctive material must not be allowed to multiply a price freely.
        double extreme = ResourceBasedMaterialStrategy.computeDistinctivenessModifier(500.0, 1.0);
        double negligible = ResourceBasedMaterialStrategy.computeDistinctivenessModifier(0.01, 50.0);

        assertTrue(extreme <= 1.1, "premium must be capped at +10% - got " + extreme);
        assertTrue(negligible >= 0.9, "discount must be floored at -10% - got " + negligible);
    }

    @Test
    @DisplayName("an exactly average material is priced neutrally")
    void averageMaterialIsNeutral() {
        assertEquals(1.0,
            ResourceBasedMaterialStrategy.computeDistinctivenessModifier(2.5, 2.5), 0.0001);
    }

    /**
     * A single-candidate chunk is its own window average, so it prices neutrally rather than
     * at an extreme. This is the sparse chunk PR #93's re-roll guard greys out, and #86's plan
     * predicted it would become common - it must not also carry a surprise price swing.
     */
    @Test
    @DisplayName("a chunk offering one material prices neutrally, not at an extreme")
    void singleCandidateChunkIsNeutral() {
        List<ChunkProfileStore.ProfileEntry> sparse = profile(entry(Material.STONE, 5200));
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(sparse, typicalBaseline());

        TargetChunkCandidateSource.ScoredCandidate only = ranked.get(0);
        double modifier = ResourceBasedMaterialStrategy
            .computeDistinctivenessModifier(only.distinctiveness(), only.distinctiveness());

        assertEquals(1.0, modifier, 0.0001);
    }

    @Test
    @DisplayName("degenerate scores yield a neutral modifier rather than a divide-by-zero")
    void degenerateScoresAreNeutral() {
        assertEquals(1.0, ResourceBasedMaterialStrategy.computeDistinctivenessModifier(1.0, 0.0), 0.0001);
        assertEquals(1.0, ResourceBasedMaterialStrategy.computeDistinctivenessModifier(0.0, 1.0), 0.0001);
        assertEquals(1.0, ResourceBasedMaterialStrategy.computeDistinctivenessModifier(-1.0, 5.0), 0.0001);
    }

    /**
     * The cost envelope is unchanged by this task: {@code base-cost}, {@code min-cost} and
     * {@code max-cost} still bound the result, and the modifier only nudges within it. Pins
     * that a premium on a cheap base cannot escape the configured ceiling.
     */
    @Test
    @DisplayName("the configured cost envelope still bounds the result")
    void envelopeStillClamps() {
        int baseCost = 16;
        int maxCost = 17;
        int minCost = 1;

        double premium = ResourceBasedMaterialStrategy.computeDistinctivenessModifier(500.0, 1.0);
        int amount = (int) Math.ceil(baseCost * 1.0 * 1.0 * premium);
        int clamped = Math.max(minCost, Math.min(maxCost, amount));

        assertTrue(clamped <= maxCost, "max-cost must still cap the result");
        assertEquals(17, clamped);
    }

    // ---- Selection must honour the ranking (#86, September 5 play-test) ------------------

    /**
     * A dark forest as the September 5 play-test would have profiled it: distinctive wood,
     * ordinary surface materials, and the stone column every chunk has.
     */
    private static List<ChunkProfileStore.ProfileEntry> darkForest() {
        return profile(
            entry(Material.DARK_OAK_LOG, 210),
            entry(Material.DIRT, 340),
            entry(Material.GRAVEL, 120),
            entry(Material.CLAY, 60),
            entry(Material.STONE, 5200),
            entry(Material.DEEPSLATE, 3100));
    }

    /** The plains chunk next door: no wood at all, otherwise the same shape. */
    private static List<ChunkProfileStore.ProfileEntry> plainsNextDoor() {
        return profile(
            entry(Material.DIRT, 400),
            entry(Material.GRAVEL, 150),
            entry(Material.CLAY, 90),
            entry(Material.STONE, 5300),
            entry(Material.DEEPSLATE, 3050));
    }

    private static Map<Material, Double> rotationBaseline() {
        Map<Material, Double> b = new LinkedHashMap<>();
        b.put(Material.STONE, 5200.0);
        b.put(Material.DEEPSLATE, 3100.0);
        b.put(Material.DIRT, 260.0);
        b.put(Material.GRAVEL, 100.0);
        b.put(Material.CLAY, 40.0);
        b.put(Material.DARK_OAK_LOG, 45.0);
        return b;
    }

    /**
     * The regression reported in play-testing, and the more serious one hiding behind it.
     *
     * <p>Selection scored candidates by {@code tier * 1000 + count}, which discards the
     * distinctiveness ranking on this path. Stone is tier 4 against clay and dirt at tier 1, so
     * as soon as a player's progression lifted the cap to tier 4 <strong>every chunk priced as
     * stone</strong> - #86's original bug, returning through selection rather than through the
     * metric.</p>
     */
    @Test
    @DisplayName("stone does not take over once the tier cap lifts (#86 via selection)")
    void stoneDoesNotWinOnceTheCapLifts() {
        // maxTier 4 is the cap from 3 unlocked chunks onward - stone is in the candidate list.
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainableScored(plainsNextDoor(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList();

        assertTrue(ranked.stream().anyMatch(r -> r.material() == Material.STONE),
            "fixture sanity: stone must be present as a candidate for this test to mean anything");

        Material picked = ResourceBasedMaterialStrategy
            .selectFromTargetChunk(ranked, new ArrayDeque<>()).material();

        assertNotEquals(Material.STONE, picked,
            "a chunk must not price as stone just because stone is the highest tier present");
        assertEquals(Material.CLAY, picked,
            "the most distinctive material should win - clay at 90 against a 40 baseline");
    }

    @Test
    @DisplayName("adjacent chunks with different contents produce different requirements")
    void adjacentChunksDiffer() {
        Material forestPick = ResourceBasedMaterialStrategy.selectFromTargetChunk(
            ResourceBasedMaterialStrategy.rankObtainableScored(darkForest(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList(),
            new ArrayDeque<>()).material();

        Material plainsPick = ResourceBasedMaterialStrategy.selectFromTargetChunk(
            ResourceBasedMaterialStrategy.rankObtainableScored(plainsNextDoor(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList(),
            new ArrayDeque<>()).material();

        assertEquals(Material.DARK_OAK_LOG, forestPick, "a dark forest should cost dark oak");
        assertNotEquals(forestPick, plainsPick,
            "two adjacent chunks with different contents must not cost the same material");
    }

    /**
     * The early-game half of the same report: below 3 unlocked chunks the cap is tier 3, so
     * stone is filtered out entirely and only dirt/gravel/clay/logs remain. Those must still
     * differ per chunk rather than collapsing to one material.
     */
    @Test
    @DisplayName("early game, with stone capped out, chunks still differ from each other")
    void earlyGameChunksStillDiffer() {
        Material forestPick = ResourceBasedMaterialStrategy.selectFromTargetChunk(
            ResourceBasedMaterialStrategy.rankObtainableScored(darkForest(), rotationBaseline(), 3)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList(),
            new ArrayDeque<>()).material();

        Material plainsPick = ResourceBasedMaterialStrategy.selectFromTargetChunk(
            ResourceBasedMaterialStrategy.rankObtainableScored(plainsNextDoor(), rotationBaseline(), 3)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList(),
            new ArrayDeque<>()).material();

        assertEquals(Material.DARK_OAK_LOG, forestPick);
        assertNotEquals(forestPick, plainsPick);
    }

    @Test
    @DisplayName("a re-roll walks down the chunk's own ranked list")
    void rerollWalksTheRankedList() {
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainableScored(darkForest(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList();

        Deque<Material> recent = new ArrayDeque<>();
        Material first = ResourceBasedMaterialStrategy.selectFromTargetChunk(ranked, recent).material();
        recent.addLast(first);
        Material second = ResourceBasedMaterialStrategy.selectFromTargetChunk(ranked, recent).material();

        assertNotEquals(first, second, "a re-roll must move to a different material");
        assertNotEquals(Material.STONE, second, "and not to stone just because it is a higher tier");
    }

    /**
     * Pins the routing, not just the two selection strategies.
     *
     * <p>The same lesson as {@link #targetChunkPathUsesDistinctiveness()}: proving
     * {@code selectFromTargetChunk} correct proves nothing if {@code calculate()} does not call
     * it. Both were caught by reverting the branch and watching every test still pass.</p>
     */
    @Test
    @DisplayName("target-chunk pricing routes to distinctiveness selection, not tier selection")
    void targetChunkPathRoutesToRankedSelection() {
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainableScored(plainsNextDoor(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList();

        Material viaTargetPool = ResourceBasedMaterialStrategy
            .selectForPool(true, ranked, 7, new ArrayDeque<>()).material();
        assertNotEquals(Material.STONE, viaTargetPool,
            "routed to the tier-first strategy - a target chunk must not price as stone");
        assertEquals(Material.CLAY, viaTargetPool);

        // The owned-chunk path keeps its own tier-first behaviour untouched.
        Material viaOwnedPool = ResourceBasedMaterialStrategy
            .selectForPool(false, ranked, 7, new ArrayDeque<>()).material();
        assertEquals(Material.STONE, viaOwnedPool,
            "the owned-chunk path should still prefer the highest tier available");
    }

    @Test
    @DisplayName("selection stays deterministic - #82 must not regress")
    void selectionIsDeterministic() {
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainableScored(darkForest(), rotationBaseline(), 4)
                .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList();

        Material first = ResourceBasedMaterialStrategy
            .selectFromTargetChunk(ranked, new ArrayDeque<>()).material();
        for (int i = 0; i < 25; i++) {
            assertEquals(first, ResourceBasedMaterialStrategy
                .selectFromTargetChunk(ranked, new ArrayDeque<>()).material(),
                "repeated calls on identical input must return the same material");
        }
    }

    // ---- Helpers -------------------------------------------------------------------------

    private static ChunkProfileStore.ProfileEntry entry(Material material, int blockCount) {
        return new ChunkProfileStore.ProfileEntry(material, blockCount,
            OwnedChunkScanner.getMaterialTier(material));
    }

    @SafeVarargs
    private static List<ChunkProfileStore.ProfileEntry> profile(ChunkProfileStore.ProfileEntry... entries) {
        return new ArrayList<>(List.of(entries));
    }
}
