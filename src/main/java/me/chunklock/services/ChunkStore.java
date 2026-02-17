package me.chunklock.services;

import me.chunklock.models.ChunkData;
import org.bukkit.Chunk;

import java.util.Set;
import java.util.UUID;

public interface ChunkStore {

    boolean initialize();

    ChunkData getChunk(Chunk chunk);

    ChunkData getChunk(String chunkKey);

    void saveChunk(Chunk chunk, ChunkData data);

    void saveChunk(String chunkKey, ChunkData data);

    void deleteChunk(Chunk chunk);

    void deleteChunk(String chunkKey);

    Set<String> getChunksByOwner(UUID ownerId);

    Set<String> getAllChunkKeys();

    int getTotalChunks();

    int getUnlockedChunksCount();

    String getChunkKey(Chunk chunk);

    String getChunkKey(String worldName, int x, int z);

    void close();

    void clearCache();
}
