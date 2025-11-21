package me.chunklock.managers;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import me.chunklock.managers.TeamManager;

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
    private final TeamManager teamManager;
    private final File file;
    private FileConfiguration config;

    private final double contestedCostMultiplier;
    private final int maxContestedClaimsPerDay;

    public ChunkLockManager(ChunkEvaluator chunkEvaluator, JavaPlugin plugin, TeamManager teamManager) {
        this.chunkEvaluator = chunkEvaluator;
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.file = new File(plugin.getDataFolder(), "data.yml");

        this.contestedCostMultiplier = plugin.getConfig().getDouble("contested-cost-multiplier", 3.0);
        this.maxContestedClaimsPerDay = plugin.getConfig().getInt("max-contested-claims-per-day", 5);

        loadAll();
    }

    public boolean isLocked(Chunk chunk) {
        return getChunkData(chunk).isLocked();
    }

    public Difficulty getDifficulty(Chunk chunk) {
        return getChunkData(chunk).getDifficulty();
    }

    public UUID getChunkOwner(Chunk chunk) {
        return getChunkData(chunk).getOwnerId();
    }

    public boolean isContestedChunk(Chunk chunk, UUID teamId) {
        UUID owner = getChunkOwner(chunk);
        return owner != null && !owner.equals(teamId);
    }

    public double getContestedCostMultiplier() {
        return contestedCostMultiplier;
    }

    public int getMaxContestedClaimsPerDay() {
        return maxContestedClaimsPerDay;
    }

    public void lockChunk(Chunk chunk, Difficulty difficulty) {
        String key = getChunkKey(chunk);
        chunkDataMap.put(key, new ChunkData(true, difficulty));
    }

    public void unlockChunk(Chunk chunk, UUID ownerId) {
        ChunkData data = getChunkData(chunk);
        data.setLocked(false);
        data.setOwnerId(ownerId);
    }

    // Backwards compatibility
    public void unlockChunk(Chunk chunk) {
        unlockChunk(chunk, null);
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
                String ownerStr = section.getString(key + ".owner", null);
                UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                double baseValue = section.getDouble(key + ".base-value", 0.0);

                String mapKey = world + ":" + x + ":" + z;
                chunkDataMap.put(mapKey, new ChunkData(locked, diff, owner, baseValue));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load chunk entry: " + key);
            }
        }

        plugin.getLogger().info("Loaded " + chunkDataMap.size() + " chunks from data.yml");
    }

    public void saveAll() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            
            config = YamlConfiguration.loadConfiguration(file);
            
            ConfigurationSection section = config.getConfigurationSection("chunks");
            if (section == null) {
                section = config.createSection("chunks");
            }

            Set<String> existingKeys = new HashSet<>(section.getKeys(false));
            for (String key : existingKeys) {
                section.set(key, null);
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
                if (entry.getValue().getOwnerId() != null) {
                    section.set(base + ".owner", entry.getValue().getOwnerId().toString());
                } else {
                    section.set(base + ".owner", null);
                }
                section.set(base + ".base-value", entry.getValue().getBaseValue());
            }

            config.save(file);
            plugin.getLogger().info("Saved " + chunkDataMap.size() + " chunks to data.yml");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save chunk data to data.yml: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error saving chunk data: " + e.getMessage());
            e.printStackTrace();
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

    public TeamManager getTeamManager() {
        return teamManager;
    }

    /**
     * Get the base value for a chunk.
     * @param chunk The chunk to get the base value for
     * @return The base value, or 0.0 if not set
     */
    public double getBaseValue(Chunk chunk) {
        return getChunkData(chunk).getBaseValue();
    }

    /**
     * Set the base value for a chunk.
     * @param chunk The chunk to set the base value for
     * @param value The base value to set
     */
    public void setBaseValue(Chunk chunk, double value) {
        getChunkData(chunk).setBaseValue(value);
    }
}