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
 * Persistent SQLite database for storing chunk cost calculations.
 * Dramatically improves performance by avoiding repeated AI calculations.
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
        this.databaseFile = new File(plugin.getDataFolder(), "chunk_costs.db");
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
            
            // Create database connection
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            // Create the table
            createTables();
            
            plugin.getLogger().info("Chunk cost database initialized: " + databaseFile.getName());
            return true;
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize chunk cost database: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create the database tables
     */
    private void createTables() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS chunk_costs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                world_name TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                player_id TEXT NOT NULL,
                biome TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                score INTEGER NOT NULL,
                cost_type TEXT NOT NULL,
                vault_cost REAL,
                material_type TEXT,
                material_amount INTEGER,
                ai_processed BOOLEAN NOT NULL,
                ai_explanation TEXT,
                calculated_at INTEGER NOT NULL,
                config_hash TEXT NOT NULL,
                UNIQUE(world_name, chunk_x, chunk_z, player_id, config_hash)
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
                            
                            // Check if cost is still valid (not older than 1 hour)
                            if (System.currentTimeMillis() - calculatedAt < 60 * 60 * 1000) {
                                EconomyManager.PaymentRequirement requirement = createRequirementFromResult(rs);
                                
                                // Cache in memory for quick access
                                memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, calculatedAt));
                                
                                plugin.getLogger().fine("Retrieved cost from database for " + cacheKey);
                                return requirement;
                            }
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
            try {
                String sql = """
                    INSERT OR REPLACE INTO chunk_costs 
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
     * Clean up old cached costs
     */
    public void cleanupOldCosts() {
        CompletableFuture.runAsync(() -> {
            try {
                long cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours
                
                String sql = "DELETE FROM chunk_costs WHERE calculated_at < ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setLong(1, cutoff);
                    int deleted = stmt.executeUpdate();
                    
                    if (deleted > 0) {
                        plugin.getLogger().info("Cleaned up " + deleted + " old cached costs");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to cleanup old costs: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate a configuration hash to detect config changes
     */
    public String generateConfigHash() {
        // Create a hash based on key configuration values that affect cost calculation
        StringBuilder configData = new StringBuilder();
        configData.append(plugin.getConfig().getString("economy.type", "materials"));
        configData.append(plugin.getConfig().getDouble("economy.vault.base-cost", 100.0));
        configData.append(plugin.getConfig().getBoolean("openai-agent.enabled", false));
        configData.append(plugin.getConfig().getString("openai-agent.model", "gpt-4o-mini"));
        
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
