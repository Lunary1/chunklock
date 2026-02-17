package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.ChunkData;
import org.bukkit.Chunk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MySqlChunkDatabase implements ChunkStore {

    private final ChunklockPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final long cacheTtlMs;

    private final Map<String, ChunkData> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public MySqlChunkDatabase(ChunklockPlugin plugin, MySqlConnectionProvider connectionProvider, long cacheTtlMs) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public boolean initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS chunk_data (" +
                "world_name VARCHAR(128) NOT NULL," +
                "chunk_x INT NOT NULL," +
                "chunk_z INT NOT NULL," +
                "locked BOOLEAN NOT NULL," +
                "difficulty VARCHAR(32) NOT NULL," +
                "owner_uuid CHAR(36) NULL," +
                "base_value INT NOT NULL," +
                "biome VARCHAR(128) NULL," +
                "score INT NOT NULL," +
                "unlocked_at BIGINT NULL," +
                "PRIMARY KEY (world_name, chunk_x, chunk_z)," +
                "INDEX idx_chunk_owner (owner_uuid)," +
                "INDEX idx_chunk_locked (locked)" +
                ")";

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
            plugin.getLogger().info("✅ MySQL ChunkStore initialized");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to initialize MySQL ChunkStore: " + e.getMessage());
            return false;
        }
    }

    @Override
    public ChunkData getChunk(Chunk chunk) {
        return getChunk(getChunkKey(chunk));
    }

    @Override
    public ChunkData getChunk(String chunkKey) {
        ChunkData cached = memoryCache.get(chunkKey);
        Long cacheTime = cacheTimestamps.get(chunkKey);
        if (cached != null && cacheTime != null && System.currentTimeMillis() - cacheTime < cacheTtlMs) {
            return cached;
        }

        ChunkKeyParts parts = parseChunkKey(chunkKey);
        if (parts == null) {
            return null;
        }

        String sql = "SELECT locked, difficulty, owner_uuid, base_value, biome, score, unlocked_at FROM chunk_data " +
                "WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parts.worldName);
            statement.setInt(2, parts.x);
            statement.setInt(3, parts.z);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ChunkData data = fromResultSet(resultSet);
                memoryCache.put(chunkKey, data);
                cacheTimestamps.put(chunkKey, System.currentTimeMillis());
                return data;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load chunk from MySQL: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void saveChunk(Chunk chunk, ChunkData data) {
        saveChunk(getChunkKey(chunk), data);
    }

    @Override
    public void saveChunk(String chunkKey, ChunkData data) {
        ChunkKeyParts parts = parseChunkKey(chunkKey);
        if (parts == null || data == null) {
            return;
        }

        String sql = "INSERT INTO chunk_data (world_name, chunk_x, chunk_z, locked, difficulty, owner_uuid, base_value, biome, score, unlocked_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE locked=VALUES(locked), difficulty=VALUES(difficulty), owner_uuid=VALUES(owner_uuid), " +
                "base_value=VALUES(base_value), biome=VALUES(biome), score=VALUES(score), unlocked_at=VALUES(unlocked_at)";

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parts.worldName);
            statement.setInt(2, parts.x);
            statement.setInt(3, parts.z);
            bindChunkData(statement, data);
            statement.executeUpdate();

            memoryCache.put(chunkKey, data);
            cacheTimestamps.put(chunkKey, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save chunk to MySQL: " + e.getMessage());
        }
    }

    @Override
    public void deleteChunk(Chunk chunk) {
        deleteChunk(getChunkKey(chunk));
    }

    @Override
    public void deleteChunk(String chunkKey) {
        ChunkKeyParts parts = parseChunkKey(chunkKey);
        if (parts == null) {
            return;
        }

        String sql = "DELETE FROM chunk_data WHERE world_name = ? AND chunk_x = ? AND chunk_z = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parts.worldName);
            statement.setInt(2, parts.x);
            statement.setInt(3, parts.z);
            statement.executeUpdate();

            memoryCache.remove(chunkKey);
            cacheTimestamps.remove(chunkKey);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete chunk from MySQL: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getChunksByOwner(UUID ownerId) {
        Set<String> chunks = new HashSet<>();
        if (ownerId == null) {
            return chunks;
        }

        String sql = "SELECT world_name, chunk_x, chunk_z FROM chunk_data WHERE owner_uuid = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    chunks.add(getChunkKey(resultSet.getString("world_name"), resultSet.getInt("chunk_x"), resultSet.getInt("chunk_z")));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to query chunks by owner from MySQL: " + e.getMessage());
        }
        return chunks;
    }

    @Override
    public Set<String> getAllChunkKeys() {
        Set<String> keys = new HashSet<>();
        String sql = "SELECT world_name, chunk_x, chunk_z FROM chunk_data";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                keys.add(getChunkKey(resultSet.getString("world_name"), resultSet.getInt("chunk_x"), resultSet.getInt("chunk_z")));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load chunk keys from MySQL: " + e.getMessage());
        }
        return keys;
    }

    @Override
    public int getTotalChunks() {
        String sql = "SELECT COUNT(*) FROM chunk_data";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to count chunks in MySQL: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getUnlockedChunksCount() {
        String sql = "SELECT COUNT(*) FROM chunk_data WHERE locked = FALSE";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to count unlocked chunks in MySQL: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    @Override
    public String getChunkKey(String worldName, int x, int z) {
        return worldName + ":" + x + ":" + z;
    }

    @Override
    public void close() {
        clearCache();
    }

    @Override
    public void clearCache() {
        memoryCache.clear();
        cacheTimestamps.clear();
    }

    private ChunkData fromResultSet(ResultSet resultSet) throws Exception {
        long unlockedAtValue = resultSet.getLong("unlocked_at");
        Long unlockedAt = resultSet.wasNull() ? null : unlockedAtValue;
        MySqlDataMapper.ChunkRow row = new MySqlDataMapper.ChunkRow(
                null,
                0,
                0,
                resultSet.getBoolean("locked"),
                resultSet.getString("difficulty"),
                resultSet.getString("owner_uuid"),
                resultSet.getInt("base_value"),
                resultSet.getString("biome"),
                resultSet.getInt("score"),
                unlockedAt
        );
        return MySqlDataMapper.toChunkData(row);
    }

    private void bindChunkData(PreparedStatement statement, ChunkData data) throws Exception {
        MySqlDataMapper.ChunkRow row = MySqlDataMapper.fromChunkData(null, 0, 0, data);
        statement.setBoolean(4, row.locked);
        statement.setString(5, row.difficulty);
        if (row.ownerUuid == null) {
            statement.setNull(6, java.sql.Types.CHAR);
        } else {
            statement.setString(6, row.ownerUuid);
        }
        statement.setInt(7, row.baseValue);
        statement.setString(8, row.biome);
        statement.setInt(9, row.score);
        if (row.unlockedAt == null) {
            statement.setNull(10, java.sql.Types.BIGINT);
        } else {
            statement.setLong(10, row.unlockedAt);
        }
    }

    public static ChunkKeyParts parseChunkKey(String chunkKey) {
        if (chunkKey == null || chunkKey.isEmpty()) {
            return null;
        }
        String[] parts = chunkKey.split(":", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            return new ChunkKeyParts(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static class ChunkKeyParts {
        public final String worldName;
        public final int x;
        public final int z;

        public ChunkKeyParts(String worldName, int x, int z) {
            this.worldName = worldName;
            this.x = x;
            this.z = z;
        }
    }
}
