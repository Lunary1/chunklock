package me.chunklock;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressTracker {
    /** Counts unlocked chunks per team leader UUID. */
    private final Map<UUID, Integer> unlockedChunkCount = new HashMap<>();
    /** Tracks whether a player has initialized the chunklock mode. */
    private final Map<UUID, Boolean> initialized = new HashMap<>();
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private final TeamManager teamManager;

    public PlayerProgressTracker(JavaPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        loadAll();
    }

    private void loadAll() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml");
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidString : players.getKeys(false)) {
            try {
                UUID teamId = UUID.fromString(uuidString);
                int count = players.getInt(uuidString + ".progress.unlocked_chunks", 0);
                boolean init = players.getBoolean(uuidString + ".progress.initialized", false);
                unlockedChunkCount.put(teamId, count);
                initialized.put(teamId, init);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load progress for UUID: " + uuidString);
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> entry : unlockedChunkCount.entrySet()) {
            String key = "players." + entry.getKey();
            config.set(key + ".progress.unlocked_chunks", entry.getValue());
            config.set(key + ".progress.initialized", initialized.getOrDefault(entry.getKey(), false));
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml");
        }
    }

    public void incrementUnlockedChunks(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        unlockedChunkCount.put(teamId, getUnlockedChunkCount(playerId) + 1);
        saveProgress(teamId); // Save immediately for safety
    }

    public int getUnlockedChunkCount(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        return unlockedChunkCount.getOrDefault(teamId, 0);
    }

    public void resetPlayer(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        unlockedChunkCount.remove(teamId);
        if (config != null) {
            config.set("players." + teamId, null);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save data.yml during reset");
            }
        }
    }

    private void saveProgress(UUID teamId) {
        if (config != null) {
            config.set("players." + teamId + ".progress.unlocked_chunks", unlockedChunkCount.getOrDefault(teamId, 0));
            config.set("players." + teamId + ".progress.initialized", initialized.getOrDefault(teamId, false));
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save progress for team: " + teamId);
            }
        }
    }

    public boolean isInitialized(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        return initialized.getOrDefault(teamId, false);
    }

    public void setInitialized(UUID playerId, boolean value) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        initialized.put(teamId, value);
        saveProgress(teamId);
    }
}