package me.chunklock.util;

import java.util.Objects;

/**
 * Represents chunk coordinates in a specific world.
 * Immutable value object for chunk location.
 */
public final class ChunkCoordinate {
    public final int x;
    public final int z; 
    public final String world;
    
    public ChunkCoordinate(int x, int z, String world) {
        this.x = x;
        this.z = z;
        this.world = world;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChunkCoordinate that = (ChunkCoordinate) obj;
        return x == that.x && z == that.z && Objects.equals(world, that.world);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(x, z, world);
    }
    
    @Override
    public String toString() {
        return world + ":" + x + ":" + z;
    }
}