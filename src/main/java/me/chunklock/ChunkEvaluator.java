package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.UUID;

/**
 * Calculates a chunk's score and difficulty based on biome, blocks and player distance.
 */
public class ChunkEvaluator {

    private static final int DISTANCE_WEIGHT = 5;
    private static final int SCAN_STEP = 4;

    private final PlayerDataManager playerDataManager;
    private final ChunkValueRegistry chunkValueRegistry;

    public ChunkEvaluator(PlayerDataManager playerDataManager, ChunkValueRegistry chunkValueRegistry) {
        this.playerDataManager = playerDataManager;
        this.chunkValueRegistry = chunkValueRegistry;
    }

    public ChunkValueData evaluateChunk(UUID playerId, Chunk chunk) {
        int score = 0;

        // 1. Distance factor
        Chunk originChunk = null;
        if (playerDataManager.getChunkSpawn(playerId) != null) {
            originChunk = playerDataManager.getChunkSpawn(playerId).getChunk();
        } else {
            // fallback: treat this as spawn chunk
            originChunk = chunk;
        }
        int dx = originChunk.getX() - chunk.getX();
        int dz = originChunk.getZ() - chunk.getZ();
        int distance = Math.abs(dx) + Math.abs(dz);
        score += distance * DISTANCE_WEIGHT;

        // 2. Biome factor
        Biome biome = chunk.getBlock(8, chunk.getWorld().getHighestBlockYAt(chunk.getBlock(8, 0, 8).getLocation()), 8).getBiome();
        score += chunkValueRegistry.getBiomeWeight(biome);

        // 3. Block scan factor
        score += scanSurfaceBlocks(chunk);

        // 4. Determine difficulty using thresholds from config
        int easyMax = chunkValueRegistry.getThreshold("easy");
        int normalMax = chunkValueRegistry.getThreshold("normal");
        int hardMax = chunkValueRegistry.getThreshold("hard");

        Difficulty difficulty;
        if (score < easyMax) {
            difficulty = Difficulty.EASY;
        } else if (score < normalMax) {
            difficulty = Difficulty.NORMAL;
        } else if (score < hardMax) {
            difficulty = Difficulty.HARD;
        } else {
            difficulty = Difficulty.IMPOSSIBLE;
        }

        return new ChunkValueData(score, difficulty, biome);
    }

    private int scanSurfaceBlocks(Chunk chunk) {
        int score = 0;
        for (int x = 0; x < 16; x += SCAN_STEP) {
            for (int z = 0; z < 16; z += SCAN_STEP) {
                int y = chunk.getWorld().getHighestBlockYAt(chunk.getBlock(x, 0, z).getLocation());
                Block block = chunk.getBlock(x, y - 1, z);
                Material mat = block.getType();
                score += chunkValueRegistry.getBlockWeight(mat);
            }
        }
        return score;
    }

    public static class ChunkValueData {
        public final int score;
        public final Difficulty difficulty;
        public final Biome biome;

        public ChunkValueData(int score, Difficulty difficulty, Biome biome) {
            this.score = score;
            this.difficulty = difficulty;
            this.biome = biome;
        }
    }
}
