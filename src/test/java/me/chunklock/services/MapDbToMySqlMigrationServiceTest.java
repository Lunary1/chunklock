package me.chunklock.services;

import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import me.chunklock.models.PlayerData;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapDbToMySqlMigrationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNotMigrateWhenMarkerExists() throws IOException {
        File marker = tempDir.resolve(".mysql_migration_completed").toFile();
        assertTrue(marker.createNewFile());

        MapChunkStore sourceChunk = new MapChunkStore();
        MapPlayerStore sourcePlayer = new MapPlayerStore();
        sourceChunk.saveChunk("world:1:1", ChunkData.builder().locked(false).difficulty(Difficulty.NORMAL).build());

        MapDbToMySqlMigrationService service = new MapDbToMySqlMigrationService(
                tempDir.toFile(), Logger.getLogger("test"), sourceChunk, sourcePlayer, new MapChunkStore(), new MapPlayerStore());

        assertFalse(service.needsMigration());
    }

    @Test
    void shouldNotMigrateWhenTargetHasData() {
        MapChunkStore sourceChunk = new MapChunkStore();
        MapPlayerStore sourcePlayer = new MapPlayerStore();
        MapChunkStore targetChunk = new MapChunkStore();
        MapPlayerStore targetPlayer = new MapPlayerStore();

        sourceChunk.saveChunk("world:1:1", ChunkData.builder().locked(false).difficulty(Difficulty.NORMAL).build());
        targetChunk.saveChunk("world:2:2", ChunkData.builder().locked(false).difficulty(Difficulty.NORMAL).build());

        MapDbToMySqlMigrationService service = new MapDbToMySqlMigrationService(
                tempDir.toFile(), Logger.getLogger("test"), sourceChunk, sourcePlayer, targetChunk, targetPlayer);

        assertFalse(service.needsMigration());
    }

    @Test
    void shouldMigrateAndCreateMarkerWhenNeeded() {
        MapChunkStore sourceChunk = new MapChunkStore();
        MapPlayerStore sourcePlayer = new MapPlayerStore();
        MapChunkStore targetChunk = new MapChunkStore();
        MapPlayerStore targetPlayer = new MapPlayerStore();

        sourceChunk.saveChunk("world:3:4", ChunkData.builder()
                .locked(false)
                .difficulty(Difficulty.HARD)
                .ownerId(UUID.randomUUID())
                .baseValue(55)
                .score(88)
                .build());

        PlayerData playerData = new PlayerData();
        playerData.setSpawnWorld("world");
        playerData.setUnlockedChunks(9);
        sourcePlayer.savePlayerData("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", playerData);

        MapDbToMySqlMigrationService service = new MapDbToMySqlMigrationService(
                tempDir.toFile(), Logger.getLogger("test"), sourceChunk, sourcePlayer, targetChunk, targetPlayer);

        assertTrue(service.needsMigration());
        assertTrue(service.migrate());
        assertFalse(service.needsMigration());

        assertNotNull(targetChunk.getChunk("world:3:4"));
        assertNotNull(targetPlayer.getPlayerData("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        assertTrue(tempDir.resolve(".mysql_migration_completed").toFile().exists());
    }

    private static final class MapChunkStore implements ChunkStore {
        private final Map<String, ChunkData> chunks = new HashMap<>();

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public ChunkData getChunk(Chunk chunk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChunkData getChunk(String chunkKey) {
            return chunks.get(chunkKey);
        }

        @Override
        public void saveChunk(Chunk chunk, ChunkData data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveChunk(String chunkKey, ChunkData data) {
            chunks.put(chunkKey, data);
        }

        @Override
        public void deleteChunk(Chunk chunk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteChunk(String chunkKey) {
            chunks.remove(chunkKey);
        }

        @Override
        public Set<String> getChunksByOwner(UUID ownerId) {
            return Set.of();
        }

        @Override
        public Set<String> getAllChunkKeys() {
            return new HashSet<>(chunks.keySet());
        }

        @Override
        public int getTotalChunks() {
            return chunks.size();
        }

        @Override
        public int getUnlockedChunksCount() {
            return (int) chunks.values().stream().filter(c -> !c.isLocked()).count();
        }

        @Override
        public String getChunkKey(Chunk chunk) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getChunkKey(String worldName, int x, int z) {
            return worldName + ":" + x + ":" + z;
        }

        @Override
        public void close() {
        }

        @Override
        public void clearCache() {
        }
    }

    private static final class MapPlayerStore implements PlayerStore {
        private final Map<String, PlayerData> players = new HashMap<>();

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public PlayerData getPlayerData(UUID playerId) {
            return players.get(playerId.toString());
        }

        @Override
        public PlayerData getPlayerData(String playerIdStr) {
            return players.get(playerIdStr);
        }

        @Override
        public void savePlayerData(UUID playerId, PlayerData data) {
            players.put(playerId.toString(), data);
        }

        @Override
        public void savePlayerData(String playerIdStr, PlayerData data) {
            players.put(playerIdStr, data);
        }

        @Override
        public void deletePlayerData(UUID playerId) {
            players.remove(playerId.toString());
        }

        @Override
        public void deletePlayerData(String playerIdStr) {
            players.remove(playerIdStr);
        }

        @Override
        public Set<String> getAllPlayerIds() {
            return new HashSet<>(players.keySet());
        }

        @Override
        public int getTotalPlayers() {
            return players.size();
        }

        @Override
        public Location getSpawnLocation(UUID playerId) {
            return null;
        }

        @Override
        public void setSpawnLocation(UUID playerId, Location location) {
        }

        @Override
        public int getUnlockedChunks(UUID playerId) {
            PlayerData data = getPlayerData(playerId);
            return data != null ? data.getUnlockedChunks() : 0;
        }

        @Override
        public void setUnlockedChunks(UUID playerId, int count) {
            PlayerData data = getPlayerData(playerId);
            if (data == null) {
                data = new PlayerData();
            }
            data.setUnlockedChunks(count);
            savePlayerData(playerId, data);
        }

        @Override
        public void incrementUnlockedChunks(UUID playerId) {
            setUnlockedChunks(playerId, getUnlockedChunks(playerId) + 1);
        }

        @Override
        public void close() {
        }

        @Override
        public void clearCache() {
        }
    }
}
