package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChunkLockManager {

    private final Map<String, ChunkData> chunkDataMap = new HashMap<>();
    private final ChunkEvaluator chunkEvaluator;

    public ChunkLockManager(ChunkEvaluator chunkEvaluator) {
        this.chunkEvaluator = chunkEvaluator;
    }

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

    public void initializeChunk(Chunk chunk, UUID playerId) {
        String key = getChunkKey(chunk);
        if (!chunkDataMap.containsKey(key)) {
            // Use ChunkEvaluator to determine difficulty based on actual chunk properties
            ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(playerId, chunk);
            chunkDataMap.put(key, new ChunkData(true, evaluation.difficulty));
        }
    }

    // Overload for backward compatibility
    public void initializeChunk(Chunk chunk) {
        initializeChunk(chunk, null); // Will use fallback logic in ChunkEvaluator
    }

    private ChunkData getChunkData(Chunk chunk) {
        initializeChunk(chunk);
        return chunkDataMap.get(getChunkKey(chunk));
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    // Method to get chunk evaluation info for display
    public ChunkEvaluator.ChunkValueData evaluateChunk(UUID playerId, Chunk chunk) {
        return chunkEvaluator.evaluateChunk(playerId, chunk);
    }

    private final Set<UUID> bypassingPlayers = new HashSet<>();

    public void setBypassing(Player player, boolean bypassing) {
        if (bypassing) {
            bypassingPlayers.add(player.getUniqueId());
        } else {
            bypassingPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isBypassing(Player player) {
        return bypassingPlayers.contains(player.getUniqueId());
}
}