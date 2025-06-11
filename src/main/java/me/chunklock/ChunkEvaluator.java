package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.UUID;
import java.util.logging.Level;

public class ChunkEvaluator {

    private final PlayerDataManager playerDataManager;
    private final ChunkValueRegistry chunkValueRegistry;

    public ChunkEvaluator(PlayerDataManager playerDataManager, ChunkValueRegistry chunkValueRegistry) {
        this.playerDataManager = playerDataManager;
        this.chunkValueRegistry = chunkValueRegistry;
    }

    public ChunkValueData evaluateChunk(UUID playerId, Chunk chunk) {
        try {
            if (chunk == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Attempted to evaluate null chunk for player " + playerId);
                return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
            }

            if (chunk.getWorld() == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Chunk has null world for player " + playerId);
                return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
            }

            int score = 0;

            // 1. Distance factor with error handling
            try {
                Chunk originChunk = getOriginChunk(playerId, chunk);
                int dx = originChunk.getX() - chunk.getX();
                int dz = originChunk.getZ() - chunk.getZ();
                int distance = Math.abs(dx) + Math.abs(dz);
                score += distance * 5;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error calculating distance for chunk evaluation", e);
                // Continue with score = 0 for distance
            }

            // 2. Biome factor with error handling
            Biome biome = Biome.PLAINS; // Default fallback
            try {
                biome = getBiomeSafely(chunk);
                score += chunkValueRegistry.getBiomeWeight(biome);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error getting biome for chunk evaluation", e);
                score += chunkValueRegistry.getBiomeWeight(Biome.PLAINS);
            }

            // 3. Block scan factor with error handling
            try {
                score += scanSurfaceBlocks(chunk);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error scanning surface blocks for chunk evaluation", e);
                // Continue without surface block score
            }

            // 4. Determine difficulty with bounds checking
            Difficulty difficulty = calculateDifficulty(score);

            return new ChunkValueData(score, difficulty, biome);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in chunk evaluation for player " + playerId, e);
            return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
        }
    }

    private Chunk getOriginChunk(UUID playerId, Chunk fallbackChunk) {
        try {
            if (playerDataManager != null && playerDataManager.getChunkSpawn(playerId) != null) {
                return playerDataManager.getChunkSpawn(playerId).getChunk();
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error getting origin chunk for player " + playerId, e);
        }
        return fallbackChunk;
    }

    private Biome getBiomeSafely(Chunk chunk) {
        try {
            // Try to get biome from center of chunk at surface level
            int surfaceY = chunk.getWorld().getHighestBlockYAt(chunk.getBlock(8, 0, 8).getLocation());
            surfaceY = Math.max(chunk.getWorld().getMinHeight(), Math.min(surfaceY, chunk.getWorld().getMaxHeight() - 1));
            return chunk.getBlock(8, surfaceY, 8).getBiome();
        } catch (Exception e) {
            // Fallback: try getting biome from a simpler location
            try {
                return chunk.getBlock(8, 64, 8).getBiome();
            } catch (Exception e2) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to get biome from chunk, using PLAINS as fallback", e2);
                return Biome.PLAINS;
            }
        }
    }

    private int scanSurfaceBlocks(Chunk chunk) {
        int score = 0;
        int successfulScans = 0;
        
        for (int x = 0; x < 16; x += 4) {
            for (int z = 0; z < 16; z += 4) {
                try {
                    int y = chunk.getWorld().getHighestBlockYAt(chunk.getBlock(x, 0, z).getLocation());
                    y = Math.max(chunk.getWorld().getMinHeight(), Math.min(y - 1, chunk.getWorld().getMaxHeight() - 1));
                    
                    Block block = chunk.getBlock(x, y, z);
                    if (block != null && block.getType() != null) {
                        Material mat = block.getType();
                        score += chunkValueRegistry.getBlockWeight(mat);
                        successfulScans++;
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error scanning block at " + x + "," + z + " in chunk", e);
                    // Continue with other blocks
                }
            }
        }
        
        if (successfulScans == 0) {
            ChunklockPlugin.getInstance().getLogger().warning("No blocks could be scanned in chunk surface scan");
        }
        
        return score;
    }

    private Difficulty calculateDifficulty(int score) {
        try {
            int easyMax = chunkValueRegistry.getThreshold("easy");
            int normalMax = chunkValueRegistry.getThreshold("normal");
            int hardMax = chunkValueRegistry.getThreshold("hard");

            if (score < easyMax) {
                return Difficulty.EASY;
            } else if (score < normalMax) {
                return Difficulty.NORMAL;
            } else if (score < hardMax) {
                return Difficulty.HARD;
            } else {
                return Difficulty.IMPOSSIBLE;
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error calculating difficulty, using NORMAL as fallback", e);
            return Difficulty.NORMAL;
        }
    }

    public static class ChunkValueData {
        public final int score;
        public final Difficulty difficulty;
        public final Biome biome;

        public ChunkValueData(int score, Difficulty difficulty, Biome biome) {
            this.score = Math.max(0, score); // Ensure non-negative score
            this.difficulty = difficulty != null ? difficulty : Difficulty.EASY;
            this.biome = biome != null ? biome : Biome.PLAINS;
        }
    }
}