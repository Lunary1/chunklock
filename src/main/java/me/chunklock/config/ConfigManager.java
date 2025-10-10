package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * Main configuration manager for the Chunklock plugin.
 * Centralizes all configuration access and provides type-safe configuration methods.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class ConfigManager {
    
    private final Plugin plugin;
    private final Logger logger;
    private FileConfiguration config;
    
    // Configuration section managers
    private ChunkConfig chunkConfig;
    private TeamConfig teamConfig;
    private EconomyConfig economyConfig;
    private HologramConfig hologramConfig;
    private UIConfig uiConfig;
    
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfiguration();
    }
    
    /**
     * Loads configuration from the config.yml file.
     */
    public void loadConfiguration() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        
        // Initialize section managers
        chunkConfig = new ChunkConfig(config);
        teamConfig = new TeamConfig(config);
        economyConfig = new EconomyConfig(config);
        hologramConfig = new HologramConfig(config);
        uiConfig = new UIConfig(config);
        
        logger.info("Configuration loaded successfully");
    }
    
    /**
     * Reloads configuration from disk.
     */
    public void reloadConfiguration() {
        loadConfiguration();
        logger.info("Configuration reloaded");
    }
    
    /**
     * Saves the current configuration to disk.
     */
    public void saveConfiguration() {
        plugin.saveConfig();
        logger.info("Configuration saved");
    }
    
    /**
     * Gets the raw FileConfiguration object.
     * 
     * @return The FileConfiguration
     */
    public FileConfiguration getRawConfig() {
        return config;
    }
    
    /**
     * Gets the chunk configuration section.
     * 
     * @return The chunk configuration
     */
    public ChunkConfig getChunkConfig() {
        return chunkConfig;
    }
    
    /**
     * Gets the team configuration section.
     * 
     * @return The team configuration
     */
    public TeamConfig getTeamConfig() {
        return teamConfig;
    }
    
    /**
     * Gets the economy configuration section.
     * 
     * @return The economy configuration
     */
    public EconomyConfig getEconomyConfig() {
        return economyConfig;
    }
    
    /**
     * Gets the hologram configuration section.
     * 
     * @return The hologram configuration
     */
    public HologramConfig getHologramConfig() {
        return hologramConfig;
    }
    
    /**
     * Gets the UI configuration section.
     * 
     * @return The UI configuration
     */
    public UIConfig getUIConfig() {
        return uiConfig;
    }
    
    /**
     * Validates the entire configuration for correctness.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validateConfiguration() {
        boolean valid = true;
        
        valid &= chunkConfig.validate();
        valid &= teamConfig.validate();
        valid &= economyConfig.validate();
        valid &= hologramConfig.validate();
        valid &= uiConfig.validate();
        
        if (!valid) {
            logger.warning("Configuration validation failed. Please check your config.yml file.");
        }
        
        return valid;
    }
    
    /**
     * Gets a string value from the configuration with a default fallback.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }
    
    /**
     * Gets an integer value from the configuration with a default fallback.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }
    
    /**
     * Gets a double value from the configuration with a default fallback.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public double getDouble(String path, double defaultValue) {
        return config.getDouble(path, defaultValue);
    }
    
    /**
     * Gets a boolean value from the configuration with a default fallback.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
    
    /**
     * Sets a value in the configuration.
     * 
     * @param path The configuration path
     * @param value The value to set
     */
    public void set(String path, Object value) {
        config.set(path, value);
    }
    
    /**
     * Gets version information from the configuration.
     * 
     * @return Configuration version, or 1 if not specified
     */
    public int getConfigVersion() {
        return config.getInt("config-version", 1);
    }
    
    /**
     * Checks if this is the first time the configuration is being loaded.
     * 
     * @return true if this is the first run, false otherwise
     */
    public boolean isFirstRun() {
        return !config.contains("first-run") || config.getBoolean("first-run", true);
    }
    
    /**
     * Marks that the first run setup has been completed.
     */
    public void markFirstRunComplete() {
        set("first-run", false);
        saveConfiguration();
    }
}