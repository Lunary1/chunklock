// ChunkLockManager.java
package me.chunklock;

import org.bukkit.Chunk;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ChunkLockManager {

    private final Map<String, ChunkData> chunkDataMap = new HashMap<>();
    private final Random random = new Random();

    public boolean isLocked(Chunk chunk) {
        return getChunkData(chunk).isLocked();
    }

    public Difficulty getDifficulty(Chunk chunk) {
        return getChunkData(chunk).getDifficulty();
    }

    public void lockChunk(Chunk chunk, Difficulty difficulty) {
        String key = getChunkKey(chunk);
        chunkDataMap.put(key, new ChunkData(true, difficulty));
    }

    public void unlockChunk(Chunk chunk) {
        ChunkData data = getChunkData(chunk);
        data.setLocked(false);
    }

    public void initializeChunk(Chunk chunk) {
        String key = getChunkKey(chunk);
        if (!chunkDataMap.containsKey(key)) {
            Difficulty difficulty = randomizeDifficulty();
            chunkDataMap.put(key, new ChunkData(true, difficulty));
        }
    }

    private Difficulty randomizeDifficulty() {
        int roll = random.nextInt(100);
        if (roll < 50) return Difficulty.EASY;
        if (roll < 80) return Difficulty.NORMAL;
        if (roll < 95) return Difficulty.HARD;
        return Difficulty.IMPOSSIBLE;
    }

    private ChunkData getChunkData(Chunk chunk) {
        initializeChunk(chunk);
        return chunkDataMap.get(getChunkKey(chunk));
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}