package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
        this.file = new File(plugin.getDataFolder(), "player_chunks.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_chunks.yml");
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    private void loadAll() {
        for (String uuidString : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                int x = config.getInt(uuidString + ".x");
                int y = config.getInt(uuidString + ".y");
                int z = config.getInt(uuidString + ".z");
                String worldName = config.getString(uuidString + ".world");
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
        for (Map.Entry<UUID, Location> entry : playerSpawns.entrySet()) {
            String key = entry.getKey().toString();
            Location loc = entry.getValue();
            config.set(key + ".x", loc.getBlockX());
            config.set(key + ".y", loc.getBlockY());
            config.set(key + ".z", loc.getBlockZ());
            config.set(key + ".world", loc.getWorld().getName());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player_chunks.yml");
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
