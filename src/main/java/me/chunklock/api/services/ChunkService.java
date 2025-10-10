package me.chunklock.api.services;

import me.chunklock.models.ChunkData;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for chunk management operations.
 * Provides methods for chunk locking, unlocking, and querying chunk states.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface ChunkService extends BaseService {
    
    /**
     * Checks if a chunk is locked for a specific player.
     * 
     * @param chunk The chunk to check
     * @param player The player to check for
     * @return true if the chunk is locked for the player, false otherwise
     */
    boolean isChunkLocked(Chunk chunk, Player player);
    
    /**
     * Checks if a chunk is locked for a specific player by UUID.
     * 
     * @param chunk The chunk to check
     * @param playerUuid The UUID of the player to check for
     * @return true if the chunk is locked for the player, false otherwise
     */
    boolean isChunkLocked(Chunk chunk, UUID playerUuid);
    
    /**
     * Gets the chunk data for a specific chunk.
     * 
     * @param chunk The chunk to get data for
     * @return Optional containing the chunk data if it exists
     */
    Optional<ChunkData> getChunkData(Chunk chunk);
    
    /**
     * Gets all unlocked chunks for a player.
     * 
     * @param player The player to get unlocked chunks for
     * @return List of unlocked chunks
     */
    List<Chunk> getUnlockedChunks(Player player);
    
    /**
     * Gets all unlocked chunks for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to get unlocked chunks for
     * @param world The world to search in
     * @return List of unlocked chunks
     */
    List<Chunk> getUnlockedChunks(UUID playerUuid, World world);
    
    /**
     * Attempts to unlock a chunk for a player.
     * 
     * @param chunk The chunk to unlock
     * @param player The player attempting to unlock
     * @return true if the chunk was successfully unlocked, false otherwise
     */
    boolean unlockChunk(Chunk chunk, Player player);
    
    /**
     * Forces a chunk to be unlocked for a player (admin operation).
     * 
     * @param chunk The chunk to unlock
     * @param playerUuid The UUID of the player to unlock for
     * @return true if the chunk was successfully unlocked
     */
    boolean forceUnlockChunk(Chunk chunk, UUID playerUuid);
    
    /**
     * Locks a chunk for a player (admin operation).
     * 
     * @param chunk The chunk to lock
     * @param playerUuid The UUID of the player to lock for
     * @return true if the chunk was successfully locked
     */
    boolean lockChunk(Chunk chunk, UUID playerUuid);
    
    /**
     * Checks if a chunk can be unlocked by a player.
     * This includes checking requirements, adjacent chunks, etc.
     * 
     * @param chunk The chunk to check
     * @param player The player attempting to unlock
     * @return true if the chunk can be unlocked, false otherwise
     */
    boolean canUnlockChunk(Chunk chunk, Player player);
    
    /**
     * Gets the cost to unlock a specific chunk for a player.
     * 
     * @param chunk The chunk to check
     * @param player The player attempting to unlock
     * @return The cost as a formatted string, or null if cannot be unlocked
     */
    String getUnlockCost(Chunk chunk, Player player);
    
    /**
     * Checks if a chunk is adjacent to any unlocked chunks for a player.
     * 
     * @param chunk The chunk to check
     * @param player The player to check for
     * @return true if the chunk is adjacent to unlocked chunks
     */
    boolean isAdjacentToUnlockedChunk(Chunk chunk, Player player);
    
    /**
     * Gets the total number of unlocked chunks for a player.
     * 
     * @param player The player to count for
     * @return The number of unlocked chunks
     */
    int getUnlockedChunkCount(Player player);
    
    /**
     * Gets the total number of unlocked chunks for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to count for
     * @param world The world to count in
     * @return The number of unlocked chunks
     */
    int getUnlockedChunkCount(UUID playerUuid, World world);
    
    /**
     * Refreshes chunk data from storage.
     * 
     * @param chunk The chunk to refresh
     */
    void refreshChunkData(Chunk chunk);
    
    /**
     * Saves chunk data to storage.
     * 
     * @param chunkData The chunk data to save
     */
    void saveChunkData(ChunkData chunkData);
}