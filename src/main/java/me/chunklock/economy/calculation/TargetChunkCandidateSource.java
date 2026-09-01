package me.chunklock.economy.calculation;

import me.chunklock.services.ChunkProfileStore;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    private TargetChunkCandidateSource() {
    }

    /** Whether enough chunks have been profiled for relative scoring to mean anything. */
    public static boolean isBaselineReady(long profiledChunks) {
        return profiledChunks >= MIN_BASELINE_CHUNKS;
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

        // Ties broken by material name so the ranking is total and deterministic. #82 was a
        // query method that returned different answers on identical input; an unstable sort
        // here would reintroduce exactly that, intermittently.
        scored.sort(Comparator
            .comparingDouble(ScoredCandidate::distinctiveness).reversed()
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
    }
}
