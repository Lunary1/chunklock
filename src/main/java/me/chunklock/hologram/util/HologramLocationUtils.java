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
     * Wall sides for chunk boundaries.
     */
    public enum WallSide {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        public final int dx;
        public final int dz;

        WallSide(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    /**
     * Calculates the hologram location for a specific wall side of a chunk.
     */
    public static Location calculateWallHologramLocation(Chunk chunk, WallSide side, 
                                                       double wallOffset, double centerOffset,
                                                       double groundClearance, int minHeight) {
        World world = chunk.getWorld();
        int startX = chunk.getX() * 16;
        int startZ = chunk.getZ() * 16;

        double x, z;
        switch (side) {
            case NORTH -> {
                x = startX + centerOffset;
                z = startZ - wallOffset;
            }
            case SOUTH -> {
                x = startX + centerOffset;
                z = startZ + 16 + wallOffset;
            }
            case EAST -> {
                x = startX + 16 + wallOffset;
                z = startZ + centerOffset;
            }
            case WEST -> {
                x = startX - wallOffset;
                z = startZ + centerOffset;
            }
            default -> throw new IllegalArgumentException("Unknown wall side: " + side);
        }

        int y = getHighestSolidY(world, (int) x, (int) z) + (int) groundClearance;
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
            if (type != Material.BARRIER && !type.name().contains("GLASS") && type.isSolid()) {
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
