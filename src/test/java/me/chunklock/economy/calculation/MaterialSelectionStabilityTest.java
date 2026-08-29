package me.chunklock.economy.calculation;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for issue #82: the unlock GUI cycled the required material every time it
 * was reopened on an unchanged chunk.
 *
 * <p>The existing economy tests all assert cost <em>values</em> - what a tier multiplier
 * produces, whether a modifier is bounded. None of them asserted what happens when the same
 * calculation runs twice, which is exactly how #82 survived a well-covered subsystem. These
 * tests assert <strong>stability across repeated calls</strong>.</p>
 *
 * <p>The scenario mirrors the original report: a player whose owned chunks contain the four
 * common Tier-1 materials, on a chunk they have not unlocked yet.</p>
 */
public class MaterialSelectionStabilityTest {

    /** The four materials from the bug report, in the order the scan would rank them. */
    private static List<OwnedChunkScanner.ResourceEntry> reportedCandidates() {
        List<OwnedChunkScanner.ResourceEntry> candidates = new ArrayList<>(List.of(
            new OwnedChunkScanner.ResourceEntry(Material.GRAVEL, 240, 1),
            new OwnedChunkScanner.ResourceEntry(Material.CLAY, 220, 1),
            new OwnedChunkScanner.ResourceEntry(Material.SAND, 200, 1),
            new OwnedChunkScanner.ResourceEntry(Material.DIRT, 180, 1)
        ));
        return candidates;
    }

    @Test
    @DisplayName("#82: repeated selection on an unchanged chunk returns the same material")
    public void testRepeatedSelectionIsStable() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material first = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 2, recent).material();

        // Opening the GUI, drawing a hologram and pre-calculating adjacent chunks all reach
        // this same path. None of them should change the answer.
        for (int reopen = 1; reopen <= 20; reopen++) {
            Material again = ResourceBasedMaterialStrategy
                .selectMaterial(candidates, 2, recent).material();
            assertEquals(first, again,
                "Requirement changed on call " + reopen + " with no unlock in between: "
                    + first + " -> " + again);
        }
    }

    @Test
    @DisplayName("#82: selection does not mutate the recent-selection history")
    public void testSelectionDoesNotMutateHistory() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        for (int i = 0; i < 10; i++) {
            ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent);
        }

        assertTrue(recent.isEmpty(),
            "Calculating a cost must not record a selection - that write side effect on a "
                + "query path is the root cause of #82. History held: " + recent);
    }

    /**
     * The specific symptom from the report: gravel -> clay -> sand -> dirt, repeating with a
     * period of 4. If selection ever records its own result again, this fails.
     */
    @Test
    @DisplayName("#82: the four-material rotation does not reappear")
    public void testNoFourCycleRotation() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Set<Material> seen = new LinkedHashSet<>();
        for (int i = 0; i < 12; i++) {
            seen.add(ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material());
        }

        assertEquals(1, seen.size(),
            "Twelve consecutive calculations for one unchanged chunk produced "
                + seen.size() + " different requirements: " + seen);
    }

    /**
     * The fix must not flatten selection to a constant. Variety is the feature the
     * anti-repeat penalty exists to provide; #82 was about <em>when</em> it advances, not
     * whether it should exist.
     */
    @Test
    @DisplayName("Variety still works: a completed unlock moves selection on")
    public void testCompletedUnlockChangesNextSelection() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        Material firstChunk = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 2, recent).material();

        // Simulate the player actually paying and unlocking, which is the only event allowed
        // to advance the history.
        ResourceBasedMaterialStrategy.rememberSelection(recent, firstChunk);

        Material secondChunk = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 3, recent).material();

        assertNotEquals(firstChunk, secondChunk,
            "After unlocking with " + firstChunk + ", the next chunk should lean towards a "
                + "different material - otherwise the fix removed variety instead of "
                + "relocating it");
    }

    /**
     * Repeated calls must stay stable after an unlock too - the new requirement should be as
     * committed as the old one was.
     */
    @Test
    @DisplayName("Selection is stable again after an unlock advances the history")
    public void testStableAfterUnlock() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        ResourceBasedMaterialStrategy.rememberSelection(recent,
            ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, recent).material());

        Material afterUnlock = ResourceBasedMaterialStrategy
            .selectMaterial(candidates, 3, recent).material();

        for (int reopen = 1; reopen <= 10; reopen++) {
            assertEquals(afterUnlock,
                ResourceBasedMaterialStrategy.selectMaterial(candidates, 3, recent).material(),
                "Requirement drifted on call " + reopen + " after an unlock");
        }
    }

    /**
     * Two players looking at equivalent chunks must not interfere with each other. Histories
     * are per-player, so a busy server should not make one player's price depend on another's
     * browsing.
     */
    @Test
    @DisplayName("One player's selection does not depend on another's")
    public void testPlayersDoNotInterfere() {
        List<OwnedChunkScanner.ResourceEntry> candidates = reportedCandidates();
        Deque<Material> playerOne = new ConcurrentLinkedDeque<>();
        Deque<Material> playerTwo = new ConcurrentLinkedDeque<>();

        Material one = ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, playerOne).material();

        for (int i = 0; i < 5; i++) {
            ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, playerTwo);
        }

        assertEquals(one,
            ResourceBasedMaterialStrategy.selectMaterial(candidates, 2, playerOne).material(),
            "Another player's cost calculations changed this player's requirement");
    }
}
