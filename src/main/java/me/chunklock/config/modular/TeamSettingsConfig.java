package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for team-settings.yml
 * Manages team system configuration.
 */
public class TeamSettingsConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public TeamSettingsConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "team-settings.yml");
        if (!file.exists()) {
            plugin.saveResource("team-settings.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public int getMaxTeamSize() {
        return config.getInt("max-team-size", 6);
    }

    public int getMaxTeamsPerServer() {
        return config.getInt("max-teams-per-server", 100);
    }

    public boolean isAllowSoloTeams() {
        return config.getBoolean("allow-solo-teams", true);
    }

    public int getJoinRequestTtlHours() {
        return config.getInt("join-request-ttl-hours", 72);
    }

    public double getTeamCostMultiplier() {
        return config.getDouble("team-cost-multiplier", 0.15);
    }

    public double getBaseTeamCost() {
        return config.getDouble("base-team-cost", 1.0);
    }

    public double getMaxCostMultiplier() {
        return config.getDouble("max-cost-multiplier", 3.0);
    }

    public double getContestedCostMultiplier() {
        return config.getDouble("contested-cost-multiplier", 3.0);
    }

    public int getMaxContestedClaimsPerDay() {
        return config.getInt("max-contested-claims-per-day", 5);
    }

    public boolean isTeamChatEnabled() {
        return config.getBoolean("enable-team-chat", true);
    }

    public boolean isLeaderboardsEnabled() {
        return config.getBoolean("enable-leaderboards", true);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

