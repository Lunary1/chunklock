package me.chunklock.economy.calculation;

import me.chunklock.services.ChunkProfileStore;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scoring a chunk's profile into ranked pricing candidates (#86, step 3.9 task 4).
 *
 * <h3>What this pins</h3>
 *
 * <p>{@code DistinctivenessMetricPrototypeTest} established, before any of this was built,
 * that relative-to-baseline scoring distinguishes all five biome fixtures where raw abundance
 * distinguishes none. That test scores a throwaway prototype. <strong>This one runs the same
 * five fixtures through the production code</strong>, because a metric that only works in the
 * prototype is worth nothing.</p>
 *
 * <p>The fixtures are deliberately identical to the prototype's, so if the two ever disagree
 * it means production drifted from the design the evidence supported.</p>
 */
class TargetChunkCandidateSourceTest {

    // ---- Fixtures: verbatim from DistinctivenessMetricPrototypeTest ---------------------

    /** Forest: trees at the surface, ordinary stone column beneath. */
    private static List<ChunkProfileStore.ProfileEntry> forest() {
        return profile(
            entry(Material.OAK_LOG, 180),
            entry(Material.DIRT, 320),
            entry(Material.STONE, 5200),
            entry(Material.DEEPSLATE, 3100));
    }

    /** Mountain: exposed stone at the surface, deeper column, more ore. */
    private static List<ChunkProfileStore.ProfileEntry> mountain() {
        return profile(
            entry(Material.STONE, 6400),
            entry(Material.ANDESITE, 420),
            entry(Material.DEEPSLATE, 3400));
    }

    /** Desert: sand cap over the usual stone column. */
    private static List<ChunkProfileStore.ProfileEntry> desert() {
        return profile(
            entry(Material.SAND, 640),
            entry(Material.CACTUS, 24),
            entry(Material.STONE, 5000),
            entry(Material.DEEPSLATE, 3000));
    }

    /** Plains: thin grass/dirt cap, otherwise indistinguishable from anywhere else. */
    private static List<ChunkProfileStore.ProfileEntry> plains() {
        return profile(
            entry(Material.DIRT, 380),
            entry(Material.WHEAT, 30),
            entry(Material.STONE, 5100),
            entry(Material.DEEPSLATE, 3050));
    }

    /** Swamp: mud and clay at the surface, ordinary depths. */
    private static List<ChunkProfileStore.ProfileEntry> swamp() {
        return profile(
            entry(Material.CLAY, 210),
            entry(Material.MUD, 260),
            entry(Material.OAK_LOG, 40),
            entry(Material.STONE, 5150),
            entry(Material.DEEPSLATE, 3020));
    }

    /**
     * A baseline as it would look after profiling a spread of ordinary chunks: stone and
     * deepslate everywhere, surface materials rarer because most chunks hold none of them.
     * Exactly the shape {@code ChunkProfileStore} builds, where a chunk containing none of a
     * material still counts toward that material's average.
     */
    private static Map<Material, Double> typicalBaseline() {
        Map<Material, Double> baseline = new LinkedHashMap<>();
        baseline.put(Material.STONE, 5200.0);
        baseline.put(Material.DEEPSLATE, 3100.0);
        baseline.put(Material.DIRT, 260.0);
        baseline.put(Material.OAK_LOG, 45.0);
        baseline.put(Material.SAND, 130.0);
        baseline.put(Material.ANDESITE, 90.0);
        baseline.put(Material.CLAY, 40.0);
        baseline.put(Material.MUD, 25.0);
        baseline.put(Material.WHEAT, 6.0);
        baseline.put(Material.CACTUS, 5.0);
        return baseline;
    }

    // ---- The headline result -------------------------------------------------------------

    /**
     * The acceptance criterion from #86, run through production code.
     *
     * <p>Five biomes, five different headline materials - and each is the material a player
     * would actually name looking at the chunk. Raw abundance gives STONE for all five.</p>
     */
    @Test
    @DisplayName("#86: five biome fixtures produce five distinct headline materials")
    void testEachBiomePricesDistinctly() {
        Map<Material, Double> baseline = typicalBaseline();

        Map<String, Material> headline = new LinkedHashMap<>();
        headline.put("Forest", top(forest(), baseline));
        headline.put("Mountain", top(mountain(), baseline));
        headline.put("Desert", top(desert(), baseline));
        headline.put("Plains", top(plains(), baseline));
        headline.put("Swamp", top(swamp(), baseline));

        assertEquals(Material.OAK_LOG, headline.get("Forest"), "a forest should cost wood");
        assertEquals(Material.ANDESITE, headline.get("Mountain"), "a mountain should cost its distinctive stone");
        assertEquals(Material.SAND, headline.get("Desert"), "a desert should cost sand");
        assertEquals(Material.WHEAT, headline.get("Plains"), "plains should cost what grows there");
        assertEquals(Material.MUD, headline.get("Swamp"), "a swamp should cost mud");

        assertEquals(5, headline.values().stream().distinct().count(),
            "all five fixtures must price distinctly, matching the prototype's result: " + headline);
    }

    /**
     * The bug #86 exists to fix, asserted directly rather than implied.
     *
     * <p>Every fixture holds more stone than anything else. If ranking ever falls back to raw
     * abundance - which is what happens if the baseline is ignored, or if it collapses to
     * counting only the chunks that contain a material - every one of these prices as stone.</p>
     */
    @Test
    @DisplayName("#86: stone never headlines, despite being the most abundant block in every fixture")
    void testStoneNeverHeadlines() {
        Map<Material, Double> baseline = typicalBaseline();

        for (List<ChunkProfileStore.ProfileEntry> fixture :
                List.of(forest(), mountain(), desert(), plains(), swamp())) {

            // Precondition: stone really is the most abundant block here, so this is a real test.
            Material mostAbundant = fixture.stream()
                .max((a, b) -> Integer.compare(a.blockCount(), b.blockCount()))
                .orElseThrow()
                .material();
            assertEquals(Material.STONE, mostAbundant,
                "fixture precondition: stone must be the most abundant block");

            assertNotEquals(Material.STONE, top(fixture, baseline),
                "raw abundance would headline stone here; relative scoring must not");
            assertNotEquals(Material.DEEPSLATE, top(fixture, baseline),
                "deepslate is abundant everywhere and is never what a chunk is 'about'");
        }
    }

    @Test
    @DisplayName("#86: two adjacent chunks with different contents price differently")
    void testAdjacentChunksDiffer() {
        Map<Material, Double> baseline = typicalBaseline();
        assertNotEquals(top(forest(), baseline), top(mountain(), baseline),
            "the headline acceptance criterion from #86");
    }

    // ---- Ranking mechanics ----------------------------------------------------------------

    @Test
    @DisplayName("candidates come back in descending distinctiveness")
    void testRankingIsOrdered() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), typicalBaseline());

        assertFalse(ranked.isEmpty());
        for (int i = 1; i < ranked.size(); i++) {
            assertTrue(ranked.get(i - 1).distinctiveness() >= ranked.get(i).distinctiveness(),
                "ranking must be monotonically descending");
        }
    }

    /**
     * #82 must not regress. Selection is a pure deterministic query, so ranking has to be
     * total - ties broken by name, not left to sort stability.
     */
    @Test
    @DisplayName("#82: ranking is deterministic across repeated calls")
    void testRankingIsDeterministic() {
        Map<Material, Double> baseline = typicalBaseline();
        List<Material> first = materials(TargetChunkCandidateSource.rank(swamp(), baseline));

        for (int i = 0; i < 20; i++) {
            assertEquals(first, materials(TargetChunkCandidateSource.rank(swamp(), baseline)),
                "repeated ranking of an unchanged chunk must not vary");
        }
    }

    @Test
    @DisplayName("distinctiveness of 1.0 means exactly average")
    void testAverageMaterialScoresOne() {
        List<ChunkProfileStore.ProfileEntry> chunk = profile(entry(Material.STONE, 5200));
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(chunk, Map.of(Material.STONE, 5200.0));

        assertEquals(1.0, ranked.get(0).distinctiveness(), 0.0001,
            "a chunk holding exactly the world average is not distinctive");
    }

    /**
     * A material the baseline has never seen must not headline on that basis alone.
     *
     * <p>Treating an unseen material as having a baseline near zero would make its first
     * sighting infinitely distinctive - so the first cactus ever profiled would headline every
     * desert regardless of how little of it there is.</p>
     */
    @Test
    @DisplayName("a material missing from the baseline does not score infinitely distinctive")
    void testUnseenMaterialIsNotInfinitelyDistinctive() {
        List<ChunkProfileStore.ProfileEntry> chunk = profile(
            entry(Material.STONE, 5200),
            entry(Material.CACTUS, 10));

        // A baseline that knows stone well but has never recorded cactus.
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(chunk, Map.of(Material.STONE, 1000.0));

        assertEquals(Material.STONE, ranked.get(0).material(),
            "10 blocks of an unseen material must not outrank a genuinely stone-rich chunk");
        assertTrue(Double.isFinite(ranked.get(1).distinctiveness()),
            "an unseen material must score finitely");
    }

    @Test
    @DisplayName("an empty or never-profiled chunk yields no candidates")
    void testUnprofiledChunkYieldsNothing() {
        assertTrue(TargetChunkCandidateSource.rank(List.of(), typicalBaseline()).isEmpty(),
            "never profiled is the signal to fall back, not an error");
        assertTrue(TargetChunkCandidateSource.rank(null, typicalBaseline()).isEmpty());
    }

    @Test
    @DisplayName("an empty baseline still ranks, treating everything as unseen")
    void testEmptyBaselineDoesNotThrow() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), Map.of());

        assertFalse(ranked.isEmpty(), "an empty baseline must not throw");
        // With no baseline every material is scored against the same constant, so this
        // degenerates to raw abundance - which is exactly why callers gate on warm-up.
        assertEquals(Material.STONE, ranked.get(0).material(),
            "records why isBaselineReady gates this: with no baseline, ranking IS raw abundance");
    }

    // ---- Warm-up ---------------------------------------------------------------------------

    @Test
    @DisplayName("#86 warm-up: the baseline is not trusted until enough chunks are profiled")
    void testWarmUpGate() {
        assertFalse(TargetChunkCandidateSource.isBaselineReady(0));
        assertFalse(TargetChunkCandidateSource.isBaselineReady(
            TargetChunkCandidateSource.MIN_BASELINE_CHUNKS - 1));
        assertTrue(TargetChunkCandidateSource.isBaselineReady(
            TargetChunkCandidateSource.MIN_BASELINE_CHUNKS));
        assertTrue(TargetChunkCandidateSource.isBaselineReady(
            TargetChunkCandidateSource.MIN_BASELINE_CHUNKS * 100L));
    }

    // ---- Interop with the existing pipeline ------------------------------------------------

    /**
     * The property that lets everything downstream stay unchanged: a target-chunk candidate
     * converts to the same {@code ResourceEntry} the owned-chunk path produces, so selection,
     * the tier cap, cost multipliers and the re-roll guard all work without modification.
     */
    @Test
    @DisplayName("a scored candidate converts to the pipeline's existing candidate type")
    void testConvertsToResourceEntry() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(forest(), typicalBaseline());

        TargetChunkCandidateSource.ScoredCandidate head = ranked.get(0);
        OwnedChunkScanner.ResourceEntry converted = head.toResourceEntry();

        assertEquals(head.material(), converted.material());
        assertEquals(head.count(), converted.count());
        assertEquals(head.tier(), converted.tier());
    }

    // ---- Helpers ---------------------------------------------------------------------------

    private static Material top(List<ChunkProfileStore.ProfileEntry> profile,
                                Map<Material, Double> baseline) {
        return TargetChunkCandidateSource.rank(profile, baseline).get(0).material();
    }

    private static List<Material> materials(List<TargetChunkCandidateSource.ScoredCandidate> ranked) {
        List<Material> out = new ArrayList<>(ranked.size());
        for (TargetChunkCandidateSource.ScoredCandidate candidate : ranked) {
            out.add(candidate.material());
        }
        return out;
    }

    private static ChunkProfileStore.ProfileEntry entry(Material material, int count) {
        return new ChunkProfileStore.ProfileEntry(material, count,
            OwnedChunkScanner.getMaterialTier(material));
    }

    @SafeVarargs
    private static List<ChunkProfileStore.ProfileEntry> profile(ChunkProfileStore.ProfileEntry... entries) {
        return List.of(entries);
    }
}
