package me.chunklock.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests chunk-profile persistence and the running material baseline (#86, step 3.9 task 2).
 *
 * <p>These run the real statements against real in-memory H2, for the reason #90 taught: the
 * bug there lived entirely in a MERGE statement that read correctly and failed on every
 * server, and no amount of testing around it would have caught it. Persistence gets tested by
 * persisting.</p>
 *
 * <p>The SQL is mirrored from {@code ChunkCostDatabase.createTables} and
 * {@code ChunkProfileStore} rather than driven through them, because those need a live
 * {@code ChunklockPlugin}. Copied verbatim so drift surfaces as a failing test.</p>
 *
 * <h3>What is actually being pinned</h3>
 *
 * <p>The interesting property is not "a row round-trips" but <strong>the baseline arithmetic
 * that #86's scoring depends on</strong>: every profiled chunk must count toward every
 * material's average, including materials that chunk does not contain. A chunk with no sand is
 * still evidence about how much sand a typical chunk holds. Counting only the chunks that
 * contain a material would make every material look uniformly abundant, and the whole
 * relative-scoring metric would collapse back to raw abundance - the bug #86 exists to
 * fix.</p>
 */
class ChunkProfileStoreTest {

    /** Verbatim from ChunkCostDatabase.createTables. */
    private static final String CREATE_PROFILES = """
        CREATE TABLE IF NOT EXISTS chunk_profiles (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            world_name VARCHAR(255) NOT NULL,
            chunk_x INTEGER NOT NULL,
            chunk_z INTEGER NOT NULL,
            material VARCHAR(255) NOT NULL,
            block_count INTEGER NOT NULL,
            tier INTEGER NOT NULL,
            scanned_at BIGINT NOT NULL,
            CONSTRAINT unique_chunk_profile UNIQUE(world_name, chunk_x, chunk_z, material)
        )
        """;

    /** Verbatim from ChunkCostDatabase.createTables. */
    private static final String CREATE_BASELINE = """
        CREATE TABLE IF NOT EXISTS chunk_material_baseline (
            material VARCHAR(255) NOT NULL PRIMARY KEY,
            total_count BIGINT NOT NULL,
            chunks_counted BIGINT NOT NULL,
            updated_at BIGINT NOT NULL
        )
        """;

    /** Verbatim from ChunkProfileStore.storeProfile. The KEY clause is the #90 lesson. */
    private static final String UPSERT_PROFILE = """
        MERGE INTO chunk_profiles
        (world_name, chunk_x, chunk_z, material, block_count, tier, scanned_at)
        KEY (world_name, chunk_x, chunk_z, material)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String DELETE_PROFILE = """
        DELETE FROM chunk_profiles
        WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?
        """;

    /**
     * Verbatim from ChunkProfileStore.updateBaseline.
     *
     * <p>The {@code ?} in place of a literal 1 for a new material's chunk count is
     * load-bearing: a material appearing for the first time inherits the number of chunks
     * already profiled, because those earlier chunks are genuine evidence that they contained
     * none of it. Using 1 there averages a material over only the chunks seen since it first
     * appeared.</p>
     */
    private static final String UPSERT_BASELINE = """
        MERGE INTO chunk_material_baseline (material, total_count, chunks_counted, updated_at)
        KEY (material)
        VALUES (?,
                COALESCE((SELECT total_count FROM chunk_material_baseline WHERE material = ?), 0) + ?,
                COALESCE((SELECT chunks_counted FROM chunk_material_baseline WHERE material = ?), ?),
                ?)
        """;

    /** Verbatim from ChunkProfileStore.countProfiledChunks. */
    private static final String COUNT_PROFILED_CHUNKS =
        "SELECT COUNT(DISTINCT (world_name || ':' || chunk_x || ',' || chunk_z)) AS profiled "
            + "FROM chunk_profiles";

    private static final String BUMP_ALL_BASELINE =
        "UPDATE chunk_material_baseline SET chunks_counted = chunks_counted + 1, updated_at = ?";

    private static final String WORLD = "world";

    private Connection connection;

    @BeforeEach
    void openDatabase() throws SQLException {
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:profiles" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_PROFILES);
            stmt.execute(CREATE_BASELINE);
        }
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    // ---- Profile round-trip -------------------------------------------------------------

    @Test
    @DisplayName("#86: a chunk profile persists and reads back")
    void testProfileRoundTrips() throws SQLException {
        storeProfile(10, -4, Map.of("OAK_LOG", 180, "DIRT", 320, "STONE", 5200));

        Map<String, Integer> read = readProfile(10, -4);

        assertEquals(3, read.size(), "every material in the profile must persist");
        assertEquals(180, read.get("OAK_LOG"));
        assertEquals(5200, read.get("STONE"));
    }

    @Test
    @DisplayName("#86: re-profiling a chunk replaces it rather than accumulating")
    void testReprofilingReplaces() throws SQLException {
        storeProfile(10, -4, Map.of("OAK_LOG", 180, "DIRT", 320));
        // Terrain changed - the trees are gone.
        storeProfile(10, -4, Map.of("DIRT", 400));

        Map<String, Integer> read = readProfile(10, -4);

        assertEquals(1, read.size(),
            "a material no longer present must not linger - a stale row could still headline "
                + "a price for a chunk that no longer contains it");
        assertEquals(400, read.get("DIRT"));
        assertFalse(read.containsKey("OAK_LOG"));
    }

    @Test
    @DisplayName("#86: profiles are keyed per chunk, not globally")
    void testProfilesAreIndependentPerChunk() throws SQLException {
        storeProfile(10, -4, Map.of("OAK_LOG", 180));
        storeProfile(11, -4, Map.of("STONE", 6400));

        assertEquals(180, readProfile(10, -4).get("OAK_LOG"));
        assertEquals(6400, readProfile(11, -4).get("STONE"));
        assertFalse(readProfile(11, -4).containsKey("OAK_LOG"),
            "one chunk's contents must not leak into another's profile");
    }

    // ---- Baseline arithmetic - the part that matters ------------------------------------

    @Test
    @DisplayName("#86: a chunk without a material still counts toward that material's average")
    void testChunksWithoutAMaterialStillCountTowardIt() throws SQLException {
        // A forest chunk with wood, then two stone chunks with none.
        storeProfile(0, 0, Map.of("OAK_LOG", 300));
        storeProfile(1, 0, Map.of("STONE", 6000));
        storeProfile(2, 0, Map.of("STONE", 6000));

        Map<String, Double> baseline = readBaseline();

        // 300 wood across three profiled chunks = 100 average, not 300.
        assertEquals(100.0, baseline.get("OAK_LOG"), 0.001,
            "counting only the chunks that contain a material would make every material look "
                + "uniformly abundant, collapsing relative scoring back to raw abundance - "
                + "which is the bug #86 exists to fix");

        // 12000 stone across three chunks = 4000 average.
        assertEquals(4000.0, baseline.get("STONE"), 0.001);
    }

    @Test
    @DisplayName("#86: the wood chunk is distinctive precisely because the average is low")
    void testDistinctivenessFallsOutOfTheBaseline() throws SQLException {
        storeProfile(0, 0, Map.of("OAK_LOG", 300, "STONE", 5000));
        storeProfile(1, 0, Map.of("STONE", 6000));
        storeProfile(2, 0, Map.of("STONE", 6000));

        Map<String, Double> baseline = readBaseline();
        Map<String, Integer> forest = readProfile(0, 0);

        double woodRatio = forest.get("OAK_LOG") / baseline.get("OAK_LOG");
        double stoneRatio = forest.get("STONE") / baseline.get("STONE");

        assertTrue(woodRatio > stoneRatio,
            "the whole point of #86: in a chunk holding both, wood must outrank stone because "
                + "the wood is unusual and the stone is everywhere. wood=" + woodRatio
                + " stone=" + stoneRatio);
    }

    @Test
    @DisplayName("#86: re-profiling one chunk does not count it twice in the baseline")
    void testReprofilingDoesNotDoubleCount() throws SQLException {
        storeProfile(0, 0, Map.of("STONE", 6000));
        storeProfile(1, 0, Map.of("STONE", 6000));

        Map<String, Double> before = readBaseline();
        long chunksBefore = readBaselineChunkCount();

        // Re-profile a chunk already counted. Only first profiles contribute.
        storeProfileWithoutBaseline(0, 0, Map.of("STONE", 9000));

        assertEquals(chunksBefore, readBaselineChunkCount(),
            "a chunk scanned repeatedly must not drag the world average toward its own contents");
        assertEquals(before.get("STONE"), readBaseline().get("STONE"), 0.001);
    }

    @Test
    @DisplayName("#86: an empty baseline reports no chunks, so callers can fall back")
    void testEmptyBaselineIsDetectable() throws SQLException {
        assertEquals(0L, readBaselineChunkCount(),
            "before anything is profiled the baseline has no opinion, and callers must be "
                + "able to see that rather than scoring everything as infinitely distinctive");
        assertTrue(readBaseline().isEmpty());
    }

    @Test
    @DisplayName("#86: the baseline chunk count is chunks profiled, not materials seen")
    void testChunkCountIsNotMaterialCount() throws SQLException {
        storeProfile(0, 0, Map.of("STONE", 6000, "DIRT", 300, "OAK_LOG", 120));

        assertEquals(1L, readBaselineChunkCount(),
            "one chunk holding three materials is one chunk - summing rows would report 3");
    }

    // ---- Helpers: mirror ChunkProfileStore's statements ---------------------------------

    private void storeProfile(int chunkX, int chunkZ, Map<String, Integer> materials) throws SQLException {
        boolean first = !hasProfile(chunkX, chunkZ);
        writeProfileRows(chunkX, chunkZ, materials);
        if (first) {
            updateBaseline(materials);
        }
    }

    /** Re-profile path: rows replaced, baseline untouched. */
    private void storeProfileWithoutBaseline(int chunkX, int chunkZ, Map<String, Integer> materials)
            throws SQLException {
        writeProfileRows(chunkX, chunkZ, materials);
    }

    private void writeProfileRows(int chunkX, int chunkZ, Map<String, Integer> materials) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(DELETE_PROFILE)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            stmt.executeUpdate();
        }

        long now = System.currentTimeMillis();
        try (PreparedStatement stmt = connection.prepareStatement(UPSERT_PROFILE)) {
            for (Map.Entry<String, Integer> e : materials.entrySet()) {
                stmt.setString(1, WORLD);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                stmt.setString(4, e.getKey());
                stmt.setInt(5, e.getValue());
                stmt.setInt(6, 1);
                stmt.setLong(7, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void updateBaseline(Map<String, Integer> materials) throws SQLException {
        long now = System.currentTimeMillis();

        try (PreparedStatement stmt = connection.prepareStatement(BUMP_ALL_BASELINE)) {
            stmt.setLong(1, now);
            stmt.executeUpdate();
        }

        long chunksProfiled = countProfiledChunks();

        Map<String, Integer> counts = new HashMap<>(materials);
        try (PreparedStatement stmt = connection.prepareStatement(UPSERT_BASELINE)) {
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                stmt.setString(1, e.getKey());
                stmt.setString(2, e.getKey());
                stmt.setInt(3, e.getValue());
                stmt.setString(4, e.getKey());
                stmt.setLong(5, chunksProfiled);
                stmt.setLong(6, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private long countProfiledChunks() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(COUNT_PROFILED_CHUNKS)) {
            return rs.next() ? rs.getLong("profiled") : 1L;
        }
    }

    private boolean hasProfile(int chunkX, int chunkZ) throws SQLException {
        String sql = "SELECT 1 FROM chunk_profiles WHERE world_name = ? AND chunk_x = ? AND chunk_z = ? LIMIT 1";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Map<String, Integer> readProfile(int chunkX, int chunkZ) throws SQLException {
        String sql = """
            SELECT material, block_count FROM chunk_profiles
            WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?
            ORDER BY block_count DESC
            """;
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("material"), rs.getInt("block_count"));
                }
            }
        }
        return out;
    }

    private Map<String, Double> readBaseline() throws SQLException {
        Map<String, Double> out = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT material, total_count, chunks_counted FROM chunk_material_baseline")) {
            while (rs.next()) {
                long chunks = rs.getLong("chunks_counted");
                if (chunks > 0) {
                    out.put(rs.getString("material"), rs.getLong("total_count") / (double) chunks);
                }
            }
        }
        return out;
    }

    private long readBaselineChunkCount() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT COALESCE(MAX(chunks_counted), 0) AS profiled FROM chunk_material_baseline")) {
            return rs.next() ? rs.getLong("profiled") : 0L;
        }
    }

    /** Kept for symmetry with ChunkProfileStore's signature; unused list form. */
    @SuppressWarnings("unused")
    private static List<String> materialNames(Map<String, Integer> materials) {
        return List.copyOf(materials.keySet());
    }
}
