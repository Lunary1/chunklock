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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The #69 obtainability ladder wiring target-chunk scoring into pricing (#86, step 3.9 task 4).
 *
 * <h3>What this pins that {@link TargetChunkCandidateSourceTest} does not</h3>
 *
 * <p>That test pins the <em>metric</em> - given a profile and a baseline, which material is
 * most distinctive. This one pins what pricing <em>does</em> with the answer: the tier cap,
 * walking down the chunk's own list when the head is unobtainable, and the fall-through to
 * owned chunks that stops #69 regressing.</p>
 *
 * <p>Task 4 is the half of #86 where behaviour actually changes, so these are the assertions
 * that would fail if the ladder were wired up wrong while the metric itself stayed correct.</p>
 */
class TargetChunkPricingLadderTest {

    // ---- Fixtures ----------------------------------------------------------------------
    //
    // Same shape as the prototype and TargetChunkCandidateSourceTest fixtures: a surface
    // signature over the stone/deepslate column that full-height sampling produces everywhere.

    private static List<ChunkProfileStore.ProfileEntry> forest() {
        return List.of(
            entry(Material.OAK_LOG, 180, 2),
            entry(Material.DIRT, 320, 1),
            entry(Material.STONE, 5200, 4),
            entry(Material.DEEPSLATE, 3100, 4));
    }

    private static List<ChunkProfileStore.ProfileEntry> mountain() {
        return List.of(
            entry(Material.STONE, 6400, 4),
            entry(Material.ANDESITE, 420, 4),
            entry(Material.DEEPSLATE, 3400, 4));
    }

    /** A chunk whose most distinctive material is gated behind late progression. */
    private static List<ChunkProfileStore.ProfileEntry> diamondRich() {
        return List.of(
            entry(Material.DIAMOND_ORE, 40, 6),
            entry(Material.OAK_LOG, 150, 2),
            entry(Material.STONE, 5000, 4),
            entry(Material.DEEPSLATE, 3000, 4));
    }

    /**
     * A world baseline in which stone and deepslate are everywhere and the surface materials
     * are genuinely rarer - which is what full-height sampling actually produces.
     */
    private static Map<Material, Double> baseline() {
        Map<Material, Double> baseline = new LinkedHashMap<>();
        baseline.put(Material.STONE, 5400.0);
        baseline.put(Material.DEEPSLATE, 3200.0);
        baseline.put(Material.DIRT, 260.0);
        baseline.put(Material.OAK_LOG, 40.0);
        baseline.put(Material.ANDESITE, 90.0);
        baseline.put(Material.DIAMOND_ORE, 6.0);
        return baseline;
    }

    private static ChunkProfileStore.ProfileEntry entry(Material material, int count, int tier) {
        return new ChunkProfileStore.ProfileEntry(material, count, tier);
    }

    private static List<Material> materials(List<OwnedChunkScanner.ResourceEntry> entries) {
        List<Material> result = new ArrayList<>(entries.size());
        entries.forEach(e -> result.add(e.material()));
        return result;
    }

    // ---- Acceptance: a chunk's price describes that chunk -------------------------------

    @Test
    @DisplayName("a forest prices as wood, not as the stone that dominates its raw counts")
    void forestHeadlinesWood() {
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainable(forest(), baseline(), 6);

        assertEquals(Material.OAK_LOG, ranked.get(0).material(),
            "a forest should cost wood - this is #86's original bug if it reads STONE");
    }

    @Test
    @DisplayName("two adjacent chunks with different contents produce different requirements")
    void adjacentChunksDiffer() {
        Map<Material, Double> baseline = baseline();

        Material forestPick = ResourceBasedMaterialStrategy
            .rankObtainable(forest(), baseline, 6).get(0).material();
        Material mountainPick = ResourceBasedMaterialStrategy
            .rankObtainable(mountain(), baseline, 6).get(0).material();

        assertNotEquals(forestPick, mountainPick,
            "adjacent chunks with materially different contents must not price identically");
        assertEquals(Material.OAK_LOG, forestPick);
        assertEquals(Material.ANDESITE, mountainPick);
    }

    @Test
    @DisplayName("requirements are not dominated by stone and deepslate despite full-height sampling")
    void stoneDoesNotHeadlineEverything() {
        Map<Material, Double> baseline = baseline();

        for (List<ChunkProfileStore.ProfileEntry> profile : List.of(forest(), mountain())) {
            Material headline = ResourceBasedMaterialStrategy
                .rankObtainable(profile, baseline, 6).get(0).material();

            assertNotEquals(Material.STONE, headline,
                "full-height sampling makes stone the most abundant block almost everywhere; "
                    + "ranking by abundance rather than distinctiveness would headline it");
            assertNotEquals(Material.DEEPSLATE, headline);
        }
    }

    // ---- Rung 2: walk down the chunk's own list ----------------------------------------

    @Test
    @DisplayName("an unobtainable headline material promotes the next one from the same chunk")
    void tierCapWalksDownTheChunksOwnList() {
        Map<Material, Double> baseline = baseline();

        // A new player: tier 3 and below. DIAMOND_ORE is tier 6.
        List<OwnedChunkScanner.ResourceEntry> newPlayer =
            ResourceBasedMaterialStrategy.rankObtainable(diamondRich(), baseline, 3);

        assertFalse(materials(newPlayer).contains(Material.DIAMOND_ORE),
            "a tier-6 material must not be offered to a player capped at tier 3 (#69)");
        assertEquals(Material.OAK_LOG, newPlayer.get(0).material(),
            "the price should still describe this chunk, using its next obtainable material");

        // A progressed player reaches the same chunk's headline material.
        List<OwnedChunkScanner.ResourceEntry> veteran =
            ResourceBasedMaterialStrategy.rankObtainable(diamondRich(), baseline, 6);

        assertEquals(Material.DIAMOND_ORE, veteran.get(0).material());
    }

    @Test
    @DisplayName("filtering happens after ranking, so removing the head promotes by distinctiveness")
    void tierFilterAppliedAfterRanking() {
        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainable(diamondRich(), baseline(), 3);

        // OAK_LOG (150/40 = 3.75) beats DIRT and the stone column on distinctiveness even
        // though STONE has thirty times its raw count. If the tier filter ran first and the
        // list were then re-sorted by abundance, STONE would lead.
        assertEquals(Material.OAK_LOG, ranked.get(0).material());
    }

    @Test
    @DisplayName("a chunk holding nothing obtainable falls through to owned chunks")
    void nothingObtainableFallsThrough() {
        List<ChunkProfileStore.ProfileEntry> allHighTier = List.of(
            entry(Material.DIAMOND_ORE, 40, 6),
            entry(Material.EMERALD_ORE, 12, 6));

        List<OwnedChunkScanner.ResourceEntry> ranked =
            ResourceBasedMaterialStrategy.rankObtainable(allHighTier, baseline(), 3);

        assertTrue(ranked.isEmpty(),
            "an empty result is the signal to use the owned-chunk rung, which is what "
                + "guarantees the player is asked for something they can actually get (#69)");
    }

    @Test
    @DisplayName("a never-profiled chunk yields no candidates rather than an error")
    void unprofiledChunkYieldsNothing() {
        assertTrue(ResourceBasedMaterialStrategy.rankObtainable(List.of(), baseline(), 6).isEmpty());
    }

    // ---- Warm-up ------------------------------------------------------------------------

    @Test
    @DisplayName("pricing stays on owned chunks until the baseline is warm")
    void warmUpGate() {
        assertFalse(TargetChunkCandidateSource.isBaselineReady(0),
            "a baseline built from nothing must not be scored against");
        assertFalse(TargetChunkCandidateSource.isBaselineReady(
            TargetChunkCandidateSource.MIN_BASELINE_CHUNKS - 1));
        assertTrue(TargetChunkCandidateSource.isBaselineReady(
            TargetChunkCandidateSource.MIN_BASELINE_CHUNKS));
    }

    // ---- #82 must not regress -----------------------------------------------------------

    @Test
    @DisplayName("repeated calculations for one unchanged chunk stay stable (#82)")
    void rankingIsStableAcrossRepeatedCalls() {
        Map<Material, Double> baseline = baseline();
        List<Material> first = materials(
            ResourceBasedMaterialStrategy.rankObtainable(forest(), baseline, 6));

        for (int i = 0; i < 25; i++) {
            assertEquals(first, materials(
                    ResourceBasedMaterialStrategy.rankObtainable(forest(), baseline, 6)),
                "#82 was a query that returned different answers on identical input; pricing is "
                    + "reached from display paths, so looking at a chunk must not change it");
        }
    }

    @Test
    @DisplayName("selection over target-chunk candidates is deterministic (#82)")
    void selectionOverTargetCandidatesIsDeterministic() {
        List<OwnedChunkScanner.ResourceEntry> candidates =
            ResourceBasedMaterialStrategy.rankObtainable(forest(), baseline(), 6);
        Deque<Material> noHistory = new ArrayDeque<>();

        Material first = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 7, noHistory).material();

        for (int i = 0; i < 10; i++) {
            assertEquals(first, ResourceBasedMaterialStrategy
                    .selectMaterial(candidates, 7, noHistory).material(),
                "selection must stay a pure deterministic query over the candidate list");
        }
        assertTrue(noHistory.isEmpty(), "selection must not record its own result (#82)");
    }

    // ---- The two candidate sources stay interchangeable ---------------------------------

    @Test
    @DisplayName("re-roll history moves selection within the target chunk's own materials")
    void rerollWalksTheSameChunksList() {
        List<OwnedChunkScanner.ResourceEntry> candidates =
            ResourceBasedMaterialStrategy.rankObtainable(forest(), baseline(), 6);

        Deque<Material> recent = new ArrayDeque<>();
        Material firstPick = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 7, recent).material();

        // A paid re-roll records the rejected material; the next pick must differ.
        ResourceBasedMaterialStrategy.rememberSelection(recent, firstPick);
        Material secondPick = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 7, recent).material();

        assertNotEquals(firstPick, secondPick,
            "a paid re-roll that returned the same material is the #83 no-op bug");
        assertTrue(materials(candidates).contains(secondPick),
            "the re-rolled material must still come from this chunk - widening to the "
                + "player's territory would stop the price describing the chunk (#86)");
    }
}
