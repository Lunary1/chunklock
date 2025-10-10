package me.chunklock.api;

import me.chunklock.api.services.*;
import org.bukkit.plugin.Plugin;

/**
 * Main API facade for the Chunklock plugin.
 * This class provides access to all public services and functionality.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public final class ChunklockAPI {
    
    private static ChunklockAPI instance;
    private final Plugin plugin;
    
    // Service references
    private ChunkService chunkService;
    private TeamService teamService;
    private PlayerProgressService playerProgressService;
    private EconomyService economyService;
    private HologramService hologramService;
    private UIService uiService;
    
    private ChunklockAPI(Plugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Gets the ChunklockAPI instance.
     * 
     * @return The API instance
     * @throws IllegalStateException if the API has not been initialized
     */
    public static ChunklockAPI getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ChunklockAPI has not been initialized yet!");
        }
        return instance;
    }
    
    /**
     * Initializes the ChunklockAPI with the plugin instance.
     * This method should only be called by the main plugin class.
     * 
     * @param plugin The plugin instance
     * @return The initialized API instance
     */
    public static ChunklockAPI initialize(Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("ChunklockAPI has already been initialized!");
        }
        instance = new ChunklockAPI(plugin);
        return instance;
    }
    
    /**
     * Shuts down the API and cleans up resources.
     * This method should only be called by the main plugin class during shutdown.
     */
    public static void shutdown() {
        if (instance != null) {
            instance.cleanupServices();
            instance = null;
        }
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance
     */
    public Plugin getPlugin() {
        return plugin;
    }
    
    /**
     * Gets the chunk management service.
     * 
     * @return The chunk service
     */
    public ChunkService getChunkService() {
        ensureInitialized();
        return chunkService;
    }
    
    /**
     * Gets the team management service.
     * 
     * @return The team service
     */
    public TeamService getTeamService() {
        ensureInitialized();
        return teamService;
    }
    
    /**
     * Gets the player progress service.
     * 
     * @return The player progress service
     */
    public PlayerProgressService getPlayerProgressService() {
        ensureInitialized();
        return playerProgressService;
    }
    
    /**
     * Gets the economy service.
     * 
     * @return The economy service
     */
    public EconomyService getEconomyService() {
        ensureInitialized();
        return economyService;
    }
    
    /**
     * Gets the hologram service.
     * 
     * @return The hologram service
     */
    public HologramService getHologramService() {
        ensureInitialized();
        return hologramService;
    }
    
    /**
     * Gets the UI service.
     * 
     * @return The UI service
     */
    public UIService getUIService() {
        ensureInitialized();
        return uiService;
    }
    
    /**
     * Sets the service implementations. This method should only be called
     * during plugin initialization.
     * 
     * @param chunkService The chunk service implementation
     * @param teamService The team service implementation
     * @param playerProgressService The player progress service implementation
     * @param economyService The economy service implementation
     * @param hologramService The hologram service implementation
     * @param uiService The UI service implementation
     */
    public void setServices(ChunkService chunkService, 
                           TeamService teamService,
                           PlayerProgressService playerProgressService,
                           EconomyService economyService,
                           HologramService hologramService,
                           UIService uiService) {
        this.chunkService = chunkService;
        this.teamService = teamService;
        this.playerProgressService = playerProgressService;
        this.economyService = economyService;
        this.hologramService = hologramService;
        this.uiService = uiService;
    }
    
    /**
     * Gets the API version.
     * 
     * @return The API version string
     */
    public String getVersion() {
        return "1.3.0";
    }
    
    /**
     * Checks if the API is properly initialized with all services.
     * 
     * @return true if all services are available, false otherwise
     */
    public boolean isFullyInitialized() {
        return chunkService != null && 
               teamService != null && 
               playerProgressService != null && 
               economyService != null && 
               hologramService != null && 
               uiService != null;
    }
    
    private void ensureInitialized() {
        if (!isFullyInitialized()) {
            throw new IllegalStateException("ChunklockAPI services are not fully initialized yet!");
        }
    }
    
    private void cleanupServices() {
        // Cleanup logic for services if needed
        chunkService = null;
        teamService = null;
        playerProgressService = null;
        economyService = null;
        hologramService = null;
        uiService = null;
    }
}