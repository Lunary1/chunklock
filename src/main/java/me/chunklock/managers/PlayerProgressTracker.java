package me.chunklock.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.managers.TeamManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressTracker {
    /** Counts unlocked chunks per team leader UUID. */
    private final Map<UUID, Integer> unlockedChunkCount = new HashMap<>();
    private static class ContestedData {
        int count;
        long lastReset;

        ContestedData(int count, long lastReset) {
            this.count = count;
            this.lastReset = lastReset;
        }
    }
    private final Map<UUID, ContestedData> contestedClaims = new HashMap<>();
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
                unlockedChunkCount.put(teamId, count);

                int contested = players.getInt(uuidString + ".progress.contested_claims.count", 0);
                long reset = players.getLong(uuidString + ".progress.contested_claims.last_reset", System.currentTimeMillis());
                contestedClaims.put(teamId, new ContestedData(contested, reset));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load progress for UUID: " + uuidString);
            }
        }
    }

    public void saveAll() {
        try {
            // CRITICAL FIX: Load existing file to preserve other sections
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            
            // Load existing configuration first
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            // Update only the progress data for each team leader
            for (Map.Entry<UUID, Integer> entry : unlockedChunkCount.entrySet()) {
                String key = "players." + entry.getKey();
                config.set(key + ".progress.unlocked_chunks", entry.getValue());

                ContestedData cd = contestedClaims.get(entry.getKey());
                if (cd != null) {
                    config.set(key + ".progress.contested_claims.count", cd.count);
                    config.set(key + ".progress.contested_claims.last_reset", cd.lastReset);
                }
            }
            
            // Save the file  
            config.save(file);
            plugin.getLogger().info("Saved progress data for " + unlockedChunkCount.size() + " teams to data.yml");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save progress data to data.yml: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error saving progress data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void incrementUnlockedChunks(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        unlockedChunkCount.put(teamId, getUnlockedChunkCount(playerId) + 1);
        saveProgress(teamId); // Save immediately for safety
    }

    public void incrementContestedClaims(UUID teamId) {
        ContestedData data = contestedClaims.computeIfAbsent(teamId, k -> new ContestedData(0, System.currentTimeMillis()));
        resetIfNeeded(data);
        data.count++;
        saveProgress(teamId);
    }

    public boolean canClaimContested(UUID teamId, int maxPerDay) {
        ContestedData data = contestedClaims.computeIfAbsent(teamId, k -> new ContestedData(0, System.currentTimeMillis()));
        resetIfNeeded(data);
        return data.count < maxPerDay;
    }

    public int getContestedClaimCount(UUID teamId) {
        ContestedData data = contestedClaims.computeIfAbsent(teamId, k -> new ContestedData(0, System.currentTimeMillis()));
        resetIfNeeded(data);
        return data.count;
    }

    private void resetIfNeeded(ContestedData data) {
        long now = System.currentTimeMillis();
        if (now - data.lastReset > 86_400_000L) {
            data.count = 0;
            data.lastReset = now;
        }
    }

    public int getUnlockedChunkCount(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        return unlockedChunkCount.getOrDefault(teamId, 0);
    }

    public void resetPlayer(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        unlockedChunkCount.remove(teamId);
        contestedClaims.remove(teamId);
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
            ContestedData cd = contestedClaims.get(teamId);
            if (cd != null) {
                config.set("players." + teamId + ".progress.contested_claims.count", cd.count);
                config.set("players." + teamId + ".progress.contested_claims.last_reset", cd.lastReset);
            }
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save progress for team: " + teamId);
            }
        }
    }
}