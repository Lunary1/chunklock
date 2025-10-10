package me.chunklock.hologram.core;

import org.bukkit.Chunk;
import org.bukkit.World;
import me.chunklock.hologram.util.HologramLocationUtils;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic hologram identifier that ensures the same parameters
 * always generate the same ID for reliable lookup and management.
 */
public final class HologramId {
    
    private final String id;
    private final UUID playerId;
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private final HologramLocationUtils.WallSide side;
    
    private HologramId(UUID playerId, String worldName, int chunkX, int chunkZ, HologramLocationUtils.WallSide side) {
        this.playerId = Objects.requireNonNull(playerId);
        this.worldName = Objects.requireNonNull(worldName);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.side = Objects.requireNonNull(side);
        this.id = String.format("chunklock:%s:%s:%d:%d:%s", 
            playerId, worldName, chunkX, chunkZ, side.name());
    }
    
    public static HologramId create(UUID playerId, Chunk chunk, HologramLocationUtils.WallSide side) {
        return new HologramId(playerId, chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), side);
    }
    
    public static HologramId create(UUID playerId, World world, int chunkX, int chunkZ, HologramLocationUtils.WallSide side) {
        return new HologramId(playerId, world.getName(), chunkX, chunkZ, side);
    }
    
    /**
     * Parse a hologram ID string back into a HologramId object.
     * Format: chunklock:{UUID}:{worldName}:{chunkX}:{chunkZ}:{side}
     * 
     * @param idString the ID string to parse
     * @return the parsed HologramId, or null if invalid format
     */
    public static HologramId parse(String idString) {
        if (idString == null || !idString.startsWith("chunklock:")) {
            return null;
        }
        
        String[] parts = idString.split(":", 6);
        if (parts.length != 6) {
            return null;
        }
        
        try {
            UUID playerId = UUID.fromString(parts[1]);
            String worldName = parts[2];
            int chunkX = Integer.parseInt(parts[3]);
            int chunkZ = Integer.parseInt(parts[4]);
            HologramLocationUtils.WallSide side = HologramLocationUtils.WallSide.valueOf(parts[5]);
            
            return new HologramId(playerId, worldName, chunkX, chunkZ, side);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Extract player UUID from hologram ID string without full parsing.
     * This is a fast method for when only the UUID is needed.
     * 
     * @param idString the ID string
     * @return the player UUID, or null if invalid format
     */
    public static UUID extractPlayerUUID(String idString) {
        if (idString == null || !idString.startsWith("chunklock:")) {
            return null;
        }
        
        int firstColon = idString.indexOf(':', 10); // Start after "chunklock:"
        if (firstColon == -1) {
            return null;
        }
        
        String uuidString = idString.substring(10, firstColon); // Extract UUID part
        try {
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    public String getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public String getWorldName() { return worldName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public HologramLocationUtils.WallSide getSide() { return side; }
    
    public String getChunkKey() {
        return worldName + ":" + chunkX + ":" + chunkZ;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof HologramId other)) return false;
        return Objects.equals(id, other.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return id;
    }
}
