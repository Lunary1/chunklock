package me.chunklock.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final File file;
    private final FileConfiguration config;
    private final Map<UUID, Location> playerSpawns = new HashMap<>();

    public PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml");
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    private void loadAll() {
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return;
        for (String uuidString : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                int x = players.getInt(uuidString + ".spawn.x");
                int y = players.getInt(uuidString + ".spawn.y");
                int z = players.getInt(uuidString + ".spawn.z");
                String worldName = players.getString(uuidString + ".spawn.world");
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    playerSpawns.put(uuid, new Location(world, x, y, z));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load spawn for UUID: " + uuidString);
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
            
            // Update only the players section
            for (Map.Entry<UUID, Location> entry : playerSpawns.entrySet()) {
                String key = "players." + entry.getKey();
                Location loc = entry.getValue();
                config.set(key + ".spawn.x", loc.getBlockX());
                config.set(key + ".spawn.y", loc.getBlockY());
                config.set(key + ".spawn.z", loc.getBlockZ());
                config.set(key + ".spawn.world", loc.getWorld().getName());
            }
            
            // Save the file
            config.save(file);
            plugin.getLogger().info("Saved " + playerSpawns.size() + " player spawn locations to data.yml");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data to data.yml: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error saving player data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasChunk(UUID uuid) {
        return playerSpawns.containsKey(uuid);
    }

    public void setChunk(UUID uuid, Location location) {
        playerSpawns.put(uuid, location);
    }

    public Location getChunkSpawn(UUID uuid) {
        return playerSpawns.get(uuid);
    }
}
