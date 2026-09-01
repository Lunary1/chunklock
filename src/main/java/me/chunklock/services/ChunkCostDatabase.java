package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.EconomyManager;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Committed chunk prices (#83) and the queries over them.
 *
 * <p>Storage is whatever {@code database.type} selects - H2 by default, MySQL when the server
 * is configured for it. This class no longer opens a database itself; it asks a
 * {@link CostStorageBackend} for a connection per unit of work. Before #95 it opened an H2
 * file unconditionally, so a MySQL server ran two backends and a network gave each node its
 * own private prices.</p>
 *
 * <p>SQL that differs between the engines lives in {@link SqlDialect}. Everything here is
 * plain enough to be shared.</p>
 */
public class ChunkCostDatabase {

    private final ChunklockPlugin plugin;
    private final CostStorageBackend backend;

    // In-memory cache for frequently accessed costs
    private final Map<String, CachedChunkCost> memoryCache = new ConcurrentHashMap<>();
    private static final long MEMORY_CACHE_TTL = 5 * 60 * 1000; // 5 minutes

    public ChunkCostDatabase(ChunklockPlugin plugin, CostStorageBackend backend) {
        this.plugin = plugin;
        this.backend = backend;
    }

    /**
     * Open the underlying storage.
     *
     * @return true when pricing persistence is available; false leaves the plugin running
     *         without it rather than failing startup
     */
    public boolean initialize() {
        return backend != null && backend.initialize();
    }

    /**
     * The backend, for sibling stores that share it.
     *
     * <p>Package-private on purpose: {@link ChunkProfileStore} keeps #86's profiles in the
     * same database so both follow {@code database.type} together and share one lifecycle.</p>
     */
    CostStorageBackend getBackend() {
        return backend;
    }

    private boolean unavailable() {
        return backend == null || !backend.isAvailable();
    }

    /**
     * Get the committed cost for a chunk, or null when none is stored.
     */
    public CompletableFuture<EconomyManager.PaymentRequirement> getCachedCost(Player player, Chunk chunk, String configHash) {
        // Read the chunk's coordinates on the calling thread. Bukkit's Chunk and Player are
        // not safe to touch from the async task below, and the values are all that is needed.
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        UUID playerId = player.getUniqueId();
        String cacheKey = getCacheKey(worldName, chunkX, chunkZ, playerId);

        return CompletableFuture.supplyAsync(() -> {
            CachedChunkCost memoryCached = memoryCache.get(cacheKey);
            if (memoryCached != null && !memoryCached.isExpired() && memoryCached.configHash.equals(configHash)) {
                plugin.getLogger().fine("Retrieved cost from memory cache for " + cacheKey);
                return memoryCached.requirement;
            }

            if (unavailable()) {
                plugin.getLogger().fine("Pricing storage unavailable, skipping cache lookup for " + cacheKey);
                return null; // Cache miss; the caller recalculates.
            }

            String sql = """
                SELECT vault_cost, material_type, material_amount, cost_type, ai_processed, ai_explanation, calculated_at
                FROM chunk_costs
                WHERE world_name = ? AND chunk_x = ? AND chunk_z = ? AND player_id = ? AND config_hash = ?
                ORDER BY calculated_at DESC LIMIT 1
            """;

            try (CostStorageBackend.BorrowedConnection borrowed = backend.borrow();
                 PreparedStatement stmt = borrowed.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                stmt.setString(4, playerId.toString());
                stmt.setString(5, configHash);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long calculatedAt = rs.getLong("calculated_at");

                        // No age check. A stored requirement is a commitment to the player
                        // (#83): once they have seen a price for a chunk, that price holds
                        // until they pay it or deliberately re-roll it. This used to expire
                        // after an hour, which is shorter than a real collection goal - a
                        // player grinding for diamonds would return to a different requirement
                        // through no action of their own, which is precisely the
                        // grind-invalidation #83 exists to stop. config_hash is still part of
                        // the lookup key, so an admin changing economy config still re-derives
                        // prices legitimately.
                        EconomyManager.PaymentRequirement requirement = createRequirementFromResult(rs);

                        memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, calculatedAt));

                        plugin.getLogger().fine("Retrieved committed cost from storage for " + cacheKey);
                        return requirement;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to retrieve cached cost: " + e.getMessage());
            }

            return null; // No valid cache found
        });
    }

    /**
     * Commit a calculated cost.
     */
    public void storeCost(Player player, Chunk chunk, EconomyManager.PaymentRequirement requirement,
                         String biome, String difficulty, int score, boolean aiProcessed,
                         String aiExplanation, String configHash) {

        // Snapshot everything off the Bukkit objects before leaving the calling thread.
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        UUID playerId = player.getUniqueId();
        String cacheKey = getCacheKey(worldName, chunkX, chunkZ, playerId);
        String materialName = requirement.getMaterial() != null
            ? me.chunklock.util.item.MaterialUtil.getMaterialName(requirement.getMaterial())
            : null;

        CompletableFuture.runAsync(() -> {
            // Cache in memory regardless, so a storage outage does not also cost the session
            // its prices.
            memoryCache.put(cacheKey, new CachedChunkCost(requirement, configHash, System.currentTimeMillis()));

            if (unavailable()) {
                plugin.getLogger().fine("Pricing storage unavailable, skipping cost storage");
                return;
            }

            // The upsert keys on unique_chunk_cost, never on the auto-increment id. Keying on
            // the id is what #90 was: every write failed with 90081 and chunk_costs stayed
            // empty on every server for months. Both dialects express that; see SqlDialect.
            String sql = backend.dialect().upsertChunkCost();

            try (CostStorageBackend.BorrowedConnection borrowed = backend.borrow();
                 PreparedStatement stmt = borrowed.get().prepareStatement(sql)) {

                stmt.setString(1, worldName);
                stmt.setInt(2, chunkX);
                stmt.setInt(3, chunkZ);
                stmt.setString(4, playerId.toString());
                stmt.setString(5, biome);
                stmt.setString(6, difficulty);
                stmt.setInt(7, score);
                stmt.setString(8, requirement.getType().getConfigName());
                stmt.setDouble(9, requirement.getVaultCost());
                stmt.setString(10, materialName);
                stmt.setInt(11, requirement.getMaterialAmount());
                stmt.setBoolean(12, aiProcessed);
                stmt.setString(13, aiExplanation);
                stmt.setLong(14, System.currentTimeMillis());
                stmt.setString(15, configHash);

                stmt.executeUpdate();

                plugin.getLogger().fine("Stored cost for " + cacheKey);
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to store cost: " + e.getMessage());
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
        String currentHash = generateConfigHash();

        CompletableFuture.runAsync(() -> {
            if (unavailable()) {
                plugin.getLogger().fine("Pricing storage unavailable, skipping cleanup");
                return;
            }

            String sql = "DELETE FROM chunk_costs WHERE config_hash <> ?";
            try (CostStorageBackend.BorrowedConnection borrowed = backend.borrow();
                 PreparedStatement stmt = borrowed.get().prepareStatement(sql)) {

                stmt.setString(1, currentHash);
                int deleted = stmt.executeUpdate();

                if (deleted > 0) {
                    plugin.getLogger().info("Cleaned up " + deleted
                        + " chunk costs from superseded economy configurations");
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
        String worldName = chunk.getWorld().getName();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        UUID playerId = player.getUniqueId();

        memoryCache.remove(getCacheKey(worldName, chunkX, chunkZ, playerId));

        // Deliberately synchronous, unlike the other database work here.
        //
        // The caller re-opens the unlock GUI immediately after this returns, which
        // recalculates and reads the stored cost back. Running the DELETE asynchronously
        // races that read: the row is often still present, the old price is returned, and
        // the player sees the requirement they just paid to replace. Correctness of a paid
        // action beats saving a few milliseconds on a click the player initiated.
        if (unavailable()) {
            return;
        }

        String sql = "DELETE FROM chunk_costs WHERE world_name = ? AND chunk_x = ? "
            + "AND chunk_z = ? AND player_id = ?";
        try (CostStorageBackend.BorrowedConnection borrowed = backend.borrow();
             PreparedStatement stmt = borrowed.get().prepareStatement(sql)) {

            stmt.setString(1, worldName);
            stmt.setInt(2, chunkX);
            stmt.setInt(3, chunkZ);
            stmt.setString(4, playerId.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clear stored cost: " + e.getMessage());
        }
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
     * Close pricing storage.
     */
    public void close() {
        if (backend != null) {
            backend.close();
        }
    }

    private String getCacheKey(String worldName, int chunkX, int chunkZ, UUID playerId) {
        return worldName + ":" + chunkX + "," + chunkZ + ":" + playerId;
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
