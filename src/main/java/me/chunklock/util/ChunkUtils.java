package me.chunklock.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utility methods related to chunks.
 */
public final class ChunkUtils {

    private ChunkUtils() {
        // Utility class
    }

    /**
     * Calculates the center location of the given chunk.
     *
     * @param chunk the chunk to get the center of
     * @return the center {@link Location} with a 0.5 offset on X and Z
     * @throws IllegalArgumentException if chunk or its world is null
     */
    public static Location getChunkCenter(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            throw new IllegalArgumentException("Invalid chunk provided");
        }

        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;

        int centerY;
        try {
            centerY = world.getHighestBlockAt(centerX, centerZ).getY() + 1;
            centerY = Math.max(world.getMinHeight() + 1,
                    Math.min(centerY, world.getMaxHeight() - 2));
        } catch (Exception e) {
            centerY = world.getSpawnLocation().getBlockY();
        }

        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }
    
    /**
     * Gets the center X coordinate of a chunk (in block coordinates).
     * 
     * @param chunkX the chunk X coordinate
     * @return the center X coordinate in blocks
     */
    public static int getChunkCenterX(int chunkX) {
        return chunkX * 16 + 8;
    }
    
    /**
     * Gets the center Z coordinate of a chunk (in block coordinates).
     * 
     * @param chunkZ the chunk Z coordinate  
     * @return the center Z coordinate in blocks
     */
    public static int getChunkCenterZ(int chunkZ) {
        return chunkZ * 16 + 8;
    }
    
    /**
     * Gets the center X coordinate of a chunk (in block coordinates).
     * 
     * @param chunk the chunk
     * @return the center X coordinate in blocks
     */
    public static int getChunkCenterX(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        return getChunkCenterX(chunk.getX());
    }
    
    /**
     * Gets the center Z coordinate of a chunk (in block coordinates).
     * 
     * @param chunk the chunk
     * @return the center Z coordinate in blocks  
     */
    public static int getChunkCenterZ(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        return getChunkCenterZ(chunk.getZ());
    }
}