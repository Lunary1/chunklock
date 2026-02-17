package me.chunklock.config.modular;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for database.yml
 * Manages persistent storage backend settings.
 */
public class DatabaseConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public DatabaseConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "database.yml");
        if (!file.exists()) {
            plugin.saveResource("database.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getType() {
        return config.getString("database.type", "mapdb").toLowerCase();
    }

    public boolean isFailFast() {
        return config.getBoolean("database.fail-fast", true);
    }

    public String getMySqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMySqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMySqlDatabase() {
        return config.getString("database.mysql.database", "chunklock");
    }

    public String getMySqlUsername() {
        return config.getString("database.mysql.username", "chunklock_user");
    }

    public String getMySqlPassword() {
        return config.getString("database.mysql.password", "change_me");
    }

    public boolean isMySqlUseSsl() {
        return config.getBoolean("database.mysql.use-ssl", false);
    }

    public int getMySqlPoolMaxSize() {
        return config.getInt("database.mysql.pool.max-size", 10);
    }

    public int getMySqlPoolMinIdle() {
        return config.getInt("database.mysql.pool.min-idle", 2);
    }

    public long getMySqlPoolConnectionTimeoutMs() {
        return config.getLong("database.mysql.pool.connection-timeout-ms", 30000L);
    }

    public long getMySqlPoolIdleTimeoutMs() {
        return config.getLong("database.mysql.pool.idle-timeout-ms", 600000L);
    }

    public long getMySqlPoolMaxLifetimeMs() {
        return config.getLong("database.mysql.pool.max-lifetime-ms", 1800000L);
    }

    public long getMySqlCacheTtlMs() {
        return config.getLong("database.mysql.cache.ttl-ms", 300000L);
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}
