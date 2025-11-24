package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.ChunkData;
import org.bukkit.Chunk;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

public class ChunkDatabase {
    
    private final ChunklockPlugin plugin;
    private final File databaseFile;
    private DB db;
    private HTreeMap<String, ChunkData> chunkMap;
    
    // In-memory cache for frequently accessed chunks
    private final Map<String, ChunkData> memoryCache = new ConcurrentHashMap<>();
    private static final long MEMORY_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public ChunkDatabase(ChunklockPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "chunks.db");
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
            HTreeMap<String, ChunkData> map = (HTreeMap<String, ChunkData>) db.hashMap("chunks")
                    .keySerializer(org.mapdb.Serializer.STRING)
                    .valueSerializer(org.mapdb.Serializer.JAVA)
                    .createOrOpen();
            chunkMap = map;

            plugin.getLogger().info("✅ ChunkDatabase initialized: " + databaseFile.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to initialize ChunkDatabase: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public ChunkData getChunk(Chunk chunk) {
        String key = getChunkKey(chunk);
        return getChunk(key);
    }

    public ChunkData getChunk(String chunkKey) {
        // Check memory cache first
        ChunkData cached = memoryCache.get(chunkKey);
        Long cacheTime = cacheTimestamps.get(chunkKey);
        if (cached != null && cacheTime != null && 
            System.currentTimeMillis() - cacheTime < MEMORY_CACHE_TTL) {
            return cached;
        }

        // Get from database
        ChunkData data = chunkMap.get(chunkKey);
        if (data != null) {
            // Update cache
            memoryCache.put(chunkKey, data);
            cacheTimestamps.put(chunkKey, System.currentTimeMillis());
        }
        return data;
    }

    public void saveChunk(Chunk chunk, ChunkData data) {
        String key = getChunkKey(chunk);
        saveChunk(key, data);
    }

    public void saveChunk(String chunkKey, ChunkData data) {
        try {
            chunkMap.put(chunkKey, data);
            db.commit();
            
            // Update cache
            memoryCache.put(chunkKey, data);
            cacheTimestamps.put(chunkKey, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save chunk to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void deleteChunk(Chunk chunk) {
        String key = getChunkKey(chunk);
        deleteChunk(key);
    }

    public void deleteChunk(String chunkKey) {
        try {
            chunkMap.remove(chunkKey);
            db.commit();
            
            // Remove from cache
            memoryCache.remove(chunkKey);
            cacheTimestamps.remove(chunkKey);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete chunk from database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Set<String> getChunksByOwner(UUID ownerId) {
        Set<String> chunks = new HashSet<>();
        if (ownerId == null) {
            return chunks;
        }
        
        String ownerIdStr = ownerId.toString();
        for (Map.Entry<String, ChunkData> entry : chunkMap.entrySet()) {
            ChunkData data = entry.getValue();
            if (data.getOwnerId() != null && data.getOwnerId().toString().equals(ownerIdStr)) {
                chunks.add(entry.getKey());
            }
        }
        return chunks;
    }

    public Set<String> getAllChunkKeys() {
        return new HashSet<>(chunkMap.keySet());
    }

    public int getTotalChunks() {
        return chunkMap.size();
    }

    public int getUnlockedChunksCount() {
        int count = 0;
        for (ChunkData data : chunkMap.values()) {
            if (!data.isLocked()) {
                count++;
            }
        }
        return count;
    }

    public String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    public String getChunkKey(String worldName, int x, int z) {
        return worldName + ":" + x + ":" + z;
    }

    public void close() {
        try {
            if (db != null && !db.isClosed()) {
                // Commit any pending transactions before closing
                db.commit();
                db.close();
                plugin.getLogger().info("Closed ChunkDatabase connection");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error closing ChunkDatabase: " + e.getMessage());
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

