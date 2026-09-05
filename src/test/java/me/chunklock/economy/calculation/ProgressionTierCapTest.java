package me.chunklock.economy.calculation;

import me.chunklock.services.ChunkProfileStore;
import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the progression counter controls, and what a stuck counter does to pricing.
 *
 * <h3>The bug these pin</h3>
 *
 * <p>Reported in the September 5 play-test: prices rotating between dirt, gravel, sand and clay
 * at roughly ten unlocked chunks, and {@code /chunklock info} reporting <strong>0</strong>.</p>
 *
 * <p>The two symptoms were one bug. Nothing ever called
 * {@code PlayerProgressTracker.incrementUnlockedChunks} - {@code unlockChunk} only flips a
 * chunk's locked flag, and the comment in {@code UnlockGui.finishUnlock} claiming the counter
 * was maintained was simply false. So every player sat at 0 forever, and 0 means
 * {@code getMaxTierForProgression} returns 3 permanently: stone and deepslate (tier 4) are
 * filtered out of every chunk, leaving only the tier 1-3 surface materials. That is the
 * reported rotation, and no amount of playing could escape it.</p>
 *
 * <p>These tests cover the pricing consequences, which are pure functions. The increment itself
 * is a one-line call in a Bukkit GUI path that cannot be unit-tested without a server.</p>
 */
class ProgressionTierCapTest {

    // ---- What the tier cap admits at each stage of progression ---------------------------

    @Test
    @DisplayName("a stuck counter caps every player at tier 3 forever")
    void stuckCounterCapsAtTierThree() {
        // The value every player had before the fix, regardless of how much they owned.
        assertEquals(3, maxTierFor(0));

        // What they should have had after the same amount of play.
        assertEquals(4, maxTierFor(3));
        assertEquals(5, maxTierFor(8));
        assertEquals(6, maxTierFor(15));
    }

    /**
     * The reported symptom, reproduced: at cap 3 a chunk's candidates collapse to its surface
     * materials, because stone and deepslate are tier 4 and filtered out entirely.
     */
    @Test
    @DisplayName("at tier cap 3 only surface materials survive - the reported rotation")
    void tierThreeLeavesOnlySurfaceMaterials() {
        List<OwnedChunkScanner.ResourceEntry> ranked = rank(ordinaryChunk(), maxTierFor(0));

        List<Material> materials = ranked.stream().map(OwnedChunkScanner.ResourceEntry::material).toList();

        assertTrue(materials.contains(Material.DIRT), "dirt survives the cap");
        assertTrue(materials.contains(Material.GRAVEL), "gravel survives the cap");
        assertTrue(materials.contains(Material.CLAY), "clay survives the cap");
        assertTrue(materials.stream().noneMatch(m -> m == Material.STONE || m == Material.DEEPSLATE),
            "stone and deepslate are tier 4 and must be filtered out at cap 3 - "
                + "this is why prices rotated between the same few surface materials");
    }

    /**
     * With the counter working, the same chunk admits its stone column - and selection must
     * still not hand the price to it. Guards the interaction between the two September 5 fixes:
     * repairing progression must not resurrect the stone-everywhere bug.
     */
    @Test
    @DisplayName("a working counter admits stone as a candidate but does not price by it")
    void workingCounterDoesNotBringBackStone() {
        List<OwnedChunkScanner.ResourceEntry> ranked = rank(ordinaryChunk(), maxTierFor(10));

        assertTrue(ranked.stream().anyMatch(r -> r.material() == Material.STONE),
            "at cap 5 the stone column is an eligible candidate");

        Material picked = ResourceBasedMaterialStrategy
            .selectFromTargetChunk(ranked, new ArrayDeque<>()).material();

        assertNotEquals(Material.STONE, picked,
            "progression must not reintroduce stone-everywhere pricing");
        assertEquals(Material.CLAY, picked, "the most distinctive material still wins");
    }

    // ---- What the counter does to the amount ---------------------------------------------

    /**
     * The counter also drives the progression cost multiplier, so a stuck counter meant prices
     * never rose with progress either - a quieter symptom than the rotation, and one a player
     * would feel as the game failing to get harder.
     */
    @Test
    @DisplayName("a stuck counter also flattens the progression cost multiplier")
    void stuckCounterFlattensCostGrowth() {
        double atZero = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(0, 50);
        double atTen = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(10, 50);
        double atThirty = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(30, 50);

        assertTrue(atTen > atZero, "ten unlocked chunks should cost more than none");
        assertTrue(atThirty > atTen, "and thirty more than ten");
    }

    @Test
    @DisplayName("chunk score raises the price independently of progression")
    void scoreRaisesPrice() {
        double lowScore = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(5, 0);
        double highScore = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(5, 100);

        assertTrue(highScore > lowScore,
            "a harder chunk should cost more than an easy one at the same progression");
    }

    // ---- Helpers -------------------------------------------------------------------------

    /** Mirrors ResourceBasedMaterialStrategy.getMaxTierForProgression, which is private. */
    private static int maxTierFor(int unlockedChunks) {
        if (unlockedChunks >= 15) return 6;
        if (unlockedChunks >= 8) return 5;
        if (unlockedChunks >= 3) return 4;
        return 3;
    }

    /** A chunk of no special character: a little of everything, over the usual stone column. */
    private static List<ChunkProfileStore.ProfileEntry> ordinaryChunk() {
        return new ArrayList<>(List.of(
            entry(Material.DIRT, 400),
            entry(Material.GRAVEL, 150),
            entry(Material.CLAY, 90),
            entry(Material.STONE, 5300),
            entry(Material.DEEPSLATE, 3050)));
    }

    private static Map<Material, Double> baseline() {
        Map<Material, Double> b = new LinkedHashMap<>();
        b.put(Material.STONE, 5200.0);
        b.put(Material.DEEPSLATE, 3100.0);
        b.put(Material.DIRT, 260.0);
        b.put(Material.GRAVEL, 100.0);
        b.put(Material.CLAY, 40.0);
        return b;
    }

    private static List<OwnedChunkScanner.ResourceEntry> rank(
            List<ChunkProfileStore.ProfileEntry> profile, int maxTier) {
        return ResourceBasedMaterialStrategy.rankObtainableScored(profile, baseline(), maxTier)
            .stream().map(TargetChunkCandidateSource.ScoredCandidate::toResourceEntry).toList();
    }

    private static ChunkProfileStore.ProfileEntry entry(Material material, int blockCount) {
        return new ChunkProfileStore.ProfileEntry(material, blockCount,
            OwnedChunkScanner.getMaterialTier(material));
    }
}
