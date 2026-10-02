package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.ChunkData;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

public class ChunkDatabase implements ChunkStore {
    
    private final ChunklockPlugin plugin;
    private final File databaseFile;
    private DB db;
    private HTreeMap<String, ChunkData> chunkMap;
    
    // In-memory cache for frequently accessed chunks
    private final Map<String, ChunkData> memoryCache = new ConcurrentHashMap<>();
    private static final long MEMORY_CACHE_TTL = 5 * 60 * 1000; // 5 minutes
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    // Commits are grouped: a transactional commit forces a disk sync, and
    // saveChunk runs on the main thread for every chunk the plugin evaluates.
    // One commit per chunk froze the server for hundreds of milliseconds when
    // borders were scanned. Writes now land in the transaction and a task
    // commits them once per second, plus once on close.
    private static final long COMMIT_INTERVAL_TICKS = 20L;
    private volatile boolean dirty = false;
    private BukkitTask commitTask;

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

            commitTask = Bukkit.getScheduler().runTaskTimer(plugin, this::commitIfDirty,
                    COMMIT_INTERVAL_TICKS, COMMIT_INTERVAL_TICKS);

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
            dirty = true;
            
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
            dirty = true;
            
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

    private void commitIfDirty() {
        if (!dirty || db == null || db.isClosed()) {
            return;
        }
        dirty = false;
        try {
            db.commit();
        } catch (Exception e) {
            dirty = true;
            plugin.getLogger().severe("Failed to commit chunk database: " + e.getMessage());
        }
    }

    public void close() {
        if (commitTask != null) {
            commitTask.cancel();
            commitTask = null;
        }
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

