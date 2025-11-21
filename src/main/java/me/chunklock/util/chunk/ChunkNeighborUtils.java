package me.chunklock.util.chunk;

import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for chunk neighbor operations.
 * Handles cardinal direction neighbor lookups with proper edge case handling.
 */
public class ChunkNeighborUtils {

    /**
     * Get the four cardinal neighbors (North, East, South, West) of a chunk.
     * Only returns neighbors that exist (handles world boundaries and unloaded chunks).
     * 
     * @param chunk The chunk to get neighbors for
     * @return List of cardinal neighbor chunks (2-4 chunks depending on location)
     */
    public static List<Chunk> getCardinalNeighbors(Chunk chunk) {
        List<Chunk> neighbors = new ArrayList<>();
        
        if (chunk == null || chunk.getWorld() == null) {
            return neighbors;
        }
        
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // North (negative Z)
        try {
            Chunk north = world.getChunkAt(chunkX, chunkZ - 1);
            if (north != null) {
                neighbors.add(north);
            }
        } catch (Exception e) {
            // Chunk doesn't exist or can't be loaded - skip it
        }
        
        // East (positive X)
        try {
            Chunk east = world.getChunkAt(chunkX + 1, chunkZ);
            if (east != null) {
                neighbors.add(east);
            }
        } catch (Exception e) {
            // Chunk doesn't exist or can't be loaded - skip it
        }
        
        // South (positive Z)
        try {
            Chunk south = world.getChunkAt(chunkX, chunkZ + 1);
            if (south != null) {
                neighbors.add(south);
            }
        } catch (Exception e) {
            // Chunk doesn't exist or can't be loaded - skip it
        }
        
        // West (negative X)
        try {
            Chunk west = world.getChunkAt(chunkX - 1, chunkZ);
            if (west != null) {
                neighbors.add(west);
            }
        } catch (Exception e) {
            // Chunk doesn't exist or can't be loaded - skip it
        }
        
        return neighbors;
    }

    /**
     * Get a specific cardinal neighbor by direction.
     * 
     * @param chunk The chunk to get the neighbor for
     * @param direction The cardinal direction (N, E, S, W)
     * @return The neighbor chunk, or null if it doesn't exist
     */
    public static Chunk getCardinalNeighbor(Chunk chunk, CardinalDirection direction) {
        if (chunk == null || chunk.getWorld() == null || direction == null) {
            return null;
        }
        
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        try {
            switch (direction) {
                case NORTH:
                    return world.getChunkAt(chunkX, chunkZ - 1);
                case EAST:
                    return world.getChunkAt(chunkX + 1, chunkZ);
                case SOUTH:
                    return world.getChunkAt(chunkX, chunkZ + 1);
                case WEST:
                    return world.getChunkAt(chunkX - 1, chunkZ);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Enum for cardinal directions.
     */
    public enum CardinalDirection {
        NORTH,
        EAST,
        SOUTH,
        WEST
    }
}

