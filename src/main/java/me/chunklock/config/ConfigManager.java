package me.chunklock.config;

import me.chunklock.config.modular.*;
import me.chunklock.util.migration.ConfigMigrator;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import java.util.logging.Logger;

/**
 * Main configuration manager for the Chunklock plugin.
 * Centralizes all configuration access and provides type-safe configuration methods.
 * 
 * Now uses a modular configuration system with separate files for each concern.
 * 
 * @author Chunklock Team
 * @version 2.0.0
 * @since 1.3.0
 */
public class ConfigManager {
    
    private final Plugin plugin;
    private final Logger logger;
    private FileConfiguration coreConfig;
    
    // Legacy configuration section managers (for backward compatibility)
    private ChunkConfig chunkConfig;
    private TeamConfig teamConfig;
    private EconomyConfig economyConfig;
    private HologramConfig hologramConfig;
    private UIConfig uiConfig;
    
    // Modular configuration managers
    private me.chunklock.config.modular.EconomyConfig modularEconomyConfig;
    private BlockValuesConfig blockValuesConfig;
    private BiomeUnlocksConfig biomeUnlocksConfig;
    private TeamSettingsConfig teamSettingsConfig;
    private BordersConfig bordersConfig;
    private WorldsConfig worldsConfig;
    private HologramsConfig hologramsConfig;
    private DebugConfig debugConfig;
    private PerformanceConfig performanceConfig;
    private DatabaseConfig databaseConfig;
    private LanguageManager languageManager;
    
    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfiguration();
    }
    
    /**
     * Loads configuration from multiple modular config files.
     * Automatically migrates from old monolithic config if needed.
     */
    public void loadConfiguration() {
        // Load core config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        coreConfig = plugin.getConfig();
        
        // Check if migration is needed
        int configVersion = coreConfig.getInt("config-version", 1);
        if (configVersion < 2) {
            logger.info("Detected old config format (version " + configVersion + "). Migrating to modular config system...");
            ConfigMigrator migrator = new ConfigMigrator(plugin);
            if (migrator.migrate()) {
                logger.info("Migration completed successfully. Reloading configs...");
                // Reload core config after migration
                plugin.reloadConfig();
                coreConfig = plugin.getConfig();
            } else {
                logger.warning("Migration encountered issues. Using defaults for modular configs.");
            }
        }
        
        // Load modular configs
        try {
            modularEconomyConfig = new me.chunklock.config.modular.EconomyConfig(plugin);
            blockValuesConfig = new BlockValuesConfig(plugin);
            biomeUnlocksConfig = new BiomeUnlocksConfig(plugin);
            teamSettingsConfig = new TeamSettingsConfig(plugin);
            bordersConfig = new BordersConfig(plugin);
            worldsConfig = new WorldsConfig(plugin);
            hologramsConfig = new HologramsConfig(plugin);
            debugConfig = new DebugConfig(plugin);
            performanceConfig = new PerformanceConfig(plugin);
            databaseConfig = new DatabaseConfig(plugin);
            languageManager = new LanguageManager(plugin);
            
            logger.info("Modular configuration loaded successfully");
        } catch (Exception e) {
            logger.severe("Failed to load modular configuration: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Initialize legacy section managers for backward compatibility
        // These will read from modular configs via adapter methods
        chunkConfig = new ChunkConfig(coreConfig);
        teamConfig = new TeamConfig(coreConfig);
        economyConfig = new EconomyConfig(coreConfig);
        hologramConfig = new HologramConfig(coreConfig);
        uiConfig = new UIConfig(coreConfig);
    }
    
    /**
     * Reloads configuration from disk.
     */
    public void reloadConfiguration() {
        loadConfiguration();
        // Reload language files
        if (languageManager != null) {
            languageManager.reload();
        }
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
     * Gets the raw FileConfiguration object for core config.
     * 
     * @return The FileConfiguration
     */
    public FileConfiguration getRawConfig() {
        return coreConfig;
    }
    
    // ===== Modular Config Getters =====
    
    public me.chunklock.config.modular.EconomyConfig getModularEconomyConfig() {
        return modularEconomyConfig;
    }
    
    public BlockValuesConfig getBlockValuesConfig() {
        return blockValuesConfig;
    }
    
    public BiomeUnlocksConfig getBiomeUnlocksConfig() {
        return biomeUnlocksConfig;
    }
    
    public TeamSettingsConfig getTeamSettingsConfig() {
        return teamSettingsConfig;
    }
    
    public BordersConfig getBordersConfig() {
        return bordersConfig;
    }
    
    public WorldsConfig getWorldsConfig() {
        return worldsConfig;
    }
    
    public HologramsConfig getHologramsConfig() {
        return hologramsConfig;
    }
    
    public DebugConfig getDebugConfig() {
        return debugConfig;
    }
    
    public PerformanceConfig getPerformanceConfig() {
        return performanceConfig;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
    
    public LanguageManager getLanguageManager() {
        return languageManager;
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
        
        // Validate legacy configs (if still in use)
        if (chunkConfig != null) valid &= chunkConfig.validate();
        if (teamConfig != null) valid &= teamConfig.validate();
        if (economyConfig != null) valid &= economyConfig.validate();
        if (hologramConfig != null) valid &= hologramConfig.validate();
        if (uiConfig != null) valid &= uiConfig.validate();
        
        // Validate modular configs exist
        if (modularEconomyConfig == null ||
            blockValuesConfig == null || biomeUnlocksConfig == null ||
            teamSettingsConfig == null || bordersConfig == null ||
            worldsConfig == null || hologramsConfig == null ||
            debugConfig == null || performanceConfig == null ||
            databaseConfig == null) {
            logger.warning("Some modular configuration files failed to load.");
            valid = false;
        }
        
        if (!valid) {
            logger.warning("Configuration validation failed. Please check your configuration files.");
        }
        
        return valid;
    }
    
    /**
     * Gets version information from the configuration.
     * 
     * @return Configuration version, or 1 if not specified
     */
    public int getConfigVersion() {
        return coreConfig.getInt("config-version", 1);
    }
    
    /**
     * Checks if this is the first time the configuration is being loaded.
     * 
     * @return true if this is the first run, false otherwise
     */
    public boolean isFirstRun() {
        return !coreConfig.contains("first-run") || coreConfig.getBoolean("first-run", true);
    }
    
    /**
     * Marks that the first run setup has been completed.
     */
    public void markFirstRunComplete() {
        set("first-run", false);
        saveConfiguration();
    }
    
    /**
     * Gets a string value from the core configuration with a default fallback.
     * For modular configs, use the specific config getters instead.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public String getString(String path, String defaultValue) {
        return coreConfig.getString(path, defaultValue);
    }
    
    /**
     * Gets an integer value from the core configuration with a default fallback.
     * For modular configs, use the specific config getters instead.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public int getInt(String path, int defaultValue) {
        return coreConfig.getInt(path, defaultValue);
    }
    
    /**
     * Gets a double value from the core configuration with a default fallback.
     * For modular configs, use the specific config getters instead.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public double getDouble(String path, double defaultValue) {
        return coreConfig.getDouble(path, defaultValue);
    }
    
    /**
     * Gets a boolean value from the core configuration with a default fallback.
     * For modular configs, use the specific config getters instead.
     * 
     * @param path The configuration path
     * @param defaultValue The default value if not found
     * @return The configuration value or default
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return coreConfig.getBoolean(path, defaultValue);
    }
    
    /**
     * Sets a value in the core configuration.
     * 
     * @param path The configuration path
     * @param value The value to set
     */
    public void set(String path, Object value) {
        coreConfig.set(path, value);
    }
}
