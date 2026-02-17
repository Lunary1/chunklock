package me.chunklock.util.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Migrates old monolithic config.yml to new modular configuration system.
 */
public class ConfigMigrator {
    private final Logger logger;
    private final File dataFolder;
    private final File oldConfigFile;

    public ConfigMigrator(Plugin plugin) {
        this.logger = plugin.getLogger();
        this.dataFolder = plugin.getDataFolder();
        this.oldConfigFile = new File(dataFolder, "config.yml");
    }

    /**
     * Migrates the old config.yml to the new modular structure.
     * 
     * @return true if migration was successful, false otherwise
     */
    public boolean migrate() {
        if (!oldConfigFile.exists()) {
            logger.info("No old config.yml found. Using default modular configs.");
            return true;
        }

        try {
            FileConfiguration oldConfig = YamlConfiguration.loadConfiguration(oldConfigFile);
            
            // Backup old config
            File backupFile = new File(dataFolder, "config.yml.backup");
            if (!backupFile.exists()) {
                try {
                    java.nio.file.Files.copy(oldConfigFile.toPath(), backupFile.toPath());
                    logger.info("Backed up old config.yml to config.yml.backup");
                } catch (IOException e) {
                    logger.warning("Failed to backup old config: " + e.getMessage());
                }
            }

            // Migrate each section
            migrateEconomy(oldConfig);
            migrateBlockValues(oldConfig);
            migrateBiomeUnlocks(oldConfig);
            migrateTeamSettings(oldConfig);
            migrateBorders(oldConfig);
            migrateWorlds(oldConfig);
            migrateHolograms(oldConfig);
            migrateDebug(oldConfig);
            migratePerformance(oldConfig);
            
            // Update core config.yml
            updateCoreConfig(oldConfig);
            
            logger.info("Migration completed successfully!");
            return true;
        } catch (Exception e) {
            logger.severe("Migration failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void migrateEconomy(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "economy.yml");
        if (file.exists()) {
            logger.info("economy.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("economy")) {
            ConfigurationSection economy = oldConfig.getConfigurationSection("economy");
            if (economy != null) {
                newConfig.set("type", economy.getString("type", "vault"));
                if (economy.isConfigurationSection("vault")) {
                    newConfig.set("vault", economy.getConfigurationSection("vault"));
                }
                if (economy.isConfigurationSection("materials")) {
                    newConfig.set("materials", economy.getConfigurationSection("materials"));
                }
            }
        } else {
            // Use defaults
            newConfig.set("type", "vault");
        }

        saveConfig(file, newConfig, "economy.yml");
    }

    private void migrateBlockValues(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "block-values.yml");
        if (file.exists()) {
            logger.info("block-values.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("chunk-values")) {
            ConfigurationSection chunkValues = oldConfig.getConfigurationSection("chunk-values");
            if (chunkValues != null) {
                if (chunkValues.isConfigurationSection("thresholds")) {
                    newConfig.set("thresholds", chunkValues.getConfigurationSection("thresholds"));
                }
                if (chunkValues.isConfigurationSection("biomes")) {
                    newConfig.set("biomes", chunkValues.getConfigurationSection("biomes"));
                }
                if (chunkValues.isConfigurationSection("blocks")) {
                    newConfig.set("blocks", chunkValues.getConfigurationSection("blocks"));
                }
            }
        }

        saveConfig(file, newConfig, "block-values.yml");
    }

    private void migrateBiomeUnlocks(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "biome-unlocks.yml");
        if (file.exists()) {
            logger.info("biome-unlocks.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("biome-unlocks")) {
            ConfigurationSection biomeUnlocks = oldConfig.getConfigurationSection("biome-unlocks");
            if (biomeUnlocks != null) {
                // Copy all biome sections
                for (String key : biomeUnlocks.getKeys(false)) {
                    newConfig.set(key, biomeUnlocks.get(key));
                }
            }
        }

        saveConfig(file, newConfig, "biome-unlocks.yml");
    }

    private void migrateTeamSettings(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "team-settings.yml");
        if (file.exists()) {
            logger.info("team-settings.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("team-settings")) {
            ConfigurationSection teamSettings = oldConfig.getConfigurationSection("team-settings");
            if (teamSettings != null) {
                // Copy all settings
                for (String key : teamSettings.getKeys(false)) {
                    newConfig.set(key, teamSettings.get(key));
                }
            }
        }

        saveConfig(file, newConfig, "team-settings.yml");
    }

    private void migrateBorders(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "borders.yml");
        if (file.exists()) {
            logger.info("borders.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("glass-borders")) {
            ConfigurationSection borders = oldConfig.getConfigurationSection("glass-borders");
            if (borders != null) {
                // Copy all border settings
                for (String key : borders.getKeys(false)) {
                    newConfig.set(key, borders.get(key));
                }
            }
        }

        saveConfig(file, newConfig, "borders.yml");
    }

    private void migrateWorlds(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "worlds.yml");
        if (file.exists()) {
            logger.info("worlds.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("worlds")) {
            ConfigurationSection worlds = oldConfig.getConfigurationSection("worlds");
            if (worlds != null) {
                // Copy all world settings
                for (String key : worlds.getKeys(false)) {
                    newConfig.set(key, worlds.get(key));
                }
            }
        }

        saveConfig(file, newConfig, "worlds.yml");
    }

    private void migrateHolograms(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "holograms.yml");
        if (file.exists()) {
            logger.info("holograms.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("holograms")) {
            ConfigurationSection holograms = oldConfig.getConfigurationSection("holograms");
            if (holograms != null) {
                // Copy all hologram settings
                for (String key : holograms.getKeys(false)) {
                    newConfig.set(key, holograms.get(key));
                }
            }
        }

        saveConfig(file, newConfig, "holograms.yml");
    }

    private void migrateDebug(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "debug.yml");
        if (file.exists()) {
            logger.info("debug.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("debug-mode")) {
            ConfigurationSection debug = oldConfig.getConfigurationSection("debug-mode");
            if (debug != null) {
                newConfig.set("enabled", debug.getBoolean("enabled", false));
                newConfig.set("borders", debug.getBoolean("borders", false));
                newConfig.set("unlock-gui", debug.getBoolean("unlock-gui", false));
                newConfig.set("chunk-finding", debug.getBoolean("chunk-finding", false));
                newConfig.set("performance", debug.getBoolean("performance", false));
            }
        } else {
            newConfig.set("enabled", false);
        }

        saveConfig(file, newConfig, "debug.yml");
    }

    private void migratePerformance(FileConfiguration oldConfig) {
        File file = new File(dataFolder, "performance.yml");
        if (file.exists()) {
            logger.info("performance.yml already exists, skipping migration");
            return;
        }

        FileConfiguration newConfig = new YamlConfiguration();
        if (oldConfig.isConfigurationSection("performance")) {
            ConfigurationSection performance = oldConfig.getConfigurationSection("performance");
            if (performance != null) {
                newConfig.set("border-update-delay", performance.getInt("border-update-delay", 2));
                newConfig.set("max-border-updates-per-tick", performance.getInt("max-border-updates-per-tick", 10));
            }
        } else {
            newConfig.set("border-update-delay", 2);
            newConfig.set("max-border-updates-per-tick", 10);
        }

        saveConfig(file, newConfig, "performance.yml");
    }

    private void updateCoreConfig(FileConfiguration oldConfig) {
        FileConfiguration newCoreConfig = new YamlConfiguration();
        newCoreConfig.set("config-version", 2);
        newCoreConfig.set("first-run", oldConfig.getBoolean("first-run", true));
        
        // Add header comment explaining modular system
        String header = "# ===== CHUNKLOCK PLUGIN - CORE CONFIGURATION =====\n" +
                "# This is the main configuration file for the Chunklock plugin.\n" +
                "# \n" +
                "# MODULAR CONFIG SYSTEM:\n" +
                "# The plugin now uses multiple focused configuration files for better organization:\n" +
                "#   - economy.yml          → Economy and payment settings\n" +
                "#   - block-values.yml     → Block values and biome weights for chunk scoring\n" +
                "#   - biome-unlocks.yml    → Biome-specific unlock requirements\n" +
                "#   - team-settings.yml    → Team system configuration\n" +
                "#   - borders.yml          → Glass border system settings\n" +
                "#   - worlds.yml           → World configuration\n" +
                "#   - holograms.yml        → Hologram display settings\n" +
                "#   - debug.yml            → Debug and logging options\n" +
                "#   - performance.yml      → Performance tuning settings\n" +
                "#   - database.yml         → Storage backend settings (MapDB/MySQL)\n" +
                "#\n" +
                "# All these files are automatically loaded from the plugin's data folder.\n" +
                "# If a file is missing, it will be created with default values.\n\n";
        
        try {
            File coreConfigFile = new File(dataFolder, "config.yml");
            java.nio.file.Files.write(coreConfigFile.toPath(), header.getBytes());
            newCoreConfig.save(coreConfigFile);
            logger.info("Updated core config.yml to version 2");
        } catch (IOException e) {
            logger.warning("Failed to update core config.yml: " + e.getMessage());
        }
    }

    private void saveConfig(File file, FileConfiguration config, String name) {
        try {
            config.save(file);
            logger.info("Migrated " + name);
        } catch (IOException e) {
            logger.warning("Failed to save " + name + ": " + e.getMessage());
        }
    }
}

