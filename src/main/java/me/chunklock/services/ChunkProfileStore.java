package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Storage for what a scan found in a single chunk, and the world-wide baseline that makes
 * those findings meaningful (#86).
 *
 * <h3>Why this is stored at all</h3>
 *
 * <p>A chunk's price should describe the chunk being bought. The scan that produces it cannot
 * run where pricing runs today: {@code AsyncCostCalculationService} calls
 * {@code calculateRequirement} from an async task, and reading block data off the main thread
 * is not safe in Paper. So the scan is decoupled - profile a chunk on the main thread while it
 * is loaded, store the result, and let pricing read the stored profile.</p>
 *
 * <h3>Why a baseline</h3>
 *
 * <p>Sampling is full-height, so every chunk is mostly stone and deepslate by raw count.
 * Picking the most abundant material would price nearly every chunk as stone. A material is
 * characteristic of a chunk when the chunk holds <em>more of it than a typical chunk does</em>,
 * so scoring needs a world-wide average to compare against. A metric prototype
 * ({@code DistinctivenessMetricPrototypeTest}) compared this against depth-weighting and raw
 * abundance: relative-to-baseline distinguished all five test biomes, depth-weighting only
 * one, raw abundance none.</p>
 *
 * <p>The baseline is kept as a running {@code total_count} and {@code chunks_counted} rather
 * than a stored mean, so profiling one chunk updates it without re-reading every profile.</p>
 *
 * <h3>Keying</h3>
 *
 * <p>A profile is per-chunk, <strong>not</strong> per-(player, chunk) - a chunk's contents do
 * not depend on who is looking. That is the one structural difference from
 * {@link ChunkCostDatabase}, which stores a committed price per player.</p>
 */
public class ChunkProfileStore {

    private final ChunklockPlugin plugin;
    private final ChunkCostDatabase database;

    public ChunkProfileStore(ChunklockPlugin plugin, ChunkCostDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * One material found in a chunk, with how many blocks of it the scan counted.
     */
    public record ProfileEntry(Material material, int blockCount, int tier) {}

    /** Pricing storage, shared with {@link ChunkCostDatabase} so both follow database.type. */
    private CostStorageBackend backend() {
        return database.getBackend();
    }

    private SqlDialect dialect() {
        return backend().dialect();
    }

    private boolean unavailable() {
        CostStorageBackend backend = database.getBackend();
        return backend == null || !backend.isAvailable();
    }

    /**
     * Store what a scan found in one chunk, and fold the result into the world baseline.
     *
     * <p>Deliberately synchronous. Callers profile a chunk on the main thread and the result
     * is usually read back immediately to price it - the same race that made
     * {@link ChunkCostDatabase#clearStoredCost} synchronous under #83. A profile scan is
     * infrequent (once per chunk, not once per view), so the cost is paid rarely.</p>
     *
     * <p>Replaces any previous profile for the chunk: stale rows for materials no longer
     * present would otherwise linger and could still headline a price.</p>
     *
     * @return true if the profile was written
     */
    public boolean storeProfile(String worldName, int chunkX, int chunkZ, List<ProfileEntry> entries) {
        if (unavailable()) {
            plugin.getLogger().fine("Pricing storage unavailable, skipping profile store");
            return false;
        }
        if (entries == null || entries.isEmpty()) {
            return false;
        }

        // The upsert keys on unique_chunk_profile, never on the auto-increment id. Keying on
        // the id is what #90 was - every write failing with 90081, undetected for months
        // because only one code path reached it. Both dialects express that; see SqlDialect.
        String upsert = dialect().upsertChunkProfile();

        String deleteStale = """
            DELETE FROM chunk_profiles
            WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?
        """;

        long now = System.currentTimeMillis();

        try (CostStorageBackend.BorrowedConnection borrowed = backend().borrow()) {
            Connection connection = borrowed.get();

            // Checked on the connection already borrowed rather than through hasProfile(),
            // which would borrow a second one and hold both. Harmless at one scan per tick
            // against a pool of ten, but nesting borrows is the habit that exhausts a pool
            // once something starts calling this in a loop.
            boolean firstProfile = !hasProfile(connection, worldName, chunkX, chunkZ);

            // Clear first so materials that are no longer present cannot linger and headline
            // a price. Re-profiling happens after terrain changes, so this is not hypothetical.
            try (PreparedStatement stmt = connection.prepareStatement(deleteStale)) {
                stmt.setString(1, worldName);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = connection.prepareStatement(upsert)) {
                for (ProfileEntry entry : entries) {
                    stmt.setString(1, worldName);
                    stmt.setInt(2, chunkX);
                    stmt.setInt(3, chunkZ);
                    stmt.setString(4, entry.material().name());
                    stmt.setInt(5, entry.blockCount());
                    stmt.setInt(6, entry.tier());
                    stmt.setLong(7, now);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            // Only a chunk's first profile contributes to the baseline. Re-profiling the same
            // chunk must not count it twice, or a chunk scanned repeatedly would drag the
            // world average toward its own contents.
            if (firstProfile) {
                updateBaseline(connection, entries, now);
            }

            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to store chunk profile for "
                + worldName + ":" + chunkX + "," + chunkZ, e);
            return false;
        }
    }

    /**
     * Read back what a scan found in one chunk. Empty when the chunk has never been profiled,
     * which is the signal to fall back to owned-chunk pricing rather than an error.
     */
    public List<ProfileEntry> getProfile(String worldName, int chunkX, int chunkZ) {
        if (unavailable()) {
            return List.of();
        }

        String sql = """
            SELECT material, block_count, tier
            FROM chunk_profiles
            WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?
            ORDER BY block_count DESC
        """;

        List<ProfileEntry> entries = new ArrayList<>();
        try (CostStorageBackend.BorrowedConnection borrowed = backend().borrow();
             PreparedStatement stmt = borrowed.get().prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Material material = Material.matchMaterial(rs.getString("material"));
                    if (material == null) {
                        // A material that no longer exists on this server version. Skip it
                        // rather than failing the whole profile - the rest is still usable.
                        continue;
                    }
                    entries.add(new ProfileEntry(material, rs.getInt("block_count"), rs.getInt("tier")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read chunk profile for "
                + worldName + ":" + chunkX + "," + chunkZ, e);
            return List.of();
        }
        return entries;
    }

    /** Whether this chunk has been profiled. Used to keep baseline contributions one-per-chunk. */
    public boolean hasProfile(String worldName, int chunkX, int chunkZ) {
        if (unavailable()) {
            return false;
        }
        try (CostStorageBackend.BorrowedConnection borrowed = backend().borrow()) {
            return hasProfile(borrowed.get(), worldName, chunkX, chunkZ);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Failed to check chunk profile presence", e);
            return false;
        }
    }

    /** As above, on a connection the caller already holds. */
    private boolean hasProfile(Connection connection, String worldName, int chunkX, int chunkZ) {
        String sql = """
            SELECT 1 FROM chunk_profiles
            WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?
            LIMIT 1
        """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, worldName);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Failed to check chunk profile presence", e);
            return false;
        }
    }

    /**
     * The world-wide mean block count per material, for scoring a chunk's distinctiveness.
     *
     * <p>Returns an empty map before anything has been profiled. Callers must treat that as
     * "no opinion yet" and fall back, rather than scoring everything as infinitely
     * distinctive.</p>
     */
    public Map<Material, Double> getBaseline() {
        if (unavailable()) {
            return Map.of();
        }

        String sql = "SELECT material, total_count, chunks_counted FROM chunk_material_baseline";

        Map<Material, Double> baseline = new LinkedHashMap<>();
        try (CostStorageBackend.BorrowedConnection borrowed = backend().borrow();
             PreparedStatement stmt = borrowed.get().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Material material = Material.matchMaterial(rs.getString("material"));
                if (material == null) {
                    continue;
                }
                long chunks = rs.getLong("chunks_counted");
                if (chunks <= 0) {
                    continue;
                }
                baseline.put(material, rs.getLong("total_count") / (double) chunks);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to read material baseline", e);
            return Map.of();
        }
        return baseline;
    }

    /** How many chunks have contributed to the baseline. Drives the warm-up decision. */
    public long getBaselineChunkCount() {
        if (unavailable()) {
            return 0L;
        }

        // Every material row counts the same chunks, so the maximum is the number of chunks
        // profiled. Not a SUM: each row counts chunks, not materials.
        String sql = "SELECT COALESCE(MAX(chunks_counted), 0) AS profiled FROM chunk_material_baseline";

        try (CostStorageBackend.BorrowedConnection borrowed = backend().borrow();
             PreparedStatement stmt = borrowed.get().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong("profiled") : 0L;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.FINE, "Failed to read baseline chunk count", e);
            return 0L;
        }
    }

    /**
     * Fold one chunk's findings into the running world average.
     *
     * <p>Every material already in the baseline has its chunk count incremented, not only the
     * ones this chunk contains. A chunk holding no sand is still evidence about how much sand
     * a typical chunk holds - counting only the chunks that contain a material would make
     * every material look uniformly abundant and defeat the whole comparison.</p>
     */
    private void updateBaseline(Connection connection, List<ProfileEntry> entries, long now)
            throws SQLException {

        Map<String, Integer> counts = new HashMap<>();
        for (ProfileEntry entry : entries) {
            counts.merge(entry.material().name(), entry.blockCount(), Integer::sum);
        }

        // Bump chunks_counted for every material already tracked. Materials this chunk does
        // contain keep that bumped count; their totals are added immediately below.
        try (PreparedStatement stmt = connection.prepareStatement(
                dialect().bumpAllBaselineChunkCounts())) {
            stmt.setLong(1, now);
            stmt.executeUpdate();
        }

        // A material appearing for the first time must inherit the number of chunks already
        // profiled, not start at 1. Otherwise its average is computed over only the chunks
        // seen since it first showed up: profile a wood chunk then two stone chunks, and
        // stone would average over 2 chunks instead of 3 - reading 6000 rather than 4000.
        // The earlier chunks genuinely are evidence that they contained none of it.
        long chunksProfiled = countProfiledChunks(connection);

        // The two engines accumulate differently - H2 reads the row it writes, MySQL cannot -
        // so the dialect owns both the statement and its binding. See SqlDialect.
        SqlDialect dialect = dialect();
        try (PreparedStatement stmt = connection.prepareStatement(dialect.upsertBaseline())) {
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                dialect.bindBaselineUpsert(stmt, entry.getKey(), entry.getValue(), chunksProfiled, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * How many distinct chunks have been profiled, counted from the profiles themselves.
     *
     * <p>Read from {@code chunk_profiles} rather than the baseline, because a material's first
     * appearance needs to know how many chunks preceded it - which the baseline cannot say
     * about a material it has never seen.</p>
     */
    private long countProfiledChunks(Connection connection) throws SQLException {
        // Dialect-owned: H2 concatenates with ||, which in MySQL is logical OR and would
        // silently collapse every chunk to one key. See SqlDialect.countProfiledChunks.
        String sql = dialect().countProfiledChunks();
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong("profiled") : 1L;
        }
    }
}
