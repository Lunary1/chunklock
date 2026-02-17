package me.chunklock.services;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.DatabaseConfig;

import java.sql.Connection;
import java.sql.SQLException;

public class MySqlConnectionProvider {

    private final ChunklockPlugin plugin;
    private final DatabaseConfig databaseConfig;
    private HikariDataSource dataSource;

    public MySqlConnectionProvider(ChunklockPlugin plugin, DatabaseConfig databaseConfig) {
        this.plugin = plugin;
        this.databaseConfig = databaseConfig;
    }

    public boolean initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(buildJdbcUrl());
            config.setUsername(databaseConfig.getMySqlUsername());
            config.setPassword(databaseConfig.getMySqlPassword());
            config.setMaximumPoolSize(databaseConfig.getMySqlPoolMaxSize());
            config.setMinimumIdle(databaseConfig.getMySqlPoolMinIdle());
            config.setConnectionTimeout(databaseConfig.getMySqlPoolConnectionTimeoutMs());
            config.setIdleTimeout(databaseConfig.getMySqlPoolIdleTimeoutMs());
            config.setMaxLifetime(databaseConfig.getMySqlPoolMaxLifetimeMs());
            config.setPoolName("Chunklock-MySQL");

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);

            try (Connection ignored = getConnection()) {
                plugin.getLogger().info("✅ MySQL connection pool initialized");
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Failed to initialize MySQL connection pool: " + e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("MySQL datasource not initialized");
        }
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private String buildJdbcUrl() {
        return "jdbc:mysql://" + databaseConfig.getMySqlHost() + ":" + databaseConfig.getMySqlPort() + "/" +
                databaseConfig.getMySqlDatabase() +
                "?useSSL=" + databaseConfig.isMySqlUseSsl() + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
