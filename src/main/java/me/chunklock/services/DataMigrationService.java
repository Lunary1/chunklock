package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataMigrationService {
    
    private final ChunklockPlugin plugin;
    private final ChunkDatabase chunkDatabase;
    private final PlayerDatabase playerDatabase;
    private final File dataYmlFile;

    public DataMigrationService(ChunklockPlugin plugin, ChunkDatabase chunkDatabase, PlayerDatabase playerDatabase) {
        this.plugin = plugin;
        this.chunkDatabase = chunkDatabase;
        this.playerDatabase = playerDatabase;
        this.dataYmlFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public boolean needsMigration() {
        // Check if migration flag exists in a separate file or if data.yml exists
        File migrationFlag = new File(plugin.getDataFolder(), ".migration_completed");
        if (migrationFlag.exists()) {
            return false; // Already migrated
        }
        
        // Check if database files exist and have data
        if (chunkDatabase.getTotalChunks() > 0 || playerDatabase.getTotalPlayers() > 0) {
            // Databases have data, assume migration already done
            markMigrationComplete();
            return false;
        }
        
        // Check if data.yml exists and has data
        if (!dataYmlFile.exists()) {
            markMigrationComplete();
            return false; // No data to migrate
        }
        
        return true;
    }

    public boolean migrate() {
        if (!needsMigration()) {
            plugin.getLogger().info("No migration needed - databases already initialized");
            return true;
        }

        plugin.getLogger().info("Starting data migration from YAML to MapDB...");
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(dataYmlFile);
            
            int chunksMigrated = migrateChunks(config);
            int playersMigrated = migratePlayers(config);
            
            // Backup original file
            File backupFile = new File(plugin.getDataFolder(), "data.yml.backup");
            try {
                java.nio.file.Files.copy(dataYmlFile.toPath(), backupFile.toPath(), 
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backed up data.yml to data.yml.backup");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to backup data.yml: " + e.getMessage());
            }
            
            markMigrationComplete();
            
            plugin.getLogger().info("✅ Migration complete: " + chunksMigrated + " chunks, " + playersMigrated + " players migrated");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Migration failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private int migrateChunks(FileConfiguration config) {
        int count = 0;
        ConfigurationSection chunksSection = config.getConfigurationSection("chunks");
        if (chunksSection == null) {
            return 0;
        }

        for (String key : chunksSection.getKeys(false)) {
            try {
                String world = chunksSection.getString(key + ".world");
                int x = chunksSection.getInt(key + ".x");
                int z = chunksSection.getInt(key + ".z");
                boolean locked = chunksSection.getBoolean(key + ".locked", true);
                String diffStr = chunksSection.getString(key + ".difficulty", "NORMAL");
                Difficulty diff = Difficulty.valueOf(diffStr.toUpperCase());
                String ownerStr = chunksSection.getString(key + ".owner", null);
                UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                
                // Get baseValue, biome, score if they exist (for newer data)
                int baseValue = chunksSection.getInt(key + ".baseValue", 0);
                String biome = chunksSection.getString(key + ".biome", null);
                int score = chunksSection.getInt(key + ".score", 0);
                Long unlockedAt = chunksSection.contains(key + ".unlockedAt") ? 
                    chunksSection.getLong(key + ".unlockedAt") : null;

                String chunkKey = chunkDatabase.getChunkKey(world, x, z);
                ChunkData chunkData = ChunkData.builder()
                        .locked(locked)
                        .difficulty(diff)
                        .ownerId(owner)
                        .baseValue(baseValue)
                        .biome(biome)
                        .score(score)
                        .unlockedAt(unlockedAt)
                        .build();

                chunkDatabase.saveChunk(chunkKey, chunkData);
                count++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate chunk entry: " + key + " - " + e.getMessage());
            }
        }

        return count;
    }

    private int migratePlayers(FileConfiguration config) {
        int count = 0;
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return 0;
        }

        for (String uuidString : playersSection.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(uuidString);
                
                // Migrate spawn location
                if (playersSection.contains(uuidString + ".spawn")) {
                    int x = playersSection.getInt(uuidString + ".spawn.x");
                    int y = playersSection.getInt(uuidString + ".spawn.y");
                    int z = playersSection.getInt(uuidString + ".spawn.z");
                    String worldName = playersSection.getString(uuidString + ".spawn.world");
                    
                    if (worldName != null) {
                        World world = Bukkit.getWorld(worldName);
                        if (world != null) {
                            Location spawn = new Location(world, x, y, z);
                            playerDatabase.setSpawnLocation(playerId, spawn);
                        }
                    }
                }
                
                // Migrate progress data
                int unlockedChunks = playersSection.getInt(uuidString + ".progress.unlocked_chunks", 0);
                if (unlockedChunks > 0) {
                    playerDatabase.setUnlockedChunks(playerId, unlockedChunks);
                }
                
                count++;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to migrate player entry: " + uuidString + " - " + e.getMessage());
            }
        }

        return count;
    }

    private void markMigrationComplete() {
        try {
            File migrationFlag = new File(plugin.getDataFolder(), ".migration_completed");
            migrationFlag.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create migration flag file: " + e.getMessage());
        }
    }
}

