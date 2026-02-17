package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.models.ChunkData;
import me.chunklock.models.PlayerData;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class MapDbToMySqlMigrationService {

    private final Logger logger;
    private final ChunkStore sourceChunkStore;
    private final PlayerStore sourcePlayerStore;
    private final ChunkStore targetChunkStore;
    private final PlayerStore targetPlayerStore;
    private final File migrationMarkerFile;

    public MapDbToMySqlMigrationService(ChunklockPlugin plugin,
                                        ChunkStore sourceChunkStore,
                                        PlayerStore sourcePlayerStore,
                                        ChunkStore targetChunkStore,
                                        PlayerStore targetPlayerStore) {
        this(plugin.getDataFolder(), plugin.getLogger(), sourceChunkStore, sourcePlayerStore, targetChunkStore, targetPlayerStore);
    }

    MapDbToMySqlMigrationService(File dataFolder,
                                 Logger logger,
                                 ChunkStore sourceChunkStore,
                                 PlayerStore sourcePlayerStore,
                                 ChunkStore targetChunkStore,
                                 PlayerStore targetPlayerStore) {
        this.logger = logger;
        this.sourceChunkStore = sourceChunkStore;
        this.sourcePlayerStore = sourcePlayerStore;
        this.targetChunkStore = targetChunkStore;
        this.targetPlayerStore = targetPlayerStore;
        this.migrationMarkerFile = new File(dataFolder, ".mysql_migration_completed");
    }

    public boolean needsMigration() {
        if (migrationMarkerFile.exists()) {
            return false;
        }

        if (targetChunkStore.getTotalChunks() > 0 || targetPlayerStore.getTotalPlayers() > 0) {
            return false;
        }

        return sourceChunkStore.getTotalChunks() > 0 || sourcePlayerStore.getTotalPlayers() > 0;
    }

    public boolean migrate() {
        if (!needsMigration()) {
            return true;
        }

        int sourceChunks = sourceChunkStore.getTotalChunks();
        int sourcePlayers = sourcePlayerStore.getTotalPlayers();
        logger.info("Starting one-time migration from MapDB to MySQL...");
        logger.info("Source totals: " + sourceChunks + " chunks, " + sourcePlayers + " players");

        try {
            int migratedChunks = migrateChunks();
            int migratedPlayers = migratePlayers();

            int targetChunks = targetChunkStore.getTotalChunks();
            int targetPlayers = targetPlayerStore.getTotalPlayers();

            if (targetChunks != sourceChunks || targetPlayers != sourcePlayers) {
                logger.severe("❌ MySQL migration verification failed. Source=" + sourceChunks + "/" + sourcePlayers +
                        " Target=" + targetChunks + "/" + targetPlayers + " (chunks/players)");
                return false;
            }

            markMigrationComplete();
            logger.info("✅ MapDB to MySQL migration complete: " + migratedChunks + " chunks, " + migratedPlayers + " players");
            return true;
        } catch (Exception e) {
            logger.severe("❌ MapDB to MySQL migration failed: " + e.getMessage());
            return false;
        }
    }

    private int migrateChunks() {
        int migrated = 0;
        for (String chunkKey : sourceChunkStore.getAllChunkKeys()) {
            ChunkData data = sourceChunkStore.getChunk(chunkKey);
            if (data != null) {
                targetChunkStore.saveChunk(chunkKey, data);
                migrated++;
            }
        }
        return migrated;
    }

    private int migratePlayers() {
        int migrated = 0;
        for (String playerId : sourcePlayerStore.getAllPlayerIds()) {
            PlayerData data = sourcePlayerStore.getPlayerData(playerId);
            if (data != null) {
                targetPlayerStore.savePlayerData(playerId, data);
                migrated++;
            }
        }
        return migrated;
    }

    private void markMigrationComplete() throws IOException {
        migrationMarkerFile.createNewFile();
    }
}
