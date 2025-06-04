package me.chunklock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressTracker {
    private final Map<UUID, Integer> unlockedChunkCount = new HashMap<>();

    public void incrementUnlockedChunks(UUID playerId) {
        unlockedChunkCount.put(playerId, getUnlockedChunkCount(playerId) + 1);
    }

    public int getUnlockedChunkCount(UUID playerId) {
        return unlockedChunkCount.getOrDefault(playerId, 0);
    }
    public void resetUnlockedChunks(UUID playerId) {
        unlockedChunkCount.put(playerId, 0);
    }
    public void resetPlayer(UUID playerId) {
        unlockedChunkCount.remove(playerId);
    }
}