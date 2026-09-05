package me.chunklock.economy.calculation;

import me.chunklock.services.ChunkProfileStore;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a stored chunk profile into ranked pricing candidates (#86, step 3.9 task 4).
 *
 * <h3>The scoring problem</h3>
 *
 * <p>Sampling is full-height, so every chunk is mostly stone and deepslate by raw count.
 * Ranking candidates by abundance would price nearly every chunk as stone - trading "always
 * gravel" for "always stone", the same bug in a new hat. A material is characteristic of a
 * chunk when the chunk holds <em>more of it than a typical chunk does</em>.</p>
 *
 * <p>So a candidate's score is its count <strong>relative to the world baseline</strong>.
 * {@code DistinctivenessMetricPrototypeTest} compared four metrics against five hand-built
 * biome fixtures before any of this was built:</p>
 *
 * <table>
 *   <caption>Distinct headline materials across five biome fixtures</caption>
 *   <tr><th>Metric</th><th>Distinct</th></tr>
 *   <tr><td>Raw abundance (the old behaviour)</td><td>1 of 5 - everything is STONE</td></tr>
 *   <tr><td>Surface-weighted (the original recommendation)</td><td>2 of 5</td></tr>
 *   <tr><td><strong>Relative-to-baseline</strong></td><td><strong>5 of 5</strong></td></tr>
 *   <tr><td>Weighted + relative</td><td>5 of 5, worse ranking</td></tr>
 * </table>
 *
 * <p>Surface-weighting <em>lost</em>: stone's abundance survives depth decay, so it still
 * headlines a forest. That prototype is kept in the tree so the rejected option does not get
 * quietly relitigated.</p>
 *
 * <h3>Warm-up</h3>
 *
 * <p>A baseline built from a handful of chunks is noise, and scoring against noise produces
 * confident nonsense. {@link #isBaselineReady} gates this: until enough chunks are profiled,
 * callers fall back to owned-chunk pricing, which is a path that has to exist anyway as the
 * last rung of the #69 obtainability ladder.</p>
 *
 * <p>Deliberately not seeded with defaults from the fixtures. Those come from five hand-built
 * test chunks, so an unusual world - a mushroom island, an amplified world - would price
 * against forest-ish assumptions with nothing saying so. Falling back is honest about knowing
 * nothing yet, and self-corrects as the world is explored.</p>
 *
 * <h3>Purity</h3>
 *
 * <p>Every method here is a pure query. That is the #82 fix and it must stay true: pricing is
 * reached from display paths - opening the GUI, rendering a hologram, pre-calculating adjacent
 * chunks - so anything that mutates state here would make merely <em>looking</em> at a chunk
 * change its price.</p>
 */
public final class TargetChunkCandidateSource {

    /**
     * How many chunks must be profiled before the baseline is trusted.
     *
     * <p>Small enough that a single-player world crosses it in a session - profiling captures
     * the entered chunk plus four neighbours, so this is roughly ten chunk crossings - and
     * large enough that the baseline is not one biome's opinion. Below this, pricing falls
     * back rather than guessing.</p>
     */
    public static final int MIN_BASELINE_CHUNKS = 50;

    /**
     * Baseline value assumed for a material the baseline has never seen.
     *
     * <p>Not zero, which would divide by zero, and not one, which would make the first
     * sighting of any material look infinitely distinctive and headline every price. One full
     * chunk-worth of "unremarkable" keeps a never-before-seen material scoring as ordinary
     * until there is evidence either way.</p>
     */
    private static final double UNSEEN_BASELINE = 64.0;

    /**
     * How far above the world average a material must sit to be called characteristic.
     *
     * <p>Distinctiveness of 1.0 is exactly average, so without a margin the ranking always
     * names a winner even when a chunk has no character at all - and on a featureless chunk
     * that winner is decided by noise. Found in the September 5 play-test: a plains chunk with
     * nothing on the surface priced as <strong>deepslate at 1.09</strong>, i.e. 9% above
     * average, because it sat slightly lower than usual.</p>
     *
     * <p>1.3 asks for a material the chunk holds roughly a third more of than normal. Below
     * that, {@link #hasDistinctiveMaterial} reports the chunk has no opinion and pricing falls
     * back to the owned-chunk rung, which is honest rather than inventing a headline.</p>
     */
    public static final double DISTINCTIVENESS_MARGIN = 1.3;

    /**
     * Bulk stone that fills the depth of every chunk regardless of what the chunk is.
     *
     * <p>These are not evidence of character. Sampling is full-height, so a chunk holds
     * thousands of them and the count swings with terrain elevation alone - which gives them
     * far more room to drift above average than a surface material with a few hundred blocks.
     * That is how deepslate came to headline a featureless plains chunk while dirt, the one
     * material actually on its surface, scored below average.</p>
     *
     * <p>They are <em>not</em> excluded, because a chunk genuinely made of stone should be
     * able to say so - a mountainside or an exposed deepslate pocket is real character.
     * {@link #FILLER_PENALTY} raises the bar they have to clear instead.</p>
     */
    private static final Set<Material> DEPTH_FILLER = Set.of(
        Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.NETHERRACK);

    /**
     * Extra margin a depth-filler material must clear on top of {@link #DISTINCTIVENESS_MARGIN}.
     *
     * <p>Set so filler needs to be roughly twice the world average before it can headline a
     * chunk. A 9% elevation wobble cannot reach that; a chunk that is genuinely mostly exposed
     * stone can.</p>
     */
    private static final double FILLER_PENALTY = 0.65;

    private TargetChunkCandidateSource() {
    }

    /** Whether enough chunks have been profiled for relative scoring to mean anything. */
    public static boolean isBaselineReady(long profiledChunks) {
        return profiledChunks >= MIN_BASELINE_CHUNKS;
    }

    /**
     * Whether any candidate is distinctive enough to describe the chunk.
     *
     * <p>The ranking always produces an order, but an order is not an opinion: on a chunk with
     * no character every score hovers around 1.0 and the top entry wins by noise. This is the
     * question the metric could not previously answer - <em>is this chunk distinctive at
     * all?</em> - and answering "no" honestly is what stops a featureless plains chunk being
     * priced in deepslate because it happened to sit 9% lower than average.</p>
     *
     * <p>Callers treat false as "fall back to the owned-chunk rung", the same as an unprofiled
     * chunk or a cold baseline. Asking a player for an ordinary material they can actually get
     * is better than inventing a headline the chunk does not have.</p>
     *
     * @param ranked candidates from {@link #rank}, already tier-filtered by the caller
     */
    public static boolean hasDistinctiveMaterial(List<ScoredCandidate> ranked) {
        return !ranked.isEmpty() && ranked.get(0).headlineScore() >= DISTINCTIVENESS_MARGIN;
    }

    /**
     * Rank a chunk's profile by how distinctive each material is for that chunk.
     *
     * <p>Returned in descending distinctiveness, so the head is the material a player would
     * name looking at the chunk: forest wood, mountain andesite, desert sand.</p>
     *
     * <p>Tier is carried straight through from the profile so the caller can apply the same
     * progression cap and cost multipliers it applies to owned-chunk candidates - the two
     * candidate sources stay interchangeable by design, which is what lets the existing
     * selection, amount and re-roll logic work unchanged.</p>
     *
     * @param profile  what the scan found in this chunk; empty means never profiled
     * @param baseline world-wide mean block count per material
     */
    public static List<ScoredCandidate> rank(List<ChunkProfileStore.ProfileEntry> profile,
                                             Map<Material, Double> baseline) {
        if (profile == null || profile.isEmpty()) {
            return List.of();
        }

        List<ScoredCandidate> scored = new ArrayList<>(profile.size());
        for (ChunkProfileStore.ProfileEntry entry : profile) {
            if (entry.blockCount() <= 0) {
                continue;
            }
            scored.add(new ScoredCandidate(
                entry.material(),
                entry.blockCount(),
                entry.tier(),
                distinctiveness(entry, baseline)));
        }

        // Ranked by headline score, not raw distinctiveness, so bulk depth stone cannot take
        // the top slot on an elevation wobble. Ties broken by material name so the ranking is
        // total and deterministic - #82 was a query method that returned different answers on
        // identical input, and an unstable sort here would reintroduce exactly that.
        scored.sort(Comparator
            .comparingDouble(ScoredCandidate::headlineScore).reversed()
            .thenComparing(candidate -> candidate.material().name()));

        return scored;
    }

    /**
     * How much more of this material the chunk holds than a typical chunk does.
     *
     * <p>1.0 means "exactly average". Above 1.0 is characteristic of this chunk. This is the
     * whole metric: it is what stops stone headlining every price despite being the most
     * abundant block almost everywhere.</p>
     */
    private static double distinctiveness(ChunkProfileStore.ProfileEntry entry,
                                          Map<Material, Double> baseline) {
        double typical = baseline == null
            ? UNSEEN_BASELINE
            : baseline.getOrDefault(entry.material(), UNSEEN_BASELINE);

        if (typical <= 0.0) {
            typical = UNSEEN_BASELINE;
        }
        return entry.blockCount() / typical;
    }

    /**
     * One ranked candidate from a chunk's own contents.
     *
     * @param distinctiveness count relative to the world baseline; 1.0 is exactly average
     */
    public record ScoredCandidate(Material material, int count, int tier, double distinctiveness) {

        /** As the candidate type the rest of the pricing pipeline already speaks. */
        public OwnedChunkScanner.ResourceEntry toResourceEntry() {
            return new OwnedChunkScanner.ResourceEntry(material, count, tier);
        }

        /**
         * Distinctiveness adjusted for how much this material's abundance actually means.
         *
         * <p>Kept separate from {@link #distinctiveness()}, which stays a plain ratio to the
         * world average where 1.0 means exactly average - that is what it is documented as and
         * what amount calculation reads. This is the ordering key, and the difference between
         * the two is deliberate: <em>how notable a material is</em> and <em>how much it should
         * be trusted to describe a chunk</em> are different questions.</p>
         *
         * <p>Depth filler is discounted because full-height sampling gives it thousands of
         * blocks per chunk, so it drifts above average on terrain elevation alone. A surface
         * material with a few hundred blocks cannot swing nearly as far, and is genuine
         * evidence of what the chunk is.</p>
         */
        public double headlineScore() {
            return DEPTH_FILLER.contains(material)
                ? distinctiveness * FILLER_PENALTY
                : distinctiveness;
        }
    }
}
