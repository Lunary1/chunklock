package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChunkLockManager {

    private final Map<String, ChunkData> chunkDataMap = new HashMap<>();
    private final ChunkEvaluator chunkEvaluator;
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public ChunkLockManager(ChunkEvaluator chunkEvaluator, JavaPlugin plugin) {
        this.chunkEvaluator = chunkEvaluator;
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "chunk_data.yml");
        loadAll();
    }

    public boolean isLocked(Chunk chunk) {
        return getChunkData(chunk).isLocked();
    }

    public Difficulty getDifficulty(Chunk chunk) {
        return getChunkData(chunk).getDifficulty();
    }

    public void lockChunk(Chunk chunk, Difficulty difficulty) {
        String key = getChunkKey(chunk);
        chunkDataMap.put(key, new ChunkData(true, difficulty));
    }

    public void unlockChunk(Chunk chunk) {
        ChunkData data = getChunkData(chunk);
        data.setLocked(false);
    }

    private void loadAll() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create chunk_data.yml");
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            try {
                String world = config.getString(key + ".world");
                int x = config.getInt(key + ".x");
                int z = config.getInt(key + ".z");
                boolean locked = config.getBoolean(key + ".locked", true);
                String diffStr = config.getString(key + ".difficulty", "NORMAL");
                Difficulty diff = Difficulty.valueOf(diffStr.toUpperCase());

                String mapKey = world + ":" + x + ":" + z;
                chunkDataMap.put(mapKey, new ChunkData(locked, diff));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load chunk entry: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + chunkDataMap.size() + " chunks from chunk_data.yml");
    }

    public void saveAll() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        for (String key : config.getKeys(false)) {
            config.set(key, null); // Clear existing entries
        }

        for (Map.Entry<String, ChunkData> entry : chunkDataMap.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 3) continue;

            String world = parts[0];
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String base = entry.getKey();

            config.set(base + ".world", world);
            config.set(base + ".x", x);
            config.set(base + ".z", z);
            config.set(base + ".locked", entry.getValue().isLocked());
            config.set(base + ".difficulty", entry.getValue().getDifficulty().name());
        }

        try {
            config.save(file);
            plugin.getLogger().info("Saved chunk data to chunk_data.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save chunk_data.yml");
        }
    }

    public void initializeChunk(Chunk chunk, UUID playerId) {
        String key = getChunkKey(chunk);
        if (!chunkDataMap.containsKey(key)) {
            // Use ChunkEvaluator to determine difficulty based on actual chunk properties
            ChunkEvaluator.ChunkValueData evaluation = chunkEvaluator.evaluateChunk(playerId, chunk);
            chunkDataMap.put(key, new ChunkData(true, evaluation.difficulty));
        }
    }

    // Overload for backward compatibility
    public void initializeChunk(Chunk chunk) {
        initializeChunk(chunk, null); // Will use fallback logic in ChunkEvaluator
    }

    private ChunkData getChunkData(Chunk chunk) {
        initializeChunk(chunk);
        return chunkDataMap.get(getChunkKey(chunk));
    }

    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    // Method to get chunk evaluation info for display
    public ChunkEvaluator.ChunkValueData evaluateChunk(UUID playerId, Chunk chunk) {
        return chunkEvaluator.evaluateChunk(playerId, chunk);
    }

    private final Set<UUID> bypassingPlayers = new HashSet<>();

    public void setBypassing(Player player, boolean bypassing) {
        if (bypassing) {
            bypassingPlayers.add(player.getUniqueId());
        } else {
            bypassingPlayers.remove(player.getUniqueId());
        }
    }

    public boolean isBypassing(Player player) {
        return bypassingPlayers.contains(player.getUniqueId());
}
}