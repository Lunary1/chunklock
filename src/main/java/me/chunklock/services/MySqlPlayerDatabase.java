package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.PlayerData;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MySqlPlayerDatabase implements PlayerStore {

    private final ChunklockPlugin plugin;
    private final MySqlConnectionProvider connectionProvider;
    private final long cacheTtlMs;

    private final Map<String, PlayerData> memoryCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public MySqlPlayerDatabase(ChunklockPlugin plugin, MySqlConnectionProvider connectionProvider, long cacheTtlMs) {
        this.plugin = plugin;
        this.connectionProvider = connectionProvider;
        this.cacheTtlMs = cacheTtlMs;
    }

    @Override
    public boolean initialize() {
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "player_uuid CHAR(36) NOT NULL PRIMARY KEY," +
                "spawn_world VARCHAR(128) NULL," +
                "spawn_x INT NOT NULL," +
                "spawn_y INT NOT NULL," +
                "spawn_z INT NOT NULL," +
                "unlocked_chunks INT NOT NULL," +
                "updated_at BIGINT NOT NULL" +
                ")";

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
            plugin.getLogger().info("✅ MySQL PlayerStore initialized");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to initialize MySQL PlayerStore: " + e.getMessage());
            return false;
        }
    }

    @Override
    public PlayerData getPlayerData(UUID playerId) {
        return getPlayerData(playerId.toString());
    }

    @Override
    public PlayerData getPlayerData(String playerIdStr) {
        PlayerData cached = memoryCache.get(playerIdStr);
        Long cacheTime = cacheTimestamps.get(playerIdStr);
        if (cached != null && cacheTime != null && System.currentTimeMillis() - cacheTime < cacheTtlMs) {
            return cached;
        }

        String sql = "SELECT spawn_world, spawn_x, spawn_y, spawn_z, unlocked_chunks, updated_at FROM player_data WHERE player_uuid = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerIdStr);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                PlayerData data = fromResultSet(resultSet);
                memoryCache.put(playerIdStr, data);
                cacheTimestamps.put(playerIdStr, System.currentTimeMillis());
                return data;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load player data from MySQL: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void savePlayerData(UUID playerId, PlayerData data) {
        savePlayerData(playerId.toString(), data);
    }

    @Override
    public void savePlayerData(String playerIdStr, PlayerData data) {
        if (data == null) {
            return;
        }

        data.setUpdatedAt(System.currentTimeMillis());

        String sql = "INSERT INTO player_data (player_uuid, spawn_world, spawn_x, spawn_y, spawn_z, unlocked_chunks, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE spawn_world=VALUES(spawn_world), spawn_x=VALUES(spawn_x), spawn_y=VALUES(spawn_y), " +
                "spawn_z=VALUES(spawn_z), unlocked_chunks=VALUES(unlocked_chunks), updated_at=VALUES(updated_at)";

        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerIdStr);
            bindPlayerData(statement, data);
            statement.executeUpdate();

            memoryCache.put(playerIdStr, data);
            cacheTimestamps.put(playerIdStr, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save player data to MySQL: " + e.getMessage());
        }
    }

    @Override
    public void deletePlayerData(UUID playerId) {
        deletePlayerData(playerId.toString());
    }

    @Override
    public void deletePlayerData(String playerIdStr) {
        String sql = "DELETE FROM player_data WHERE player_uuid = ?";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerIdStr);
            statement.executeUpdate();
            memoryCache.remove(playerIdStr);
            cacheTimestamps.remove(playerIdStr);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete player data from MySQL: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getAllPlayerIds() {
        Set<String> ids = new HashSet<>();
        String sql = "SELECT player_uuid FROM player_data";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ids.add(resultSet.getString("player_uuid"));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load player ids from MySQL: " + e.getMessage());
        }
        return ids;
    }

    @Override
    public int getTotalPlayers() {
        String sql = "SELECT COUNT(*) FROM player_data";
        try (Connection connection = connectionProvider.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to count players in MySQL: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public Location getSpawnLocation(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        if (data == null || data.getSpawnWorld() == null) {
            return null;
        }

        org.bukkit.World world = plugin.getServer().getWorld(data.getSpawnWorld());
        if (world == null) {
            return null;
        }

        return new Location(world, data.getSpawnX(), data.getSpawnY(), data.getSpawnZ());
    }

    @Override
    public void setSpawnLocation(UUID playerId, Location location) {
        PlayerData data = getPlayerData(playerId);
        if (data == null) {
            data = new PlayerData();
        }

        data.setSpawnWorld(location.getWorld().getName());
        data.setSpawnX(location.getBlockX());
        data.setSpawnY(location.getBlockY());
        data.setSpawnZ(location.getBlockZ());

        savePlayerData(playerId, data);
    }

    @Override
    public int getUnlockedChunks(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        return data != null ? data.getUnlockedChunks() : 0;
    }

    @Override
    public void setUnlockedChunks(UUID playerId, int count) {
        PlayerData data = getPlayerData(playerId);
        if (data == null) {
            data = new PlayerData();
        }

        data.setUnlockedChunks(count);
        savePlayerData(playerId, data);
    }

    @Override
    public void incrementUnlockedChunks(UUID playerId) {
        int current = getUnlockedChunks(playerId);
        setUnlockedChunks(playerId, current + 1);
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

    private PlayerData fromResultSet(ResultSet resultSet) throws Exception {
        MySqlDataMapper.PlayerRow row = new MySqlDataMapper.PlayerRow(
                null,
                resultSet.getString("spawn_world"),
                resultSet.getInt("spawn_x"),
                resultSet.getInt("spawn_y"),
                resultSet.getInt("spawn_z"),
                resultSet.getInt("unlocked_chunks"),
                resultSet.getLong("updated_at")
        );
        return MySqlDataMapper.toPlayerData(row);
    }

    private void bindPlayerData(PreparedStatement statement, PlayerData data) throws Exception {
        MySqlDataMapper.PlayerRow row = MySqlDataMapper.fromPlayerData(null, data);
        statement.setString(2, row.spawnWorld);
        statement.setInt(3, row.spawnX);
        statement.setInt(4, row.spawnY);
        statement.setInt(5, row.spawnZ);
        statement.setInt(6, row.unlockedChunks);
        statement.setLong(7, row.updatedAt);
    }
}
