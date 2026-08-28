package me.chunklock.hologram;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the hologram state cache eviction policy (issue #74).
 *
 * <p>{@code hologramStates} keeps entries after a hologram despawns so it can be respawned
 * cheaply, but its keys combine player UUID, chunk coordinates and wall side. Without
 * eviction it grows as roughly players * chunks * 4 and was only cleared on shutdown,
 * which is the unbounded memory growth reported in #74.
 *
 * <p>These tests exercise the policy directly with a lightweight stand-in for
 * HologramState, since the real class requires a running Bukkit server.
 */
public class HologramStateEvictionTest {

    /** Minimal stand-in carrying only the two fields the policy reads. */
    private record FakeState(boolean spawned, long lastUpdateTick) {}

    private static Map<String, FakeState> mapOf(FakeState... states) {
        Map<String, FakeState> map = new LinkedHashMap<>();
        for (int i = 0; i < states.length; i++) {
            map.put("id-" + i, states[i]);
        }
        return map;
    }

    private static HologramService.EvictionResult evict(Map<String, FakeState> map, long now,
                                                        long staleTicks, int softLimit, int trimBatch) {
        return HologramService.evictStaleStates(
            map, now, staleTicks, softLimit, trimBatch,
            FakeState::spawned, FakeState::lastUpdateTick);
    }

    /**
     * The core of #74: despawned entries that nobody has touched must not live forever.
     */
    @Test
    public void evictsDespawnedEntriesOlderThanThreshold() {
        // now = 1000, staleTicks = 500, so anything last touched at or before tick 500 is stale
        Map<String, FakeState> states = mapOf(
            new FakeState(false, 0L),      // age 1000 -> evict
            new FakeState(false, 900L),    // age  100 -> keep
            new FakeState(false, 100L)     // age  900 -> evict
        );

        HologramService.EvictionResult result = evict(states, 1000L, 500L, 10_000, 2_000);

        assertEquals(2, result.stale());
        assertEquals(1, states.size(), "only the recently-touched despawned entry should remain");
        assertEquals(900L, states.values().iterator().next().lastUpdateTick());
    }

    /**
     * Spawned entries track live holograms - evicting them would orphan a visible display.
     */
    @Test
    public void neverEvictsSpawnedEntries() {
        Map<String, FakeState> states = mapOf(
            new FakeState(true, 0L),   // ancient but SPAWNED -> keep
            new FakeState(true, 0L),
            new FakeState(false, 0L)   // ancient, despawned   -> evict
        );

        HologramService.EvictionResult result = evict(states, 1_000_000L, 500L, 10_000, 2_000);

        assertEquals(1, result.stale());
        assertEquals(2, states.size());
        assertTrue(states.values().stream().allMatch(FakeState::spawned),
            "every surviving entry should be a spawned one");
    }

    /**
     * The hard cap is the safety net if the staleness rule alone cannot keep up.
     */
    @Test
    public void enforcesHardCapByEvictingOldestFirst() {
        Map<String, FakeState> states = new LinkedHashMap<>();
        // 100 despawned entries, all recent enough to survive rule 1
        for (int i = 0; i < 100; i++) {
            states.put("id-" + i, new FakeState(false, 1000L + i));
        }

        // softLimit 100 is reached; trim 10 oldest
        HologramService.EvictionResult result = evict(states, 1000L, 100_000L, 100, 10);

        assertEquals(0, result.stale(), "nothing is stale yet");
        assertEquals(10, result.overflow());
        assertEquals(90, states.size());

        long oldestRemaining = states.values().stream()
            .mapToLong(FakeState::lastUpdateTick).min().orElseThrow();
        assertEquals(1010L, oldestRemaining, "the 10 oldest entries should have gone first");
    }

    /**
     * A server below both thresholds must not lose cached state - eviction has a cost
     * (recomputing hologram content), so it should only happen when actually needed.
     */
    @Test
    public void leavesHealthyCacheUntouched() {
        Map<String, FakeState> states = mapOf(
            new FakeState(false, 900L),
            new FakeState(true, 950L),
            new FakeState(false, 1000L)
        );

        HologramService.EvictionResult result = evict(states, 1000L, 500L, 10_000, 2_000);

        assertEquals(0, result.total());
        assertEquals(3, states.size());
    }

    /**
     * Regression guard for #74 itself: simulate exploration and confirm the cache plateaus
     * instead of growing without bound. Before the fix this map only shrank on shutdown.
     */
    @Test
    public void cacheStaysBoundedUnderSustainedExploration() {
        Map<String, FakeState> states = new ConcurrentHashMap<>();
        int softLimit = 500;
        int trimBatch = 100;
        long staleTicks = 200L;

        long tick = 0L;
        for (int round = 0; round < 50; round++) {
            // each round: a player walks past 40 new chunk sides, then they despawn
            for (int i = 0; i < 40; i++) {
                states.put("chunk-" + round + "-" + i, new FakeState(false, tick));
            }
            tick += 20L;
            evict(states, tick, staleTicks, softLimit, trimBatch);
        }

        assertTrue(states.size() <= softLimit + 40,
            "cache should plateau near the limit, was " + states.size());
        // 50 rounds * 40 entries = 2000 inserted; unbounded growth would keep them all
        assertTrue(states.size() < 2000, "cache must not retain every entry ever created");
    }

    @Test
    public void handlesEmptyMap() {
        Map<String, FakeState> states = new LinkedHashMap<>();
        HologramService.EvictionResult result = evict(states, 1000L, 500L, 10, 5);
        assertEquals(0, result.total());
        assertTrue(states.isEmpty());
    }
}
