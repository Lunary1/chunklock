package me.chunklock.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs both dialects' real statements (#95).
 *
 * <h3>Why this exists</h3>
 *
 * <p>Routing pricing data to MySQL is not a matter of swapping a connection: the SQL
 * {@link ChunkCostDatabase} and {@link ChunkProfileStore} ran was H2-specific and would fail
 * on a real MySQL server. {@code MERGE ... KEY} does not exist in MySQL, and the baseline
 * upsert read the table it was writing, which MySQL rejects outright.</p>
 *
 * <p>#90 is the reason these are executed rather than asserted on as strings: that bug lived
 * entirely inside a MERGE statement that read perfectly and failed on every single write, on
 * every server, for months. Persistence gets tested by persisting.</p>
 *
 * <h3>What these tests can and cannot prove</h3>
 *
 * <p>The MySQL cases run against <strong>H2 in MySQL compatibility mode</strong>, not a real
 * MySQL server. That is a genuine limit and worth stating plainly rather than glossing:</p>
 *
 * <ul>
 *   <li><strong>They do prove</strong> the MySQL statements parse, upsert on the right unique
 *       key, accumulate correctly, and are idempotent across a restart - which is exactly the
 *       class of defect #90 was.</li>
 *   <li><strong>They do prove</strong> both dialects produce identical baseline numbers from
 *       the same profiling sequence, so a chunk scores the same whichever backend runs.</li>
 *   <li><strong>They do not prove</strong> the H2-only statements would be rejected by a real
 *       MySQL server. H2's MySQL mode still accepts {@code MERGE ... KEY}, and still treats
 *       {@code ||} as concatenation rather than logical OR - both checked by running them,
 *       not assumed. So the two hazards the dialect exists to avoid cannot be demonstrated
 *       here; they are asserted on statement text and explained where they sit.</li>
 * </ul>
 *
 * <p>So a real-MySQL play-test remains necessary before #95 is called done. What these tests
 * remove is the possibility of shipping SQL that was never executed at all.</p>
 */
class SqlDialectTest {

    private Connection connection;

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    private Connection openH2() throws SQLException {
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:dialect_h2_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return connection;
    }

    /**
     * H2 in MySQL compatibility mode. Not a real MySQL server - see the class comment for
     * what that does and does not buy.
     */
    private Connection openMySqlMode() throws SQLException {
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:dialect_my_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return connection;
    }

    private void createSchema(Connection c, SqlDialect dialect) throws SQLException {
        try (Statement stmt = c.createStatement()) {
            stmt.execute(dialect.createChunkCostsTable());
            stmt.execute(dialect.createChunkProfilesTable());
            stmt.execute(dialect.createBaselineTable());
            for (String index : dialect.createIndexes()) {
                stmt.execute(index);
            }
        }
    }

    // ---- Schema -------------------------------------------------------------------------

    @Nested
    @DisplayName("Schema creation")
    class SchemaCreation {

        @Test
        @DisplayName("both dialects create all three pricing tables")
        void testBothDialectsCreateSchema() throws SQLException {
            createSchema(openH2(), SqlDialect.H2);
            assertTablesExist();

            closeQuietly();
            createSchema(openMySqlMode(), SqlDialect.MYSQL);
            assertTablesExist();
        }

        @Test
        @DisplayName("#95: creating the schema twice is idempotent, so a restart does not throw")
        void testSchemaIsIdempotent() throws SQLException {
            // MySQL has no CREATE INDEX IF NOT EXISTS, which is why its indexes are declared
            // inline. Running creation twice is what every server restart does; getting this
            // wrong throws on the second boot rather than the first.
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);
            createSchema(c, SqlDialect.MYSQL);
            assertTablesExist();
        }

        private void assertTablesExist() throws SQLException {
            for (String table : new String[] {"chunk_costs", "chunk_profiles", "chunk_material_baseline"}) {
                try (Statement stmt = connection.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rs.next(), table + " must be queryable");
                }
            }
        }

        private void closeQuietly() throws SQLException {
            connection.close();
        }
    }

    // ---- Cost upsert --------------------------------------------------------------------

    @Nested
    @DisplayName("Committed cost upsert (#83, #90)")
    class CostUpsert {

        @Test
        @DisplayName("MySQL: a cost persists, then upserts in place rather than duplicating")
        void testMySqlCostUpsert() throws SQLException {
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);

            storeCost(c, SqlDialect.MYSQL, "world", 4, 9, "OAK_LOG", 12);
            assertEquals(1, countCosts(c), "first write must insert a row");
            assertEquals("OAK_LOG", readMaterial(c));

            // Same chunk, same player, same config - a re-roll replacing the price.
            storeCost(c, SqlDialect.MYSQL, "world", 4, 9, "SAND", 20);

            assertEquals(1, countCosts(c),
                "upserting the same (world, chunk, player, config) must not create a second row");
            assertEquals("SAND", readMaterial(c), "the stored price must be the new one");
        }

        @Test
        @DisplayName("MySQL: costs stay separate per chunk")
        void testMySqlCostsSeparatePerChunk() throws SQLException {
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);

            storeCost(c, SqlDialect.MYSQL, "world", 1, 1, "OAK_LOG", 12);
            storeCost(c, SqlDialect.MYSQL, "world", 2, 1, "SAND", 20);

            assertEquals(2, countCosts(c), "different chunks are different commitments");
        }

        @Test
        @DisplayName("#90: neither dialect keys the upsert on the auto-increment id")
        void testNeitherDialectKeysOnPrimaryKey() {
            // #90 in one assertion. H2 keyed its MERGE on the primary key - the id column no
            // statement supplies - so every write failed with 90081 and chunk_costs stayed
            // empty on every server. Both dialects must name the real unique constraint: H2
            // with an explicit KEY clause, MySQL by omitting id so ON DUPLICATE KEY matches
            // unique_chunk_cost.
            String h2 = SqlDialect.H2.upsertChunkCost();
            assertTrue(h2.contains("KEY (world_name, chunk_x, chunk_z, player_id, config_hash)"),
                "H2's MERGE must key on unique_chunk_cost, not the primary key");

            String mysql = SqlDialect.MYSQL.upsertChunkCost();
            assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE"),
                "MySQL must upsert rather than plain insert");
            assertTrue(!mysql.contains("(id,") && !mysql.contains(" id,"),
                "MySQL must not write id, or the unique key is not what matches");
        }
    }

    // ---- Baseline arithmetic ------------------------------------------------------------

    @Nested
    @DisplayName("Material baseline (#86)")
    class Baseline {

        /**
         * The property #86's whole metric rests on, now verified in the MySQL dialect too.
         *
         * <p>A chunk containing none of a material is still evidence about that material.
         * Counting only the chunks that contain it makes everything look uniformly abundant,
         * which collapses relative scoring straight back to raw abundance - the exact bug #86
         * exists to fix.</p>
         */
        @Test
        @DisplayName("MySQL: a chunk without a material still counts toward that material")
        void testChunksWithoutAMaterialStillCountTowardIt() throws SQLException {
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);

            // A wood chunk, then two stone chunks. Stone appears for the first time in the
            // second chunk and must still average over all three.
            profileChunk(c, SqlDialect.MYSQL, 0, 0, new String[] {"OAK_LOG"}, new int[] {180});
            profileChunk(c, SqlDialect.MYSQL, 1, 0, new String[] {"STONE"}, new int[] {6000});
            profileChunk(c, SqlDialect.MYSQL, 2, 0, new String[] {"STONE"}, new int[] {6000});

            assertEquals(3, chunksCounted(c, "STONE"),
                "stone must average over all three profiled chunks, not only the two holding it");
            assertEquals(4000.0, baselineAverage(c, "STONE"), 0.01,
                "12000 blocks over 3 chunks is 4000; averaging over 2 would read 6000 "
                    + "and make every chunk look equally stony");
        }

        @Test
        @DisplayName("MySQL: profiling accumulates totals rather than overwriting them")
        void testBaselineAccumulates() throws SQLException {
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);

            profileChunk(c, SqlDialect.MYSQL, 0, 0, new String[] {"STONE"}, new int[] {100});
            profileChunk(c, SqlDialect.MYSQL, 1, 0, new String[] {"STONE"}, new int[] {200});

            assertEquals(300L, totalCount(c, "STONE"),
                "MySQL accumulates in its ON DUPLICATE KEY UPDATE clause; overwriting would read 200");
        }

        @Test
        @DisplayName("both dialects agree on the same profiling sequence")
        void testDialectsAgree() throws SQLException {
            // The point of the abstraction: whichever backend a server runs, a chunk must
            // score the same. Same sequence, both engines, same numbers out.
            Connection h2 = openH2();
            createSchema(h2, SqlDialect.H2);
            profileChunk(h2, SqlDialect.H2, 0, 0, new String[] {"OAK_LOG"}, new int[] {180});
            profileChunk(h2, SqlDialect.H2, 1, 0, new String[] {"STONE"}, new int[] {6000});
            profileChunk(h2, SqlDialect.H2, 2, 0, new String[] {"STONE"}, new int[] {6000});
            double h2Stone = baselineAverage(h2, "STONE");
            long h2Chunks = chunksCounted(h2, "STONE");
            h2.close();

            Connection my = openMySqlMode();
            createSchema(my, SqlDialect.MYSQL);
            profileChunk(my, SqlDialect.MYSQL, 0, 0, new String[] {"OAK_LOG"}, new int[] {180});
            profileChunk(my, SqlDialect.MYSQL, 1, 0, new String[] {"STONE"}, new int[] {6000});
            profileChunk(my, SqlDialect.MYSQL, 2, 0, new String[] {"STONE"}, new int[] {6000});

            assertEquals(h2Stone, baselineAverage(my, "STONE"), 0.01,
                "a chunk must score the same whichever backend the server runs");
            assertEquals(h2Chunks, chunksCounted(my, "STONE"),
                "baseline chunk counts must not depend on the storage engine");
        }
    }

    // ---- Chunk counting -----------------------------------------------------------------

    @Nested
    @DisplayName("Counting profiled chunks")
    class ChunkCounting {

        @Test
        @DisplayName("#95: MySQL counts distinct chunks correctly with CONCAT")
        void testMySqlCountsDistinctChunks() throws SQLException {
            Connection c = openMySqlMode();
            createSchema(c, SqlDialect.MYSQL);

            profileChunk(c, SqlDialect.MYSQL, 0, 0, new String[] {"STONE"}, new int[] {10});
            profileChunk(c, SqlDialect.MYSQL, 1, 0, new String[] {"STONE"}, new int[] {10});
            profileChunk(c, SqlDialect.MYSQL, 2, 0, new String[] {"STONE"}, new int[] {10});

            assertEquals(3L, countProfiledChunks(c, SqlDialect.MYSQL));
        }

        /**
         * The MySQL statement must use {@code CONCAT}, not {@code ||}.
         *
         * <p><strong>This asserts on the statement text, deliberately, and the reason is worth
         * recording.</strong> The intent was to prove the hazard by running H2's {@code ||}
         * version under MySQL mode and watching the count collapse. It does not collapse:
         * H2's MySQL compatibility mode still treats {@code ||} as concatenation, so it
         * returns the same 3. The behavioural test was written, run, and <em>failed to
         * demonstrate what it claimed</em> - so it was replaced with this rather than kept as
         * decoration.</p>
         *
         * <p>On a real MySQL server in its default mode {@code ||} is logical OR: the key
         * evaluates to a single value, every chunk collapses to one key, and the count reads 1
         * however many chunks were profiled. Every material's baseline would then divide by 1
         * and read as its raw total - silently defeating #86's scoring rather than throwing.
         * That is why {@code CONCAT} is not a stylistic preference, and why a real-MySQL
         * play-test is still required before #95 is called done.</p>
         */
        @Test
        @DisplayName("#95: the MySQL count uses CONCAT, since || is logical OR on a real server")
        void testMySqlCountUsesConcatNotPipes() {
            String mysql = SqlDialect.MYSQL.countProfiledChunks();
            assertTrue(mysql.contains("CONCAT("),
                "MySQL must concatenate the chunk key with CONCAT");
            assertTrue(!mysql.contains("||"),
                "|| is logical OR on a real MySQL server: the count would silently read 1 "
                    + "and every baseline would divide by 1");

            // H2 keeps ||, which is correct there and is what its own tests exercise.
            assertTrue(SqlDialect.H2.countProfiledChunks().contains("||"));
        }
    }

    // ---- Helpers ------------------------------------------------------------------------

    private void storeCost(Connection c, SqlDialect dialect, String world, int x, int z,
                           String material, int amount) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(dialect.upsertChunkCost())) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, z);
            stmt.setString(4, "11111111-1111-1111-1111-111111111111");
            stmt.setString(5, "FOREST");
            stmt.setString(6, "NORMAL");
            stmt.setInt(7, 10);
            stmt.setString(8, "materials");
            stmt.setDouble(9, 0.0);
            stmt.setString(10, material);
            stmt.setInt(11, amount);
            stmt.setBoolean(12, false);
            stmt.setString(13, null);
            stmt.setLong(14, System.currentTimeMillis());
            stmt.setString(15, "confighash");
            stmt.executeUpdate();
        }
    }

    /** Mirrors ChunkProfileStore.storeProfile: replace rows, then fold into the baseline. */
    private void profileChunk(Connection c, SqlDialect dialect, int x, int z,
                              String[] materials, int[] counts) throws SQLException {
        long now = System.currentTimeMillis();

        try (PreparedStatement stmt = c.prepareStatement(
                "DELETE FROM chunk_profiles WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?")) {
            stmt.setString(1, "world");
            stmt.setInt(2, x);
            stmt.setInt(3, z);
            stmt.executeUpdate();
        }

        try (PreparedStatement stmt = c.prepareStatement(dialect.upsertChunkProfile())) {
            for (int i = 0; i < materials.length; i++) {
                stmt.setString(1, "world");
                stmt.setInt(2, x);
                stmt.setInt(3, z);
                stmt.setString(4, materials[i]);
                stmt.setInt(5, counts[i]);
                stmt.setInt(6, 1);
                stmt.setLong(7, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        try (PreparedStatement stmt = c.prepareStatement(dialect.bumpAllBaselineChunkCounts())) {
            stmt.setLong(1, now);
            stmt.executeUpdate();
        }

        long chunksProfiled = countProfiledChunks(c, dialect);

        try (PreparedStatement stmt = c.prepareStatement(dialect.upsertBaseline())) {
            for (int i = 0; i < materials.length; i++) {
                dialect.bindBaselineUpsert(stmt, materials[i], counts[i], chunksProfiled, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private long countProfiledChunks(Connection c, SqlDialect dialect) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(dialect.countProfiledChunks());
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong("profiled") : 0L;
        }
    }

    private int countCosts(Connection c) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM chunk_costs")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String readMaterial(Connection c) throws SQLException {
        try (Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT material_type FROM chunk_costs LIMIT 1")) {
            rs.next();
            return rs.getString(1);
        }
    }

    private long chunksCounted(Connection c, String material) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT chunks_counted FROM chunk_material_baseline WHERE material = ?")) {
            stmt.setString(1, material);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private long totalCount(Connection c, String material) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT total_count FROM chunk_material_baseline WHERE material = ?")) {
            stmt.setString(1, material);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private double baselineAverage(Connection c, String material) throws SQLException {
        try (PreparedStatement stmt = c.prepareStatement(
                "SELECT total_count, chunks_counted FROM chunk_material_baseline WHERE material = ?")) {
            stmt.setString(1, material);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return 0.0;
                }
                long chunks = rs.getLong("chunks_counted");
                return chunks <= 0 ? 0.0 : rs.getLong("total_count") / (double) chunks;
            }
        }
    }
}
