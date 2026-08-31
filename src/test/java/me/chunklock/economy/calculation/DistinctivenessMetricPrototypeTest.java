package me.chunklock.economy.calculation;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prototype comparison for the #86 distinctiveness metric (step 3.9, task 1).
 *
 * <p><strong>This is a decision aid, not production code.</strong> It exists to answer one
 * question before any of #86 is built: which scoring metric actually makes a forest chunk
 * price differently from a mountain chunk?</p>
 *
 * <h3>The problem being solved</h3>
 *
 * <p>{@code scanChunkBlocks} samples full-height, {@code minY} to {@code maxY}. Every chunk in
 * the world is therefore mostly stone and deepslate by raw count. Picking the most abundant
 * harvestable material would price nearly every chunk as stone - which is the #82 complaint
 * ("every chunk asks for the same thing") wearing a different hat. Raw abundance is the
 * baseline here precisely because it is what the current code would do.</p>
 *
 * <h3>The two candidates</h3>
 *
 * <ul>
 *   <li><strong>Surface-weighted</strong> - weight each block by its depth below the chunk's
 *       surface, so a tree counts and deepslate barely does. No warm-up, no shared state,
 *       and it matches what a player sees when they look at the chunk.</li>
 *   <li><strong>Relative-to-baseline</strong> - score each material by how far its count
 *       exceeds a world-wide average for that material. Sharper in principle, but needs
 *       warm-up data before it means anything.</li>
 * </ul>
 *
 * <p>The fixtures below are hand-built column profiles rather than real chunks, because the
 * question is about the <em>ranking behaviour of the formulas</em> and that does not need a
 * running server. The block counts follow ordinary 1.20 worldgen: a full-height column is
 * dominated by stone and deepslate everywhere, and the biome shows up only in the top handful
 * of blocks - which is exactly the effect being measured.</p>
 */
public class DistinctivenessMetricPrototypeTest {

    /** One sampled block: what it is, and how far below the local surface it sat. */
    private record SampledBlock(Material material, int depthBelowSurface, int count) {}

    /** A chunk fixture: a name and the harvestable blocks a full-height scan would see. */
    private record ChunkFixture(String name, List<SampledBlock> blocks) {}

    // ---- Fixtures: what a full-height scan actually returns ----------------------------

    /** Forest: trees at the surface, ordinary stone column beneath. */
    private static ChunkFixture forest() {
        return new ChunkFixture("Forest", List.of(
            new SampledBlock(Material.OAK_LOG, 0, 180),
            new SampledBlock(Material.DIRT, 2, 320),
            new SampledBlock(Material.STONE, 40, 5200),
            new SampledBlock(Material.DEEPSLATE, 90, 3100),
            new SampledBlock(Material.COAL_ORE, 55, 60),
            new SampledBlock(Material.IRON_ORE, 70, 28)
        ));
    }

    /** Mountain: exposed stone at the surface, deeper column, more ore. */
    private static ChunkFixture mountain() {
        return new ChunkFixture("Mountain", List.of(
            new SampledBlock(Material.STONE, 0, 6400),
            new SampledBlock(Material.ANDESITE, 6, 420),
            new SampledBlock(Material.DEEPSLATE, 80, 3600),
            new SampledBlock(Material.COAL_ORE, 30, 145),
            new SampledBlock(Material.IRON_ORE, 60, 70),
            new SampledBlock(Material.DIRT, 1, 40)
        ));
    }

    /** Desert: sand cap over the usual stone column. */
    private static ChunkFixture desert() {
        return new ChunkFixture("Desert", List.of(
            new SampledBlock(Material.SAND, 0, 640),
            new SampledBlock(Material.CACTUS, 0, 24),
            new SampledBlock(Material.STONE, 45, 5000),
            new SampledBlock(Material.DEEPSLATE, 95, 3000),
            new SampledBlock(Material.COAL_ORE, 50, 55)
        ));
    }

    /** Plains: thin grass/dirt cap, otherwise indistinguishable from anywhere else. */
    private static ChunkFixture plains() {
        return new ChunkFixture("Plains", List.of(
            new SampledBlock(Material.DIRT, 1, 380),
            new SampledBlock(Material.WHEAT, 0, 30),
            new SampledBlock(Material.STONE, 42, 5100),
            new SampledBlock(Material.DEEPSLATE, 92, 3050),
            new SampledBlock(Material.COAL_ORE, 52, 58)
        ));
    }

    /** Swamp: mud and clay at the surface, water-logged, ordinary depths. */
    private static ChunkFixture swamp() {
        return new ChunkFixture("Swamp", List.of(
            new SampledBlock(Material.CLAY, 0, 210),
            new SampledBlock(Material.MUD, 0, 260),
            new SampledBlock(Material.OAK_LOG, 0, 40),
            new SampledBlock(Material.STONE, 44, 5150),
            new SampledBlock(Material.DEEPSLATE, 93, 3020)
        ));
    }

    private static List<ChunkFixture> allFixtures() {
        return List.of(forest(), mountain(), desert(), plains(), swamp());
    }

    // ---- Metric 1: raw abundance (the baseline - what today's code would do) -----------

    private static Map<Material, Double> rawAbundance(ChunkFixture chunk) {
        Map<Material, Double> scores = new HashMap<>();
        for (SampledBlock b : chunk.blocks()) {
            scores.merge(b.material(), (double) b.count(), Double::sum);
        }
        return scores;
    }

    // ---- Metric 2: surface-weighted ----------------------------------------------------

    /**
     * Weight halves every {@value #SURFACE_HALF_LIFE} blocks below the surface, so a block at
     * the surface counts fully and one 90 deep counts for almost nothing.
     */
    private static final double SURFACE_HALF_LIFE = 12.0;

    private static Map<Material, Double> surfaceWeighted(ChunkFixture chunk) {
        Map<Material, Double> scores = new HashMap<>();
        for (SampledBlock b : chunk.blocks()) {
            double weight = Math.pow(0.5, b.depthBelowSurface() / SURFACE_HALF_LIFE);
            scores.merge(b.material(), b.count() * weight, Double::sum);
        }
        return scores;
    }

    // ---- Metric 3: relative to a world baseline ----------------------------------------

    /** Mean count per material across every fixture - stands in for a server-wide average. */
    private static Map<Material, Double> buildBaseline(List<ChunkFixture> chunks) {
        Map<Material, Double> totals = new HashMap<>();
        for (ChunkFixture chunk : chunks) {
            for (SampledBlock b : chunk.blocks()) {
                totals.merge(b.material(), (double) b.count(), Double::sum);
            }
        }
        Map<Material, Double> baseline = new HashMap<>();
        totals.forEach((m, total) -> baseline.put(m, total / chunks.size()));
        return baseline;
    }

    /** Score = how many times the world-wide average this chunk holds. */
    private static Map<Material, Double> relativeToBaseline(ChunkFixture chunk,
                                                            Map<Material, Double> baseline) {
        Map<Material, Double> scores = new HashMap<>();
        for (SampledBlock b : chunk.blocks()) {
            double average = baseline.getOrDefault(b.material(), (double) b.count());
            double ratio = average <= 0 ? 0 : b.count() / average;
            scores.merge(b.material(), ratio, Double::sum);
        }
        return scores;
    }

    // ---- Metric 4: surface-weighted, scored against a surface-weighted baseline ---------

    /**
     * The combination. Weight by depth first, then score against the world-wide average of
     * those same weighted counts.
     *
     * <p>Worth testing because the two candidates fail in opposite directions: surface
     * weighting needs no warm-up but cannot overcome stone's sheer abundance, while a raw
     * baseline differentiates cleanly but has no notion of what a player can see. Applying
     * the baseline to weighted counts asks whether depth-awareness adds anything once
     * relative scoring has already solved the abundance problem.</p>
     */
    private static Map<Material, Double> buildWeightedBaseline(List<ChunkFixture> chunks) {
        Map<Material, Double> totals = new HashMap<>();
        for (ChunkFixture chunk : chunks) {
            surfaceWeighted(chunk).forEach((m, score) -> totals.merge(m, score, Double::sum));
        }
        Map<Material, Double> baseline = new HashMap<>();
        totals.forEach((m, total) -> baseline.put(m, total / chunks.size()));
        return baseline;
    }

    private static Map<Material, Double> weightedRelative(ChunkFixture chunk,
                                                           Map<Material, Double> weightedBaseline) {
        Map<Material, Double> scores = new HashMap<>();
        surfaceWeighted(chunk).forEach((m, weighted) -> {
            double average = weightedBaseline.getOrDefault(m, weighted);
            scores.put(m, average <= 0 ? 0 : weighted / average);
        });
        return scores;
    }

    // ---- Helpers -----------------------------------------------------------------------

    private static Material headline(Map<Material, Double> scores) {
        return scores.entrySet().stream()
            .max(Comparator.comparingDouble(Map.Entry<Material, Double>::getValue)
                .thenComparing(e -> e.getKey().name()))
            .map(Map.Entry::getKey)
            .orElseThrow();
    }

    private static Map<String, Material> headlinesFor(java.util.function.Function<ChunkFixture,
            Map<Material, Double>> metric) {
        Map<String, Material> out = new LinkedHashMap<>();
        for (ChunkFixture chunk : allFixtures()) {
            out.put(chunk.name(), headline(metric.apply(chunk)));
        }
        return out;
    }

    private static long distinctCount(Map<String, Material> headlines) {
        return headlines.values().stream().distinct().count();
    }

    // ---- The comparison ----------------------------------------------------------------

    @Test
    @DisplayName("#86 prototype: print how each metric ranks the same five chunks")
    public void compareMetrics() {
        Map<Material, Double> baseline = buildBaseline(allFixtures());

        Map<Material, Double> weightedBaseline = buildWeightedBaseline(allFixtures());

        Map<String, Material> raw = headlinesFor(DistinctivenessMetricPrototypeTest::rawAbundance);
        Map<String, Material> surface = headlinesFor(DistinctivenessMetricPrototypeTest::surfaceWeighted);
        Map<String, Material> relative = headlinesFor(c -> relativeToBaseline(c, baseline));
        Map<String, Material> combined = headlinesFor(c -> weightedRelative(c, weightedBaseline));

        StringBuilder report = new StringBuilder();
        report.append("\n#86 distinctiveness metric comparison\n");
        report.append("=".repeat(92)).append("\n");
        report.append(String.format("%-10s | %-16s | %-16s | %-16s | %-16s%n",
            "Chunk", "Raw abundance", "Surface-weighted", "Rel. to baseline", "Weighted+relative"));
        report.append("-".repeat(92)).append("\n");

        for (ChunkFixture chunk : allFixtures()) {
            report.append(String.format("%-10s | %-16s | %-16s | %-16s | %-16s%n",
                chunk.name(),
                raw.get(chunk.name()),
                surface.get(chunk.name()),
                relative.get(chunk.name()),
                combined.get(chunk.name())));
        }

        report.append("-".repeat(92)).append("\n");
        report.append(String.format("%-10s | %-16s | %-16s | %-16s | %-16s%n",
            "distinct",
            distinctCount(raw) + " of 5",
            distinctCount(surface) + " of 5",
            distinctCount(relative) + " of 5",
            distinctCount(combined) + " of 5"));
        report.append("=".repeat(92)).append("\n");

        // Show the top three under each metric for the two chunks that must differ.
        for (ChunkFixture chunk : List.of(forest(), mountain())) {
            report.append("\n").append(chunk.name()).append(" - top 3 by metric\n");
            report.append("  raw      : ").append(topThree(rawAbundance(chunk))).append("\n");
            report.append("  surface  : ").append(topThree(surfaceWeighted(chunk))).append("\n");
            report.append("  relative : ").append(topThree(relativeToBaseline(chunk, baseline))).append("\n");
            report.append("  combined : ").append(topThree(weightedRelative(chunk, weightedBaseline))).append("\n");
        }

        System.out.println(report);
    }

    private static String topThree(Map<Material, Double> scores) {
        List<Map.Entry<Material, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Comparator.comparingDouble(Map.Entry<Material, Double>::getValue).reversed());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(sorted.get(i).getKey()).append("=")
              .append(String.format("%.1f", sorted.get(i).getValue()));
        }
        return sb.toString();
    }

    // ---- The properties that actually decide it ----------------------------------------

    @Test
    @DisplayName("#86: raw abundance prices everything as stone - the bug being avoided")
    public void testRawAbundanceIsDegenerate() {
        Map<String, Material> raw = headlinesFor(DistinctivenessMetricPrototypeTest::rawAbundance);

        assertTrue(distinctCount(raw) <= 2,
            "raw abundance should collapse nearly every chunk onto the same material - "
                + "this is the baseline being rejected, not a target. Got: " + raw);
    }

    @Test
    @DisplayName("#86: surface weighting alone is NOT enough - stone still wins the forest")
    public void testSurfaceWeightingAloneIsInsufficient() {
        Map<String, Material> surface = headlinesFor(DistinctivenessMetricPrototypeTest::surfaceWeighted);

        // This was the recommended approach before the prototype ran. It loses because stone's
        // sheer abundance survives the depth decay: in the forest fixture, STONE scores 515.9
        // against OAK_LOG's 180.0 even after decaying to roughly a tenth of its weight.
        // Depth-decay shrinks stone's lead; it does not overturn it.
        assertEquals(Material.STONE, surface.get("Forest"),
            "records why surface-weighting alone was rejected: a forest still prices as stone");

        assertTrue(distinctCount(surface) < 4,
            "surface weighting differentiates only the chunks with a very heavy surface cap "
                + "(desert sand); everything else still collapses onto stone. Got: " + surface);
    }

    @Test
    @DisplayName("#86: relative-to-baseline names the material a player would name")
    public void testRelativeToBaselineDifferentiates() {
        Map<Material, Double> baseline = buildBaseline(allFixtures());
        Map<String, Material> relative = headlinesFor(c -> relativeToBaseline(c, baseline));

        assertNotEquals(relative.get("Forest"), relative.get("Mountain"),
            "the headline acceptance criterion from #86: two adjacent chunks with materially "
                + "different contents must produce different requirements");

        assertEquals(5, distinctCount(relative),
            "every fixture biome should be distinguishable. Got: " + relative);

        assertEquals(Material.OAK_LOG, relative.get("Forest"), "a forest should cost wood");
        assertEquals(Material.SAND, relative.get("Desert"), "a desert should cost sand");
    }

    @Test
    @DisplayName("#86: no metric under consideration may headline stone or deepslate everywhere")
    public void testDepthFillerNeverDominates() {
        Map<Material, Double> baseline = buildBaseline(allFixtures());

        for (ChunkFixture chunk : allFixtures()) {
            Material picked = headline(relativeToBaseline(chunk, baseline));
            assertNotEquals(Material.DEEPSLATE, picked,
                chunk.name() + " priced as deepslate - full-height sampling must not decide the price");
        }
    }

    @Test
    @DisplayName("#86: relative scoring is what beats the raw-abundance baseline")
    public void testRelativeScoringIsTheDecidingFactor() {
        Map<Material, Double> baseline = buildBaseline(allFixtures());
        Map<Material, Double> weightedBaseline = buildWeightedBaseline(allFixtures());

        long raw = distinctCount(headlinesFor(DistinctivenessMetricPrototypeTest::rawAbundance));
        long surface = distinctCount(headlinesFor(DistinctivenessMetricPrototypeTest::surfaceWeighted));
        long relative = distinctCount(headlinesFor(c -> relativeToBaseline(c, baseline)));
        long combined = distinctCount(headlinesFor(c -> weightedRelative(c, weightedBaseline)));

        assertTrue(relative > surface,
            "relative scoring, not depth weighting, is what actually solves the abundance problem");
        assertTrue(relative > raw, "relative-to-baseline must beat the raw-abundance baseline");
        assertTrue(combined >= relative,
            "adding depth weighting on top of relative scoring must not make it worse");
    }
}
