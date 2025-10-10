package me.chunklock.api.services;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * Service interface for hologram management operations.
 * Provides methods for creating, updating, and managing holograms throughout the plugin.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface HologramService extends BaseService {
    
    /**
     * Checks if hologram functionality is enabled and available.
     * 
     * @return true if holograms are enabled, false otherwise
     */
    boolean isHologramEnabled();
    
    /**
     * Gets the name of the currently active hologram provider.
     * 
     * @return The provider name (e.g., "FancyHolograms", "HolographicDisplays", "DecentHolograms")
     */
    String getProviderName();
    
    /**
     * Creates a hologram at the specified location with the given content.
     * 
     * @param id Unique identifier for the hologram
     * @param location The location where the hologram should be displayed
     * @param lines The lines of text to display in the hologram
     * @return true if the hologram was successfully created, false otherwise
     */
    boolean createHologram(String id, Location location, List<String> lines);
    
    /**
     * Updates an existing hologram with new content.
     * 
     * @param id The unique identifier of the hologram to update
     * @param lines The new lines of text to display
     * @return true if the hologram was successfully updated, false otherwise
     */
    boolean updateHologram(String id, List<String> lines);
    
    /**
     * Removes a hologram by its unique identifier.
     * 
     * @param id The unique identifier of the hologram to remove
     * @return true if the hologram was successfully removed, false otherwise
     */
    boolean removeHologram(String id);
    
    /**
     * Checks if a hologram with the specified ID exists.
     * 
     * @param id The unique identifier to check
     * @return true if the hologram exists, false otherwise
     */
    boolean hologramExists(String id);
    
    /**
     * Gets the location of a hologram.
     * 
     * @param id The unique identifier of the hologram
     * @return The location of the hologram, or null if not found
     */
    Location getHologramLocation(String id);
    
    /**
     * Moves a hologram to a new location.
     * 
     * @param id The unique identifier of the hologram to move
     * @param newLocation The new location for the hologram
     * @return true if the hologram was successfully moved, false otherwise
     */
    boolean moveHologram(String id, Location newLocation);
    
    /**
     * Shows a hologram to a specific player.
     * 
     * @param id The unique identifier of the hologram
     * @param player The player to show the hologram to
     * @return true if the hologram was successfully shown, false otherwise
     */
    boolean showHologram(String id, Player player);
    
    /**
     * Hides a hologram from a specific player.
     * 
     * @param id The unique identifier of the hologram
     * @param player The player to hide the hologram from
     * @return true if the hologram was successfully hidden, false otherwise
     */
    boolean hideHologram(String id, Player player);
    
    /**
     * Shows a hologram to all players within range.
     * 
     * @param id The unique identifier of the hologram
     * @return true if the hologram was successfully shown, false otherwise
     */
    boolean showHologramToAll(String id);
    
    /**
     * Hides a hologram from all players.
     * 
     * @param id The unique identifier of the hologram
     * @return true if the hologram was successfully hidden, false otherwise
     */
    boolean hideHologramFromAll(String id);
    
    /**
     * Creates a temporary hologram that automatically disappears after a specified time.
     * 
     * @param id Unique identifier for the hologram
     * @param location The location where the hologram should be displayed
     * @param lines The lines of text to display
     * @param duration Duration in milliseconds before the hologram disappears
     * @return true if the hologram was successfully created, false otherwise
     */
    boolean createTemporaryHologram(String id, Location location, List<String> lines, long duration);
    
    /**
     * Creates a player-specific hologram that only the specified player can see.
     * 
     * @param id Unique identifier for the hologram
     * @param location The location where the hologram should be displayed
     * @param lines The lines of text to display
     * @param player The player who should see the hologram
     * @return true if the hologram was successfully created, false otherwise
     */
    boolean createPlayerHologram(String id, Location location, List<String> lines, Player player);
    
    /**
     * Gets all hologram IDs managed by this service.
     * 
     * @return List of all hologram IDs
     */
    List<String> getAllHologramIds();
    
    /**
     * Gets all holograms visible to a specific player.
     * 
     * @param player The player to check for
     * @return List of hologram IDs visible to the player
     */
    List<String> getVisibleHolograms(Player player);
    
    /**
     * Removes all holograms managed by this service.
     * 
     * @return true if all holograms were successfully removed, false otherwise
     */
    boolean removeAllHolograms();
    
    /**
     * Removes all holograms in a specific world.
     * 
     * @param worldName The name of the world to clear holograms from
     * @return true if all holograms were successfully removed, false otherwise
     */
    boolean removeAllHologramsInWorld(String worldName);
    
    /**
     * Reloads hologram configuration and refreshes all holograms.
     */
    void reloadHolograms();
    
    /**
     * Gets statistics about hologram usage and performance.
     * 
     * @return Map of hologram statistics
     */
    java.util.Map<String, Object> getHologramStats();
    
    /**
     * Sets the visibility range for holograms.
     * 
     * @param range The maximum distance at which holograms are visible
     */
    void setVisibilityRange(double range);
    
    /**
     * Gets the current visibility range for holograms.
     * 
     * @return The visibility range in blocks
     */
    double getVisibilityRange();
}