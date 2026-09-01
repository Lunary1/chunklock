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

    /**
     * Where pricing data goes: chunk costs (#83) and chunk profiles plus the material
     * baseline (#86).
     *
     * <p>Routed off the <em>selection</em> rather than off {@code database.type} directly, so
     * it follows the decision actually taken. That matters in one case: with
     * {@code fail-fast: false} a MySQL server whose database is unreachable falls back to
     * MapDB, and pricing must fall back with it rather than pointing at a pool that never
     * initialized.</p>
     *
     * <p>Before #95 this was not routed at all - {@link ChunkCostDatabase} opened an H2 file
     * unconditionally, so a MySQL network gave every node its own prices and its own
     * baseline.</p>
     */
    public static CostStorageBackend createCostStorageBackend(ChunklockPlugin plugin,
                                                              StorageSelection selection) {
        MySqlConnectionProvider provider =
            selection != null ? selection.getMySqlConnectionProvider() : null;
        boolean mysqlMode = selection != null && selection.isMysqlMode();

        if (resolveCostStorage(mysqlMode, provider != null) == CostStorageDecision.MYSQL) {
            return new MySqlCostStorageBackend(plugin, provider);
        }
        return new H2CostStorageBackend(plugin);
    }

    /**
     * The routing decision on its own, so it can be tested without a live plugin.
     *
     * <p>Both conditions are required, and the second is not paranoia: with
     * {@code fail-fast: false} a MySQL server whose database is unreachable falls back to
     * MapDB and its selection carries no provider. Routing pricing to MySQL on the strength of
     * the config alone would then hand every query a pool that never initialized.</p>
     */
    static CostStorageDecision resolveCostStorage(boolean mysqlMode, boolean hasProvider) {
        return mysqlMode && hasProvider ? CostStorageDecision.MYSQL : CostStorageDecision.H2;
    }

    /** Where pricing data goes. */
    enum CostStorageDecision {
        H2,
        MYSQL
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
