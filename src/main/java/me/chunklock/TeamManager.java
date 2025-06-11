package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple team manager. Each player has a team leader UUID. Players with the same leader
 * are considered on the same team. Data is persisted in teams.yml.
 */
public class TeamManager {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<UUID, UUID> teamLeaders = new HashMap<>();

    public TeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        loadAll();
    }

    private void loadAll() {
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create teams.yml");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        for (String key : config.getKeys(false)) {
            try {
                UUID player = UUID.fromString(key);
                UUID leader = UUID.fromString(config.getString(key));
                teamLeaders.put(player, leader);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load team info for " + key);
            }
        }
    }

    public void saveAll() {
        for (Map.Entry<UUID, UUID> entry : teamLeaders.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue().toString());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml");
        }
    }

    public UUID getTeamLeader(UUID player) {
        return teamLeaders.getOrDefault(player, player);
    }

    public void setTeamLeader(UUID player, UUID leader) {
        teamLeaders.put(player, leader);
        saveAll();
    }

    public boolean sameTeam(UUID a, UUID b) {
        return getTeamLeader(a).equals(getTeamLeader(b));
    }
}
