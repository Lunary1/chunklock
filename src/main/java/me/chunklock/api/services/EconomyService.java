package me.chunklock.api.services;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import java.math.BigDecimal;
import java.util.List;

/**
 * Service interface for economy operations.
 * Provides methods for handling payments and cost calculations for chunk unlocking.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface EconomyService extends BaseService {
    
    /**
     * Checks if the economy system is enabled and available.
     * 
     * @return true if economy is available, false otherwise
     */
    boolean isEconomyEnabled();
    
    /**
     * Gets the type of economy system currently in use.
     * 
     * @return The economy type ("materials", "vault", etc.)
     */
    String getEconomyType();
    
    /**
     * Calculates the cost to unlock a specific chunk for a player.
     * 
     * @param chunk The chunk to calculate cost for
     * @param player The player attempting to unlock
     * @return The cost as a BigDecimal, or null if cost cannot be calculated
     */
    BigDecimal calculateUnlockCost(Chunk chunk, Player player);
    
    /**
     * Gets a formatted string representation of the unlock cost.
     * 
     * @param chunk The chunk to get cost for
     * @param player The player attempting to unlock
     * @return Formatted cost string (e.g., "$100.00" or "10x Diamond")
     */
    String getFormattedUnlockCost(Chunk chunk, Player player);
    
    /**
     * Checks if a player can afford to unlock a specific chunk.
     * 
     * @param chunk The chunk to check
     * @param player The player to check affordability for
     * @return true if the player can afford to unlock the chunk, false otherwise
     */
    boolean canAffordUnlock(Chunk chunk, Player player);
    
    /**
     * Processes payment for unlocking a chunk.
     * 
     * @param chunk The chunk being unlocked
     * @param player The player making the payment
     * @return true if payment was successful, false otherwise
     */
    boolean processUnlockPayment(Chunk chunk, Player player);
    
    /**
     * Refunds a payment to a player (used for failed unlocks or admin operations).
     * 
     * @param player The player to refund
     * @param amount The amount to refund
     * @return true if refund was successful, false otherwise
     */
    boolean refundPayment(Player player, BigDecimal amount);
    
    /**
     * Gets the player's current balance.
     * 
     * @param player The player to check balance for
     * @return The player's balance as a BigDecimal
     */
    BigDecimal getBalance(Player player);
    
    /**
     * Gets a formatted string representation of the player's balance.
     * 
     * @param player The player to check balance for
     * @return Formatted balance string
     */
    String getFormattedBalance(Player player);
    
    /**
     * Gets the required materials for unlocking a chunk (for material-based economy).
     * 
     * @param chunk The chunk to get requirements for
     * @param player The player attempting to unlock
     * @return List of required materials with quantities, or empty list if not applicable
     */
    List<String> getRequiredMaterials(Chunk chunk, Player player);
    
    /**
     * Checks if a player has the required materials for unlocking a chunk.
     * 
     * @param chunk The chunk to check requirements for
     * @param player The player to check materials for
     * @return true if the player has all required materials, false otherwise
     */
    boolean hasRequiredMaterials(Chunk chunk, Player player);
    
    /**
     * Removes the required materials from a player's inventory for chunk unlocking.
     * 
     * @param chunk The chunk being unlocked
     * @param player The player to take materials from
     * @return true if materials were successfully removed, false otherwise
     */
    boolean takeMaterials(Chunk chunk, Player player);
    
    /**
     * Returns materials to a player's inventory (used for failed unlocks or refunds).
     * 
     * @param player The player to return materials to
     * @param materials List of materials to return
     * @return true if materials were successfully returned, false otherwise
     */
    boolean returnMaterials(Player player, List<String> materials);
    
    /**
     * Gets the base cost multiplier for the current difficulty.
     * 
     * @return The difficulty multiplier
     */
    double getDifficultyMultiplier();
    
    /**
     * Gets the biome-specific cost multiplier for a chunk.
     * 
     * @param chunk The chunk to get multiplier for
     * @return The biome multiplier
     */
    double getBiomeMultiplier(Chunk chunk);
    
    /**
     * Gets the progression-based cost multiplier for a player.
     * This typically increases as the player unlocks more chunks.
     * 
     * @param player The player to get multiplier for
     * @return The progression multiplier
     */
    double getProgressionMultiplier(Player player);
    
    /**
     * Validates economy configuration and settings.
     * 
     * @return true if economy configuration is valid, false otherwise
     */
    boolean validateConfiguration();
    
    /**
     * Reloads economy configuration from the config file.
     */
    void reloadConfiguration();
    
    /**
     * Gets economy statistics and information.
     * 
     * @return Map of economy statistics
     */
    java.util.Map<String, Object> getEconomyStats();
}