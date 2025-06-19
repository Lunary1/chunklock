package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages glass borders around unlocked chunks.
 * Places glass blocks on the edges of locked chunks that are adjacent to unlocked chunks,
 * creating a visual boundary that players can right-click to open unlock GUIs.
 */
public class ChunkBorderManager implements Listener {
    
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final JavaPlugin plugin;
    
    // Store border blocks per player: Player UUID -> Location -> Original BlockData
    private final Map<UUID, Map<Location, BlockData>> playerBorders = new ConcurrentHashMap<>();
    
    // Store which chunk each border block belongs to: Location -> Chunk coordinates
    private final Map<Location, ChunkCoordinate> borderToChunk = new ConcurrentHashMap<>();
    
    // Configuration values (loaded from config.yml)
    private boolean enabled;
    private boolean useFullHeight;
    private int borderHeight;
    private int minYOffset;
    private int maxYOffset;
    private int scanRange;
    private long updateDelay;
    private long updateCooldown;
    private boolean showForBypassPlayers;
    private boolean autoUpdateOnMovement;
    private boolean restoreOriginalBlocks;
    private boolean debugLogging;
    private Material borderMaterial;
    private boolean skipValuableOres;
    private boolean skipFluids;
    private boolean skipImportantBlocks;

    public ChunkBorderManager(ChunkLockManager chunkLockManager, UnlockGui unlockGui) {
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.plugin = ChunklockPlugin.getInstance();
        
        loadConfiguration();
    }
    
    /**
     * Loads configuration from config.yml
     */
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Load glass-borders configuration section
        enabled = config.getBoolean("glass-borders.enabled", true);
        useFullHeight = config.getBoolean("glass-borders.use-full-height", true);
        borderHeight = config.getInt("glass-borders.border-height", 3);
        minYOffset = config.getInt("glass-borders.min-y-offset", -2);
        maxYOffset = config.getInt("glass-borders.max-y-offset", 4);
        scanRange = config.getInt("glass-borders.scan-range", 8);
        updateDelay = config.getLong("glass-borders.update-delay", 20L);
        updateCooldown = config.getLong("glass-borders.update-cooldown", 2000L);
        showForBypassPlayers = config.getBoolean("glass-borders.show-for-bypass-players", false);
        autoUpdateOnMovement = config.getBoolean("glass-borders.auto-update-on-movement", true);
        restoreOriginalBlocks = config.getBoolean("glass-borders.restore-original-blocks", true);
        debugLogging = config.getBoolean("glass-borders.debug-logging", false);
        
        // Parse border material
        String materialName = config.getString("glass-borders.border-material", "LIGHT_GRAY_STAINED_GLASS");
        try {
            borderMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "', using LIGHT_GRAY_STAINED_GLASS");
            borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS;
        }
        
        skipValuableOres = config.getBoolean("glass-borders.skip-valuable-ores", true);
        skipFluids = config.getBoolean("glass-borders.skip-fluids", true);
        skipImportantBlocks = config.getBoolean("glass-borders.skip-important-blocks", true);
        
        if (debugLogging) {
            plugin.getLogger().info("Glass borders " + (enabled ? "enabled" : "disabled") + 
                " - Material: " + borderMaterial + ", Range: " + scanRange + 
                ", Full Height: " + useFullHeight + (useFullHeight ? "" : ", Height: " + borderHeight));
        }
    }
    
    /**
     * Reloads configuration (called during plugin reload)
     */
    public void reloadConfiguration() {
        loadConfiguration();
        
        // If borders were disabled, clean up all existing borders
        if (!enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeBordersForPlayer(player);
            }
        }
    }
    
    /**
     * Returns whether the border system is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Updates borders for a player based on their unlocked chunks
     */
    public void updateBordersForPlayer(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            if (debugLogging && !enabled) {
                plugin.getLogger().info("Border system is disabled, skipping border update for " + (player != null ? player.getName() : "null"));
            }
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        try {
            // Check if player is in bypass mode - no borders for bypass players unless configured
            if (chunkLockManager.isBypassing(player) && !showForBypassPlayers) {
                if (debugLogging) {
                    plugin.getLogger().info("Player " + player.getName() + " is in bypass mode, removing borders");
                }
                removeBordersForPlayer(player);
                return;
            }
            
            if (debugLogging) {
                plugin.getLogger().info("Updating borders for player " + player.getName());
            }
            
            // Remove existing borders first
            removeBordersForPlayer(player);
            
            // Find all unlocked chunks for this player in the nearby area
            Set<ChunkCoordinate> unlockedChunks = findUnlockedChunks(player);
            
            if (debugLogging) {
                plugin.getLogger().info("Found " + unlockedChunks.size() + " unlocked chunks for " + player.getName());
                for (ChunkCoordinate coord : unlockedChunks) {
                    plugin.getLogger().info("  Unlocked chunk: " + coord.x + "," + coord.z);
                }
            }
            
            if (!unlockedChunks.isEmpty()) {
                // Place borders around adjacent locked chunks
                placeBordersForUnlockedChunks(player, unlockedChunks);
                
                if (debugLogging) {
                    plugin.getLogger().info("Placed borders for " + unlockedChunks.size() + " unlocked chunks for player " + player.getName());
                }
            } else {
                if (debugLogging) {
                    plugin.getLogger().info("No unlocked chunks found for " + player.getName() + ", no borders to place");
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error updating borders for player " + player.getName(), e);
        }
    }
    
    /**
     * Finds all unlocked chunks for a player in the nearby area
     */
    private Set<ChunkCoordinate> findUnlockedChunks(Player player) {
        Set<ChunkCoordinate> unlockedChunks = new HashSet<>();
        World world = player.getWorld();
        Chunk playerChunk = player.getLocation().getChunk();
        
        // Check chunks in a configurable range around the player
        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dz = -scanRange; dz <= scanRange; dz++) {
                try {
                    Chunk chunk = world.getChunkAt(playerChunk.getX() + dx, playerChunk.getZ() + dz);
                    chunkLockManager.initializeChunk(chunk, player.getUniqueId());
                    
                    if (!chunkLockManager.isLocked(chunk)) {
                        unlockedChunks.add(new ChunkCoordinate(chunk.getX(), chunk.getZ(), world.getName()));
                    }
                } catch (Exception e) {
                    // Skip chunks that can't be loaded
                    continue;
                }
            }
        }
        
        return unlockedChunks;
    }
    
    /**
     * Places borders around all adjacent locked chunks
     */
    private void placeBordersForUnlockedChunks(Player player, Set<ChunkCoordinate> unlockedChunks) {
        UUID playerId = player.getUniqueId();
        Map<Location, BlockData> borderBlocks = new HashMap<>();
        int adjacentChecked = 0;
        int bordersPlaced = 0;
        
        for (ChunkCoordinate unlockedChunk : unlockedChunks) {
            // Check all 8 adjacent chunks (N, S, E, W, NE, NW, SE, SW)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip the center chunk itself
                    
                    adjacentChecked++;
                    
                    ChunkCoordinate adjacentCoord = new ChunkCoordinate(
                        unlockedChunk.x + dx, 
                        unlockedChunk.z + dz, 
                        unlockedChunk.world
                    );
                    
                    // Skip if the adjacent chunk is also unlocked
                    if (unlockedChunks.contains(adjacentCoord)) {
                        continue;
                    }
                    
                    // Check if the adjacent chunk is locked
                    try {
                        Chunk adjacentChunk = player.getWorld().getChunkAt(adjacentCoord.x, adjacentCoord.z);
                        chunkLockManager.initializeChunk(adjacentChunk, playerId);
                        
                        if (chunkLockManager.isLocked(adjacentChunk)) {
                            if (debugLogging) {
                                plugin.getLogger().info("Found locked adjacent chunk at " + adjacentCoord.x + "," + adjacentCoord.z + 
                                    " next to unlocked chunk " + unlockedChunk.x + "," + unlockedChunk.z);
                            }
                            
                            // Place border on the edge of the locked chunk facing the unlocked chunk
                            int bordersBefore = borderBlocks.size();
                            placeBorderBetweenChunks(player, unlockedChunk, adjacentCoord, dx, dz, borderBlocks);
                            int bordersAdded = borderBlocks.size() - bordersBefore;
                            bordersPlaced += bordersAdded;
                            
                            if (debugLogging && bordersAdded > 0) {
                                plugin.getLogger().info("Placed " + bordersAdded + " border blocks between chunks " + 
                                    unlockedChunk.x + "," + unlockedChunk.z + " and " + adjacentCoord.x + "," + adjacentCoord.z);
                            }
                        }
                    } catch (Exception e) {
                        // Skip chunks that can't be accessed
                        if (debugLogging) {
                            plugin.getLogger().warning("Error checking adjacent chunk " + adjacentCoord.x + "," + adjacentCoord.z + ": " + e.getMessage());
                        }
                        continue;
                    }
                }
            }
        }
        
        // Store the border blocks for this player
        if (!borderBlocks.isEmpty()) {
            playerBorders.put(playerId, borderBlocks);
            if (debugLogging) {
                plugin.getLogger().info("Stored " + borderBlocks.size() + " border blocks for player " + player.getName() + 
                    " (checked " + adjacentChecked + " adjacent chunks, placed " + bordersPlaced + " total borders)");
            }
        } else {
            if (debugLogging) {
                plugin.getLogger().info("No border blocks to store for player " + player.getName() + 
                    " (checked " + adjacentChecked + " adjacent chunks)");
            }
        }
    }
    
    /**
     * Places border blocks between an unlocked chunk and a locked chunk
     */
    private void placeBorderBetweenChunks(Player player, ChunkCoordinate unlockedChunk, 
                                        ChunkCoordinate lockedChunk, int dx, int dz, 
                                        Map<Location, BlockData> borderBlocks) {
        World world = player.getWorld();
        
        List<Location> borderLocations = calculateBorderLocations(world, lockedChunk, dx, dz, player);
        
        if (debugLogging) {
            plugin.getLogger().info("Calculated " + borderLocations.size() + " border locations between " + 
                unlockedChunk.x + "," + unlockedChunk.z + " and " + lockedChunk.x + "," + lockedChunk.z + 
                " (direction: " + dx + "," + dz + ")");
        }
        
        int blocksPlaced = 0;
        int blocksSkipped = 0;
        
        for (Location location : borderLocations) {
            try {
                Block block = location.getBlock();
                
                // Don't replace certain important blocks
                if (shouldSkipBlock(block)) {
                    blocksSkipped++;
                    continue;
                }
                
                // Don't place borders if there's already a border block from another border
                if (block.getType() == borderMaterial) {
                    blocksSkipped++;
                    continue;
                }
                
                // Store original block data
                borderBlocks.put(location, block.getBlockData().clone());
                
                // Track which chunk this border belongs to
                borderToChunk.put(location, lockedChunk);
                
                // Place border block
                block.setType(borderMaterial);
                blocksPlaced++;
                
            } catch (Exception e) {
                if (debugLogging) {
                    plugin.getLogger().log(Level.FINE, 
                        "Error placing border block at " + location, e);
                }
            }
        }
        
        if (debugLogging) {
            plugin.getLogger().info("Border placement complete: " + blocksPlaced + " blocks placed, " + 
                blocksSkipped + " blocks skipped for direction " + dx + "," + dz);
        }
    }
    
    /**
     * Calculates border block locations for a locked chunk based on where the unlocked chunk is
     */
    private List<Location> calculateBorderLocations(World world, ChunkCoordinate lockedChunk, 
                                                   int dx, int dz, Player player) {
        List<Location> locations = new ArrayList<>();
        
        // Get appropriate Y level for border placement
        int baseY = getBaseYForBorder(world, lockedChunk, player);
        
        if (debugLogging) {
            plugin.getLogger().info("Calculating border for locked chunk " + lockedChunk.x + "," + lockedChunk.z + 
                " with direction " + dx + "," + dz + " (dx>0=locked is east, dz>0=locked is south)");
        }
        
        // Determine which edge(s) of the locked chunk to place borders on
        // The border should be on the edge of the locked chunk that faces the unlocked chunk
        
        if (dx == 1 && dz == 0) {
            // Locked chunk is to the EAST, place border on WEST edge of locked chunk
            int borderX = lockedChunk.x * 16; // West edge
            int startZ = lockedChunk.z * 16;
            for (int z = startZ; z < startZ + 15; z++) { // exclude south corner
                addBorderColumn(locations, world, borderX, baseY, z);
            }
            if (debugLogging) {
                plugin.getLogger().info("Placing border on WEST edge (x=" + borderX + ") of locked chunk " + lockedChunk.x + "," + lockedChunk.z);
            }
        } else if (dx == -1 && dz == 0) {
            // Locked chunk is to the WEST, place border on EAST edge of locked chunk
            int borderX = lockedChunk.x * 16 + 15; // East edge
            int startZ = lockedChunk.z * 16;
            for (int z = startZ + 1; z < startZ + 16; z++) { // exclude north corner
                addBorderColumn(locations, world, borderX, baseY, z);
            }
            if (debugLogging) {
                plugin.getLogger().info("Placing border on EAST edge (x=" + borderX + ") of locked chunk " + lockedChunk.x + "," + lockedChunk.z);
            }
        } else if (dx == 0 && dz == 1) {
            // Locked chunk is to the SOUTH, place border on NORTH edge of locked chunk
            int borderZ = lockedChunk.z * 16; // North edge
            int startX = lockedChunk.x * 16;
            for (int x = startX + 1; x < startX + 16; x++) { // exclude west corner
                addBorderColumn(locations, world, x, baseY, borderZ);
            }
            if (debugLogging) {
                plugin.getLogger().info("Placing border on NORTH edge (z=" + borderZ + ") of locked chunk " + lockedChunk.x + "," + lockedChunk.z);
            }
        } else if (dx == 0 && dz == -1) {
            // Locked chunk is to the NORTH, place border on SOUTH edge of locked chunk
            int borderZ = lockedChunk.z * 16 + 15; // South edge
            int startX = lockedChunk.x * 16;
            for (int x = startX; x < startX + 15; x++) { // exclude east corner
                addBorderColumn(locations, world, x, baseY, borderZ);
            }
            if (debugLogging) {
                plugin.getLogger().info("Placing border on SOUTH edge (z=" + borderZ + ") of locked chunk " + lockedChunk.x + "," + lockedChunk.z);
            }
        } else if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
            // Diagonal adjacency - place a single column at the corner
            int borderX, borderZ;

            if (dx == 1 && dz == 1) {
                // Locked chunk is SOUTHEAST, border on NORTHWEST corner
                borderX = lockedChunk.x * 16;
                borderZ = lockedChunk.z * 16;
            } else if (dx == 1 && dz == -1) {
                // Locked chunk is NORTHEAST, border on SOUTHWEST corner
                borderX = lockedChunk.x * 16;
                borderZ = lockedChunk.z * 16 + 15;
            } else if (dx == -1 && dz == 1) {
                // Locked chunk is SOUTHWEST, border on NORTHEAST corner
                borderX = lockedChunk.x * 16 + 15;
                borderZ = lockedChunk.z * 16;
            } else { // dx == -1 && dz == -1
                // Locked chunk is NORTHWEST, border on SOUTHEAST corner
                borderX = lockedChunk.x * 16 + 15;
                borderZ = lockedChunk.z * 16 + 15;
            }

            addBorderColumn(locations, world, borderX, baseY, borderZ);

            if (debugLogging) {
                plugin.getLogger().info("Placing diagonal border at corner (" + borderX + "," + borderZ + ") of locked chunk " + lockedChunk.x + "," + lockedChunk.z);
            }
        }
        
        return locations;
    }
    
    /**
     * Adds a vertical column of border blocks at the specified x, z coordinates
     */
    private void addBorderColumn(List<Location> locations, World world, int x, int baseY, int z) {
        if (useFullHeight) {
            // Use full world height from bedrock to max
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight() - 1; // -1 because getMaxHeight is exclusive
            
            if (debugLogging) {
                // Only log first few columns to avoid spam
                if (locations.size() < 32) {
                    plugin.getLogger().info("Adding full height border column at " + x + "," + z + 
                        " from Y " + minY + " to " + maxY + " (height: " + (maxY - minY + 1) + ")");
                }
            }
            
            for (int y = minY; y <= maxY; y++) {
                locations.add(new Location(world, x, y, z));
            }
        } else {
            // Use configured height offsets
            for (int y = baseY + minYOffset; y <= baseY + maxYOffset; y++) {
                // Ensure Y is within world bounds
                if (y >= world.getMinHeight() && y <= world.getMaxHeight()) {
                    locations.add(new Location(world, x, y, z));
                }
            }
        }
    }
    
    /**
     * Determines the base Y level for border placement
     */
    private int getBaseYForBorder(World world, ChunkCoordinate chunkCoord, Player player) {
        try {
            // Try to get a good Y level based on the chunk's terrain
            int centerX = chunkCoord.x * 16 + 8;
            int centerZ = chunkCoord.z * 16 + 8;
            int surfaceY = world.getHighestBlockYAt(centerX, centerZ);
            
            // Use player's level as a reference point
            int playerY = player.getLocation().getBlockY();
            
            // Use a level that's reasonable based on both surface and player position
            int baseY = Math.max(surfaceY, Math.min(playerY, surfaceY + 10));
            
            // Ensure it's within reasonable bounds
            return Math.max(baseY, world.getMinHeight() + 10);
            
        } catch (Exception e) {
            // Fallback to a reasonable default
            return Math.max(64, world.getMinHeight() + 10);
        }
    }
    
    /**
     * Checks if a block should be skipped when placing borders
     */
    private boolean shouldSkipBlock(Block block) {
        Material type = block.getType();
        
        // Always skip these critical blocks
        if (type == Material.BEDROCK || 
            type == Material.SPAWNER ||
            type == Material.END_PORTAL ||
            type == Material.END_PORTAL_FRAME ||
            type == Material.NETHER_PORTAL) {
            return true;
        }
        
        // Skip important blocks if configured
        if (skipImportantBlocks) {
            if (type == Material.BEACON ||
                type == Material.CONDUIT ||
                type == Material.CHEST ||
                type == Material.TRAPPED_CHEST ||
                type == Material.SHULKER_BOX) {
                return true;
            }
        }
        
        // Skip valuable ores if configured
        if (skipValuableOres) {
            if (type == Material.DIAMOND_ORE || 
                type == Material.EMERALD_ORE ||
                type == Material.ANCIENT_DEBRIS ||
                type == Material.DEEPSLATE_DIAMOND_ORE ||
                type == Material.DEEPSLATE_EMERALD_ORE ||
                type == Material.GOLD_ORE ||
                type == Material.DEEPSLATE_GOLD_ORE) {
                return true;
            }
        }
        
        // Skip fluids if configured
        if (skipFluids) {
            if (type == Material.WATER || type == Material.LAVA) {
                return true;
            }
        }
        
        // Don't replace existing border material
        if (type == borderMaterial) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Removes all borders for a player and restores original blocks
     */
    public void removeBordersForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Map<Location, BlockData> borders = playerBorders.remove(playerId);
        
        if (borders == null || borders.isEmpty()) {
            return;
        }
        
        try {
            int restoredCount = 0;
            for (Map.Entry<Location, BlockData> entry : borders.entrySet()) {
                Location location = entry.getKey();
                BlockData originalData = entry.getValue();
                
                // Remove from chunk mapping
                borderToChunk.remove(location);
                
                // Restore original block if configured and it's still our border material
                if (restoreOriginalBlocks) {
                    try {
                        Block block = location.getBlock();
                        if (block.getType() == borderMaterial) {
                            block.setBlockData(originalData);
                            restoredCount++;
                        }
                    } catch (Exception e) {
                        // Skip blocks that can't be restored (chunk might be unloaded)
                        continue;
                    }
                } else {
                    // Just remove the border block without restoring original
                    try {
                        Block block = location.getBlock();
                        if (block.getType() == borderMaterial) {
                            block.setType(Material.AIR);
                            restoredCount++;
                        }
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
            
            plugin.getLogger().fine(
                "Restored " + restoredCount + "/" + borders.size() + " border blocks for player " + player.getName());
                
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error removing borders for player " + player.getName(), e);
        }
    }
    
    /**
     * Updates borders after a chunk is unlocked
     */
    public void onChunkUnlocked(Player player, Chunk unlockedChunk) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        
        plugin.getLogger().fine(
            "Chunk unlocked, updating borders for " + player.getName() + " at " + 
            unlockedChunk.getX() + "," + unlockedChunk.getZ());
        
        // Delay the border update to allow chunk state to propagate
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                updateBordersForPlayer(player);
            }
        }, updateDelay);
    }
    
    // Event Handlers
    
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only proceed if borders are enabled
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != borderMaterial) {
            return;
        }
        
        Player player = event.getPlayer();
        Location location = clickedBlock.getLocation();
        
        // Check if this border block is part of a border
        ChunkCoordinate chunkCoord = borderToChunk.get(location);
        if (chunkCoord == null) {
            return;
        }
        
        // Check if player has borders at this location (only show GUI to the player who owns the border)
        Map<Location, BlockData> playerBorderBlocks = playerBorders.get(player.getUniqueId());
        if (playerBorderBlocks == null || !playerBorderBlocks.containsKey(location)) {
            return;
        }
        
        // Cancel the event to prevent normal block interaction
        event.setCancelled(true);
        
        try {
            // Get the chunk and open unlock GUI
            World world = player.getWorld();
            Chunk chunk = world.getChunkAt(chunkCoord.x, chunkCoord.z);
            
            // Verify the chunk is still locked
            chunkLockManager.initializeChunk(chunk, player.getUniqueId());
            if (chunkLockManager.isLocked(chunk)) {
                unlockGui.open(player, chunk);
                
                // Show chunk info
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
                
                player.sendMessage("§6🔍 Viewing unlock requirements for chunk " + chunkCoord.x + ", " + chunkCoord.z);
                player.sendMessage("§7Biome: " + biomeName + " | Difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score);
            } else {
                // Chunk was unlocked, update borders
                player.sendMessage("§aThis chunk has already been unlocked!");
                updateBordersForPlayer(player);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error handling border click for player " + player.getName(), e);
            player.sendMessage("§cError opening unlock GUI for this chunk.");
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        
        // Update borders after a delay to allow other systems to initialize
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (debugLogging) {
                    plugin.getLogger().info("Running delayed border update for joined player " + player.getName());
                }
                updateBordersForPlayer(player);
            }
        }, 40L); // 2 second delay (increased to ensure chunk loading is complete)
        
        // Also schedule a second update to catch any delayed chunk loading
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (debugLogging) {
                    plugin.getLogger().info("Running secondary border update for joined player " + player.getName());
                }
                updateBordersForPlayer(player);
            }
        }, 100L); // 5 second delay
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up borders when player leaves
        removeBordersForPlayer(player);
    }
    
    /**
     * Cleanup method for plugin disable
     */
    public void cleanup() {
        try {
            plugin.getLogger().info("Cleaning up ChunkBorderManager...");
            
            // Remove all borders for all players
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeBordersForPlayer(player);
            }
            
            playerBorders.clear();
            borderToChunk.clear();
            
            plugin.getLogger().info("ChunkBorderManager cleanup completed");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error during border cleanup", e);
        }
    }
    
    /**
     * Force refresh borders for all online players (for admin commands)
     */
    public void refreshAllBorders() {
        if (!enabled) {
            plugin.getLogger().info("Glass borders are disabled in configuration");
            return;
        }
        
        if (debugLogging) {
            plugin.getLogger().info("Refreshing borders for all " + Bukkit.getOnlinePlayers().size() + " online players");
        }
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateBordersForPlayer(player);
        }
    }
    
    /**
     * Force refresh borders for a specific player (for admin commands)
     */
    public void refreshBordersForPlayer(Player player) {
        if (!enabled) {
            plugin.getLogger().info("Glass borders are disabled in configuration");
            return;
        }
        
        if (debugLogging) {
            plugin.getLogger().info("Force refreshing borders for player " + player.getName());
        }
        
        updateBordersForPlayer(player);
    }
    
    /**
     * Get statistics for debugging
     */
    public Map<String, Object> getBorderStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("borderMaterial", borderMaterial.name());
        stats.put("scanRange", scanRange);
        stats.put("useFullHeight", useFullHeight);
        stats.put("borderHeight", borderHeight);
        stats.put("debugLogging", debugLogging);
        stats.put("playersWithBorders", playerBorders.size());
        
        int totalBorderBlocks = 0;
        for (Map<Location, BlockData> borders : playerBorders.values()) {
            totalBorderBlocks += borders.size();
        }
        stats.put("totalBorderBlocks", totalBorderBlocks);
        stats.put("borderToChunkMappings", borderToChunk.size());
        
        // Configuration summary
        stats.put("config", Map.of(
            "autoUpdateOnMovement", autoUpdateOnMovement,
            "showForBypassPlayers", showForBypassPlayers,
            "restoreOriginalBlocks", restoreOriginalBlocks,
            "updateCooldown", updateCooldown + "ms",
            "skipValuableOres", skipValuableOres,
            "skipFluids", skipFluids,
            "skipImportantBlocks", skipImportantBlocks
        ));
        
        return stats;
    }
    
    /**
     * Gets the configured update cooldown for external use
     */
    public long getUpdateCooldown() {
        return updateCooldown;
    }
    
    /**
     * Gets whether auto-update on movement is enabled
     */
    public boolean isAutoUpdateOnMovementEnabled() {
        return enabled && autoUpdateOnMovement;
    }
    
    /**
     * Simple class to represent chunk coordinates
     */
    private static class ChunkCoordinate {
        final int x, z;
        final String world;
        
        ChunkCoordinate(int x, int z, String world) {
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
}