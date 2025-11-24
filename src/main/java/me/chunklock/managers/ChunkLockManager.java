package me.chunklock.managers;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import me.chunklock.services.ChunkDatabase;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.TeamManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ChunkLockManager {

    private final ChunkDatabase chunkDatabase;
    private final ChunkEvaluator chunkEvaluator;
    private final JavaPlugin plugin;
    private final TeamManager teamManager;

    private final double contestedCostMultiplier;
    private final int maxContestedClaimsPerDay;

    public ChunkLockManager(ChunkEvaluator chunkEvaluator, JavaPlugin plugin, TeamManager teamManager) {
        this.chunkEvaluator = chunkEvaluator;
        this.plugin = plugin;
        this.teamManager = teamManager;
        
        // Get ChunkDatabase from plugin instance
        if (plugin instanceof ChunklockPlugin) {
            this.chunkDatabase = ((ChunklockPlugin) plugin).getChunkDatabase();
        } else {
            throw new IllegalStateException("ChunkLockManager requires ChunklockPlugin instance");
        }

        this.contestedCostMultiplier = plugin.getConfig().getDouble("contested-cost-multiplier", 3.0);
        this.maxContestedClaimsPerDay = plugin.getConfig().getInt("max-contested-claims-per-day", 5);
    }

    public boolean isLocked(Chunk chunk) {
        return getChunkData(chunk).isLocked();
    }

    public Difficulty getDifficulty(Chunk chunk) {
        return getChunkData(chunk).getDifficulty();
    }

    public UUID getChunkOwner(Chunk chunk) {
        return getChunkData(chunk).getOwnerId();
    }

    public boolean isContestedChunk(Chunk chunk, UUID teamId) {
        UUID owner = getChunkOwner(chunk);
        return owner != null && !owner.equals(teamId);
    }

    public double getContestedCostMultiplier() {
        return contestedCostMultiplier;
    }

    public int getMaxContestedClaimsPerDay() {
        return maxContestedClaimsPerDay;
    }

    public void lockChunk(Chunk chunk, Difficulty difficulty) {
        String key = getChunkKey(chunk);
        ChunkData existing = chunkDatabase.getChunk(key);
        if (existing != null) {
            existing.setLocked(true);
            existing.setDifficulty(difficulty);
            chunkDatabase.saveChunk(key, existing);
        } else {
            ChunkData newData = ChunkData.builder()
                    .locked(true)
                    .difficulty(difficulty)
                    .build();
            chunkDatabase.saveChunk(key, newData);
        }
    }

    public void unlockChunk(Chunk chunk, UUID ownerId) {
        ChunkData data = getChunkData(chunk);
        data.setLocked(false);
        data.setOwnerId(ownerId);
        data.setUnlockedAt(System.currentTimeMillis());
        chunkDatabase.saveChunk(chunk, data);
    }

    // Backwards compatibility
    public void unlockChunk(Chunk chunk) {
        unlockChunk(chunk, null);
    }

    /**
     * Resets all chunks for a specific player by re-locking everything except their new starting chunk
     */
    public void resetPlayerChunks(UUID playerId, Chunk newStartingChunk) {
        plugin.getLogger().info("Resetting all chunks for player " + playerId + ", keeping chunk " + 
            newStartingChunk.getX() + "," + newStartingChunk.getZ() + " unlocked");
        
        int chunksLocked = 0;
        String startingChunkKey = getChunkKey(newStartingChunk);
        
        // Get all chunks owned by this player
        Set<String> playerChunks = chunkDatabase.getChunksByOwner(playerId);
        
        // Re-lock all chunks except the new starting chunk
        for (String chunkKey : playerChunks) {
            if (!chunkKey.equals(startingChunkKey)) {
                ChunkData chunkData = chunkDatabase.getChunk(chunkKey);
                if (chunkData != null && !chunkData.isLocked()) {
                    chunkData.setLocked(true);
                    chunkDatabase.saveChunk(chunkKey, chunkData);
                    chunksLocked++;
                }
            }
        }
        
        // Ensure the starting chunk is unlocked
        ChunkData startingData = getChunkData(newStartingChunk);
        startingData.setLocked(false);
        chunkDatabase.saveChunk(newStartingChunk, startingData);
        
        plugin.getLogger().info("Re-locked " + chunksLocked + " chunks for player " + playerId);
    }

    /**
     * Gets all unlocked chunks in the world (for debugging/admin purposes)
     */
    public Set<String> getUnlockedChunks() {
        Set<String> unlockedChunks = new HashSet<>();
        for (String chunkKey : chunkDatabase.getAllChunkKeys()) {
            ChunkData data = chunkDatabase.getChunk(chunkKey);
            if (data != null && !data.isLocked()) {
                unlockedChunks.add(chunkKey);
            }
        }
        return unlockedChunks;
    }

    /**
     * Counts total unlocked chunks in the world
     */
    public int getTotalUnlockedChunks() {
        return chunkDatabase.getUnlockedChunksCount();
    }

    /**
     * Forces a re-evaluation and re-locking of all chunks
     */
    public void resetAllChunks() {
        plugin.getLogger().info("Performing complete chunk reset - locking all chunks");
        
        for (String chunkKey : chunkDatabase.getAllChunkKeys()) {
            ChunkData data = chunkDatabase.getChunk(chunkKey);
            if (data != null) {
                data.setLocked(true);
                chunkDatabase.saveChunk(chunkKey, data);
            }
        }
        
        plugin.getLogger().info("All chunks have been locked");
    }

    public void initializeChunk(Chunk chunk, UUID playerId) {
        String key = getChunkKey(chunk);
        ChunkData existing = chunkDatabase.getChunk(key);
        if (existing == null) {
            // Use ChunkEvaluator to determine difficulty, biome, score based on actual chunk properties
            ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(playerId, chunk);
            ChunkData newData = ChunkData.builder()
                    .locked(true)
                    .difficulty(evaluation.difficulty)
                    .baseValue(evaluation.score) // Store score as baseValue
                    .biome(evaluation.biome != null ? evaluation.biome.key().asString() : null)
                    .score(evaluation.score)
                    .build();
            chunkDatabase.saveChunk(key, newData);
        }
    }

    // Overload for backward compatibility
    public void initializeChunk(Chunk chunk) {
        initializeChunk(chunk, null); // Will use fallback logic in ChunkEvaluator
    }

    private ChunkData getChunkData(Chunk chunk) {
        initializeChunk(chunk);
        return chunkDatabase.getChunk(chunk);
    }

    private String getChunkKey(Chunk chunk) {
        return chunkDatabase.getChunkKey(chunk);
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

    public TeamManager getTeamManager() {
        return teamManager;
    }
}