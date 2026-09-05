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
 * What a chunk with no character should cost (#86, September 5 play-test).
 *
 * <h3>The gap this closes</h3>
 *
 * <p>Ranking always produces an order, but an order is not an opinion. On a featureless chunk
 * every score hovers near 1.0 and the top entry wins by noise - and the noisiest entry is
 * always bulk depth stone, because full-height sampling gives it thousands of blocks that swing
 * with terrain elevation while a surface material has a few hundred.</p>
 *
 * <p>Reported symptom: <em>"a simple plains biome with nothing on the surface costs 12
 * deepslate"</em>. Measured at <strong>1.09</strong> - nine percent above average, purely
 * because the chunk sat slightly lower than usual. Asking a player to mine below y=0 for a
 * chunk whose defining feature is that it has none is the opposite of what #86 promises.</p>
 *
 * <p>Two guards, deliberately separate: {@code headlineScore} discounts depth filler so it
 * cannot win on a wobble, and {@link TargetChunkCandidateSource#hasDistinctiveMaterial} refuses
 * to name a headline at all below {@link TargetChunkCandidateSource#DISTINCTIVENESS_MARGIN}.</p>
 */
class FeaturelessChunkTest {

    // ---- Fixtures ------------------------------------------------------------------------

    /** The reported chunk: plains, nothing on the surface, ordinary depth beneath. */
    private static List<ChunkProfileStore.ProfileEntry> featurelessPlains() {
        return profile(
            entry(Material.DIRT, 300),
            entry(Material.GRAVEL, 90),
            entry(Material.STONE, 5100),
            entry(Material.DEEPSLATE, 3400));
    }

    /** The same chunk sitting lower than usual - more deepslate purely from elevation. */
    private static List<ChunkProfileStore.ProfileEntry> lowElevationPlains() {
        return profile(
            entry(Material.DIRT, 240),
            entry(Material.STONE, 4300),
            entry(Material.DEEPSLATE, 3600));
    }

    /** A chunk that genuinely is mostly exposed stone - a mountainside. */
    private static List<ChunkProfileStore.ProfileEntry> exposedStone() {
        return profile(
            entry(Material.DIRT, 40),
            entry(Material.STONE, 13000),
            entry(Material.DEEPSLATE, 3200));
    }

    /** A dark forest: genuinely characteristic. */
    private static List<ChunkProfileStore.ProfileEntry> darkForest() {
        return profile(
            entry(Material.DARK_OAK_LOG, 210),
            entry(Material.DIRT, 340),
            entry(Material.STONE, 5200),
            entry(Material.DEEPSLATE, 3100));
    }

    private static Map<Material, Double> baseline() {
        Map<Material, Double> b = new LinkedHashMap<>();
        b.put(Material.STONE, 5200.0);
        b.put(Material.DEEPSLATE, 3300.0);
        b.put(Material.DIRT, 260.0);
        b.put(Material.GRAVEL, 100.0);
        b.put(Material.DARK_OAK_LOG, 45.0);
        return b;
    }

    // ---- The reported bug ----------------------------------------------------------------

    @Test
    @DisplayName("a featureless plains chunk has no distinctive material, so it does not price itself")
    void featurelessChunkDeclinesToPriceItself() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(featurelessPlains(), baseline());

        assertFalse(TargetChunkCandidateSource.hasDistinctiveMaterial(ranked),
            "nothing in a featureless chunk clears the margin, so pricing must fall back "
                + "rather than naming a headline decided by noise");
    }

    /**
     * The exact reported case. Deepslate scored 1.09 - above every other candidate, and far
     * below anything that deserves to be called characteristic.
     */
    @Test
    @DisplayName("deepslate cannot headline a chunk on an elevation wobble")
    void deepslateDoesNotWinOnAWobble() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(lowElevationPlains(), baseline());

        assertNotEquals(Material.DEEPSLATE, ranked.get(0).material(),
            "deepslate at ~1.09x average is an elevation artefact, not chunk character");
        assertFalse(TargetChunkCandidateSource.hasDistinctiveMaterial(ranked),
            "and the chunk as a whole still has nothing to say about itself");
    }

    // ---- What must keep working ----------------------------------------------------------

    @Test
    @DisplayName("a genuinely characteristic chunk is unaffected")
    void distinctiveChunkStillPricesItself() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(darkForest(), baseline());

        assertTrue(TargetChunkCandidateSource.hasDistinctiveMaterial(ranked),
            "a dark forest holds ~4.7x the baseline dark oak - clearly characteristic");
        assertEquals(Material.DARK_OAK_LOG, ranked.get(0).material());
    }

    /**
     * Depth filler is discounted, not banned. A chunk that is genuinely mostly exposed stone
     * should still be able to say so - that is real character, not an artefact.
     */
    @Test
    @DisplayName("a chunk that really is mostly stone can still say so")
    void genuinelyStonyChunkCanHeadlineStone() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(exposedStone(), baseline());

        assertEquals(Material.STONE, ranked.get(0).material(),
            "2.5x the baseline stone survives the filler discount");
        assertTrue(TargetChunkCandidateSource.hasDistinctiveMaterial(ranked));
    }

    // ---- The two scores are deliberately different ---------------------------------------

    /**
     * {@code distinctiveness} stays a plain ratio to the world average, because that is what it
     * is documented as and what amount calculation reads. Only {@code headlineScore} carries
     * the filler discount. Conflating them broke
     * {@code TargetChunkCandidateSourceTest.testAverageMaterialScoresOne}, which is why they
     * are separate.
     */
    @Test
    @DisplayName("distinctiveness stays a plain ratio; only the headline score is discounted")
    void scoresAreSeparate() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(profile(entry(Material.STONE, 5200)),
                Map.of(Material.STONE, 5200.0));

        TargetChunkCandidateSource.ScoredCandidate stone = ranked.get(0);
        assertEquals(1.0, stone.distinctiveness(), 0.0001,
            "exactly average is still exactly 1.0");
        assertTrue(stone.headlineScore() < stone.distinctiveness(),
            "but as depth filler it is discounted for the purpose of headlining a chunk");
    }

    @Test
    @DisplayName("a surface material is never discounted")
    void surfaceMaterialIsNotDiscounted() {
        List<TargetChunkCandidateSource.ScoredCandidate> ranked =
            TargetChunkCandidateSource.rank(profile(entry(Material.DIRT, 260)),
                Map.of(Material.DIRT, 260.0));

        TargetChunkCandidateSource.ScoredCandidate dirt = ranked.get(0);
        assertEquals(dirt.distinctiveness(), dirt.headlineScore(), 0.0001);
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
