package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.PlayerData;
import org.bukkit.Location;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

public class PlayerDatabase implements PlayerStore {
    
    private final ChunklockPlugin plugin;
    private final File databaseFile;
    private DB db;
    private HTreeMap<String, PlayerData> playerMap;
    
    // In-memory cache for frequently accessed players
    private final Map<String, PlayerData> memoryCache = new ConcurrentHashMap<>();
    private static final long MEMORY_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public PlayerDatabase(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "players.db");
    }

    public boolean initialize() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            db = DBMaker.fileDB(databaseFile)
                    .fileMmapEnableIfSupported()
                    .transactionEnable()
                    .closeOnJvmShutdown()
                    .cleanerHackEnable()
                    .make();

            @SuppressWarnings("unchecked")
            HTreeMap<String, PlayerData> map = (HTreeMap<String, PlayerData>) db.hashMap("players")
                    .keySerializer(org.mapdb.Serializer.STRING)
                    .valueSerializer(org.mapdb.Serializer.JAVA)
                    .createOrOpen();
            playerMap = map;

            plugin.getLogger().info("✅ PlayerDatabase initialized: " + databaseFile.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to initialize PlayerDatabase: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public PlayerData getPlayerData(UUID playerId) {
        String key = playerId.toString();
        return getPlayerData(key);
    }

    public PlayerData getPlayerData(String playerIdStr) {
        // Check memory cache first
        PlayerData cached = memoryCache.get(playerIdStr);
        Long cacheTime = cacheTimestamps.get(playerIdStr);
        if (cached != null && cacheTime != null && 
            System.currentTimeMillis() - cacheTime < MEMORY_CACHE_TTL) {
            return cached;
        }

        // Get from database
        PlayerData data = playerMap.get(playerIdStr);
        if (data != null) {
            // Update cache
            memoryCache.put(playerIdStr, data);
            cacheTimestamps.put(playerIdStr, System.currentTimeMillis());
        }
        return data;
    }

    public void savePlayerData(UUID playerId, PlayerData data) {
        String key = playerId.toString();
        savePlayerData(key, data);
    }

    public void savePlayerData(String playerIdStr, PlayerData data) {
        try {
            data.setUpdatedAt(System.currentTimeMillis());
            playerMap.put(playerIdStr, data);
            db.commit();
            
            // Update cache
            memoryCache.put(playerIdStr, data);
            cacheTimestamps.put(playerIdStr, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save player data to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deletePlayerData(UUID playerId) {
        String key = playerId.toString();
        deletePlayerData(key);
    }

    public void deletePlayerData(String playerIdStr) {
        try {
            playerMap.remove(playerIdStr);
            db.commit();
            
            // Remove from cache
            memoryCache.remove(playerIdStr);
            cacheTimestamps.remove(playerIdStr);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete player data from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Set<String> getAllPlayerIds() {
        return new HashSet<>(playerMap.keySet());
    }

    public int getTotalPlayers() {
        return playerMap.size();
    }

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

    public int getUnlockedChunks(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        return data != null ? data.getUnlockedChunks() : 0;
    }

    public void setUnlockedChunks(UUID playerId, int count) {
        PlayerData data = getPlayerData(playerId);
        if (data == null) {
            data = new PlayerData();
        }
        
        data.setUnlockedChunks(count);
        savePlayerData(playerId, data);
    }

    public void incrementUnlockedChunks(UUID playerId) {
        int current = getUnlockedChunks(playerId);
        setUnlockedChunks(playerId, current + 1);
    }

    public void close() {
        try {
            if (db != null && !db.isClosed()) {
                // Commit any pending transactions before closing
                db.commit();
                db.close();
                plugin.getLogger().info("Closed PlayerDatabase connection");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error closing PlayerDatabase: " + e.getMessage());
        } finally {
            // Clear cache on close
            clearCache();
        }
    }

    public void clearCache() {
        memoryCache.clear();
        cacheTimestamps.clear();
    }
}

