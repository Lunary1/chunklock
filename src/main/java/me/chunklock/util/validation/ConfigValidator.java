package me.chunklock.util.validation;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * Validates config.yml and adds only missing entries without overwriting existing values.
 * Used by /chunklock reload to ensure config completeness while preserving user customizations.
 */
public class ConfigValidator {
    
    private final JavaPlugin plugin;
    private final File configFile;
    
    // Required top-level sections for the plugin to function
    private static final Set<String> REQUIRED_SECTIONS = Set.of(
        "team-settings",
        "chunk-values", 
        "biome-unlocks",
        "glass-borders",
        "worlds",
        "performance"
    );
    
    // Required subsections within chunk-values
    private static final Set<String> REQUIRED_CHUNK_VALUE_SECTIONS = Set.of(
        "thresholds",
        "biomes", 
        "blocks"
    );
    
    public ConfigValidator(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
    }
    
    /**
     * Validates config and adds only missing entries. Called by /chunklock reload.
     * Never overwrites existing values - only fills in gaps.
     */
    public void validateAndEnsureComplete() {
        plugin.getLogger().info("[ConfigValidator] Checking config.yml for missing entries...");
        
        if (!configFile.exists()) {
            plugin.getLogger().info("[ConfigValidator] config.yml not found, copying from resources...");
            // Use the plugin's saveResource to copy the complete config from resources
            plugin.saveResource("config.yml", false);
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        boolean needsUpdate = addMissingSections(config);
        
        if (needsUpdate) {
            try {
                config.save(configFile);
                plugin.getLogger().info("[ConfigValidator] Added missing entries to config.yml (existing values preserved)");
            } catch (IOException e) {
                plugin.getLogger().severe("[ConfigValidator] Failed to update config.yml: " + e.getMessage());
            }
        } else {
            plugin.getLogger().info("[ConfigValidator] Config complete - no missing entries found");
        }
    }
    
    /**
     * Add only missing sections to the config without overwriting existing values.
     * Returns true if any changes were made.
     */
    private boolean addMissingSections(FileConfiguration config) {
        boolean changed = false;
        
        // Add missing top-level sections
        for (String section : REQUIRED_SECTIONS) {
            if (!config.isConfigurationSection(section)) {
                plugin.getLogger().info("[ConfigValidator] Adding missing section: " + section);
                addDefaultSection(config, section);
                changed = true;
            }
        }
        
        // Add missing chunk-values subsections
        if (config.isConfigurationSection("chunk-values")) {
            var chunkValues = config.getConfigurationSection("chunk-values");
            for (String subsection : REQUIRED_CHUNK_VALUE_SECTIONS) {
                if (!chunkValues.isConfigurationSection(subsection)) {
                    plugin.getLogger().info("[ConfigValidator] Adding missing chunk-values subsection: " + subsection);
                    addDefaultChunkValueSubsection(config, subsection);
                    changed = true;
                }
            }
        }
        
        // Add individual missing settings without overwriting existing ones
        changed |= addMissingIfNotExists(config, "worlds.enabled-worlds", Arrays.asList("world", "world_nether", "world_the_end"));
        changed |= addMissingIfNotExists(config, "worlds.auto-assign-on-world-change", true);
        changed |= addMissingIfNotExists(config, "contested-cost-multiplier", 3.0);
        changed |= addMissingIfNotExists(config, "max-contested-claims-per-day", 5);
        
        // Add missing glass-borders settings
        changed |= addMissingGlassBorderSettings(config);
        
        // Add missing performance settings
        changed |= addMissingPerformanceSettings(config);
        
        return changed;
    }
    
    /**
     * Add a setting only if it doesn't already exist
     */
    private boolean addMissingIfNotExists(FileConfiguration config, String path, Object defaultValue) {
        if (!config.contains(path)) {
            config.set(path, defaultValue);
            return true;
        }
        return false;
    }
    
    /**
     * Add a default section only if it doesn't exist
     */
    private void addDefaultSection(FileConfiguration config, String sectionName) {
        switch (sectionName) {
            case "team-settings" -> addDefaultTeamSettings(config);
            case "chunk-values" -> addDefaultChunkValues(config);
            case "biome-unlocks" -> addDefaultBiomeUnlocks(config);
            case "glass-borders" -> addDefaultGlassBorders(config);
            case "worlds" -> addDefaultWorlds(config);
            case "performance" -> addDefaultPerformance(config);
        }
    }
    
    private void addDefaultTeamSettings(FileConfiguration config) {
        // Only add missing team settings, don't overwrite existing
        addMissingIfNotExists(config, "team-settings.max-team-size", 6);
        addMissingIfNotExists(config, "team-settings.max-teams-per-server", 100);
        addMissingIfNotExists(config, "team-settings.allow-solo-teams", true);
        addMissingIfNotExists(config, "team-settings.join-request-ttl-hours", 72);
        addMissingIfNotExists(config, "team-settings.team-cost-multiplier", 0.15);
        addMissingIfNotExists(config, "team-settings.base-team-cost", 1.0);
        addMissingIfNotExists(config, "team-settings.max-cost-multiplier", 3.0);
        addMissingIfNotExists(config, "team-settings.contested-cost-multiplier", 3.0);
        addMissingIfNotExists(config, "team-settings.max-contested-claims-per-day", 5);
        addMissingIfNotExists(config, "team-settings.enable-team-chat", true);
        addMissingIfNotExists(config, "team-settings.enable-leaderboards", true);
    }
    
    private void addDefaultChunkValues(FileConfiguration config) {
        // Only add if the entire section is missing
        if (!config.isConfigurationSection("chunk-values")) {
            config.createSection("chunk-values");
        }
        addDefaultChunkValueSubsection(config, "thresholds");
        addDefaultChunkValueSubsection(config, "biomes");
        addDefaultChunkValueSubsection(config, "blocks");
    }
    
    private void addDefaultChunkValueSubsection(FileConfiguration config, String subsection) {
        switch (subsection) {
            case "thresholds" -> {
                addMissingIfNotExists(config, "chunk-values.thresholds.easy", 25);
                addMissingIfNotExists(config, "chunk-values.thresholds.normal", 50);
                addMissingIfNotExists(config, "chunk-values.thresholds.hard", 80);
            }
            case "biomes" -> {
                // Only add minimal biomes if none exist - USE UPPERCASE
                if (!config.contains("chunk-values.biomes.PLAINS")) {
                    addMinimalBiomeWeights(config);
                }
            }
            case "blocks" -> {
                // Only add minimal blocks if none exist - USE UPPERCASE
                if (!config.contains("chunk-values.blocks.COAL_ORE")) {
                    addMinimalBlockWeights(config);
                }
            }
        }
    }
    
    private void addMinimalBiomeWeights(FileConfiguration config) {
        // Add only essential biomes to get started - FIXED TO UPPERCASE
        config.set("chunk-values.biomes.PLAINS", 5);
        config.set("chunk-values.biomes.FOREST", 6);
        config.set("chunk-values.biomes.DESERT", 8);
        config.set("chunk-values.biomes.JUNGLE", 15);
        config.set("chunk-values.biomes.OCEAN", 6);
        config.set("chunk-values.biomes.NETHER_WASTES", 30);
        config.set("chunk-values.biomes.THE_END", 50);
    }
    
    private void addMinimalBlockWeights(FileConfiguration config) {
        // Add only essential blocks to get started - FIXED TO UPPERCASE
        config.set("chunk-values.blocks.COAL_ORE", 1);
        config.set("chunk-values.blocks.IRON_ORE", 2);
        config.set("chunk-values.blocks.GOLD_ORE", 3);
        config.set("chunk-values.blocks.DIAMOND_ORE", 5);
        config.set("chunk-values.blocks.SPAWNER", 8);
    }
    
    private void addDefaultBiomeUnlocks(FileConfiguration config) {
        // Add only a few essential biome unlocks if none exist - FIXED TO UPPERCASE
        if (!config.contains("biome-unlocks.NETHER_WASTES")) {
            config.set("biome-unlocks.NETHER_WASTES.OBSIDIAN", 10);
            config.set("biome-unlocks.THE_END.ENDER_PEARL", 12);
            config.set("biome-unlocks.JUNGLE.JUNGLE_SAPLING", 8);
        }
    }
    
    private void addDefaultGlassBorders(FileConfiguration config) {
        // Add core glass-borders settings if they don't exist
        addMissingIfNotExists(config, "glass-borders.enabled", true);
        addMissingIfNotExists(config, "glass-borders.use-full-height", true);
        addMissingIfNotExists(config, "glass-borders.border-height", 3);
        addMissingIfNotExists(config, "glass-borders.min-y-offset", -2);
        addMissingIfNotExists(config, "glass-borders.max-y-offset", 4);
        addMissingIfNotExists(config, "glass-borders.scan-range", 8);
        addMissingIfNotExists(config, "glass-borders.update-delay", 20);
        addMissingIfNotExists(config, "glass-borders.update-cooldown", 2000);
        addMissingIfNotExists(config, "glass-borders.border-material", "LIGHT_GRAY_STAINED_GLASS");
        
        // Add the rest of the glass-borders settings
        addMissingGlassBorderSettings(config);
    }
    
    private boolean addMissingGlassBorderSettings(FileConfiguration config) {
        boolean changed = false;
        
        // Only add if they don't exist
        changed |= addMissingIfNotExists(config, "glass-borders.show-for-bypass-players", false);
        changed |= addMissingIfNotExists(config, "glass-borders.auto-update-on-movement", true);
        changed |= addMissingIfNotExists(config, "glass-borders.restore-original-blocks", true);
        changed |= addMissingIfNotExists(config, "glass-borders.debug-logging", false);
        
        return changed;
    }
    
    private void addDefaultWorlds(FileConfiguration config) {
        addMissingIfNotExists(config, "worlds.enabled-worlds", Arrays.asList("world", "world_nether", "world_the_end"));
        addMissingIfNotExists(config, "worlds.auto-assign-on-world-change", true);
    }
    
    private void addDefaultPerformance(FileConfiguration config) {
        addMissingIfNotExists(config, "performance.border-update-delay", 2);
        addMissingIfNotExists(config, "performance.max-border-updates-per-tick", 10);
    }
    
    private boolean addMissingPerformanceSettings(FileConfiguration config) {
        boolean changed = false;
        
        changed |= addMissingIfNotExists(config, "performance.border-update-delay", 2);
        changed |= addMissingIfNotExists(config, "performance.max-border-updates-per-tick", 10);
        
        return changed;
    }
    
    /**
     * Check if the current config file has all required entries
     */
    public boolean isConfigComplete() {
        if (!configFile.exists()) return false;
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        
        // Check all required sections exist
        for (String section : REQUIRED_SECTIONS) {
            if (!config.isConfigurationSection(section)) {
                return false;
            }
        }
        
        // Check chunk-values subsections
        if (config.isConfigurationSection("chunk-values")) {
            var chunkValues = config.getConfigurationSection("chunk-values");
            for (String subsection : REQUIRED_CHUNK_VALUE_SECTIONS) {
                if (!chunkValues.isConfigurationSection(subsection)) {
                    return false;
                }
            }
        } else {
            return false;
        }
        
        // Check critical individual settings - removed glass-borders.skip-valuable-ores since we removed that feature
        return config.contains("worlds.enabled-worlds");
    }
}