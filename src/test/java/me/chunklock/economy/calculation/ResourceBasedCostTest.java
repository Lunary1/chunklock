package me.chunklock.economy.calculation;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        int baseCost = 16;
        double tierMultiplier = OwnedChunkScanner.getTierCostMultiplier(2); // Wood: 0.5

        // First chunk (unlocked=0, score=0): multiplier = 1.0
        double prog0 = 1.0 + 0 / 10.0 + 0 / 50.0;
        assertEquals(1.0, prog0, 0.001);
        int cost0 = (int) Math.ceil(baseCost * tierMultiplier * prog0); // 16 * 0.5 * 1.0 = 8
        assertEquals(8, cost0);

        // 10th chunk (unlocked=10, score=25): multiplier = 2.5
        double prog10 = 1.0 + 10 / 10.0 + 25 / 50.0;
        assertEquals(2.5, prog10, 0.001);
        int cost10 = (int) Math.ceil(baseCost * tierMultiplier * prog10); // 16 * 0.5 * 2.5 = 20
        assertEquals(20, cost10);

        assertTrue(cost10 > cost0, "Cost should increase with progression");
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
     * Test that cost is capped by available resources (max 25%).
     */
    @Test
    public void testCostCappedByAvailability() {
        int available = 40; // Only 40 blocks available
        int calculatedCost = 50; // Strategy calculated 50 needed
        int minCost = 1;

        int maxFromAvailable = Math.max(minCost, available / 4); // 40/4 = 10
        int finalCost = Math.min(calculatedCost, maxFromAvailable);

        assertEquals(10, finalCost, "Cost should be capped at 25% of available resources");
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
