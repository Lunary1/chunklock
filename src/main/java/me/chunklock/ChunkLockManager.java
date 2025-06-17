package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
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
        this.file = new File(plugin.getDataFolder(), "data.yml");
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

    /**
     * Resets all chunks for a specific player by re-locking everything except their new starting chunk
     */
    public void resetPlayerChunks(UUID playerId, Chunk newStartingChunk) {
        plugin.getLogger().info("Resetting all chunks for player " + playerId + ", keeping chunk " + 
            newStartingChunk.getX() + "," + newStartingChunk.getZ() + " unlocked");
        
        int chunksLocked = 0;
        String startingChunkKey = getChunkKey(newStartingChunk);
        
        // Re-lock all chunks except the new starting chunk
        for (Map.Entry<String, ChunkData> entry : chunkDataMap.entrySet()) {
            String chunkKey = entry.getKey();
            ChunkData chunkData = entry.getValue();
            
            // Don't lock the new starting chunk
            if (!chunkKey.equals(startingChunkKey)) {
                if (!chunkData.isLocked()) {
                    chunkData.setLocked(true);
                    chunksLocked++;
                }
            }
        }
        
        // Ensure the starting chunk is unlocked
        ChunkData startingData = getChunkData(newStartingChunk);
        startingData.setLocked(false);
        
        plugin.getLogger().info("Re-locked " + chunksLocked + " chunks for player " + playerId);
        
        // Save the changes immediately
        saveAll();
    }

    /**
     * Gets all unlocked chunks in the world (for debugging/admin purposes)
     */
    public Set<String> getUnlockedChunks() {
        Set<String> unlockedChunks = new HashSet<>();
        for (Map.Entry<String, ChunkData> entry : chunkDataMap.entrySet()) {
            if (!entry.getValue().isLocked()) {
                unlockedChunks.add(entry.getKey());
            }
        }
        return unlockedChunks;
    }

    /**
     * Counts total unlocked chunks in the world
     */
    public int getTotalUnlockedChunks() {
        int count = 0;
        for (ChunkData data : chunkDataMap.values()) {
            if (!data.isLocked()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Forces a re-evaluation and re-locking of all chunks
     */
    public void resetAllChunks() {
        plugin.getLogger().info("Performing complete chunk reset - locking all chunks");
        
        for (ChunkData data : chunkDataMap.values()) {
            data.setLocked(true);
        }
        
        saveAll();
        plugin.getLogger().info("All chunks have been locked");
    }

    private void loadAll() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml");
                return;
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("chunks");
        if (section == null) {
            section = config.createSection("chunks");
        }

        for (String key : section.getKeys(false)) {
            try {
                String world = section.getString(key + ".world");
                int x = section.getInt(key + ".x");
                int z = section.getInt(key + ".z");
                boolean locked = section.getBoolean(key + ".locked", true);
                String diffStr = section.getString(key + ".difficulty", "NORMAL");
                Difficulty diff = Difficulty.valueOf(diffStr.toUpperCase());

                String mapKey = world + ":" + x + ":" + z;
                chunkDataMap.put(mapKey, new ChunkData(locked, diff));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load chunk entry: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + chunkDataMap.size() + " chunks from data.yml");
    }

    public void saveAll() {
        if (config == null) {
            config = new YamlConfiguration();
        }

        ConfigurationSection section = config.getConfigurationSection("chunks");
        if (section == null) {
            section = config.createSection("chunks");
        }
        for (String key : section.getKeys(false)) {
            section.set(key, null); // Clear existing entries
        }

        for (Map.Entry<String, ChunkData> entry : chunkDataMap.entrySet()) {
            String[] parts = entry.getKey().split(":");
            if (parts.length != 3) continue;

            String world = parts[0];
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);
            String base = entry.getKey();

            section.set(base + ".world", world);
            section.set(base + ".x", x);
            section.set(base + ".z", z);
            section.set(base + ".locked", entry.getValue().isLocked());
            section.set(base + ".difficulty", entry.getValue().getDifficulty().name());
        }

        try {
            config.save(file);
            plugin.getLogger().info("Saved chunk data to data.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save data.yml");
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