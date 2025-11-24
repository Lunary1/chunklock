package me.chunklock.hologram.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Material;

/**
 * Utility class for hologram positioning and location calculations.
 */
public final class HologramLocationUtils {

    private HologramLocationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Wall sides for chunk boundaries - deterministic chunk-centric ordering.
     * Each side represents the wall where the hologram should be placed for that chunk edge.
     */
    public enum WallSide {
        /** North wall - where Z is minimal for the chunk (chunkZ * 16) */
        NORTH(0, -1, 0),
        /** East wall - where X is maximal for the chunk (chunkX * 16 + 15) */ 
        EAST(1, 0, 1),
        /** South wall - where Z is maximal for the chunk (chunkZ * 16 + 15) */
        SOUTH(0, 1, 2),
        /** West wall - where X is minimal for the chunk (chunkX * 16) */
        WEST(-1, 0, 3);

        public final int dx;
        public final int dz;
        public final int order; // Deterministic ordering for tie-breaking

        WallSide(int dx, int dz, int order) {
            this.dx = dx;
            this.dz = dz;
            this.order = order;
        }
        
        /**
         * Gets all sides in deterministic order (NORTH, EAST, SOUTH, WEST).
         */
        public static WallSide[] getOrderedSides() {
            return new WallSide[]{NORTH, EAST, SOUTH, WEST};
        }
    }

    /**
     * Calculates the hologram location for a specific wall side of a chunk.
     * Uses chunk-centric side determination - each side is placed at the corresponding chunk boundary.
     */
    public static Location calculateWallHologramLocation(Chunk chunk, WallSide side, 
                                                       double wallOffset, double centerOffset,
                                                       double groundClearance, int minHeight) {
        World world = chunk.getWorld();
        
        // Chunk boundaries - these are the actual chunk coordinate boundaries
        int chunkMinX = chunk.getX() * 16;      // X coordinate of west edge
        int chunkMaxX = chunkMinX + 15;         // X coordinate of east edge  
        int chunkMinZ = chunk.getZ() * 16;      // Z coordinate of north edge
        int chunkMaxZ = chunkMinZ + 15;         // Z coordinate of south edge

        double x, z;
        switch (side) {
            case NORTH -> {
                // North wall: at the north edge of chunk (minimal Z)
                x = chunkMinX + centerOffset;       // Position along chunk width (X axis)
                z = chunkMinZ - wallOffset;         // Just outside north edge (negative Z direction)
            }
            case SOUTH -> {
                // South wall: at the south edge of chunk (maximal Z)  
                x = chunkMinX + centerOffset;       // Position along chunk width (X axis)
                z = chunkMaxZ + 1 + wallOffset;     // Just outside south edge (positive Z direction)
            }
            case EAST -> {
                // East wall: at the east edge of chunk (maximal X)
                x = chunkMaxX + 1 + wallOffset;     // Just outside east edge (positive X direction)
                z = chunkMinZ + centerOffset;       // Position along chunk depth (Z axis)
            }
            case WEST -> {
                // West wall: at the west edge of chunk (minimal X)
                x = chunkMinX - wallOffset;         // Just outside west edge (negative X direction)
                z = chunkMinZ + centerOffset;       // Position along chunk depth (Z axis)
            }
            default -> throw new IllegalArgumentException("Unknown wall side: " + side);
        }

        // Find ground level at the hologram location
        int groundY = getHighestSolidY(world, (int) Math.floor(x), (int) Math.floor(z));
        int y = groundY + (int) Math.ceil(groundClearance);
        
        // Ensure minimum height
        if (y < minHeight) {
            y = minHeight;
        }

        Location location = new Location(world, x, y, z);
        location.setYaw(getWallFacingYaw(side));
        location.setPitch(0.0f);

        return location;
    }

    /**
     * Gets the rotation angle for a hologram to face toward the chunk center.
     */
    public static float getWallFacingYaw(WallSide wallSide) {
        return switch (wallSide) {
            case NORTH -> 180.0f;  // North wall: face south (toward chunk center)
            case SOUTH -> 0.0f;    // South wall: face north (toward chunk center)
            case EAST -> 270.0f;   // East wall: face west (toward chunk center)
            case WEST -> 90.0f;    // West wall: face east (toward chunk center)
        };
    }

    /**
     * Gets the rotation angle in radians.
     */
    public static float getWallFacingYawRadians(WallSide wallSide) {
        return (float) Math.toRadians(getWallFacingYaw(wallSide));
    }

    /**
     * Finds the highest solid block at the given coordinates, ignoring glass or barrier blocks.
     */
    public static int getHighestSolidY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y > world.getMaxHeight()) {
            y = world.getMaxHeight();
        }

        while (y > world.getMinHeight()) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type != Material.BARRIER && !me.chunklock.util.item.MaterialUtil.getMaterialName(type).contains("GLASS") && type.isSolid()) {
                return y;
            }
            y--;
        }
        return world.getMinHeight();
    }

    /**
     * Generates a unique hologram key for a player and chunk.
     */
    public static String generateHologramKey(java.util.UUID playerId, Chunk chunk, String suffix) {
        String base = playerId + "_" + chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
        return suffix != null ? base + "_" + suffix : base;
    }

    /**
     * Generates a chunk key for tracking purposes.
     */
    public static String generateChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
}
