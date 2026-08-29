package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Persistent H2 database for storing chunk cost calculations.
 * Dramatically improves performance by avoiding repeated AI calculations.
 * Uses H2 (pure Java) instead of SQLite for smaller plugin size.
 */
public class ChunkCostDatabase {
    
    private final ChunklockPlugin plugin;
    private final File databaseFile;
    private Connection connection;
    
    // In-memory cache for frequently accessed costs
    private final Map<String, CachedChunkCost> memoryCache = new ConcurrentHashMap<>();
    private static final long MEMORY_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    
    public ChunkCostDatabase(ChunklockPlugin plugin) {
        this.plugin = plugin;
        // H2 creates .mv.db file automatically, but we specify base name
        this.databaseFile = new File(plugin.getDataFolder(), "chunk_costs");
    }
    
    /**
     * Initialize the database connection and create tables
     */
    public boolean initialize() {
        try {
            // Ensure the data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // Explicitly load H2 driver (needed after shading/relocation)
            try {
                // Try relocated package first (after Maven Shade relocation)
                Class.forName("me.chunklock.libs.h2.Driver");
            } catch (ClassNotFoundException e) {
                // Fallback to standard package if not relocated
                try {
                    Class.forName("org.h2.Driver");
                } catch (ClassNotFoundException e2) {
                    plugin.getLogger().warning("H2 driver class not found in either location");
                    throw new SQLException("H2 JDBC driver not found", e2);
                }
            }
            
            // Create database connection (H2 format: jdbc:h2:file:path)
            String url = "jdbc:h2:file:" + databaseFile.getAbsolutePath() + ";AUTO_SERVER=TRUE";
            connection = DriverManager.getConnection(url);
            
            // Create the table
            createTables();
            
            plugin.getLogger().info("Chunk cost database initialized: " + databaseFile.getName());
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Failed to initialize chunk cost database: " + e.getMessage());
            e.printStackTrace();
            connection = null; // Ensure connection is null on failure
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Unexpected error initializing chunk cost database: " + e.getMessage());
            e.printStackTrace();
            connection = null; // Ensure connection is null on failure
            return false;
        }
    }
    
    /**
     * Create the database tables
     */
    private void createTables() throws SQLException {
            String createTableSQL = """
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
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
            
            // Create indexes for better performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunk_location ON chunk_costs(world_name, chunk_x, chunk_z)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_costs ON chunk_costs(player_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_calculated_at ON chunk_costs(calculated_at)");
        }
    }
    
    /**
     * Get cached cost for a chunk
     */
    public CompletableFuture<EconomyManager.PaymentRequirement> getCachedCost(Player player, Chunk chunk, String configHash) {
        return CompletableFuture.supplyAsync(() -> {
            String cacheKey = getCacheKey(chunk, player.getUniqueId());
            
            // Check memory cache first
            CachedChunkCost memoryCached = memoryCache.get(cacheKey);
            if (memoryCached != null && !memoryCached.isExpired() && memoryCached.configHash.equals(configHash)) {
                plugin.getLogger().fine("Retrieved cost from memory cache for " + cacheKey);
                return memoryCached.requirement;
            }
            
            // Check if database connection is available
            if (connection == null) {
                plugin.getLogger().fine("Database connection not available, skipping cache lookup for " + cacheKey);
                return null; // Return null to indicate cache miss, calculation will proceed
            }
            
            // Check database
            try {
                String sql = """
                    SELECT vault_cost, material_type, material_amount, cost_type, ai_processed, ai_explanation, calculated_at
                    FROM chunk_costs 
                    WHERE world_name = ? AND chunk_x = ? AND chunk_z = ? AND player_id = ? AND config_hash = ?
                    ORDER BY calculated_at DESC LIMIT 1
                """;
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, chunk.getWorld().getName());
                    stmt.setInt(2, chunk.getX());
                    stmt.setInt(3, chunk.getZ());
                    stmt.setString(4, player.getUniqueId().toString());
                    stmt.setString(5, configHash);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            long calculatedAt = rs.getLong("calculated_at");

                            // No age check. A stored requirement is a commitment to the
                            // player (#83): once they have seen a price for a chunk, that
                            // price holds until they pay it or deliberately re-roll it.
                            // This used to expire after an hour, which is shorter than a
                            // real collection goal - a player grinding for diamonds would
                            // return to a different requirement through no action of their
                            // own, which is precisely the grind-invalidation #83 exists to
                            // stop. config_hash is still part of the lookup key, so an admin
                            // changing economy config still re-derives prices legitimately.
                            EconomyManager.PaymentRequirement requirement = createRequirementFromResult(rs);

                            // Cache in memory for quick access
                            memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, calculatedAt));

                            plugin.getLogger().fine("Retrieved committed cost from database for " + cacheKey);
                            return requirement;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to retrieve cached cost: " + e.getMessage());
            }
            
            return null; // No valid cache found
        });
    }
    
    /**
     * Store calculated cost in database
     */
    public void storeCost(Player player, Chunk chunk, EconomyManager.PaymentRequirement requirement, 
                         String biome, String difficulty, int score, boolean aiProcessed, 
                         String aiExplanation, String configHash) {
        
        CompletableFuture.runAsync(() -> {
            // Check if database connection is available
            if (connection == null) {
                plugin.getLogger().fine("Database connection not available, skipping cost storage");
                // Still cache in memory even if database is unavailable
                String cacheKey = getCacheKey(chunk, player.getUniqueId());
                memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, System.currentTimeMillis()));
                return;
            }
            
            try {
                // H2 uses MERGE instead of INSERT OR REPLACE
                String sql = """
                    MERGE INTO chunk_costs 
                    (world_name, chunk_x, chunk_z, player_id, biome, difficulty, score, cost_type, 
                     vault_cost, material_type, material_amount, ai_processed, ai_explanation, 
                     calculated_at, config_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
                
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, chunk.getWorld().getName());
                    stmt.setInt(2, chunk.getX());
                    stmt.setInt(3, chunk.getZ());
                    stmt.setString(4, player.getUniqueId().toString());
                    stmt.setString(5, biome);
                    stmt.setString(6, difficulty);
                    stmt.setInt(7, score);
                    stmt.setString(8, requirement.getType().getConfigName());
                    stmt.setDouble(9, requirement.getVaultCost());
                    stmt.setString(10, requirement.getMaterial() != null ? me.chunklock.util.item.MaterialUtil.getMaterialName(requirement.getMaterial()) : null);
                    stmt.setInt(11, requirement.getMaterialAmount());
                    stmt.setBoolean(12, aiProcessed);
                    stmt.setString(13, aiExplanation);
                    stmt.setLong(14, System.currentTimeMillis());
                    stmt.setString(15, configHash);
                    
                    stmt.executeUpdate();
                    
                    // Also cache in memory
                    String cacheKey = getCacheKey(chunk, player.getUniqueId());
                    memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, System.currentTimeMillis()));
                    
                    plugin.getLogger().fine("Stored cost in database for " + cacheKey);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to store cost in database: " + e.getMessage());
            }
        });
    }
    
    /**
     * Remove stored costs whose configuration no longer applies.
     *
     * <p><strong>Age is deliberately not a criterion.</strong> This method used to delete
     * every row older than 24 hours. Under #83 that would silently break the price
     * commitment: a player grinding for an expensive material across two evenings would
     * return to a different requirement, which is the exact bug the commitment exists to
     * prevent. The method had no callers at the time, so nothing was actually expiring - but
     * scheduling it would have quietly undone the feature.</p>
     *
     * <p>What is safe to remove is any row whose {@code config_hash} differs from the
     * current configuration. Those can never be read again - {@code getCachedCost} matches
     * on {@code config_hash} - so they are dead rows rather than commitments.</p>
     */
    public void cleanupOrphanedCosts() {
        CompletableFuture.runAsync(() -> {
            // Check if database connection is available
            if (connection == null) {
                plugin.getLogger().fine("Database connection not available, skipping cleanup");
                return;
            }

            try {
                String currentHash = generateConfigHash();

                String sql = "DELETE FROM chunk_costs WHERE config_hash <> ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, currentHash);
                    int deleted = stmt.executeUpdate();

                    if (deleted > 0) {
                        plugin.getLogger().info("Cleaned up " + deleted
                            + " chunk costs from superseded economy configurations");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to cleanup orphaned costs: " + e.getMessage());
            }
        });
    }

    /**
     * Forget the committed cost for one chunk, so the next calculation commits a fresh one.
     *
     * <p>This is the storage half of a deliberate re-roll (#83). It is only ever reached
     * after {@link me.chunklock.economy.ChunkPriceRerollService} has taken payment, and
     * after an unlock completes.</p>
     */
    public void clearStoredCost(Player player, Chunk chunk) {
        String cacheKey = getCacheKey(chunk, player.getUniqueId());
        memoryCache.remove(cacheKey);

        CompletableFuture.runAsync(() -> {
            if (connection == null) {
                return;
            }
            try {
                String sql = "DELETE FROM chunk_costs WHERE world_name = ? AND chunk_x = ? "
                    + "AND chunk_z = ? AND player_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, chunk.getWorld().getName());
                    stmt.setInt(2, chunk.getX());
                    stmt.setInt(3, chunk.getZ());
                    stmt.setString(4, player.getUniqueId().toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to clear stored cost: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate a configuration hash to detect config changes
     */
    public String generateConfigHash() {
        // Every setting that can change a calculated price belongs here. Under #83 a stored
        // requirement is honored indefinitely, so this hash is now the ONLY mechanism that
        // re-derives prices after an admin retunes the economy. Anything omitted becomes a
        // price that can never be corrected without clearing the table by hand.
        var config = plugin.getConfig();
        StringBuilder configData = new StringBuilder();
        configData.append(config.getString("economy.type", "materials")).append('|');
        configData.append(config.getString("economy.materials.cost-mode", "biome")).append('|');
        configData.append(config.getDouble("economy.vault.base-cost", 100.0)).append('|');
        configData.append(config.getDouble("economy.vault.cost-per-unlocked", 25.0)).append('|');
        configData.append(config.getInt("economy.materials.resource-scan.base-cost", 16)).append('|');
        configData.append(config.getInt("economy.materials.resource-scan.min-cost", 1)).append('|');
        configData.append(config.getInt("economy.materials.resource-scan.max-cost", 128)).append('|');
        configData.append(config.getInt("economy.materials.resource-scan.min-abundance", 10));

        return String.valueOf(configData.toString().hashCode());
    }
    
    /**
     * Close database connection
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Closed chunk cost database connection");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing database: " + e.getMessage());
        }
    }
    
    private String getCacheKey(Chunk chunk, UUID playerId) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + "," + chunk.getZ() + ":" + playerId;
    }
    
    private EconomyManager.PaymentRequirement createRequirementFromResult(ResultSet rs) throws SQLException {
        EconomyManager.EconomyType type = EconomyManager.EconomyType.fromString(rs.getString("cost_type"));
        double vaultCost = rs.getDouble("vault_cost");
        String materialName = rs.getString("material_type");
        int materialAmount = rs.getInt("material_amount");
        
        if (type == EconomyManager.EconomyType.VAULT) {
            return new EconomyManager.PaymentRequirement(vaultCost);
        } else {
            Material material = materialName != null ? Material.valueOf(materialName) : Material.WHEAT;
            return new EconomyManager.PaymentRequirement(material, materialAmount);
        }
    }
    
    /**
     * Inner class for memory cache
     */
    private static class CachedChunkCost {
        final EconomyManager.PaymentRequirement requirement;
        final String configHash;
        final long cachedAt;
        
        CachedChunkCost(EconomyManager.PaymentRequirement requirement, String configHash, long cachedAt) {
            this.requirement = requirement;
            this.configHash = configHash;
            this.cachedAt = cachedAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > MEMORY_CACHE_TTL;
        }
    }
}
