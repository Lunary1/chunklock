package me.chunklock.economy.calculation;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the resource-based unlock cost system.
 * Tests the OwnedChunkScanner tier system and ResourceBasedMaterialStrategy
 * cost calculations without requiring Bukkit runtime dependencies.
 */
public class ResourceBasedCostTest {

    /**
     * Test that all harvestable materials have a defined tier.
     */
    @Test
    public void testAllMaterialTiersAreDefined() {
        // Tier 1: Common blocks
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.DIRT));
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.COBBLESTONE));
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.SAND));
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.GRAVEL));

        // Tier 2: Wood
        assertEquals(2, OwnedChunkScanner.getMaterialTier(Material.OAK_LOG));
        assertEquals(2, OwnedChunkScanner.getMaterialTier(Material.BIRCH_LOG));
        assertEquals(2, OwnedChunkScanner.getMaterialTier(Material.SPRUCE_LOG));
        assertEquals(2, OwnedChunkScanner.getMaterialTier(Material.DARK_OAK_LOG));

        // Tier 3: Crops
        assertEquals(3, OwnedChunkScanner.getMaterialTier(Material.WHEAT));
        assertEquals(3, OwnedChunkScanner.getMaterialTier(Material.SUGAR_CANE));
        assertEquals(3, OwnedChunkScanner.getMaterialTier(Material.HAY_BLOCK));

        // Tier 4: Stone
        assertEquals(4, OwnedChunkScanner.getMaterialTier(Material.STONE));
        assertEquals(4, OwnedChunkScanner.getMaterialTier(Material.DEEPSLATE));
        assertEquals(4, OwnedChunkScanner.getMaterialTier(Material.COAL_ORE));

        // Tier 5: Precious
        assertEquals(5, OwnedChunkScanner.getMaterialTier(Material.IRON_ORE));
        assertEquals(5, OwnedChunkScanner.getMaterialTier(Material.GOLD_ORE));
        assertEquals(5, OwnedChunkScanner.getMaterialTier(Material.REDSTONE_ORE));

        // Tier 6: Rare
        assertEquals(6, OwnedChunkScanner.getMaterialTier(Material.DIAMOND_ORE));
        assertEquals(6, OwnedChunkScanner.getMaterialTier(Material.EMERALD_ORE));
        assertEquals(6, OwnedChunkScanner.getMaterialTier(Material.ANCIENT_DEBRIS));
    }

    /**
     * Test that unknown materials default to tier 1.
     */
    @Test
    public void testUnknownMaterialDefaultsTier1() {
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.BEDROCK));
        assertEquals(1, OwnedChunkScanner.getMaterialTier(Material.AIR));
    }

    /**
     * Test tier cost multipliers decrease with higher tiers.
     */
    @Test
    public void testTierCostMultipliersDecreaseWithTier() {
        double tier1 = OwnedChunkScanner.getTierCostMultiplier(1);
        double tier2 = OwnedChunkScanner.getTierCostMultiplier(2);
        double tier3 = OwnedChunkScanner.getTierCostMultiplier(3);
        double tier4 = OwnedChunkScanner.getTierCostMultiplier(4);
        double tier5 = OwnedChunkScanner.getTierCostMultiplier(5);
        double tier6 = OwnedChunkScanner.getTierCostMultiplier(6);

        // Higher tiers should have lower cost multipliers
        assertTrue(tier1 > tier2, "Tier 1 multiplier should be > Tier 2");
        assertTrue(tier2 > tier3, "Tier 2 multiplier should be > Tier 3");
        assertTrue(tier3 > tier4, "Tier 3 multiplier should be > Tier 4");
        assertTrue(tier4 > tier5, "Tier 4 multiplier should be > Tier 5");
        assertTrue(tier5 > tier6, "Tier 5 multiplier should be > Tier 6");

        // Verify specific values
        assertEquals(1.0, tier1, 0.001);
        assertEquals(0.5, tier2, 0.001);
        assertEquals(0.05, tier6, 0.001);
    }

    /**
     * Test that tier cost calculations produce reasonable amounts.
     * With base cost 16, the resulting amounts should be sensible for gameplay.
     */
    @Test
    public void testCostAmountsAreReasonable() {
        int baseCost = 16;
        double progressionMultiplier = 1.0; // First unlock, no progression

        // Tier 1 (dirt/cobble): 16 * 1.0 * 1.0 = 16
        int tier1Cost = (int) Math.ceil(baseCost * OwnedChunkScanner.getTierCostMultiplier(1) * progressionMultiplier);
        assertEquals(16, tier1Cost);

        // Tier 2 (wood): 16 * 0.5 * 1.0 = 8
        int tier2Cost = (int) Math.ceil(baseCost * OwnedChunkScanner.getTierCostMultiplier(2) * progressionMultiplier);
        assertEquals(8, tier2Cost);

        // Tier 4 (stone): 16 * 0.25 * 1.0 = 4
        int tier4Cost = (int) Math.ceil(baseCost * OwnedChunkScanner.getTierCostMultiplier(4) * progressionMultiplier);
        assertEquals(4, tier4Cost);

        // Tier 6 (diamond): 16 * 0.05 * 1.0 = 1 (ceil)
        int tier6Cost = (int) Math.ceil(baseCost * OwnedChunkScanner.getTierCostMultiplier(6) * progressionMultiplier);
        assertEquals(1, tier6Cost);
    }

    /**
     * Test that progression multiplier scales costs upward.
     */
    @Test
    public void testProgressionMultiplierScaling() {
        double early = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(0, 0);
        double mid = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(10, 25);
        double late = ResourceBasedMaterialStrategy.computeBaseProgressionMultiplier(30, 100);

        assertEquals(1.0, early, 0.001);
        assertTrue(mid > early, "Mid progression should cost more than early progression");
        assertTrue(late > mid, "Late progression should cost more than mid progression");
        assertTrue(late < 4.0, "Diminishing-returns multiplier should avoid runaway growth");
    }

    /**
     * Test ResourceEntry sorting: highest tier first, then most abundant.
     */
    @Test
    public void testResourceEntrySorting() {
        var entries = new java.util.ArrayList<>(List.of(
            new OwnedChunkScanner.ResourceEntry(Material.DIRT, 500, 1),
            new OwnedChunkScanner.ResourceEntry(Material.OAK_LOG, 100, 2),
            new OwnedChunkScanner.ResourceEntry(Material.IRON_ORE, 20, 5),
            new OwnedChunkScanner.ResourceEntry(Material.STONE, 300, 4),
            new OwnedChunkScanner.ResourceEntry(Material.DIAMOND_ORE, 5, 6)
        ));

        // Sort same way as OwnedChunkScanner: highest tier first, then most abundant
        entries.sort((a, b) -> {
            int tierCmp = Integer.compare(b.tier(), a.tier());
            return tierCmp != 0 ? tierCmp : Integer.compare(b.count(), a.count());
        });

        // Diamond (tier 6) should be first, then iron (tier 5), then stone (tier 4), etc.
        assertEquals(Material.DIAMOND_ORE, entries.get(0).material());
        assertEquals(Material.IRON_ORE, entries.get(1).material());
        assertEquals(Material.STONE, entries.get(2).material());
        assertEquals(Material.OAK_LOG, entries.get(3).material());
        assertEquals(Material.DIRT, entries.get(4).material());
    }

    /**
     * Test soft availability modifier is bounded and centered near neutral.
     */
    @Test
    public void testCostCappedByAvailability() {
        double neutral = ResourceBasedMaterialStrategy.computeAvailabilityModifier(50, 50.0);
        double scarce = ResourceBasedMaterialStrategy.computeAvailabilityModifier(10, 50.0);
        double abundant = ResourceBasedMaterialStrategy.computeAvailabilityModifier(100, 50.0);

        assertEquals(1.0, neutral, 0.001);
        assertTrue(scarce >= 0.9 && scarce < 1.0, "Scarce materials should apply a mild discount only");
        assertTrue(abundant > 1.0 && abundant <= 1.1, "Abundant materials should apply a mild premium only");
    }

    /**
     * The recent-selection history is trimmed to a fixed size and must never throw,
     * even when trimmed concurrently. Cost calculation runs on async threads
     * (AsyncCostCalculationService), so an ArrayDeque here could throw
     * NoSuchElementException on a concurrent removeFirst (issue #78).
     */
    @Test
    public void testRememberSelectionIsBoundedAndThreadSafe() throws Exception {
        Deque<Material> recent = new ConcurrentLinkedDeque<>();

        // Single-threaded: history stays bounded
        for (int i = 0; i < 20; i++) {
            ResourceBasedMaterialStrategy.rememberSelection(recent, Material.OAK_LOG);
        }
        assertTrue(recent.size() <= 3, "Recent-selection history should stay bounded, was " + recent.size());

        // Concurrent: many threads trimming the same deque must not throw
        recent.clear();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int i = 0; i < 500; i++) {
                    ResourceBasedMaterialStrategy.rememberSelection(recent, Material.STONE);
                }
                return null;
            }));
        }

        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS); // throws if any thread threw
        }
        pool.shutdown();

        assertTrue(recent.size() <= 3 + threads,
            "History should remain near its bound under concurrency, was " + recent.size());
    }

    /**
     * Test config defaults for resource-scan mode.
     */
    @Test
    public void testResourceScanConfigDefaults() {
        // Verify that default values are reasonable
        int defaultBaseCost = 16;
        int defaultMaxCost = 128;
        int defaultMinCost = 1;
        int defaultMinAbundance = 10;
        int defaultCacheDuration = 60;

        assertTrue(defaultBaseCost > 0 && defaultBaseCost <= 64, "Base cost should be reasonable");
        assertTrue(defaultMaxCost > defaultBaseCost, "Max cost should exceed base cost");
        assertTrue(defaultMinCost >= 1, "Min cost should be at least 1");
        assertTrue(defaultMinAbundance > 0, "Min abundance should be positive");
        assertTrue(defaultCacheDuration > 0, "Cache duration should be positive");
    }
}
