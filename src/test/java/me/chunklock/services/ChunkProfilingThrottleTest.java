package me.chunklock.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the per-tick scan budget that keeps chunk profiling affordable (#86, task 3).
 *
 * <p>A full-height chunk scan reads roughly 24,000 blocks. Profiling is triggered from a
 * movement handler and requests five chunks at a time (the one entered plus four neighbours),
 * so without a budget a player running along a frontier would trigger scan after scan on the
 * main thread. This project has already paid for that lesson once: #74 was memory exhaustion
 * from per-chunk work that nobody had bounded.</p>
 *
 * <p>The budget is asserted here rather than in play because a tick spike is exactly the kind
 * of thing that looks fine on a local single-player test and falls over on a real server -
 * the same gap that left #74 open pending real-load data.</p>
 *
 * <p>{@link ChunkProfilingService#currentTick()} is overridden so the budget can be driven
 * deterministically; the rest of the throttling logic is the production code.</p>
 */
class ChunkProfilingThrottleTest {

    /**
     * Drives the tick counter by hand and counts how often the budget was granted.
     *
     * <p>Subclasses the service rather than mocking it, so the arithmetic under test is the
     * shipped arithmetic. Only the clock is replaced.</p>
     */
    private static final class FakeClockService extends ChunkProfilingService {
        private long tick = 0L;

        FakeClockService() {
            super(null, null);
        }

        @Override
        long currentTick() {
            return tick;
        }

        void advanceTick() {
            tick++;
        }

        /** Exercises the budget directly - profileIfNeeded needs a live Chunk. */
        boolean tryConsume() {
            return consumeTickBudget();
        }
    }

    @Test
    @DisplayName("#86: only one chunk is scanned per tick, however many are requested")
    void testBudgetIsOnePerTick() {
        FakeClockService service = new FakeClockService();

        assertTrue(service.tryConsume(), "the first request in a tick must be granted");

        // A movement handler asks for the entered chunk plus four neighbours.
        for (int i = 0; i < 4; i++) {
            assertFalse(service.tryConsume(),
                "further requests in the same tick must be refused - five 24,000-block scans "
                    + "in one tick is exactly the spike this budget exists to prevent");
        }
    }

    @Test
    @DisplayName("#86: the budget refills on the next tick")
    void testBudgetRefills() {
        FakeClockService service = new FakeClockService();

        assertTrue(service.tryConsume());
        assertFalse(service.tryConsume());

        service.advanceTick();

        assertTrue(service.tryConsume(),
            "a new tick must allow work again, or profiling would stall permanently after "
                + "the first chunk");
    }

    @Test
    @DisplayName("#86: a busy stretch still scans steadily rather than stalling or bursting")
    void testSteadyRateAcrossTicks() {
        FakeClockService service = new FakeClockService();

        int granted = 0;
        for (int tick = 0; tick < 20; tick++) {
            // Every tick, pretend five chunks want profiling.
            for (int request = 0; request < 5; request++) {
                if (service.tryConsume()) {
                    granted++;
                }
            }
            service.advanceTick();
        }

        assertEquals(20, granted,
            "100 requests across 20 ticks must yield exactly 20 scans - one per tick, no "
                + "bursting and no starvation");
    }

    @Test
    @DisplayName("#86: the budget tracks ticks, not calls, so a quiet period does not bank scans")
    void testBudgetDoesNotAccumulate() {
        FakeClockService service = new FakeClockService();

        // Ten quiet ticks where nothing asks to be profiled.
        for (int i = 0; i < 10; i++) {
            service.advanceTick();
        }

        assertTrue(service.tryConsume(), "the current tick's budget is available");
        assertFalse(service.tryConsume(),
            "unused budget from quiet ticks must not accumulate into a burst - a player "
                + "arriving at a new frontier would trigger the whole backlog at once");
    }
}
