package me.chunklock.economy.calculation;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for a re-roll that charged the player and changed nothing (#83).
 *
 * <p>Found in play-testing: the Vault balance was deducted, the stored price was cleared,
 * and the recalculated requirement came back <strong>identical</strong>.</p>
 *
 * <p>The cause is a direct consequence of the #82 fix. {@link
 * ResourceBasedMaterialStrategy#selectMaterial} is deterministic on purpose - that is what
 * stopped the GUI rotating materials as players looked at chunks. But determinism means
 * clearing a price and recalculating from unchanged inputs reproduces exactly the price that
 * was cleared. Nothing about a re-roll changed the inputs, so nothing about the output could
 * change either.</p>
 *
 * <p>The only lever that moves selection is the recent-selection history and its -350
 * anti-repeat penalty, which previously only a completed unlock advanced. A re-roll now
 * advances it too, recording the material the player paid to reject.</p>
 *
 * <p>These tests assert the <em>user-visible</em> property - "the material changes" - rather
 * than the mechanism, because the previous suite covered the mechanism thoroughly and still
 * missed this.</p>
 */
public class RerollChangesMaterialTest {

    /** A player whose owned chunks hold the four common Tier-1 materials. */
    private static List<OwnedChunkScanner.ResourceEntry> candidates() {
        return new ArrayList<>(List.of(
            new OwnedChunkScanner.ResourceEntry(Material.GRAVEL, 240, 1),
            new OwnedChunkScanner.ResourceEntry(Material.CLAY, 220, 1),
            new OwnedChunkScanner.ResourceEntry(Material.SAND, 200, 1),
            new OwnedChunkScanner.ResourceEntry(Material.DIRT, 180, 1)
        ));
    }

    @Test
    @DisplayName("#83: re-rolling changes the required material")
    public void testRerollChangesTheMaterial() {
        List<OwnedChunkScanner.ResourceEntry> candidates = candidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material before = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();

        // What a re-roll now does: record the rejected material, then recalculate.
        ResourceBasedMaterialStrategy.rememberSelection(recent, before);
        Material after = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();

        assertNotEquals(before, after,
            "the player paid for a re-roll and must not be handed the same requirement back; "
                + "both calls returned " + before);
    }

    @Test
    @DisplayName("#83: without recording the rejection, the re-roll is a no-op")
    public void testWithoutRecordingNothingChanges() {
        List<OwnedChunkScanner.ResourceEntry> candidates = candidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        // Exactly what the first implementation did: clear the stored price and recalculate,
        // without touching the history. This pins the shape of the bug so a future change
        // cannot quietly reintroduce it.
        Material before = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        Material after = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();

        assertEquals(before, after,
            "documents the bug: selection is deterministic, so clearing and recalculating "
                + "alone can never produce a different material");
    }

    @Test
    @DisplayName("#83: consecutive re-rolls keep producing new materials")
    public void testConsecutiveRerollsKeepMoving() {
        List<OwnedChunkScanner.ResourceEntry> candidates = candidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material first = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        ResourceBasedMaterialStrategy.rememberSelection(recent, first);

        Material second = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        ResourceBasedMaterialStrategy.rememberSelection(recent, second);

        Material third = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();

        assertNotEquals(first, second, "first re-roll must move off the original material");
        assertNotEquals(second, third, "second re-roll must move again");
        assertNotEquals(first, third,
            "a player paying twice in a row should not land back on what they first rejected");
    }

    @Test
    @DisplayName("#83: a player with one usable material has nothing to re-roll into")
    public void testSingleCandidateCannotChange() {
        List<OwnedChunkScanner.ResourceEntry> onlyOne = new ArrayList<>(List.of(
            new OwnedChunkScanner.ResourceEntry(Material.COBBLESTONE, 500, 1)
        ));
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material before = ResourceBasedMaterialStrategy.selectMaterial(onlyOne, 2, recent).material();
        ResourceBasedMaterialStrategy.rememberSelection(recent, before);
        Material after = ResourceBasedMaterialStrategy.selectMaterial(onlyOne, 2, recent).material();

        // Not a defect in the re-roll, but a real case worth knowing about: a player whose
        // territory holds exactly one usable material cannot be offered an alternative, so
        // charging them for a re-roll takes payment for something impossible.
        assertEquals(before, after,
            "with one candidate there is no alternative to offer - the GUI should surface "
                + "this rather than charge for a re-roll that cannot change anything");
    }

    @Test
    @DisplayName("#83: recording a rejection preserves #82 view stability")
    public void testDisplayRemainsStableAfterRerolling() {
        List<OwnedChunkScanner.ResourceEntry> candidates = candidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material rejected = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        ResourceBasedMaterialStrategy.rememberSelection(recent, rejected);

        // After a re-roll, simply looking at the chunk repeatedly must still be stable - the
        // #82 guarantee has to survive this change.
        Material a = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        Material b = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();
        Material c = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material();

        assertEquals(a, b, "#82: repeated views must not rotate the requirement");
        assertEquals(b, c, "#82: repeated views must not rotate the requirement");
    }
}
