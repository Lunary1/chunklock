package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages glass borders between unlocked and locked chunks
 */
public class ChunkBorderManager {
    
    private final JavaPlugin plugin;
    private final ChunkLockManager chunkLockManager;
    private final PlayerProgressTracker progressTracker;
    
    // Store original block data for each border block
    // Key format: "worldName:x:y:z"
    private final Map<String, BorderBlockData> borderBlocks = new ConcurrentHashMap<>();
    
    // Track which chunks have borders for each player
    // Key: playerId, Value: Set of chunk keys
    private final Map<UUID, Set<String>> playerBorders = new ConcurrentHashMap<>();
    
    // Configuration
    private Material borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS;
    private int borderHeight = 20; // Total height of the border wall in blocks
    private int borderBaseOffset = -5; // Start border 5 blocks below ground level
    private boolean persistBorders = true; // Whether to save border data to disk
    private boolean fullHeightBorders = false; // Whether to make borders go from bedrock to sky
    
    private final File dataFile;
    
    public ChunkBorderManager(JavaPlugin plugin, ChunkLockManager chunkLockManager, 
                            PlayerProgressTracker progressTracker) {
        this.plugin = plugin;
        this.chunkLockManager = chunkLockManager;
        this.progressTracker = progressTracker;
        this.dataFile = new File(plugin.getDataFolder(), "borders.yml");
        
        loadConfiguration();
        if (persistBorders) {
            loadBorderData();
        }
    }
    
    /**
     * Updates all borders for a specific player
     */
    public void updateBordersForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        World world = player.getWorld();
        
        // Remove old borders for this player
        removeAllBordersForPlayer(playerId);
        
        // Find all chunks that need borders
        Set<String> chunksNeedingBorders = new HashSet<>();
        
        // Get all unlocked chunks for this player (we need to check team progress)
        Set<Chunk> unlockedChunks = getUnlockedChunksForPlayer(player);
        
        // For each unlocked chunk, check adjacent chunks
        for (Chunk unlockedChunk : unlockedChunks) {
            checkAdjacentChunksForBorders(unlockedChunk, player, chunksNeedingBorders);
        }
        
        // Create borders for identified chunks
        for (String chunkKey : chunksNeedingBorders) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 3) {
                try {
                    World chunkWorld = Bukkit.getWorld(parts[0]);
                    if (chunkWorld != null) {
                        int x = Integer.parseInt(parts[1]);
                        int z = Integer.parseInt(parts[2]);
                        Chunk chunk = chunkWorld.getChunkAt(x, z);
                        createBordersForChunk(chunk, player);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid chunk key format: " + chunkKey);
                }
            }
        }
        
        if (persistBorders) {
            saveBorderData();
        }
    }
    
    /**
     * Creates glass borders for a locked chunk adjacent to unlocked chunks
     */
    private void createBordersForChunk(Chunk lockedChunk, Player player) {
        World world = lockedChunk.getWorld();
        int chunkX = lockedChunk.getX();
        int chunkZ = lockedChunk.getZ();
        UUID playerId = player.getUniqueId();
        
        // Check each side of the chunk
        createBorderOnSide(lockedChunk, player, Direction.NORTH);
        createBorderOnSide(lockedChunk, player, Direction.SOUTH);
        createBorderOnSide(lockedChunk, player, Direction.EAST);
        createBorderOnSide(lockedChunk, player, Direction.WEST);
        
        // Track this chunk as having borders for this player
        playerBorders.computeIfAbsent(playerId, k -> new HashSet<>())
                    .add(getChunkKey(lockedChunk));
    }
    
    /**
     * Creates a border on one side of a chunk if adjacent chunk is unlocked
     */
    private void createBorderOnSide(Chunk lockedChunk, Player player, Direction direction) {
        World world = lockedChunk.getWorld();
        Chunk adjacentChunk = getAdjacentChunk(lockedChunk, direction);
        
        if (adjacentChunk == null) return;
        
        // Initialize the adjacent chunk
        chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
        
        // Only create border if adjacent chunk is unlocked
        if (chunkLockManager.isLocked(adjacentChunk)) {
            return;
        }
        
        // Calculate border coordinates - initialize with defaults
        int startX = 0, endX = 0, startZ = 0, endZ = 0;
        int baseX = lockedChunk.getX() * 16;
        int baseZ = lockedChunk.getZ() * 16;
        
        switch (direction) {
            case NORTH -> {
                startX = baseX;
                endX = baseX + 15;
                startZ = endZ = baseZ;
            }
            case SOUTH -> {
                startX = baseX;
                endX = baseX + 15;
                startZ = endZ = baseZ + 15;
            }
            case EAST -> {
                startX = endX = baseX + 15;
                startZ = baseZ;
                endZ = baseZ + 15;
            }
            case WEST -> {
                startX = endX = baseX;
                startZ = baseZ;
                endZ = baseZ + 15;
            }
            default -> {
                // This should never happen with an enum, but satisfies the compiler
                plugin.getLogger().warning("Unknown direction in createBorderOnSide: " + direction);
                return;
            }
        }
        
        // Place border blocks
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                placeBorderColumn(world, x, z, player.getUniqueId());
            }
        }
    }
    
    /**
     * Places a vertical column of border blocks at the specified coordinates
     * 
     * Height calculation:
     * - Normal mode: Starts at (ground + borderBaseOffset) and goes up by borderHeight blocks
     *   Example: ground at Y=64, offset=-5, height=20 → borders from Y=59 to Y=79
     * - Full height mode: Goes from world min height to max height (e.g., Y=-64 to Y=319)
     */
    private void placeBorderColumn(World world, int x, int z, UUID playerId) {
        int startY, endY;
        
        if (fullHeightBorders) {
            // Full height mode: from bottom of world to top
            startY = world.getMinHeight();
            endY = world.getMaxHeight() - 1;
        } else {
            // Ground-relative mode: based on highest block
            int groundY = world.getHighestBlockYAt(x, z);
            
            // Start below ground level
            startY = Math.max(world.getMinHeight(), groundY + borderBaseOffset);
            
            // Go up from start position by borderHeight blocks
            endY = Math.min(world.getMaxHeight() - 1, startY + borderHeight);
        }
        
        for (int y = startY; y <= endY; y++) {
            Block block = world.getBlockAt(x, y, z);
            
            // Skip if block is already a border or if it's bedrock
            if (block.getType() == borderMaterial || block.getType() == Material.BEDROCK) {
                continue;
            }
            
            // Skip liquids to avoid weird behavior
            if (block.isLiquid()) {
                continue;
            }
            
            // Skip air blocks below ground level to avoid floating glass
            if (y < world.getHighestBlockYAt(x, z) - 1 && block.getType().isAir()) {
                continue;
            }
            
            // Store original block data
            String blockKey = getBlockKey(block);
            BorderBlockData originalData = new BorderBlockData(
                block.getType(),
                block.getBlockData().getAsString(),
                playerId,
                System.currentTimeMillis()
            );
            
            borderBlocks.put(blockKey, originalData);
            
            // Place glass block
            block.setType(borderMaterial);
        }
    }
    
    /**
     * Removes all borders for a specific player
     */
    public void removeAllBordersForPlayer(UUID playerId) {
        Set<String> chunks = playerBorders.remove(playerId);
        if (chunks == null) return;
        
        // Remove all border blocks created by this player
        borderBlocks.entrySet().removeIf(entry -> {
            BorderBlockData data = entry.getValue();
            if (data.creatorId.equals(playerId)) {
                restoreBlock(entry.getKey());
                return true;
            }
            return false;
        });
        
        if (persistBorders) {
            saveBorderData();
        }
    }
    
    /**
     * Removes borders around a specific chunk after it's unlocked
     */
    public void removeBordersAroundChunk(Chunk unlockedChunk, Player player) {
        // Remove borders on all sides of the unlocked chunk
        for (Direction direction : Direction.values()) {
            removeBorderOnSide(unlockedChunk, direction, player.getUniqueId());
        }
        
        // Update borders for adjacent chunks (they might need new borders on other sides)
        for (Direction direction : Direction.values()) {
            Chunk adjacentChunk = getAdjacentChunk(unlockedChunk, direction);
            if (adjacentChunk != null) {
                chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                if (chunkLockManager.isLocked(adjacentChunk)) {
                    // This chunk might need borders on its other sides
                    createBordersForChunk(adjacentChunk, player);
                }
            }
        }
        
        if (persistBorders) {
            saveBorderData();
        }
    }
    
    /**
     * Removes border on one side of a chunk
     */
    private void removeBorderOnSide(Chunk chunk, Direction direction, UUID playerId) {
        // Initialize with defaults to satisfy compiler
        int startX = 0, endX = 0, startZ = 0, endZ = 0;
        int baseX = chunk.getX() * 16;
        int baseZ = chunk.getZ() * 16;
        
        switch (direction) {
            case NORTH -> {
                startX = baseX;
                endX = baseX + 15;
                startZ = endZ = baseZ;
            }
            case SOUTH -> {
                startX = baseX;
                endX = baseX + 15;
                startZ = endZ = baseZ + 15;
            }
            case EAST -> {
                startX = endX = baseX + 15;
                startZ = baseZ;
                endZ = baseZ + 15;
            }
            case WEST -> {
                startX = endX = baseX;
                startZ = baseZ;
                endZ = baseZ + 15;
            }
            default -> {
                // This should never happen with an enum, but satisfies the compiler
                plugin.getLogger().warning("Unknown direction in removeBorderOnSide: " + direction);
                return;
            }
        }
        
        World world = chunk.getWorld();
        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                removeBorderColumn(world, x, z, playerId);
            }
        }
    }
    
    /**
     * Removes a vertical column of border blocks
     */
    private void removeBorderColumn(World world, int x, int z, UUID playerId) {
        int startY, endY;
        
        if (fullHeightBorders) {
            // Full height mode: check entire world height
            startY = world.getMinHeight();
            endY = world.getMaxHeight() - 1;
        } else {
            // Ground-relative mode: check a generous area around ground level
            int groundY = world.getHighestBlockYAt(x, z);
            startY = Math.max(world.getMinHeight(), groundY + borderBaseOffset - 10); // Extra buffer below
            endY = Math.min(world.getMaxHeight() - 1, startY + borderHeight + 20); // Extra buffer above
        }
        
        for (int y = startY; y <= endY; y++) {
            Block block = world.getBlockAt(x, y, z);
            String blockKey = getBlockKey(block);
            
            BorderBlockData data = borderBlocks.get(blockKey);
            if (data != null && data.creatorId.equals(playerId)) {
                restoreBlock(blockKey);
                borderBlocks.remove(blockKey);
            }
        }
    }
    
    /**
     * Checks if a block is a border block
     */
    public boolean isBorderBlock(Block block) {
        if (block.getType() != borderMaterial) {
            return false;
        }
        return borderBlocks.containsKey(getBlockKey(block));
    }
    
    /**
     * Gets the chunk that a border block is protecting (the locked chunk)
     */
    public Chunk getBorderChunk(Block borderBlock, Player player) {
        if (!isBorderBlock(borderBlock)) {
            return null;
        }
        
        // Check all adjacent chunks to find which one is locked
        Chunk blockChunk = borderBlock.getChunk();
        
        for (Direction direction : Direction.values()) {
            Chunk adjacentChunk = getAdjacentChunk(blockChunk, direction);
            if (adjacentChunk != null) {
                chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                if (chunkLockManager.isLocked(adjacentChunk)) {
                    return adjacentChunk;
                }
            }
        }
        
        // If no locked chunk found, return the chunk the block is in
        return blockChunk;
    }
    
    /**
     * Restores a block to its original state
     */
    private void restoreBlock(String blockKey) {
        String[] parts = blockKey.split(":");
        if (parts.length != 4) return;
        
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return;
            
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            
            Block block = world.getBlockAt(x, y, z);
            BorderBlockData data = borderBlocks.get(blockKey);
            
            if (data != null && block.getType() == borderMaterial) {
                block.setType(data.originalMaterial);
                try {
                    block.setBlockData(Bukkit.createBlockData(data.originalBlockData));
                } catch (Exception e) {
                    // Fallback if block data is invalid
                    plugin.getLogger().fine("Could not restore block data for " + blockKey);
                }
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Invalid block key format: " + blockKey);
        }
    }
    
    /**
     * Gets all unlocked chunks for a player (including team chunks)
     */
    private Set<Chunk> getUnlockedChunksForPlayer(Player player) {
        Set<Chunk> unlockedChunks = new HashSet<>();
        World world = player.getWorld();
        
        // This is a simplified approach - in practice, you'd want to track unlocked chunks more efficiently
        // For now, we'll check chunks in a reasonable radius around the player
        int radius = 10; // Check 10 chunks in each direction
        Chunk playerChunk = player.getLocation().getChunk();
        
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Chunk chunk = world.getChunkAt(playerChunk.getX() + dx, playerChunk.getZ() + dz);
                chunkLockManager.initializeChunk(chunk, player.getUniqueId());
                if (!chunkLockManager.isLocked(chunk)) {
                    unlockedChunks.add(chunk);
                }
            }
        }
        
        return unlockedChunks;
    }
    
    /**
     * Checks adjacent chunks to see if they need borders
     */
    private void checkAdjacentChunksForBorders(Chunk unlockedChunk, Player player, Set<String> chunksNeedingBorders) {
        for (Direction direction : Direction.values()) {
            Chunk adjacentChunk = getAdjacentChunk(unlockedChunk, direction);
            if (adjacentChunk != null) {
                chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                if (chunkLockManager.isLocked(adjacentChunk)) {
                    chunksNeedingBorders.add(getChunkKey(adjacentChunk));
                }
            }
        }
    }
    
    /**
     * Gets an adjacent chunk in the specified direction
     */
    private Chunk getAdjacentChunk(Chunk chunk, Direction direction) {
        World world = chunk.getWorld();
        int x = chunk.getX();
        int z = chunk.getZ();
        
        switch (direction) {
            case NORTH -> z--;
            case SOUTH -> z++;
            case EAST -> x++;
            case WEST -> x--;
        }
        
        return world.getChunkAt(x, z);
    }
    
    /**
     * Cleanup method called on plugin disable
     */
    public void cleanup() {
        if (persistBorders) {
            saveBorderData();
        }
        
        // Restore all blocks
        for (String blockKey : new HashSet<>(borderBlocks.keySet())) {
            restoreBlock(blockKey);
        }
        
        borderBlocks.clear();
        playerBorders.clear();
    }
    
    // Utility methods
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    private String getBlockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }
    
    // Configuration methods
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        String materialName = config.getString("borders.material", "LIGHT_GRAY_STAINED_GLASS");
        try {
            borderMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material: " + materialName + ", using LIGHT_GRAY_STAINED_GLASS");
            borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS;
        }
        
        borderHeight = config.getInt("borders.height", 20);
        borderBaseOffset = config.getInt("borders.base-offset", -5);
        persistBorders = config.getBoolean("borders.persist", true);
        fullHeightBorders = config.getBoolean("borders.full-height", false);
    }
    
    // Persistence methods
    private void loadBorderData() {
        if (!dataFile.exists()) return;
        
        try {
            FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection bordersSection = data.getConfigurationSection("borders");
            
            if (bordersSection != null) {
                for (String blockKey : bordersSection.getKeys(false)) {
                    ConfigurationSection blockSection = bordersSection.getConfigurationSection(blockKey);
                    if (blockSection == null) continue;
                    
                    Material material = Material.valueOf(blockSection.getString("material", "AIR"));
                    String blockData = blockSection.getString("blockData", "");
                    UUID creatorId = UUID.fromString(blockSection.getString("creator", ""));
                    long timestamp = blockSection.getLong("timestamp", System.currentTimeMillis());
                    
                    borderBlocks.put(blockKey, new BorderBlockData(material, blockData, creatorId, timestamp));
                }
            }
            
            // Load player borders
            ConfigurationSection playersSection = data.getConfigurationSection("players");
            if (playersSection != null) {
                for (String playerIdStr : playersSection.getKeys(false)) {
                    UUID playerId = UUID.fromString(playerIdStr);
                    List<String> chunks = playersSection.getStringList(playerIdStr);
                    playerBorders.put(playerId, new HashSet<>(chunks));
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading border data", e);
        }
    }
    
    private void saveBorderData() {
        try {
            FileConfiguration data = new YamlConfiguration();
            
            // Save border blocks
            ConfigurationSection bordersSection = data.createSection("borders");
            for (Map.Entry<String, BorderBlockData> entry : borderBlocks.entrySet()) {
                ConfigurationSection blockSection = bordersSection.createSection(entry.getKey());
                BorderBlockData blockData = entry.getValue();
                
                blockSection.set("material", blockData.originalMaterial.name());
                blockSection.set("blockData", blockData.originalBlockData);
                blockSection.set("creator", blockData.creatorId.toString());
                blockSection.set("timestamp", blockData.timestamp);
            }
            
            // Save player borders
            ConfigurationSection playersSection = data.createSection("players");
            for (Map.Entry<UUID, Set<String>> entry : playerBorders.entrySet()) {
                playersSection.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
            }
            
            data.save(dataFile);
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Error saving border data", e);
        }
    }
    
    // Inner classes
    private static class BorderBlockData {
        final Material originalMaterial;
        final String originalBlockData;
        final UUID creatorId;
        final long timestamp;
        
        BorderBlockData(Material originalMaterial, String originalBlockData, UUID creatorId, long timestamp) {
            this.originalMaterial = originalMaterial;
            this.originalBlockData = originalBlockData;
            this.creatorId = creatorId;
            this.timestamp = timestamp;
        }
    }
    
    private enum Direction {
        NORTH, SOUTH, EAST, WEST
    }
    
    // Public configuration methods
    public void setBorderMaterial(Material material) {
        this.borderMaterial = material;
    }
    
    public Material getBorderMaterial() {
        return borderMaterial;
    }
    
    public void setBorderHeight(int height) {
        this.borderHeight = height;
    }
    
    public int getBorderHeight() {
        return borderHeight;
    }
}