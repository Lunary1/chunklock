package me.chunklock.api.services;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Service interface for user interface operations.
 * Provides methods for managing GUIs, menus, and other user interface components.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface UIService extends BaseService {
    
    /**
     * Opens the main unlock GUI for a player.
     * 
     * @param player The player to open the GUI for
     * @return true if the GUI was successfully opened, false otherwise
     */
    boolean openUnlockGui(Player player);
    
    /**
     * Opens the team management GUI for a player.
     * 
     * @param player The player to open the GUI for
     * @return true if the GUI was successfully opened, false otherwise
     */
    boolean openTeamGui(Player player);
    
    /**
     * Opens the player statistics GUI for a player.
     * 
     * @param player The player to open the GUI for
     * @return true if the GUI was successfully opened, false otherwise
     */
    boolean openStatsGui(Player player);
    
    /**
     * Opens the configuration/settings GUI for a player (admin only).
     * 
     * @param player The player to open the GUI for
     * @return true if the GUI was successfully opened, false otherwise
     */
    boolean openConfigGui(Player player);
    
    /**
     * Closes any open Chunklock GUI for a player.
     * 
     * @param player The player to close GUIs for
     */
    void closeGui(Player player);
    
    /**
     * Checks if a player currently has a Chunklock GUI open.
     * 
     * @param player The player to check
     * @return true if the player has a GUI open, false otherwise
     */
    boolean hasGuiOpen(Player player);
    
    /**
     * Gets the type of GUI currently open for a player.
     * 
     * @param player The player to check
     * @return The GUI type, or null if no GUI is open
     */
    String getOpenGuiType(Player player);
    
    /**
     * Refreshes the currently open GUI for a player.
     * This updates the content without closing and reopening.
     * 
     * @param player The player to refresh the GUI for
     * @return true if the GUI was successfully refreshed, false otherwise
     */
    boolean refreshGui(Player player);
    
    /**
     * Sends a formatted message to a player.
     * 
     * @param player The player to send the message to
     * @param message The message to send
     */
    void sendMessage(Player player, String message);
    
    /**
     * Sends a formatted message to a player with placeholder replacement.
     * 
     * @param player The player to send the message to
     * @param message The message template
     * @param placeholders Key-value pairs for placeholder replacement
     */
    void sendMessage(Player player, String message, java.util.Map<String, String> placeholders);
    
    /**
     * Sends an action bar message to a player.
     * 
     * @param player The player to send the action bar to
     * @param message The message to display
     */
    void sendActionBar(Player player, String message);
    
    /**
     * Sends a title and subtitle to a player.
     * 
     * @param player The player to send the title to
     * @param title The main title text
     * @param subtitle The subtitle text
     * @param fadeIn Fade in time in ticks
     * @param stay Stay time in ticks
     * @param fadeOut Fade out time in ticks
     */
    void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut);
    
    /**
     * Shows a boss bar to a player with the specified message and progress.
     * 
     * @param player The player to show the boss bar to
     * @param message The message to display on the boss bar
     * @param progress The progress (0.0 to 1.0)
     * @param duration Duration in ticks to show the boss bar
     */
    void showBossBar(Player player, String message, double progress, int duration);
    
    /**
     * Removes any active boss bar from a player.
     * 
     * @param player The player to remove the boss bar from
     */
    void removeBossBar(Player player);
    
    /**
     * Creates a confirmation dialog for a player.
     * 
     * @param player The player to show the dialog to
     * @param message The confirmation message
     * @param onConfirm Runnable to execute if confirmed
     * @param onCancel Runnable to execute if cancelled
     */
    void showConfirmationDialog(Player player, String message, Runnable onConfirm, Runnable onCancel);
    
    /**
     * Plays a sound effect for a player.
     * 
     * @param player The player to play the sound for
     * @param sound The sound to play
     * @param volume The volume (0.0 to 1.0)
     * @param pitch The pitch (0.5 to 2.0)
     */
    void playSound(Player player, String sound, float volume, float pitch);
    
    /**
     * Shows particle effects at a location for a player.
     * 
     * @param player The player to show particles to
     * @param particle The particle type
     * @param location The location to spawn particles
     * @param count The number of particles
     */
    void showParticles(Player player, String particle, org.bukkit.Location location, int count);
    
    /**
     * Creates a custom inventory GUI with the specified title and size.
     * 
     * @param title The title of the inventory
     * @param size The size of the inventory (must be multiple of 9)
     * @return The created inventory
     */
    Inventory createCustomInventory(String title, int size);
    
    /**
     * Registers a GUI state for a player to track open inventories.
     * 
     * @param player The player to register state for
     * @param guiType The type of GUI being opened
     * @param inventory The inventory being opened
     */
    void registerGuiState(Player player, String guiType, Inventory inventory);
    
    /**
     * Unregisters GUI state for a player.
     * 
     * @param player The player to unregister state for
     */
    void unregisterGuiState(Player player);
    
    /**
     * Gets the number of players currently using GUIs.
     * 
     * @return The number of active GUI users
     */
    int getActiveGuiUsers();
    
    /**
     * Closes all open GUIs for all players.
     */
    void closeAllGuis();
    
    /**
     * Reloads UI configuration and settings.
     */
    void reloadConfiguration();
    
    /**
     * Gets UI service statistics.
     * 
     * @return Map of UI statistics
     */
    java.util.Map<String, Object> getUIStats();
}