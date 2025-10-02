package me.chunklock.hologram;

import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.compute.HologramComputeService;
import me.chunklock.hologram.config.HologramConfiguration;
import me.chunklock.hologram.core.*;
import me.chunklock.hologram.core.HologramData;
import me.chunklock.hologram.provider.FancyHologramsProvider;
import me.chunklock.hologram.state.WorldReadyManager;
import me.chunklock.hologram.util.HologramLocationUtils;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Enhanced hologram management service with WorldReady gating, async compute,
 * strict eligibility filtering, and proper side resolution.
 * 
 * Key improvements:
 * - WorldReady gate: No holograms until player is truly location-stable
 * - Async compute: Heavy calculations off main thread
 * - Strict eligibility: Only unlocked + frontier chunks (no diagonals)
 * - Fixed side resolver: Chunk-centric placement (not all WEST)
 */
public final class HologramServiceEnhanced {

    private final HologramConfiguration config;
    private final HologramProvider provider;
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final WorldManager worldManager;
    private final HologramDebouncer debouncer;
    private final HologramComputeService computeService;
    private final WorldReadyManager worldReadyManager;
    private final boolean available;

    // Core state tracking
    private final Map<HologramId, HologramState> hologramStates = new ConcurrentHashMap<>();
    private final Map<HologramId, me.chunklock.hologram.api.Hologram> spawnedHolograms = new ConcurrentHashMap<>();
    
    // Player-specific active sets for distance culling
    private final Map<UUID, Set<HologramId>> activeHologramSets = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastPlayerLocations = new ConcurrentHashMap<>();
    
    // Cached wall locations per chunk to avoid recomputation
    private final Map<String, Map<HologramLocationUtils.WallSide, Location>> cachedWallLocations = new ConcurrentHashMap<>();
    
    // Background tasks
    private BukkitTask distanceCullingTask;
    private BukkitTask cleanupTask;

    private HologramServiceEnhanced(Builder builder) {
        this.config = new HologramConfiguration(ChunklockPlugin.getInstance());
        this.provider = initializeProvider();
        this.chunkLockManager = builder.chunkLockManager;
        this.biomeUnlockRegistry = builder.biomeUnlockRegistry;
        this.worldManager = builder.worldManager;
        this.debouncer = new HologramDebouncer(config.getDebounceDelayTicks());
        this.computeService = new HologramComputeService(chunkLockManager);
        this.worldReadyManager = new WorldReadyManager();
        this.available = provider.isAvailable() && config.isEnabled();
        
        if (available) {
            startBackgroundTasks();
            cleanupOrphanedHolograms();
        }
        
        logInitializationStatus();
    }

    /**
     * Creates a new HologramService instance.
     */
    public static HologramServiceEnhanced create(ChunkLockManager chunkLockManager,
                                       BiomeUnlockRegistry biomeUnlockRegistry,
                                       WorldManager worldManager) {
        return new Builder()
            .chunkLockManager(chunkLockManager)
            .biomeUnlockRegistry(biomeUnlockRegistry)
            .worldManager(worldManager)
            .build();
    }

    /**
     * Main entry point: create or update a hologram with debouncing.
     * This ensures exactly one hologram exists per (player, chunk, side).
     */
    public void createOrUpdateHologram(Player player, Chunk chunk, HologramLocationUtils.WallSide side, 
                                     List<String> lines) {
        if (!isAvailable()) return;
        
        // Gate: Only process if player is world-ready
        if (!worldReadyManager.isPlayerReady(player.getUniqueId())) {
            if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                ChunklockPlugin.getInstance().getLogger().fine("Hologram: Skipping create/update for " + 
                    player.getName() + " - not world ready");
            }
            return;
        }
        
        HologramId hologramId = HologramId.create(player.getUniqueId(), chunk, side);
        Location location = getOrComputeWallLocation(chunk, side);
        
        // Use debouncing to coalesce rapid updates
        debouncer.scheduleUpdate(hologramId, location, lines, () -> {
            performCreateOrUpdate(hologramId, location, lines, player);
        });
    }

    /**
     * Updates just the lines of an existing hologram without changing location.
     */
    public void updateHologramLines(HologramId hologramId, List<String> newLines) {
        if (!isAvailable()) return;
        
        // Gate: Only process if player is world-ready
        UUID playerId = hologramId.getPlayerId();
        if (!worldReadyManager.isPlayerReady(playerId)) {
            return;
        }
        
        HologramState currentState = hologramStates.get(hologramId);
        if (currentState == null) {
            ChunklockPlugin.getInstance().getLogger().warning("Attempted to update lines for non-existent hologram: " + hologramId);
            return;
        }
        
        debouncer.scheduleUpdate(hologramId, currentState.getLocation(), newLines, () -> {
            performUpdateLines(hologramId, newLines);
        });
    }

    /**
     * Registers a player as transitioning to a new world.
     * Called when player starts/teleports to a new per-player world.
     */
    public void registerPlayerWorldTransition(Player player, String targetWorldName) {
        UUID playerId = player.getUniqueId();
        
        // Clear any existing hologram state for this player
        despawnPlayerHolograms(player);
        
        // Register for world ready tracking
        worldReadyManager.registerPlayerWorldTransition(playerId, targetWorldName);
        
        // Set callback to refresh holograms when player becomes ready
        worldReadyManager.setStabilityCallback(playerId, this::performInitialRefreshForPlayer);
        
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Hologram: Registered world transition for " + 
                player.getName() + " to " + targetWorldName);
        }
    }

    /**
     * Forces a player to be considered location stable (e.g., after known world setup completion).
     */
    public void forcePlayerLocationStable(Player player) {
        worldReadyManager.forcePlayerLocationStable(player.getUniqueId());
    }

    /**
     * Called by WorldManager when player world setup is complete.
     * This replaces the old delayed refresh mechanism with proper WorldReady integration.
     */
    public void onPlayerWorldSetupComplete(Player player) {
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Hologram: World setup complete for " + 
                player.getName() + " - forcing location stable");
        }
        
        // Force the player to be considered location stable
        forcePlayerLocationStable(player);
        
        // The initial refresh will be triggered automatically via the stability callback
    }

    /**
     * Updates the active hologram set for a player based on their current position.
     * Called from movement events and periodic sweeps.
     * Now uses async compute → main thread apply pattern.
     */
    public void updateActiveHologramsForPlayer(Player player) {
        if (!isAvailable() || !worldManager.isWorldEnabled(player.getWorld())) {
            despawnPlayerHolograms(player);
            return;
        }
        
        if (chunkLockManager.isBypassing(player)) {
            despawnPlayerHolograms(player);
            return;
        }
        
        // Gate: Only process if player is world-ready
        UUID playerId = player.getUniqueId();
        if (!worldReadyManager.isPlayerReady(playerId)) {
            // Queue a refresh for when player becomes ready
            worldReadyManager.setPendingRefresh(playerId);
            return;
        }
        
        Location currentLocation = player.getLocation();
        
        // Check if player moved significantly since last update
        Location lastLocation = lastPlayerLocations.get(playerId);
        if (lastLocation != null && currentLocation.distanceSquared(lastLocation) < 16.0) { // 4 block threshold
            return; // Skip update if player hasn't moved much
        }
        
        lastPlayerLocations.put(playerId, currentLocation.clone());
        
        // Use async compute service for heavy calculations
        computeService.computeEligibleHologramsAsync(player, config.getMaxViewDistance(), 
            result -> applyEligibilityResult(player, result));
    }

    /**
     * Applies the result of async eligibility computation on the main thread.
     */
    private void applyEligibilityResult(Player player, HologramComputeService.EligibilityResult result) {
        UUID playerId = player.getUniqueId();
        Set<HologramId> newActiveSet = result.getEligibleIds();
        Set<HologramId> currentActiveSet = activeHologramSets.getOrDefault(playerId, new HashSet<>());
        
        // Apply hologram limit per player
        if (newActiveSet.size() > config.getMaxActiveHologramsPerPlayer()) {
            newActiveSet = prioritizeHolograms(player, newActiveSet);
        }
        
        // Determine what needs to be spawned/despawned
        Set<HologramId> toSpawn = new HashSet<>(newActiveSet);
        toSpawn.removeAll(currentActiveSet);
        
        Set<HologramId> toDespawn = new HashSet<>(currentActiveSet);
        toDespawn.removeAll(newActiveSet);
        
        // Execute spawn/despawn operations
        for (HologramId hologramId : toDespawn) {
            hideOrDespawnHologram(hologramId);
        }
        
        for (HologramId hologramId : toSpawn) {
            showOrSpawnHologram(hologramId, player);
        }
        
        activeHologramSets.put(playerId, newActiveSet);
        
        // Diagnostic logging (temporary)
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Hologram eligibility for " + player.getName() + 
                ": world=" + player.getWorld().getName() + 
                ", unlocked=" + result.getUnlockedCount() + 
                ", frontier=" + result.getFrontierCount() + 
                ", eligible=" + result.getEligibleCount() +
                ", spawned=" + toSpawn.size() + 
                ", despawned=" + toDespawn.size());
            
            // Log first 5 eligible IDs and their sides
            Iterator<HologramId> iterator = result.getEligibleIds().iterator();
            int count = 0;
            while (iterator.hasNext() && count < 5) {
                HologramId id = iterator.next();
                ChunklockPlugin.getInstance().getLogger().fine("  Eligible: " + 
                    id.getChunkX() + "," + id.getChunkZ() + " side=" + id.getSide());
                count++;
            }
        }
    }

    /**
     * Performs the initial hologram refresh when a player becomes location stable.
     */
    private void performInitialRefreshForPlayer(Player player) {
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Hologram: Initial refresh triggered for " + 
                player.getName() + " (location stable)");
        }
        
        // Schedule on main thread (this callback is already on main thread from WorldReadyManager)
        Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
            updateActiveHologramsForPlayer(player);
        });
    }

    /**
     * Despawns a single hologram by ID.
     */
    public void despawnHologram(HologramId hologramId) {
        if (!isAvailable()) return;
        
        debouncer.cancelUpdate(hologramId);
        
        me.chunklock.hologram.api.Hologram hologram = spawnedHolograms.remove(hologramId);
        if (hologram != null) {
            provider.removeHologram(hologram);
            ChunklockPlugin.getInstance().getLogger().fine("Despawned hologram: " + hologramId);
        }
        
        HologramState state = hologramStates.get(hologramId);
        if (state != null) {
            hologramStates.put(hologramId, state.withSpawnState(false, false, getCurrentTick()));
        }
        
        // Remove from active sets
        for (Set<HologramId> activeSet : activeHologramSets.values()) {
            activeSet.remove(hologramId);
        }
    }

    /**
     * Despawns all holograms for a specific player.
     */
    public void despawnPlayerHolograms(Player player) {
        if (!isAvailable()) return;
        
        UUID playerId = player.getUniqueId();
        debouncer.cancelPlayerUpdates(playerId);
        computeService.cancelPlayerJobs(playerId);
        
        // Find all holograms for this player
        List<HologramId> playerHolograms = hologramStates.keySet().stream()
            .filter(id -> id.getPlayerId().equals(playerId))
            .collect(Collectors.toList());
        
        for (HologramId hologramId : playerHolograms) {
            despawnHologram(hologramId);
        }
        
        // Clean up player-specific data
        activeHologramSets.remove(playerId);
        lastPlayerLocations.remove(playerId);
        worldReadyManager.removePlayer(playerId);
        
        ChunklockPlugin.getInstance().getLogger().fine("Despawned " + playerHolograms.size() + " holograms for player " + player.getName());
    }

    /**
     * Despawns all holograms for a specific chunk when it's unlocked.
     */
    public void despawnChunkHolograms(Player player, Chunk chunk) {
        if (!isAvailable()) return;
        
        for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.getOrderedSides()) {
            HologramId hologramId = HologramId.create(player.getUniqueId(), chunk, side);
            despawnHologram(hologramId);
        }
        
        // Remove cached wall locations for this chunk
        cachedWallLocations.remove(getChunkKey(chunk));
    }

    /**
     * Global cleanup - removes all holograms and stops background tasks.
     */
    public void cleanup() {
        if (distanceCullingTask != null && !distanceCullingTask.isCancelled()) {
            distanceCullingTask.cancel();
        }
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        
        debouncer.cleanup();
        computeService.cleanup();
        worldReadyManager.cleanup();
        
        // Despawn all holograms
        for (me.chunklock.hologram.api.Hologram hologram : spawnedHolograms.values()) {
            provider.removeHologram(hologram);
        }
        
        spawnedHolograms.clear();
        hologramStates.clear();
        activeHologramSets.clear();
        lastPlayerLocations.clear();
        cachedWallLocations.clear();
        
        if (provider != null) {
            provider.cleanup();
        }
        
        ChunklockPlugin.getInstance().getLogger().info("HologramService cleanup completed");
    }

    // Implementation methods below...
    
    private void performCreateOrUpdate(HologramId hologramId, Location location, List<String> lines, Player player) {
        HologramState currentState = hologramStates.get(hologramId);
        
        // Check if update is actually needed
        if (currentState != null && !currentState.needsUpdate(location, lines)) {
            return; // No change needed
        }
        
        me.chunklock.hologram.api.Hologram existingHologram = spawnedHolograms.get(hologramId);
        
        if (existingHologram != null) {
            // Try to update existing hologram in place
            HologramData updateData = HologramData.builder(hologramId, location)
                .lines(lines)
                .viewDistance(config.getViewDistance())
                .persistent(false)
                .build();
            
            if (provider.updateHologram(existingHologram, updateData)) {
                // Update successful
                hologramStates.put(hologramId, new HologramState(location, lines, true, true, getCurrentTick()));
                ChunklockPlugin.getInstance().getLogger().fine("Updated hologram in place: " + hologramId);
                return;
            } else {
                // Update failed, remove and recreate
                provider.removeHologram(existingHologram);
                spawnedHolograms.remove(hologramId);
            }
        }
        
        // Create new hologram
        createNewHologram(hologramId, location, lines);
    }
    
    private void performUpdateLines(HologramId hologramId, List<String> newLines) {
        HologramState currentState = hologramStates.get(hologramId);
        if (currentState == null) return;
        
        me.chunklock.hologram.api.Hologram hologram = spawnedHolograms.get(hologramId);
        if (hologram != null) {
            HologramData updateData = HologramData.builder(hologramId, currentState.getLocation())
                .lines(newLines)
                .viewDistance(config.getViewDistance())
                .persistent(false)
                .build();
            
            if (provider.updateHologram(hologram, updateData)) {
                hologramStates.put(hologramId, currentState.withContent(currentState.getLocation(), newLines, getCurrentTick()));
                ChunklockPlugin.getInstance().getLogger().fine("Updated hologram lines: " + hologramId);
            }
        }
    }
    
    private void createNewHologram(HologramId hologramId, Location location, List<String> lines) {
        HologramData hologramData = HologramData.builder(hologramId, location)
            .lines(lines)
            .viewDistance(config.getViewDistance())
            .persistent(false)
            .build();
        
        Optional<me.chunklock.hologram.api.Hologram> result = provider.createHologram(hologramData);
        if (result.isPresent()) {
            me.chunklock.hologram.api.Hologram hologram = result.get();
            spawnedHolograms.put(hologramId, hologram);
            hologramStates.put(hologramId, new HologramState(location, lines, true, true, getCurrentTick()));
            ChunklockPlugin.getInstance().getLogger().fine("Created new hologram: " + hologramId);
            
            // Diagnostic: Log wall placement for debugging
            logWallPlacement(hologramId, location);
        } else {
            ChunklockPlugin.getInstance().getLogger().warning("Failed to create hologram: " + hologramId);
        }
    }
    
    /**
     * Diagnostic logging for wall placement (temporary).
     */
    private void logWallPlacement(HologramId hologramId, Location location) {
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Wall placement: chunk(" + 
                hologramId.getChunkX() + "," + hologramId.getChunkZ() + ") " +
                "side=" + hologramId.getSide() + " " +
                "location=(" + String.format("%.2f", location.getX()) + "," + 
                String.format("%.2f", location.getY()) + "," + 
                String.format("%.2f", location.getZ()) + ")");
        }
    }
    
    private Set<HologramId> prioritizeHolograms(Player player, Set<HologramId> candidates) {
        Location playerLoc = player.getLocation();
        Chunk playerChunk = playerLoc.getChunk();
        
        return candidates.stream()
            .sorted((a, b) -> {
                // Priority 1: Current chunk walls first
                boolean aIsCurrentChunk = a.getChunkX() == playerChunk.getX() && a.getChunkZ() == playerChunk.getZ();
                boolean bIsCurrentChunk = b.getChunkX() == playerChunk.getX() && b.getChunkZ() == playerChunk.getZ();
                
                if (aIsCurrentChunk != bIsCurrentChunk) {
                    return aIsCurrentChunk ? -1 : 1;
                }
                
                // Priority 2: Distance to player
                HologramState stateA = hologramStates.get(a);
                HologramState stateB = hologramStates.get(b);
                
                if (stateA != null && stateB != null) {
                    double distA = stateA.getLocation().distanceSquared(playerLoc);
                    double distB = stateB.getLocation().distanceSquared(playerLoc);
                    return Double.compare(distA, distB);
                }
                
                return 0;
            })
            .limit(config.getMaxActiveHologramsPerPlayer())
            .collect(Collectors.toSet());
    }
    
    private void hideOrDespawnHologram(HologramId hologramId) {
        me.chunklock.hologram.api.Hologram hologram = spawnedHolograms.get(hologramId);
        if (hologram != null) {
            // For now, just despawn. Could implement hide/show if FancyHolograms supports it
            provider.removeHologram(hologram);
            spawnedHolograms.remove(hologramId);
            
            HologramState state = hologramStates.get(hologramId);
            if (state != null) {
                hologramStates.put(hologramId, state.withSpawnState(false, false, getCurrentTick()));
            }
        }
    }
    
    private void showOrSpawnHologram(HologramId hologramId, Player player) {
        HologramState state = hologramStates.get(hologramId);
        if (state == null) {
            // Need to create hologram data for this chunk
            tryCreateHologramForChunk(hologramId, player);
        } else if (!state.isSpawned()) {
            // Respawn existing hologram
            createNewHologram(hologramId, state.getLocation(), state.getLines());
        }
    }
    
    private void tryCreateHologramForChunk(HologramId hologramId, Player player) {
        try {
            World world = Bukkit.getWorld(hologramId.getWorldName());
            if (world == null) return;
            
            Chunk chunk = world.getChunkAt(hologramId.getChunkX(), hologramId.getChunkZ());
            chunkLockManager.initializeChunk(chunk, player.getUniqueId());
            
            if (!chunkLockManager.isLocked(chunk)) {
                return; // Skip unlocked chunks
            }
            
            // Generate hologram content based on chunk state
            List<String> lines = generateHologramLines(player, chunk);
            if (lines != null && !lines.isEmpty()) {
                Location location = getOrComputeWallLocation(chunk, hologramId.getSide());
                createNewHologram(hologramId, location, lines);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to create hologram for chunk " + hologramId.getChunkKey(), e);
        }
    }
    
    private List<String> generateHologramLines(Player player, Chunk chunk) {
        // This would be implemented based on your existing hologram content generation logic
        // For now, return a simple placeholder
        return Arrays.asList(
            "§6Locked Chunk",
            "§7" + chunk.getX() + ", " + chunk.getZ()
        );
    }
    
    private Location getOrComputeWallLocation(Chunk chunk, HologramLocationUtils.WallSide side) {
        String chunkKey = getChunkKey(chunk);
        Map<HologramLocationUtils.WallSide, Location> wallMap = cachedWallLocations.get(chunkKey);
        
        if (wallMap == null) {
            wallMap = new EnumMap<>(HologramLocationUtils.WallSide.class);
            cachedWallLocations.put(chunkKey, wallMap);
        }
        
        Location location = wallMap.get(side);
        if (location == null) {
            location = HologramLocationUtils.calculateWallHologramLocation(chunk, side, 
                config.getWallOffset(), config.getCenterOffset(),
                config.getGroundClearance(), config.getMinHeight());
            wallMap.put(side, location);
        }
        
        return location.clone();
    }
    
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    private long getCurrentTick() {
        return Bukkit.getCurrentTick();
    }
    
    private HologramProvider initializeProvider() {
        return new FancyHologramsProvider();
    }
    
    private void startBackgroundTasks() {
        // Distance culling task (reduced frequency since we have WorldReady gating)
        distanceCullingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (worldManager.isWorldEnabled(player.getWorld()) && 
                        !chunkLockManager.isBypassing(player) &&
                        worldReadyManager.isPlayerReady(player.getUniqueId())) {
                        updateActiveHologramsForPlayer(player);
                    }
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 40L, 40L); // Every 2 seconds
        
        // Cleanup task for stale data
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupStaleData();
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 6000L, 6000L); // Every 5 minutes
    }
    
    private void cleanupStaleData() {
        // Remove stale player data for offline players
        Set<UUID> onlinePlayerIds = Bukkit.getOnlinePlayers().stream()
            .map(Player::getUniqueId)
            .collect(Collectors.toSet());
        
        activeHologramSets.keySet().retainAll(onlinePlayerIds);
        lastPlayerLocations.keySet().retainAll(onlinePlayerIds);
        
        // Remove stale hologram states for offline players
        hologramStates.entrySet().removeIf(entry -> 
            !onlinePlayerIds.contains(entry.getKey().getPlayerId()));
        
        spawnedHolograms.entrySet().removeIf(entry -> 
            !onlinePlayerIds.contains(entry.getKey().getPlayerId()));
    }
    
    private void cleanupOrphanedHolograms() {
        // Clean up any orphaned holograms on startup
        try {
            // Remove all existing holograms (provider doesn't have removeAllHolograms method)
            for (me.chunklock.hologram.api.Hologram hologram : spawnedHolograms.values()) {
                provider.removeHologram(hologram);
            }
            spawnedHolograms.clear();
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error during orphaned hologram cleanup", e);
        }
    }
    
    private void logInitializationStatus() {
        if (available) {
            ChunklockPlugin.getInstance().getLogger().info("Enhanced HologramService initialized successfully with WorldReady gating");
        } else {
            ChunklockPlugin.getInstance().getLogger().warning("HologramService not available - check FancyHolograms and configuration");
        }
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public HologramConfiguration getConfig() {
        return config;
    }
    
    public WorldReadyManager getWorldReadyManager() {
        return worldReadyManager;
    }
    
    // Builder pattern for service creation
    public static class Builder {
        ChunkLockManager chunkLockManager;
        BiomeUnlockRegistry biomeUnlockRegistry;
        WorldManager worldManager;
        
        Builder chunkLockManager(ChunkLockManager chunkLockManager) {
            this.chunkLockManager = chunkLockManager;
            return this;
        }
        
        Builder biomeUnlockRegistry(BiomeUnlockRegistry biomeUnlockRegistry) {
            this.biomeUnlockRegistry = biomeUnlockRegistry;
            return this;
        }
        
        Builder worldManager(WorldManager worldManager) {
            this.worldManager = worldManager;
            return this;
        }
        
        HologramServiceEnhanced build() {
            return new HologramServiceEnhanced(this);
        }
    }
}
