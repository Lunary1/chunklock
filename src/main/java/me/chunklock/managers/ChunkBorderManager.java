package me.chunklock.managers;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.Action;

import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.ui.UnlockGui;
import me.chunklock.managers.TeamManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.ChunklockPlugin;
import me.chunklock.border.BorderConfig;
import me.chunklock.border.BorderConfigLoader;
import me.chunklock.border.BorderPlacementService;
import me.chunklock.border.BorderUpdateQueue;
import me.chunklock.border.BorderStateManager;
import me.chunklock.util.ChunkCoordinate;

import java.util.*;
import java.util.logging.Level;

/**
 * Manages glass borders around unlocked chunks.
 * Places glass blocks on the edges of locked chunks that are adjacent to unlocked chunks,
 * creating a visual boundary that players can right-click to open unlock GUIs.
 */
public class ChunkBorderManager {
    
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final PlayerProgressTracker progressTracker;
    private final BorderConfigLoader configLoader = new BorderConfigLoader();
    private BorderPlacementService placementService;
    private final BorderStateManager borderState;
    private final BorderUpdateQueue updateQueue;
    
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
    private Material ownBorderMaterial = Material.LIME_STAINED_GLASS;
    private Material enemyBorderMaterial = Material.RED_STAINED_GLASS;
    private boolean skipValuableOres;
    private boolean skipFluids;
    private boolean skipImportantBlocks;
    private int borderUpdateDelayTicks = 2;
    private int maxBorderUpdatesPerTick = 10;

    public ChunkBorderManager(ChunkLockManager chunkLockManager, UnlockGui unlockGui, TeamManager teamManager, PlayerProgressTracker progressTracker) {
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.teamManager = teamManager;
        this.progressTracker = progressTracker;
        this.plugin = ChunklockPlugin.getInstance();

        loadConfiguration();
        this.borderState = new BorderStateManager();
        this.placementService = new BorderPlacementService(chunkLockManager, teamManager, createConfigObject());
        this.updateQueue = new BorderUpdateQueue(plugin, maxBorderUpdatesPerTick, this::updateBordersForPlayer);
    }
    
    /**
     * Loads configuration from config.yml
     */
    private void loadConfiguration() {
        BorderConfig cfg = configLoader.load(plugin);

        enabled = cfg.enabled;
        useFullHeight = cfg.useFullHeight;
        borderHeight = cfg.borderHeight;
        minYOffset = cfg.minYOffset;
        maxYOffset = cfg.maxYOffset;
        scanRange = cfg.scanRange;
        updateDelay = cfg.updateDelay;
        updateCooldown = cfg.updateCooldown;
        showForBypassPlayers = cfg.showForBypassPlayers;
        autoUpdateOnMovement = cfg.autoUpdateOnMovement;
        restoreOriginalBlocks = cfg.restoreOriginalBlocks;
        debugLogging = cfg.debugLogging;
        borderMaterial = cfg.borderMaterial;
        skipValuableOres = cfg.skipValuableOres;
        skipFluids = cfg.skipFluids;
        skipImportantBlocks = cfg.skipImportantBlocks;
        borderUpdateDelayTicks = cfg.borderUpdateDelayTicks;
        maxBorderUpdatesPerTick = cfg.maxBorderUpdatesPerTick;
        
        if (debugLogging) {
            plugin.getLogger().info("Glass borders " + (enabled ? "enabled" : "disabled") +
                " - Material: " + borderMaterial + ", Range: " + scanRange +
                ", Full Height: " + useFullHeight + (useFullHeight ? "" : ", Height: " + borderHeight));
            plugin.getLogger().info("Border queue: delay " + borderUpdateDelayTicks + " ticks, max " + maxBorderUpdatesPerTick + " per tick");
        }
    }

    public void shutdown() {
        if (updateQueue != null) {
            updateQueue.shutdown();
        }
        // Clean up existing borders
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeBordersForPlayer(player);
        }
    }
    
    /**
     * Reloads configuration (called during plugin reload)
     */
    public void reloadConfiguration() {
        loadConfiguration();
        this.placementService = new BorderPlacementService(chunkLockManager, teamManager, createConfigObject());
        
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
                for (ChunkCoordinate coord : unlockedChunks) {
                    Chunk chunk = player.getWorld().getChunkAt(coord.x, coord.z);
                    placementService.createBordersForChunk(player, chunk, borderState);
                }

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
     * Removes all borders for a player and restores original blocks
     */
    public void removeBordersForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        Map<Location, BlockData> borders = borderState.removeAllBordersForPlayer(playerId);
        
        if (borders.isEmpty()) {
            return;
        }
        
        try {
            int restoredCount = 0;
            for (Map.Entry<Location, BlockData> entry : borders.entrySet()) {
                Location location = entry.getKey();
                BlockData originalData = entry.getValue();
                
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
    public void onChunkUnlocked(Player player, Chunk chunk) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        
        plugin.getLogger().fine(
            "Chunk unlocked, updating borders for " + player.getName() + " at " + 
            chunk.getX() + "," + chunk.getZ());
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            placementService.removeSharedBorders(chunk, player, borderState);
            placementService.createBordersForChunk(player, chunk, borderState);

            // Refresh neighboring unlocked chunks
            for (BorderDirection dir : BorderDirection.values()) {
                try {
                    Chunk neighbor = chunk.getWorld().getChunkAt(chunk.getX() + dir.dx, chunk.getZ() + dir.dz);
                    chunkLockManager.initializeChunk(neighbor, player.getUniqueId());
                    if (!chunkLockManager.isLocked(neighbor)) {
                        placementService.removeSharedBorders(neighbor, player, borderState);
                        placementService.createBordersForChunk(player, neighbor, borderState);
                    }
                } catch (Exception ignored) {
                }
            }
        }, updateDelay);
    }
    
    // Event Handlers
    
    @EventHandler(priority = EventPriority.HIGH)
    public void handlePlayerInteract(PlayerInteractEvent event) {
        // Only proceed if borders are enabled
        if (!enabled || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        // Only handle main hand interactions to prevent double firing
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        Material type = clickedBlock.getType();
        if (type != borderMaterial && type != ownBorderMaterial && type != enemyBorderMaterial) {
            return;
        }

        Player player = event.getPlayer();

        // Determine which chunk this border corresponds to
        Chunk chunk = getBorderChunk(clickedBlock, player);
        if (chunk == null) {
            return; // Not a tracked border block
        }

        // Cancel the event to prevent normal block interaction
        event.setCancelled(true);

        try {
            // Ensure chunk data is initialized
            chunkLockManager.initializeChunk(chunk, player.getUniqueId());
            
            // Verify the chunk is still locked
            if (chunkLockManager.isLocked(chunk)) {
                UUID teamId = teamManager.getTeamLeader(player.getUniqueId());
                boolean contested = chunkLockManager.isContestedChunk(chunk, teamId);

                if (contested) {
                    int maxClaims = chunkLockManager.getMaxContestedClaimsPerDay();
                    if (!progressTracker.canClaimContested(teamId, maxClaims)) {
                        player.sendMessage("§cYour team reached the contested claim limit for today.");
                        return;
                    }
                }

                unlockGui.open(player, chunk);

                // Show chunk info
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);

                player.sendMessage("§6🔍 Viewing unlock requirements for chunk " + chunk.getX() + ", " + chunk.getZ());
                player.sendMessage("§7Biome: " + biomeName + " | Difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score);
            } else {
                // Chunk was unlocked, update borders
                player.sendMessage("§aThis chunk has already been unlocked!");
                scheduleBorderUpdate(player);
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, 
                "Error handling border click for player " + player.getName(), e);
            player.sendMessage("§cError opening unlock GUI for this chunk.");
        }
    }
    
    @EventHandler
    public void handlePlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        
        // Update borders after a delay to allow other systems to initialize
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (debugLogging) {
                    plugin.getLogger().info("Running delayed border update for joined player " + player.getName());
                }
                scheduleBorderUpdate(player);
            }
        }, 40L); // 2 second delay (increased to ensure chunk loading is complete)
        
        // Also schedule a second update to catch any delayed chunk loading
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                if (debugLogging) {
                    plugin.getLogger().info("Running secondary border update for joined player " + player.getName());
                }
                scheduleBorderUpdate(player);
            }
        }, 100L); // 5 second delay
    }
    
    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
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
            
            borderState.clearAllBorders();
            
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
            scheduleBorderUpdate(player);
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
        
        scheduleBorderUpdate(player);
    }

    private BorderConfig createConfigObject() {
        BorderConfig cfg = new BorderConfig();
        cfg.enabled = enabled;
        cfg.useFullHeight = useFullHeight;
        cfg.borderHeight = borderHeight;
        cfg.minYOffset = minYOffset;
        cfg.maxYOffset = maxYOffset;
        cfg.scanRange = scanRange;
        cfg.updateDelay = updateDelay;
        cfg.updateCooldown = updateCooldown;
        cfg.showForBypassPlayers = showForBypassPlayers;
        cfg.autoUpdateOnMovement = autoUpdateOnMovement;
        cfg.restoreOriginalBlocks = restoreOriginalBlocks;
        cfg.debugLogging = debugLogging;
        cfg.borderMaterial = borderMaterial;
        cfg.skipValuableOres = skipValuableOres;
        cfg.skipFluids = skipFluids;
        cfg.skipImportantBlocks = skipImportantBlocks;
        cfg.borderUpdateDelayTicks = borderUpdateDelayTicks;
        cfg.maxBorderUpdatesPerTick = maxBorderUpdatesPerTick;
        return cfg;
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
        stats.put("updateQueue", updateQueue.getStats().toString());
        
        // Use BorderStateManager for statistics
        BorderStateManager.BorderStateStats borderStats = borderState.getStats();
        stats.put("playersWithBorders", borderStats.playersWithBorders);
        stats.put("totalBorderBlocks", borderStats.totalBorderBlocks);
        stats.put("borderToChunkMappings", borderStats.borderToChunkMappings);

        // Configuration summary
        stats.put("config", Map.of(
            "autoUpdateOnMovement", autoUpdateOnMovement,
            "showForBypassPlayers", showForBypassPlayers,
            "restoreOriginalBlocks", restoreOriginalBlocks,
            "updateCooldown", updateCooldown + "ms",
            "borderUpdateDelayTicks", borderUpdateDelayTicks,
            "maxBorderUpdatesPerTick", maxBorderUpdatesPerTick,
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

    /** Schedule a border update for a player */
    public void scheduleBorderUpdate(Player player) {
        if (!enabled || player == null) return;
        updateQueue.scheduleBorderUpdate(player);
    }

    /**
     * Gets the chunk associated with a border block for the given player.
     * Returns null if the block is not a tracked border.
     */
    public Chunk getBorderChunk(Block block, Player player) {
        if (block == null || player == null) return null;

        // Verify this block belongs to this player's border set
        Location loc = block.getLocation();
        if (!borderState.isPlayerBorderBlock(player.getUniqueId(), loc)) {
            return null;
        }

        ChunkCoordinate coord = borderState.getChunkForBorder(loc);
        if (coord == null) return null;

        World world = Bukkit.getWorld(coord.world);
        if (world == null) return null;

        try {
            return world.getChunkAt(coord.x, coord.z);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the given block is part of any glass border.
     */
    public boolean isBorderBlock(Block block) {
        if (!enabled || block == null) return false;
        return borderState.isBorderBlock(block, borderMaterial, ownBorderMaterial, enemyBorderMaterial);
    }

    /**
     * Gets the chunk this border block protects, regardless of player.
     */
    public Chunk getBorderChunk(Block block) {
        if (block == null) return null;

        ChunkCoordinate coord = borderState.getChunkForBorder(block.getLocation());
        if (coord == null) return null;

        World world = Bukkit.getWorld(coord.world);
        if (world == null) return null;

        try {
            return world.getChunkAt(coord.x, coord.z);
        } catch (Exception e) {
            return null;
        }
    }

    /** Direction for chunk borders */
    private enum BorderDirection {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);

        final int dx;
        final int dz;

        BorderDirection(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }
}