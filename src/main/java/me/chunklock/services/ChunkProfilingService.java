package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.calculation.OwnedChunkScanner;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Captures what a chunk contains, on the main thread, while the chunk is loaded (#86).
 *
 * <h3>Why this exists as its own service</h3>
 *
 * <p>#86 needs a chunk's price to describe that chunk. The scan that produces it cannot run
 * where pricing runs: {@link AsyncCostCalculationService} calls
 * {@code EconomyManager.calculateRequirement} from {@code runTaskAsynchronously}, and reading
 * block data off the main thread is not safe in Paper. {@code getChunkAt} from an async thread
 * can also force a synchronous load, which is the performance problem
 * {@code OwnedChunkScanner.scanChunk} avoids by skipping unloaded chunks entirely.</p>
 *
 * <p>So profiling is decoupled from pricing: capture here, on the main thread, when a chunk is
 * already loaded; store it via {@link ChunkProfileStore}; and let pricing read the stored
 * profile whenever it runs and on whatever thread.</p>
 *
 * <h3>The cost of scanning, and why it is throttled</h3>
 *
 * <p>A full-height scan of a chunk reads {@code 8 x 8 x worldHeight} blocks - around 24,000
 * on a standard 1.20 world. That is far too expensive to do per movement event, so this class
 * owns the policy that keeps it rare:</p>
 *
 * <ul>
 *   <li><strong>Once per chunk, ever.</strong> A stored profile is never recomputed unless
 *       something explicitly asks. Terrain barely changes, and a chunk locked to a player
 *       changes not at all.</li>
 *   <li><strong>An in-memory set of already-profiled chunks</strong>, so the common case
 *       costs a hash lookup rather than a database round trip.</li>
 *   <li><strong>A budget per tick.</strong> Even when many chunks want profiling - a player
 *       running along a frontier - only {@value #MAX_SCANS_PER_TICK} happens per tick.</li>
 * </ul>
 *
 * <p>The budget matters more than it looks: #74 was a memory exhaustion bug, so this project
 * has already paid for treating per-chunk work as something that must be bounded rather than
 * assumed cheap.</p>
 */
public class ChunkProfilingService {

    /**
     * How many chunks may be scanned in a single tick.
     *
     * <p>One. A scan is ~24,000 block reads, and the work is never urgent - a chunk that goes
     * unprofiled this tick is profiled on the next crossing, and pricing falls back to
     * owned-chunk behaviour meanwhile. Nothing is broken by being slow here, and a tick spike
     * on a paid product is a support ticket.</p>
     */
    static final int MAX_SCANS_PER_TICK = 1;

    /** Sample every Nth block horizontally, matching OwnedChunkScanner's grid. */
    private static final int HORIZONTAL_STEP = 2;

    /** Each sampled column stands for this many real columns (HORIZONTAL_STEP squared). */
    private static final int SAMPLE_WEIGHT = HORIZONTAL_STEP * HORIZONTAL_STEP;

    private final ChunklockPlugin plugin;
    private final ChunkProfileStore profileStore;

    /**
     * Chunks known to be profiled already, so the hot path avoids the database.
     *
     * <p>Bounded implicitly by how many chunks a server actually visits, and only holds short
     * keys. It is a cache of "yes", never of "no" - a miss falls through to the store rather
     * than concluding a chunk is unprofiled.</p>
     */
    private final Set<String> knownProfiled = ConcurrentHashMap.newKeySet();

    private long currentTick = -1L;
    private int scansThisTick = 0;

    public ChunkProfilingService(ChunklockPlugin plugin, ChunkProfileStore profileStore) {
        this.plugin = plugin;
        this.profileStore = profileStore;
    }

    /**
     * Profile this chunk if it has not been profiled and the tick budget allows.
     *
     * <p>Safe to call from any main-thread event as often as it likes - the throttling is this
     * method's job, not the caller's. Does nothing at all if the chunk is not loaded, so a
     * caller never forces a chunk load.</p>
     *
     * @return true if a scan actually ran
     */
    public boolean profileIfNeeded(Chunk chunk) {
        if (chunk == null) {
            return false;
        }

        String key = key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (knownProfiled.contains(key)) {
            return false;
        }

        // Never force a load. An unloaded chunk simply waits for a visit that loads it.
        if (!chunk.getWorld().isChunkLoaded(chunk.getX(), chunk.getZ())) {
            return false;
        }

        if (!consumeTickBudget()) {
            return false;
        }

        // Database check only after the cheap guards, since it is the expensive one.
        if (profileStore.hasProfile(chunk.getWorld().getName(), chunk.getX(), chunk.getZ())) {
            knownProfiled.add(key);
            return false;
        }

        return profileNow(chunk);
    }

    /**
     * Scan and store this chunk unconditionally, ignoring the tick budget and any existing
     * profile. For commands and for re-profiling after terrain changes.
     *
     * @return true if the profile was stored
     */
    public boolean profileNow(Chunk chunk) {
        if (chunk == null) {
            return false;
        }

        try {
            List<ChunkProfileStore.ProfileEntry> entries = scan(chunk);
            if (entries.isEmpty()) {
                // A chunk with nothing harvestable - all air, or all water. Storing an empty
                // profile would be indistinguishable from "never profiled", and it should not
                // contribute an all-zero sample to the baseline either.
                plugin.getLogger().fine("No harvestable materials in chunk "
                    + chunk.getX() + "," + chunk.getZ() + " - not profiling");
                return false;
            }

            boolean stored = profileStore.storeProfile(
                chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), entries);

            if (stored) {
                knownProfiled.add(key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
                plugin.getLogger().fine("Profiled chunk " + chunk.getX() + "," + chunk.getZ()
                    + " - " + entries.size() + " materials");
            }
            return stored;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to profile chunk "
                + chunk.getX() + "," + chunk.getZ(), e);
            return false;
        }
    }

    /**
     * Count the harvestable blocks in one chunk.
     *
     * <p>Mirrors {@code OwnedChunkScanner.scanChunkBlocks}: same sampling grid, same
     * multiplier to compensate for it, same harvestable filter and tier lookup. Kept separate
     * rather than shared because that method accumulates into a caller's map across many
     * chunks, which is the aggregation #86 is moving away from.</p>
     *
     * <p><strong>Main thread only.</strong> This reads block state directly.</p>
     */
    private List<ChunkProfileStore.ProfileEntry> scan(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        Map<Material, Integer> counts = new HashMap<>();

        for (int x = 0; x < 16; x += HORIZONTAL_STEP) {
            for (int z = 0; z < 16; z += HORIZONTAL_STEP) {
                int worldX = chunk.getX() * 16 + x;
                int worldZ = chunk.getZ() * 16 + z;

                for (int y = maxY - 1; y >= minY; y--) {
                    Block block = world.getBlockAt(worldX, y, worldZ);
                    Material mat = block.getType();

                    if (mat.isAir()) {
                        continue;
                    }
                    if (OwnedChunkScanner.isHarvestable(mat)) {
                        counts.merge(mat, SAMPLE_WEIGHT, Integer::sum);
                    }
                }
            }
        }

        List<ChunkProfileStore.ProfileEntry> entries = new ArrayList<>(counts.size());
        counts.forEach((material, count) -> entries.add(new ChunkProfileStore.ProfileEntry(
            material, count, OwnedChunkScanner.getMaterialTier(material))));
        return entries;
    }

    /**
     * Whether this tick still has scan budget, consuming one if so.
     *
     * <p>Uses the server's full tick count rather than wall-clock time so the budget tracks
     * actual server work: on a struggling server ticks are longer and scans get correspondingly
     * rarer, which is the behaviour you want from a throttle.</p>
     *
     * <p>Package-private rather than private so {@code ChunkProfilingThrottleTest} can drive
     * the budget without a running server. The alternative - asserting this through
     * {@link #profileIfNeeded} - needs live {@code Chunk} objects, which would leave the
     * throttle untested.</p>
     */
    boolean consumeTickBudget() {
        long tick = currentTick();
        if (tick != currentTick) {
            currentTick = tick;
            scansThisTick = 0;
        }
        if (scansThisTick >= MAX_SCANS_PER_TICK) {
            return false;
        }
        scansThisTick++;
        return true;
    }

    /** Overridable for tests, which have no running server to ask. */
    long currentTick() {
        try {
            return plugin.getServer().getCurrentTick();
        } catch (Exception e) {
            // Older APIs, or no server. Fall back to coarse time so throttling still applies.
            return System.currentTimeMillis() / 50L;
        }
    }

    /** Forget that a chunk was profiled, so the next visit re-scans it. */
    public void invalidate(String worldName, int chunkX, int chunkZ) {
        knownProfiled.remove(key(worldName, chunkX, chunkZ));
    }

    /** How many chunks this session has confirmed as profiled. Diagnostics. */
    public int getKnownProfiledCount() {
        return knownProfiled.size();
    }

    private static String key(String worldName, int chunkX, int chunkZ) {
        return worldName + ":" + chunkX + "," + chunkZ;
    }
}
