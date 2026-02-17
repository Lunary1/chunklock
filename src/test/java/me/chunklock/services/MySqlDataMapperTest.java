package me.chunklock.services;

import me.chunklock.models.ChunkData;
import me.chunklock.models.Difficulty;
import me.chunklock.models.PlayerData;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MySqlDataMapperTest {

    @Test
    void shouldRoundTripChunkData() {
        UUID owner = UUID.randomUUID();
        ChunkData source = ChunkData.builder()
                .locked(false)
                .difficulty(Difficulty.HARD)
                .ownerId(owner)
                .baseValue(42)
                .biome("minecraft:plains")
                .score(99)
                .unlockedAt(123456789L)
                .build();

        MySqlDataMapper.ChunkRow row = MySqlDataMapper.fromChunkData("world", 1, 2, source);
        ChunkData target = MySqlDataMapper.toChunkData(row);

        assertEquals(source.isLocked(), target.isLocked());
        assertEquals(source.getDifficulty(), target.getDifficulty());
        assertEquals(source.getOwnerId(), target.getOwnerId());
        assertEquals(source.getBaseValue(), target.getBaseValue());
        assertEquals(source.getBiome(), target.getBiome());
        assertEquals(source.getScore(), target.getScore());
        assertEquals(source.getUnlockedAt(), target.getUnlockedAt());
    }

    @Test
    void shouldRoundTripPlayerData() {
        PlayerData source = new PlayerData();
        source.setSpawnWorld("world");
        source.setSpawnX(10);
        source.setSpawnY(64);
        source.setSpawnZ(-5);
        source.setUnlockedChunks(7);
        source.setUpdatedAt(9999L);

        MySqlDataMapper.PlayerRow row = MySqlDataMapper.fromPlayerData(UUID.randomUUID().toString(), source);
        PlayerData target = MySqlDataMapper.toPlayerData(row);

        assertEquals(source.getSpawnWorld(), target.getSpawnWorld());
        assertEquals(source.getSpawnX(), target.getSpawnX());
        assertEquals(source.getSpawnY(), target.getSpawnY());
        assertEquals(source.getSpawnZ(), target.getSpawnZ());
        assertEquals(source.getUnlockedChunks(), target.getUnlockedChunks());
        assertEquals(source.getUpdatedAt(), target.getUpdatedAt());
    }

    @Test
    void shouldHandleNullOwnerAndUnlockTimestamp() {
        ChunkData source = ChunkData.builder()
                .locked(true)
                .difficulty(Difficulty.NORMAL)
                .ownerId(null)
                .baseValue(0)
                .biome(null)
                .score(0)
                .unlockedAt(null)
                .build();

        MySqlDataMapper.ChunkRow row = MySqlDataMapper.fromChunkData("world", 0, 0, source);
        ChunkData target = MySqlDataMapper.toChunkData(row);

        assertNull(target.getOwnerId());
        assertNull(target.getUnlockedAt());
    }
}
