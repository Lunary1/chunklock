package me.chunklock.border;

import me.chunklock.util.chunk.ChunkCoordinate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all border blocks across all players.
 * Tracks which borders belong to which players and which chunks they protect.
 */
public class BorderStateManager {
    
    // Store border blocks per player: Player UUID -> Location -> Original BlockData
    private final Map<UUID, Map<Location, BlockData>> playerBorders = new ConcurrentHashMap<>();
    
    // Store which chunk each border block belongs to: Location -> Chunk coordinates
    private final Map<Location, ChunkCoordinate> borderToChunk = new ConcurrentHashMap<>();
    
    /**
     * Add a border block for a specific player.
     */
    public void addBorderBlock(UUID playerId, Location location, BlockData originalData, ChunkCoordinate chunk) {
        Map<Location, BlockData> playerMap = playerBorders.computeIfAbsent(playerId, k -> new HashMap<>());
        playerMap.put(location, originalData);
        borderToChunk.put(location, chunk);
    }
    
    /**
     * Get the original block data for a border at a specific location for a player.
     */
    public BlockData getBorderData(UUID playerId, Location location) {
        Map<Location, BlockData> playerMap = playerBorders.get(playerId);
        return playerMap != null ? playerMap.get(location) : null;
    }
    
    /**
     * Get the chunk coordinate that a border block protects.
     */
    public ChunkCoordinate getChunkForBorder(Location location) {
        return borderToChunk.get(location);
    }
    
    /**
     * Get all border locations for a specific player.
     */
    public Map<Location, BlockData> getPlayerBorders(UUID playerId) {
        return playerBorders.get(playerId);
    }
    
    /**
     * Remove a specific border block for a player.
     */
    public BlockData removeBorderBlock(UUID playerId, Location location) {
        Map<Location, BlockData> playerMap = playerBorders.get(playerId);
        if (playerMap != null) {
            BlockData originalData = playerMap.remove(location);
            borderToChunk.remove(location);
            
            // Clean up empty player map
            if (playerMap.isEmpty()) {
                playerBorders.remove(playerId);
            }
            
            return originalData;
        }
        return null;
    }
    
    /**
     * Remove all borders for a specific player and return them.
     */
    public Map<Location, BlockData> removeAllBordersForPlayer(UUID playerId) {
        Map<Location, BlockData> borders = playerBorders.remove(playerId);
        
        if (borders != null) {
            // Remove from chunk mapping
            for (Location location : borders.keySet()) {
                borderToChunk.remove(location);
            }
        }
        
        return borders != null ? borders : new HashMap<>();
    }
    
    /**
     * Check if a player has any borders.
     */
    public boolean hasPlayerBorders(UUID playerId) {
        Map<Location, BlockData> borders = playerBorders.get(playerId);
        return borders != null && !borders.isEmpty();
    }
    
    /**
     * Check if a specific location is a tracked border block for a player.
     */
    public boolean isPlayerBorderBlock(UUID playerId, Location location) {
        Map<Location, BlockData> playerMap = playerBorders.get(playerId);
        return playerMap != null && playerMap.containsKey(location);
    }
    
    /**
     * Check if a location is any border block (regardless of player).
     */
    public boolean isBorderBlock(Location location) {
        return borderToChunk.containsKey(location);
    }
    
    /**
     * Check if a block is a border block based on material and location tracking.
     */
    public boolean isBorderBlock(Block block, Material borderMaterial, Material ownBorderMaterial, Material enemyBorderMaterial) {
        if (block == null) return false;
        
        Material type = block.getType();
        // CHANGED: Only check for the main border material from config
        if (type != borderMaterial) {
            return false;
        }
        
        return borderToChunk.containsKey(block.getLocation());
    }
    
    /**
     * Get the player map for direct access (for cases where we need to modify it directly).
     * Use with caution - prefer the other methods when possible.
     */
    public Map<Location, BlockData> getPlayerBorderMap(UUID playerId) {
        return playerBorders.computeIfAbsent(playerId, k -> new HashMap<>());
    }
    
    /**
     * Clear all borders for all players.
     */
    public void clearAllBorders() {
        playerBorders.clear();
        borderToChunk.clear();
    }
    
    /**
     * Get statistics about the border state.
     */
    public BorderStateStats getStats() {
        int totalBorderBlocks = 0;
        for (Map<Location, BlockData> borders : playerBorders.values()) {
            totalBorderBlocks += borders.size();
        }
        
        return new BorderStateStats(
            playerBorders.size(),
            totalBorderBlocks,
            borderToChunk.size()
        );
    }
    
    /**
     * Statistics about the border state.
     */
    public static class BorderStateStats {
        public final int playersWithBorders;
        public final int totalBorderBlocks;
        public final int borderToChunkMappings;
        
        public BorderStateStats(int playersWithBorders, int totalBorderBlocks, int borderToChunkMappings) {
            this.playersWithBorders = playersWithBorders;
            this.totalBorderBlocks = totalBorderBlocks;
            this.borderToChunkMappings = borderToChunkMappings;
        }
        
        @Override
        public String toString() {
            return "BorderState{players=" + playersWithBorders + 
                   ", blocks=" + totalBorderBlocks + 
                   ", mappings=" + borderToChunkMappings + "}";
        }
    }
}