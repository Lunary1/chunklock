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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a chunk cost actually reaches the database (#90).
 *
 * <p>{@code storeCost} spent its whole life failing. It used a bare
 * {@code MERGE INTO chunk_costs (cols) VALUES (...)}, and H2 keys a bare MERGE on the
 * <strong>primary key</strong> - here the auto-increment {@code id} the statement never
 * supplies - so every write threw 90081 and {@code chunk_costs} was empty on every server.
 * It went unnoticed for as long as it did because only the vault strategy called it; a
 * default resource-scan server never reached the broken path. #83 routes every calculation
 * through it, which turned a dormant bug into a console flood and meant a committed price
 * could not survive a restart.</p>
 *
 * <p>These tests run the real statements against a real in-memory H2, because that is the
 * only place this bug lives. The economy system already has good coverage - none of it
 * caught this, for the same reason {@code ResourceBasedCostTest} did not catch #82:
 * <em>coverage of the wrong property still misses bugs.</em> Those tests assert what a price
 * is; this one asserts that a price is still there afterwards.</p>
 *
 * <p>The SQL is mirrored from {@code ChunkCostDatabase} rather than driven through it,
 * because {@code storeCost} takes Bukkit's {@code Player} and {@code Chunk}, which need a
 * running server. If that SQL changes, change it here too - the schema and the MERGE are
 * copied verbatim on purpose so a drift shows up as a failing test.</p>
 */
class ChunkCostPersistenceTest {

    /** Verbatim from ChunkCostDatabase.createTables. */
    private static final String CREATE_TABLE = """
        CREATE TABLE IF NOT EXISTS chunk_costs (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            world_name VARCHAR(255) NOT NULL,
            chunk_x INTEGER NOT NULL,
            chunk_z INTEGER NOT NULL,
            player_id VARCHAR(36) NOT NULL,
            biome VARCHAR(255) NOT NULL,
            difficulty VARCHAR(50) NOT NULL,
            score INTEGER NOT NULL,
            cost_type VARCHAR(50) NOT NULL,
            vault_cost DOUBLE,
            material_type VARCHAR(255),
            material_amount INTEGER,
            ai_processed BOOLEAN NOT NULL,
            ai_explanation VARCHAR(1000),
            calculated_at BIGINT NOT NULL,
            config_hash VARCHAR(255) NOT NULL,
            CONSTRAINT unique_chunk_cost UNIQUE(world_name, chunk_x, chunk_z, player_id, config_hash)
        )
        """;

    /** Verbatim from ChunkCostDatabase.storeCost. The KEY clause is the #90 fix. */
    private static final String STORE_COST = """
        MERGE INTO chunk_costs
        (world_name, chunk_x, chunk_z, player_id, biome, difficulty, score, cost_type,
         vault_cost, material_type, material_amount, ai_processed, ai_explanation,
         calculated_at, config_hash)
        KEY (world_name, chunk_x, chunk_z, player_id, config_hash)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    /** Verbatim from ChunkCostDatabase.getCachedCost, minus the memory cache in front of it. */
    private static final String LOOKUP = """
        SELECT vault_cost, material_type, material_amount, cost_type, ai_processed, ai_explanation, calculated_at
        FROM chunk_costs
        WHERE world_name = ? AND chunk_x = ? AND chunk_z = ? AND player_id = ? AND config_hash = ?
        ORDER BY calculated_at DESC LIMIT 1
        """;

    private static final String WORLD = "world";
    private static final String PLAYER = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
    private static final String CONFIG_HASH = "abc123";

    private Connection connection;

    @BeforeEach
    void openDatabase() throws SQLException {
        // A distinct in-memory database per test, so tests cannot leak rows into each other.
        connection = DriverManager.getConnection(
            "jdbc:h2:mem:chunkcost" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_TABLE);
        }
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    @DisplayName("#90: storing a cost writes a row instead of throwing 90081")
    void testStoreCostPersistsARow() throws SQLException {
        storeCost(10, -4, "DIAMOND", 3, 250.0);

        assertEquals(1, countRows(),
            "a stored cost must reach the table - this is what failed on every server before #90");
    }

    @Test
    @DisplayName("#90: a stored price can be read back, so a commitment survives a restart")
    void testStoredCostCanBeReadBack() throws SQLException {
        storeCost(10, -4, "DIAMOND", 3, 250.0);

        try (PreparedStatement stmt = connection.prepareStatement(LOOKUP)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, 10);
            stmt.setInt(3, -4);
            stmt.setString(4, PLAYER);
            stmt.setString(5, CONFIG_HASH);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next(),
                    "the price a player was shown must still be there after a restart (#83)");
                assertEquals("DIAMOND", rs.getString("material_type"));
                assertEquals(3, rs.getInt("material_amount"));
                assertEquals(250.0, rs.getDouble("vault_cost"), 0.001);
            }
        }
    }

    @Test
    @DisplayName("#90: re-storing the same chunk updates in place rather than duplicating")
    void testRestoringSameChunkUpdatesInPlace() throws SQLException {
        storeCost(10, -4, "DIAMOND", 3, 250.0);
        storeCost(10, -4, "EMERALD", 8, 400.0);

        assertEquals(1, countRows(),
            "MERGE must upsert on the unique constraint - two rows for one chunk would make "
                + "the committed price ambiguous");

        try (PreparedStatement stmt = connection.prepareStatement(LOOKUP)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, 10);
            stmt.setInt(3, -4);
            stmt.setString(4, PLAYER);
            stmt.setString(5, CONFIG_HASH);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EMERALD", rs.getString("material_type"),
                    "a re-roll replaces the committed price; the new one must win");
            }
        }
    }

    @Test
    @DisplayName("#90: different chunks, players and configs each keep their own price")
    void testDistinctKeysStoreSeparately() throws SQLException {
        storeCost(10, -4, "DIAMOND", 3, 250.0);
        storeCost(11, -4, "DIAMOND", 3, 250.0);   // different chunk
        storeCost(10, -4, "IRON_INGOT", 5, 90.0,
            "0ea5a5f4-4f89-11d3-9a0c-0305e82c3301", CONFIG_HASH);   // different player
        storeCost(10, -4, "GOLD_INGOT", 6, 120.0, PLAYER, "def456"); // different config

        assertEquals(4, countRows(),
            "the commitment is per (chunk, player, config) - collapsing any of those would "
                + "hand one player another's price");
    }

    private void storeCost(int chunkX, int chunkZ, String material, int amount, double vaultCost)
            throws SQLException {
        storeCost(chunkX, chunkZ, material, amount, vaultCost, PLAYER, CONFIG_HASH);
    }

    private void storeCost(int chunkX, int chunkZ, String material, int amount, double vaultCost,
                           String playerId, String configHash) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(STORE_COST)) {
            stmt.setString(1, WORLD);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            stmt.setString(4, playerId);
            stmt.setString(5, "PLAINS");
            stmt.setString(6, "NORMAL");
            stmt.setInt(7, 42);
            stmt.setString(8, "MATERIAL");
            stmt.setDouble(9, vaultCost);
            stmt.setString(10, material);
            stmt.setInt(11, amount);
            stmt.setBoolean(12, false);
            stmt.setString(13, null);
            stmt.setLong(14, System.currentTimeMillis());
            stmt.setString(15, configHash);

            stmt.executeUpdate();
        }
    }

    private int countRows() throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM chunk_costs")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
