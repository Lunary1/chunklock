package me.chunklock;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerProgressTracker {
    private final Map<UUID, Integer> unlockedChunkCount = new HashMap<>();
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public PlayerProgressTracker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_progress.yml");
        loadAll();
    }

    private void loadAll() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_progress.yml");
                return;
            }
        }
        
        config = YamlConfiguration.loadConfiguration(file);
        
        for (String uuidString : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                int count = config.getInt(uuidString + ".unlocked_chunks", 0);
                unlockedChunkCount.put(uuid, count);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load progress for UUID: " + uuidString);
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, Integer> entry : unlockedChunkCount.entrySet()) {
            String key = entry.getKey().toString();
            config.set(key + ".unlocked_chunks", entry.getValue());
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player_progress.yml");
        }
    }

    public void incrementUnlockedChunks(UUID playerId) {
        unlockedChunkCount.put(playerId, getUnlockedChunkCount(playerId) + 1);
        saveProgress(playerId); // Save immediately for safety
    }

    public int getUnlockedChunkCount(UUID playerId) {
        return unlockedChunkCount.getOrDefault(playerId, 0);
    }

    public void resetPlayer(UUID playerId) {
        unlockedChunkCount.remove(playerId);
        if (config != null) {
            config.set(playerId.toString(), null);
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save player_progress.yml during reset");
            }
        }
    }

    private void saveProgress(UUID playerId) {
        if (config != null) {
            config.set(playerId.toString() + ".unlocked_chunks", getUnlockedChunkCount(playerId));
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save progress for player: " + playerId);
            }
        }
    }
}