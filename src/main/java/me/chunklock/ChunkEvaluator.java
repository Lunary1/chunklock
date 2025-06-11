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
                ChunklockPlugin.getInstance().getLogger().warning("Attempted to evaluate null chunk" + 
                    (playerId != null ? " for player " + playerId : ""));
                return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
            }

            if (chunk.getWorld() == null) {
                ChunklockPlugin.getInstance().getLogger().warning("Chunk has null world" + 
                    (playerId != null ? " for player " + playerId : ""));
                return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
            }

            int score = 0;

            // 1. Distance factor with improved error handling
            try {
                Chunk originChunk = getOriginChunk(playerId, chunk);
                int dx = originChunk.getX() - chunk.getX();
                int dz = originChunk.getZ() - chunk.getZ();
                int distance = Math.abs(dx) + Math.abs(dz);
                score += distance * 5;
            } catch (IllegalArgumentException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid argument calculating distance for chunk evaluation", e);
                // Continue with score = 0 for distance
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid state calculating distance for chunk evaluation", e);
                // Continue with score = 0 for distance
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Unexpected error calculating distance for chunk evaluation", e);
                // Continue with score = 0 for distance
            }

            // 2. Biome factor with improved error handling
            Biome biome = Biome.PLAINS; // Default fallback
            try {
                biome = getBiomeSafely(chunk);
                score += chunkValueRegistry.getBiomeWeight(biome);
            } catch (IllegalArgumentException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid biome data for chunk evaluation", e);
                score += chunkValueRegistry.getBiomeWeight(Biome.PLAINS);
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid world state getting biome for chunk evaluation", e);
                score += chunkValueRegistry.getBiomeWeight(Biome.PLAINS);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Unexpected error getting biome for chunk evaluation", e);
                score += chunkValueRegistry.getBiomeWeight(Biome.PLAINS);
            }

            // 3. Block scan factor with improved error handling
            try {
                score += scanSurfaceBlocks(chunk);
            } catch (IllegalArgumentException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid arguments scanning surface blocks for chunk evaluation", e);
                // Continue without surface block score
            } catch (IllegalStateException e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid world state scanning surface blocks for chunk evaluation", e);
                // Continue without surface block score
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Unexpected error scanning surface blocks for chunk evaluation", e);
                // Continue without surface block score
            }

            // 4. Determine difficulty with bounds checking
            Difficulty difficulty = calculateDifficulty(score);

            return new ChunkValueData(score, difficulty, biome);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Critical error in chunk evaluation" + 
                (playerId != null ? " for player " + playerId : ""), e);
            return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
        }
    }

    private Chunk getOriginChunk(UUID playerId, Chunk fallbackChunk) throws IllegalArgumentException, IllegalStateException {
        try {
            if (playerId != null && playerDataManager != null && playerDataManager.getChunkSpawn(playerId) != null) {
                var spawnLocation = playerDataManager.getChunkSpawn(playerId);
                if (spawnLocation != null && spawnLocation.getWorld() != null) {
                    return spawnLocation.getChunk();
                }
            }
        } catch (IllegalArgumentException e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid player ID or spawn data for player " + playerId, e);
            throw e;
        } catch (IllegalStateException e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid world state getting origin chunk for player " + playerId, e);
            throw e;
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Unexpected error getting origin chunk for player " + playerId, e);
        }
        
        if (fallbackChunk == null) {
            throw new IllegalArgumentException("Both player origin and fallback chunk are null");
        }
        
        return fallbackChunk;
    }

    private Biome getBiomeSafely(Chunk chunk) throws IllegalArgumentException, IllegalStateException {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        
        if (chunk.getWorld() == null) {
            throw new IllegalStateException("Chunk world is null");
        }

        try {
            // Try to get biome from center of chunk at surface level
            int surfaceY = chunk.getWorld().getHighestBlockYAt(chunk.getBlock(8, 0, 8).getLocation());
            surfaceY = Math.max(chunk.getWorld().getMinHeight(), Math.min(surfaceY, chunk.getWorld().getMaxHeight() - 1));
            
            Block centerBlock = chunk.getBlock(8, surfaceY, 8);
            if (centerBlock != null) {
                return centerBlock.getBiome();
            }
        } catch (IllegalArgumentException e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid block coordinates getting biome", e);
            // Fall through to fallback
        } catch (IllegalStateException e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid world state getting surface biome", e);
            // Fall through to fallback
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error getting surface biome", e);
            // Fall through to fallback
        }

        // Fallback: try getting biome from a simpler location
        try {
            Block fallbackBlock = chunk.getBlock(8, 64, 8);
            if (fallbackBlock != null) {
                return fallbackBlock.getBiome();
            }
        } catch (IllegalArgumentException e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid fallback block coordinates", e);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error getting fallback biome", e);
        }

        // Final fallback
        ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to get biome from chunk, using PLAINS as fallback");
        return Biome.PLAINS;
    }

    private int scanSurfaceBlocks(Chunk chunk) throws IllegalArgumentException, IllegalStateException {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        
        if (chunk.getWorld() == null) {
            throw new IllegalStateException("Chunk world is null");
        }

        int score = 0;
        int successfulScans = 0;
        
        // Sample fewer blocks for better performance (every 4 blocks instead of dense sampling)
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
                } catch (IllegalArgumentException e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid block coordinates at " + x + "," + z + " in chunk", e);
                    // Continue with other blocks
                } catch (IllegalStateException e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Invalid world state scanning block at " + x + "," + z + " in chunk", e);
                    // Continue with other blocks
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
            if (chunkValueRegistry == null) {
                ChunklockPlugin.getInstance().getLogger().warning("ChunkValueRegistry is null, using NORMAL difficulty as fallback");
                return Difficulty.NORMAL;
            }

            int easyMax = chunkValueRegistry.getThreshold("easy");
            int normalMax = chunkValueRegistry.getThreshold("normal");
            int hardMax = chunkValueRegistry.getThreshold("hard");

            // Validate thresholds are in order
            if (easyMax >= normalMax || normalMax >= hardMax) {
                ChunklockPlugin.getInstance().getLogger().warning("Invalid difficulty thresholds, using defaults");
                return score < 30 ? Difficulty.EASY : 
                       score < 60 ? Difficulty.NORMAL : 
                       score < 90 ? Difficulty.HARD : Difficulty.IMPOSSIBLE;
            }

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

    /**
     * Validates that the evaluation parameters are reasonable
     */
    public boolean validateChunkEvaluation(UUID playerId, Chunk chunk) {
        try {
            if (chunk == null) return false;
            if (chunk.getWorld() == null) return false;
            
            // Validate chunk coordinates are reasonable
            int x = chunk.getX();
            int z = chunk.getZ();
            if (Math.abs(x) > 30000 || Math.abs(z) > 30000) {
                ChunklockPlugin.getInstance().getLogger().warning("Chunk coordinates out of reasonable range: " + x + "," + z);
                return false;
            }
            
            return true;
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error validating chunk evaluation parameters", e);
            return false;
        }
    }

    /**
     * Optimized evaluation for caching (without player-specific distance calculation)
     */
    public ChunkValueData evaluateChunkForCache(Chunk chunk) {
        try {
            if (chunk == null || chunk.getWorld() == null) {
                return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
            }

            int score = 0;

            // Skip distance calculation for cached evaluation
            // Only include biome and block factors

            // Biome factor
            Biome biome = Biome.PLAINS;
            try {
                biome = getBiomeSafely(chunk);
                score += chunkValueRegistry.getBiomeWeight(biome);
            } catch (Exception e) {
                score += chunkValueRegistry.getBiomeWeight(Biome.PLAINS);
            }

            // Block scan factor
            try {
                score += scanSurfaceBlocks(chunk);
            } catch (Exception e) {
                // Continue without surface block score
            }

            Difficulty difficulty = calculateDifficulty(score);
            return new ChunkValueData(score, difficulty, biome);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error in cached chunk evaluation", e);
            return new ChunkValueData(0, Difficulty.EASY, Biome.PLAINS);
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

        @Override
        public String toString() {
            return String.format("ChunkValueData{score=%d, difficulty=%s, biome=%s}", score, difficulty, biome);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ChunkValueData that = (ChunkValueData) obj;
            return score == that.score && 
                   difficulty == that.difficulty && 
                   biome == that.biome;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(score, difficulty, biome);
        }
    }
}