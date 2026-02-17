package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.DatabaseConfig;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static StorageSelection createStores(ChunklockPlugin plugin) {
        DatabaseConfig config = plugin.getConfigManager().getDatabaseConfig();
        String type = config.getType();

        if ("mysql".equalsIgnoreCase(type)) {
            MySqlConnectionProvider provider = new MySqlConnectionProvider(plugin, config);
            StartupDecision decision = resolveStartupDecision(type, provider.initialize(), config.isFailFast());
            if (decision == StartupDecision.MYSQL) {
                long ttl = config.getMySqlCacheTtlMs();
                return new StorageSelection(
                    new MySqlChunkDatabase(plugin, provider, ttl),
                    new MySqlPlayerDatabase(plugin, provider, ttl),
                    provider,
                    true,
                    false
                );
            }

            if (decision == StartupDecision.FAILURE) {
                plugin.getLogger().severe("MySQL backend selected with fail-fast=true; startup cannot continue.");
                return new StorageSelection(null, null, null, true, true);
            }

            plugin.getLogger().severe("MySQL backend failed; falling back to MapDB because fail-fast=false.");
            return createMapDbStores(plugin);
        }

        return createMapDbStores(plugin);
    }

    static StartupDecision resolveStartupDecision(String configuredType, boolean mysqlInitialized, boolean failFast) {
        if (!"mysql".equalsIgnoreCase(configuredType)) {
            return StartupDecision.MAPDB;
        }
        if (mysqlInitialized) {
            return StartupDecision.MYSQL;
        }
        if (failFast) {
            return StartupDecision.FAILURE;
        }
        return StartupDecision.MAPDB;
    }

    public static StorageSelection createMapDbStores(ChunklockPlugin plugin) {
        return new StorageSelection(
                new ChunkDatabase(plugin),
                new PlayerDatabase(plugin),
                null,
                false,
                false
        );
    }

    public static final class StorageSelection {
        private final ChunkStore chunkStore;
        private final PlayerStore playerStore;
        private final MySqlConnectionProvider mySqlConnectionProvider;
        private final boolean mysqlMode;
        private final boolean startupFailure;

        public StorageSelection(ChunkStore chunkStore,
                                PlayerStore playerStore,
                                MySqlConnectionProvider mySqlConnectionProvider,
                                boolean mysqlMode,
                                boolean startupFailure) {
            this.chunkStore = chunkStore;
            this.playerStore = playerStore;
            this.mySqlConnectionProvider = mySqlConnectionProvider;
            this.mysqlMode = mysqlMode;
            this.startupFailure = startupFailure;
        }

        public ChunkStore getChunkStore() {
            return chunkStore;
        }

        public PlayerStore getPlayerStore() {
            return playerStore;
        }

        public MySqlConnectionProvider getMySqlConnectionProvider() {
            return mySqlConnectionProvider;
        }

        public boolean isMysqlMode() {
            return mysqlMode;
        }

        public boolean isStartupFailure() {
            return startupFailure;
        }
    }

    enum StartupDecision {
        MAPDB,
        MYSQL,
        FAILURE
    }
}
