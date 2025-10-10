package me.chunklock.api.services;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for tracking player progress and statistics.
 * Provides methods for monitoring player advancement through the chunk unlock system.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface PlayerProgressService extends BaseService {
    
    /**
     * Gets the total progress percentage for a player.
     * 
     * @param player The player to get progress for
     * @return Progress percentage (0.0 to 100.0)
     */
    double getProgressPercentage(Player player);
    
    /**
     * Gets the total progress percentage for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to get progress for
     * @return Progress percentage (0.0 to 100.0)
     */
    double getProgressPercentage(UUID playerUuid);
    
    /**
     * Gets the number of biomes unlocked by a player.
     * 
     * @param player The player to check
     * @return Number of unique biomes unlocked
     */
    int getUnlockedBiomeCount(Player player);
    
    /**
     * Gets the number of biomes unlocked by a player by UUID.
     * 
     * @param playerUuid The UUID of the player to check
     * @return Number of unique biomes unlocked
     */
    int getUnlockedBiomeCount(UUID playerUuid);
    
    /**
     * Gets all statistics for a player.
     * 
     * @param player The player to get statistics for
     * @return Map of statistic names to values
     */
    Map<String, Object> getPlayerStats(Player player);
    
    /**
     * Gets all statistics for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to get statistics for
     * @return Map of statistic names to values
     */
    Map<String, Object> getPlayerStats(UUID playerUuid);
    
    /**
     * Updates a specific statistic for a player.
     * 
     * @param player The player to update statistics for
     * @param statistic The name of the statistic
     * @param value The new value
     */
    void updateStatistic(Player player, String statistic, Object value);
    
    /**
     * Updates a specific statistic for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to update statistics for
     * @param statistic The name of the statistic
     * @param value The new value
     */
    void updateStatistic(UUID playerUuid, String statistic, Object value);
    
    /**
     * Increments a numeric statistic for a player.
     * 
     * @param player The player to update statistics for
     * @param statistic The name of the statistic
     * @param amount The amount to increment by
     */
    void incrementStatistic(Player player, String statistic, long amount);
    
    /**
     * Increments a numeric statistic for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to update statistics for
     * @param statistic The name of the statistic
     * @param amount The amount to increment by
     */
    void incrementStatistic(UUID playerUuid, String statistic, long amount);
    
    /**
     * Gets the total playtime for a player in the chunk unlock world.
     * 
     * @param player The player to check
     * @return Playtime in milliseconds
     */
    long getPlaytime(Player player);
    
    /**
     * Gets the total playtime for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to check
     * @return Playtime in milliseconds
     */
    long getPlaytime(UUID playerUuid);
    
    /**
     * Records that a player has unlocked a chunk.
     * This updates relevant statistics and progress tracking.
     * 
     * @param player The player who unlocked the chunk
     * @param biome The biome of the unlocked chunk
     */
    void recordChunkUnlock(Player player, String biome);
    
    /**
     * Records that a player has unlocked a chunk by UUID.
     * This updates relevant statistics and progress tracking.
     * 
     * @param playerUuid The UUID of the player who unlocked the chunk
     * @param biome The biome of the unlocked chunk
     */
    void recordChunkUnlock(UUID playerUuid, String biome);
    
    /**
     * Gets the rank of a player based on their progress.
     * 
     * @param player The player to get rank for
     * @return The player's rank (1 = highest progress)
     */
    int getPlayerRank(Player player);
    
    /**
     * Gets the rank of a player by UUID based on their progress.
     * 
     * @param playerUuid The UUID of the player to get rank for
     * @return The player's rank (1 = highest progress)
     */
    int getPlayerRank(UUID playerUuid);
    
    /**
     * Gets the top players by progress.
     * 
     * @param limit The maximum number of players to return
     * @return Map of player UUIDs to their progress percentages, ordered by progress
     */
    Map<UUID, Double> getTopPlayers(int limit);
    
    /**
     * Resets all progress for a player.
     * 
     * @param player The player to reset progress for
     */
    void resetProgress(Player player);
    
    /**
     * Resets all progress for a player by UUID.
     * 
     * @param playerUuid The UUID of the player to reset progress for
     */
    void resetProgress(UUID playerUuid);
    
    /**
     * Saves player progress data to storage.
     * 
     * @param playerUuid The UUID of the player to save data for
     */
    void savePlayerData(UUID playerUuid);
    
    /**
     * Loads player progress data from storage.
     * 
     * @param playerUuid The UUID of the player to load data for
     */
    void loadPlayerData(UUID playerUuid);
}