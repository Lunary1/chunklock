package me.chunklock.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Utility class that migrates legacy configuration and data files
 * from older versions of the plugin into the new single-file layout.
 */
public class DataMigrator {
    private final JavaPlugin plugin;

    public DataMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Perform all migration steps.
     */
    public void migrate() {
        migrateConfig();
        migrateData();
        migrateTeams();
    }

    // ------- Config.yml migration -------
    private void migrateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration config = configFile.exists()
                ? YamlConfiguration.loadConfiguration(configFile)
                : new YamlConfiguration();
        boolean changed = false;

        // chunk_values.yml -> config.yml chunk-values
        File oldChunkValues = new File(plugin.getDataFolder(), "chunk_values.yml");
        if (oldChunkValues.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldChunkValues);
            ConfigurationSection dest = config.getConfigurationSection("chunk-values");
            if (dest == null) dest = config.createSection("chunk-values");
            copySection(old, dest);
            backupOldFile(oldChunkValues);
            plugin.getLogger().info("Migrated chunk_values.yml into config.yml");
            changed = true;
        }

        // biome_costs.yml -> config.yml biome-unlocks
        File oldBiomeCosts = new File(plugin.getDataFolder(), "biome_costs.yml");
        if (oldBiomeCosts.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldBiomeCosts);
            ConfigurationSection dest = config.getConfigurationSection("biome-unlocks");
            if (dest == null) dest = config.createSection("biome-unlocks");
            copySection(old, dest);
            backupOldFile(oldBiomeCosts);
            plugin.getLogger().info("Migrated biome_costs.yml into config.yml");
            changed = true;
        }

        // team_config.yml -> config.yml team-settings
        File oldTeamCfg = new File(plugin.getDataFolder(), "team_config.yml");
        if (oldTeamCfg.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldTeamCfg);
            ConfigurationSection dest = config.getConfigurationSection("team-settings");
            if (dest == null) dest = config.createSection("team-settings");
            copySection(old, dest);
            backupOldFile(oldTeamCfg);
            plugin.getLogger().info("Migrated team_config.yml into config.yml");
            changed = true;
        }

        if (changed) {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save migrated config.yml: " + e.getMessage());
            }
        }
    }

    // ------- data.yml migration -------
    private void migrateData() {
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        FileConfiguration data = dataFile.exists()
                ? YamlConfiguration.loadConfiguration(dataFile)
                : new YamlConfiguration();
        boolean changed = false;

        // player_chunks.yml -> players spawn section
        File oldSpawns = new File(plugin.getDataFolder(), "player_chunks.yml");
        if (oldSpawns.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldSpawns);
            for (String uuid : old.getKeys(false)) {
                String base = "players." + uuid + ".spawn";
                data.set(base + ".x", old.getInt(uuid + ".x"));
                data.set(base + ".y", old.getInt(uuid + ".y"));
                data.set(base + ".z", old.getInt(uuid + ".z"));
                data.set(base + ".world", old.getString(uuid + ".world"));
            }
            backupOldFile(oldSpawns);
            plugin.getLogger().info("Migrated player_chunks.yml into data.yml");
            changed = true;
        }

        // player_progress.yml -> players progress section
        File oldProgress = new File(plugin.getDataFolder(), "player_progress.yml");
        if (oldProgress.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldProgress);
            for (String uuid : old.getKeys(false)) {
                String base = "players." + uuid + ".progress";
                data.set(base + ".unlocked_chunks", old.getInt(uuid + ".unlocked_chunks", 0));
            }
            backupOldFile(oldProgress);
            plugin.getLogger().info("Migrated player_progress.yml into data.yml");
            changed = true;
        }

        // chunk_data.yml -> chunks section
        File oldChunkData = new File(plugin.getDataFolder(), "chunk_data.yml");
        if (oldChunkData.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(oldChunkData);
            ConfigurationSection dest = data.getConfigurationSection("chunks");
            if (dest == null) dest = data.createSection("chunks");
            for (String key : old.getKeys(false)) {
                dest.set(key + ".world", old.getString(key + ".world"));
                dest.set(key + ".x", old.getInt(key + ".x"));
                dest.set(key + ".z", old.getInt(key + ".z"));
                dest.set(key + ".locked", old.getBoolean(key + ".locked", true));
                dest.set(key + ".difficulty", old.getString(key + ".difficulty", "NORMAL"));
            }
            backupOldFile(oldChunkData);
            plugin.getLogger().info("Migrated chunk_data.yml into data.yml");
            changed = true;
        }

        if (changed) {
            try {
                data.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save migrated data.yml: " + e.getMessage());
            }
        }
    }

    // ------- teams.yml migration -------
    private void migrateTeams() {
        File teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        FileConfiguration teams = teamsFile.exists()
                ? YamlConfiguration.loadConfiguration(teamsFile)
                : new YamlConfiguration();
        boolean changed = false;

        // convert simple mapping format
        if (teamsFile.exists() && !teams.isConfigurationSection("mapping")) {
            ConfigurationSection mapping = teams.createSection("mapping");
            for (String key : teams.getKeys(false)) {
                if (teams.isString(key)) {
                    mapping.set(key, teams.getString(key));
                    teams.set(key, null);
                    changed = true;
                }
            }
            if (changed) {
                plugin.getLogger().info("Converted legacy teams.yml format");
            }
        }

        // teams_enhanced.yml -> merge into teams.yml
        File enhanced = new File(plugin.getDataFolder(), "teams_enhanced.yml");
        if (enhanced.exists()) {
            FileConfiguration old = YamlConfiguration.loadConfiguration(enhanced);
            for (String key : old.getKeys(false)) {
                teams.set(key, old.get(key));
            }
            backupOldFile(enhanced);
            plugin.getLogger().info("Migrated teams_enhanced.yml into teams.yml");
            changed = true;
        }

        if (changed) {
            try {
                teams.save(teamsFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save migrated teams.yml: " + e.getMessage());
            }
        }
    }

    // Utility: copy all keys from src into dest
    private void copySection(ConfigurationSection src, ConfigurationSection dest) {
        for (String key : src.getKeys(false)) {
            dest.set(key, src.get(key));
        }
    }

    // Utility: rename old file with .old extension
    private void backupOldFile(File file) {
        File backup = new File(file.getParentFile(), file.getName() + ".old");
        //noinspection ResultOfMethodCallIgnored
        file.renameTo(backup);
    }
}
