package me.chunklock.services;

import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import me.chunklock.models.PlayerData;

import java.util.UUID;

public final class MySqlDataMapper {

    private MySqlDataMapper() {
    }

    public static ChunkRow fromChunkData(String worldName, int x, int z, ChunkData data) {
        return new ChunkRow(
                worldName,
                x,
                z,
                data.isLocked(),
                data.getDifficulty() != null ? data.getDifficulty().name() : Difficulty.NORMAL.name(),
                data.getOwnerId() != null ? data.getOwnerId().toString() : null,
                data.getBaseValue(),
                data.getBiome(),
                data.getScore(),
                data.getUnlockedAt()
        );
    }

    public static ChunkData toChunkData(ChunkRow row) {
        return ChunkData.builder()
                .locked(row.locked)
                .difficulty(Difficulty.valueOf(row.difficulty))
                .ownerId(row.ownerUuid != null ? UUID.fromString(row.ownerUuid) : null)
                .baseValue(row.baseValue)
                .biome(row.biome)
                .score(row.score)
                .unlockedAt(row.unlockedAt)
                .build();
    }

    public static PlayerRow fromPlayerData(String playerUuid, PlayerData data) {
        return new PlayerRow(
                playerUuid,
                data.getSpawnWorld(),
                data.getSpawnX(),
                data.getSpawnY(),
                data.getSpawnZ(),
                data.getUnlockedChunks(),
                data.getUpdatedAt()
        );
    }

    public static PlayerData toPlayerData(PlayerRow row) {
        PlayerData data = new PlayerData();
        data.setSpawnWorld(row.spawnWorld);
        data.setSpawnX(row.spawnX);
        data.setSpawnY(row.spawnY);
        data.setSpawnZ(row.spawnZ);
        data.setUnlockedChunks(row.unlockedChunks);
        data.setUpdatedAt(row.updatedAt);
        return data;
    }

    public static class ChunkRow {
        public final String worldName;
        public final int x;
        public final int z;
        public final boolean locked;
        public final String difficulty;
        public final String ownerUuid;
        public final int baseValue;
        public final String biome;
        public final int score;
        public final Long unlockedAt;

        public ChunkRow(String worldName,
                        int x,
                        int z,
                        boolean locked,
                        String difficulty,
                        String ownerUuid,
                        int baseValue,
                        String biome,
                        int score,
                        Long unlockedAt) {
            this.worldName = worldName;
            this.x = x;
            this.z = z;
            this.locked = locked;
            this.difficulty = difficulty;
            this.ownerUuid = ownerUuid;
            this.baseValue = baseValue;
            this.biome = biome;
            this.score = score;
            this.unlockedAt = unlockedAt;
        }
    }

    public static class PlayerRow {
        public final String playerUuid;
        public final String spawnWorld;
        public final int spawnX;
        public final int spawnY;
        public final int spawnZ;
        public final int unlockedChunks;
        public final long updatedAt;

        public PlayerRow(String playerUuid,
                         String spawnWorld,
                         int spawnX,
                         int spawnY,
                         int spawnZ,
                         int unlockedChunks,
                         long updatedAt) {
            this.playerUuid = playerUuid;
            this.spawnWorld = spawnWorld;
            this.spawnX = spawnX;
            this.spawnY = spawnY;
            this.spawnZ = spawnZ;
            this.unlockedChunks = unlockedChunks;
            this.updatedAt = updatedAt;
        }
    }
}
