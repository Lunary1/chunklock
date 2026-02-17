package me.chunklock.hologram;

import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.config.HologramConfiguration;
import me.chunklock.hologram.core.*;
import me.chunklock.hologram.core.HologramData;
import me.chunklock.hologram.provider.FancyHologramsProvider;
import me.chunklock.hologram.provider.CMIHologramsProvider;
import me.chunklock.hologram.util.HologramLocationUtils;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Centralized hologram management service with distance culling, debouncing,
 * and deterministic IDs. Prevents duplication and scales to hundreds of chunks.
 */
public final class HologramService {

    private final HologramConfiguration config;
    private final HologramProvider provider;
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final WorldManager worldManager;
    private final me.chunklock.economy.EconomyManager economyManager;
    private final HologramDebouncer debouncer;
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

    private HologramService(Builder builder) {
        this.config = new HologramConfiguration(ChunklockPlugin.getInstance());
        this.provider = initializeProvider();
        this.chunkLockManager = builder.chunkLockManager;
        this.biomeUnlockRegistry = builder.biomeUnlockRegistry;
        this.worldManager = builder.worldManager;
        this.economyManager = builder.economyManager;
        this.debouncer = new HologramDebouncer(config.getDebounceDelayTicks());
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
    public static HologramService create(ChunkLockManager chunkLockManager,
                                       BiomeUnlockRegistry biomeUnlockRegistry,
                                       WorldManager worldManager) {
        return new Builder()
            .chunkLockManager(chunkLockManager)
            .biomeUnlockRegistry(biomeUnlockRegistry)
            .worldManager(worldManager)
            .economyManager(ChunklockPlugin.getInstance().getEconomyManager())
            .build();
    }

    /**
     * Main entry point: create or update a hologram with debouncing.
     * This ensures exactly one hologram exists per (player, chunk, side).
     */
    public void createOrUpdateHologram(Player player, Chunk chunk, HologramLocationUtils.WallSide side, 
                                     List<String> lines) {
        if (!isAvailable()) return;
        
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
        
        ChunklockPlugin.getInstance().getLogger().fine("Despawned " + playerHolograms.size() + " holograms for player " + player.getName());
    }

    /**
     * Despawns all holograms for a specific chunk when it's unlocked.
     */
    public void despawnChunkHolograms(Player player, Chunk chunk) {
        if (!isAvailable()) return;
        
        for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.values()) {
            HologramId hologramId = HologramId.create(player.getUniqueId(), chunk, side);
            despawnHologram(hologramId);
        }
        
        // Remove cached wall locations for this chunk
        cachedWallLocations.remove(getChunkKey(chunk));
    }

    /**
     * Updates the active hologram set for a player based on their current position.
     * Called from movement events and periodic sweeps.
     */
    public void updateActiveHologramsForPlayer(Player player) {
        updateActiveHologramsForPlayer(player, false);
    }
    
    /**
     * Update active holograms for a player with optional delayed refresh
     */
    public void updateActiveHologramsForPlayer(Player player, boolean delayedRefresh) {
        if (!isAvailable()) {
            despawnPlayerHolograms(player);
            return;
        }
        
        boolean worldEnabled = worldManager.isWorldEnabled(player.getWorld());
        if (!worldEnabled) {
            if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                ChunklockPlugin.getInstance().getLogger().fine(
                    "Despawning holograms for " + player.getName() + " - world '" + 
                    player.getWorld().getName() + "' is not enabled for ChunkLock. " +
                    "Enabled worlds: " + worldManager.getEnabledWorlds());
            }
            despawnPlayerHolograms(player);
            return;
        }

        if (chunkLockManager.isBypassing(player)) {
            despawnPlayerHolograms(player);
            return;
        }

        UUID playerId = player.getUniqueId();
        Location currentLocation = player.getLocation();

        // Check if player moved significantly since last update (skip for delayed refresh)
        if (!delayedRefresh) {
            Location lastLocation = lastPlayerLocations.get(playerId);
            if (lastLocation != null && currentLocation.distanceSquared(lastLocation) < 16.0) { // 4 block threshold
                return; // Skip update if player hasn't moved much
            }
        }

        lastPlayerLocations.put(playerId, currentLocation.clone());

        // Find candidate chunks and their holograms
        Set<HologramId> newActiveSet = findActiveHologramCandidates(player);
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
        
        // Find holograms that need content updates (when inventory changes)
        Set<HologramId> toUpdate = new HashSet<>(newActiveSet);
        toUpdate.retainAll(currentActiveSet); // Only existing holograms

        // Execute spawn/despawn operations
        for (HologramId hologramId : toDespawn) {
            hideOrDespawnHologram(hologramId);
        }

        for (HologramId hologramId : toSpawn) {
            showOrSpawnHologram(hologramId, player);
        }
        
        // Update content of existing holograms to reflect current inventory/material counts
        for (HologramId hologramId : toUpdate) {
            updateExistingHologramContent(hologramId, player);
        }

        activeHologramSets.put(playerId, newActiveSet);

        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Updated active holograms for " + player.getName() + 
                ": +" + toSpawn.size() + " -" + toDespawn.size() + " ~" + toUpdate.size() + 
                " (total: " + newActiveSet.size() + ", eligibility-based: " + !delayedRefresh + ")");
        }        // Schedule delayed refresh for initial world join (ISSUE A FIX)
        if (delayedRefresh) {
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                if (player.isOnline() && worldManager.isWorldEnabled(player.getWorld())) {
                    if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                        ChunklockPlugin.getInstance().getLogger().fine(
                            "Executing delayed hologram refresh for " + player.getName());
                    }
                    updateActiveHologramsForPlayer(player, false);
                }
            }, 20L); // 1 second delay to ensure data is loaded
        }
    }
    
    /**
     * Schedule delayed hologram refresh for world creation/join scenarios
     * This ensures holograms only show after ownership and chunk data are properly loaded
     */
    public void scheduleDelayedRefreshForPlayer(Player player) {
        if (!isAvailable() || !worldManager.isWorldEnabled(player.getWorld())) {
            return;
        }
        
        Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
            if (player.isOnline() && worldManager.isWorldEnabled(player.getWorld())) {
                if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                    ChunklockPlugin.getInstance().getLogger().fine(
                        "Executing delayed hologram refresh after world setup for " + player.getName());
                }
                updateActiveHologramsForPlayer(player, true);
            }
        }, 40L); // 2 second delay to ensure all systems are initialized
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
        
        if (!currentState.needsUpdate(currentState.getLocation(), newLines)) {
            return; // No change needed
        }
        
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
            } else {
                // Update failed, recreate
                provider.removeHologram(hologram);
                spawnedHolograms.remove(hologramId);
                createNewHologram(hologramId, currentState.getLocation(), newLines);
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
            spawnedHolograms.put(hologramId, result.get());
            hologramStates.put(hologramId, new HologramState(location, lines, true, true, getCurrentTick()));
            ChunklockPlugin.getInstance().getLogger().fine("Created new hologram: " + hologramId);
        } else {
            ChunklockPlugin.getInstance().getLogger().warning("Failed to create hologram: " + hologramId);
        }
    }
    
    private Set<HologramId> findActiveHologramCandidates(Player player) {
        Set<HologramId> candidates = new HashSet<>();
        
        // Get frontier chunks and unlocked chunks for adjacency checking
        Set<Chunk> unlockedChunks = findPlayerUnlockedChunks(player);
        Set<Chunk> frontierChunks = findFrontierChunks(player, unlockedChunks);
        
        Location playerLoc = player.getLocation();
        double maxDistance = config.getMaxViewDistance();
        
        // Only create holograms for frontier chunks, and only on sides facing unlocked chunks
        for (Chunk frontierChunk : frontierChunks) {
            for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.values()) {
                // Check if this side faces toward an unlocked chunk
                if (sideFacesUnlockedChunk(frontierChunk, side, unlockedChunks)) {
                    Location wallLocation = getOrComputeWallLocation(frontierChunk, side);
                    
                    double distance = wallLocation.distanceSquared(playerLoc);
                    if (distance <= maxDistance * maxDistance) {
                        HologramId hologramId = HologramId.create(player.getUniqueId(), frontierChunk, side);
                        candidates.add(hologramId);
                    }
                }
            }
        }
        
        return candidates;
    }
    
    /**
     * Find unlocked chunks owned by the player in a reasonable scan range
     */
    private Set<Chunk> findPlayerUnlockedChunks(Player player) {
        Set<Chunk> unlockedChunks = new HashSet<>();
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        Chunk playerChunk = player.getLocation().getChunk();
        
        // Use a reasonable scan range (not full view distance to avoid performance issues)
        int scanRange = Math.min(8, player.getClientViewDistance());
        
        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dz = -scanRange; dz <= scanRange; dz++) {
                try {
                    Chunk chunk = world.getChunkAt(playerChunk.getX() + dx, playerChunk.getZ() + dz);
                    chunkLockManager.initializeChunk(chunk, playerId);
                    
                    boolean isLocked = chunkLockManager.isLocked(chunk);
                    UUID owner = chunkLockManager.getChunkOwner(chunk);
                    
                    // Include ONLY unlocked chunks that belong to this player
                    if (!isLocked) {
                        // Only include chunks actually owned by this player (exclude unowned chunks)
                        if (owner != null && owner.equals(playerId)) {
                            unlockedChunks.add(chunk);
                        }
                    }
                } catch (Exception e) {
                    // Skip problematic chunks
                    continue;
                }
            }
        }
        
        return unlockedChunks;
    }
    
    /**
     * Find frontier chunks - locked chunks that are adjacent to unlocked chunks
     */
    private Set<Chunk> findFrontierChunks(Player player, Set<Chunk> unlockedChunks) {
        Set<Chunk> frontierChunks = new HashSet<>();
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        
        // Check neighbors of each unlocked chunk
        for (Chunk unlockedChunk : unlockedChunks) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip the center chunk
                    
                    try {
                        Chunk neighbor = world.getChunkAt(
                            unlockedChunk.getX() + dx, 
                            unlockedChunk.getZ() + dz
                        );
                        
                        chunkLockManager.initializeChunk(neighbor, playerId);
                        
                        boolean isLocked = chunkLockManager.isLocked(neighbor);
                        UUID owner = chunkLockManager.getChunkOwner(neighbor);
                        
                        // Include locked neighbors that can be unlocked by this player
                        if (isLocked) {
                            // Include chunks owned by this player or unowned chunks
                            if (owner == null || owner.equals(playerId)) {
                                frontierChunks.add(neighbor);
                            }
                        }
                    } catch (Exception e) {
                        // Skip problematic chunks
                        continue;
                    }
                }
            }
        }
        
        return frontierChunks;
    }
    
    /**
     * Check if a wall side of a frontier chunk faces toward an unlocked chunk
     */
    private boolean sideFacesUnlockedChunk(Chunk frontierChunk, HologramLocationUtils.WallSide side, Set<Chunk> unlockedChunks) {
        // Calculate the adjacent chunk position based on the wall side
        int adjX = frontierChunk.getX();
        int adjZ = frontierChunk.getZ();
        
        switch (side) {
            case NORTH: adjZ -= 1; break;  // North = negative Z
            case SOUTH: adjZ += 1; break;  // South = positive Z
            case EAST:  adjX += 1; break;  // East = positive X
            case WEST:  adjX -= 1; break;  // West = negative X
        }
        
        // Check if the adjacent chunk is in the unlocked set
        for (Chunk unlockedChunk : unlockedChunks) {
            if (unlockedChunk.getX() == adjX && unlockedChunk.getZ() == adjZ) {
                return true; // This side faces an unlocked chunk
            }
        }
        
        return false; // This side doesn't face any unlocked chunks
    }
    
    private Set<HologramId> prioritizeHolograms(Player player, Set<HologramId> candidates) {
        Location playerLoc = player.getLocation();
        Chunk playerChunk = playerLoc.getChunk();
        
        List<HologramId> sorted = candidates.stream()
            .sorted((a, b) -> {
                // Get hologram states for distance calculation
                HologramState stateA = hologramStates.get(a);
                HologramState stateB = hologramStates.get(b);
                
                // If either state is missing, use fallback comparison
                if (stateA == null && stateB == null) {
                    return a.getId().compareTo(b.getId()); // Stable fallback
                }
                if (stateA == null) return 1;  // b has priority
                if (stateB == null) return -1; // a has priority
                
                // Priority 1: Distance to player (closest first)
                double distA = stateA.getLocation().distanceSquared(playerLoc);
                double distB = stateB.getLocation().distanceSquared(playerLoc);
                int distanceComparison = Double.compare(distA, distB);
                if (distanceComparison != 0) {
                    return distanceComparison;
                }
                
                // Priority 2: Current chunk walls first (same distance case)
                boolean aIsCurrentChunk = a.getChunkX() == playerChunk.getX() && a.getChunkZ() == playerChunk.getZ();
                boolean bIsCurrentChunk = b.getChunkX() == playerChunk.getX() && b.getChunkZ() == playerChunk.getZ();
                
                if (aIsCurrentChunk != bIsCurrentChunk) {
                    return aIsCurrentChunk ? -1 : 1;
                }
                
                // Priority 3: Side order (NORTH, EAST, SOUTH, WEST)
                int sideComparison = getSideOrder(a.getSide()) - getSideOrder(b.getSide());
                if (sideComparison != 0) {
                    return sideComparison;
                }
                
                // Priority 4: Stable string ID as final fallback
                return a.getId().compareTo(b.getId());
            })
            .collect(Collectors.toList());
        
        // Debug log for testing
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Hologram priority order for " + player.getName() + ": " + 
                sorted.stream().limit(5).map(id -> id.getId()).collect(Collectors.joining(", "))
            );
        }
        
        return sorted.stream()
            .limit(config.getMaxActiveHologramsPerPlayer())
            .collect(Collectors.toSet());
    }
    
    /**
     * Get numeric order for wall sides to ensure consistent sorting.
     */
    private int getSideOrder(HologramLocationUtils.WallSide side) {
        switch (side) {
            case NORTH: return 0;
            case EAST: return 1;
            case SOUTH: return 2;
            case WEST: return 3;
            default: return 4;
        }
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
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
            
            // Calculate cost asynchronously to avoid blocking the main thread
            // Use AsyncCostCalculationService if available, otherwise calculate on async thread
            me.chunklock.services.AsyncCostCalculationService asyncService = 
                ChunklockPlugin.getInstance().getAsyncCostCalculationService();
            
            if (asyncService != null) {
                // Use async service for non-blocking calculation
                asyncService.getCostAsync(player, chunk).thenAccept(paymentRequirement -> {
                    // Schedule hologram update on main thread
                    Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                        if (paymentRequirement != null) {
                            createHologramWithRequirement(hologramId, player, chunk, evaluation, paymentRequirement);
                        } else {
                            // Fallback to default if async calculation fails
                            createHologramWithFallback(hologramId, player, chunk, evaluation);
                        }
                    });
                }).exceptionally(throwable -> {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                        "Async cost calculation failed for hologram " + hologramId, throwable);
                    // Fallback on main thread
                    Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                        createHologramWithFallback(hologramId, player, chunk, evaluation);
                    });
                    return null;
                });
            } else {
                // Fallback: calculate on async thread if service not available
                Bukkit.getScheduler().runTaskAsynchronously(ChunklockPlugin.getInstance(), () -> {
                    try {
                        var paymentRequirement = economyManager.calculateRequirement(player, chunk, evaluation.biome, evaluation);
                        // Update hologram on main thread
                        Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                            createHologramWithRequirement(hologramId, player, chunk, evaluation, paymentRequirement);
                        });
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                            "Async cost calculation failed for hologram " + hologramId, e);
                        Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                            createHologramWithFallback(hologramId, player, chunk, evaluation);
                        });
                    }
                });
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error creating hologram for chunk " + hologramId, e);
        }
    }
    
    /**
     * Create hologram with calculated payment requirement
     */
    private void createHologramWithRequirement(HologramId hologramId, Player player, Chunk chunk,
                                               me.chunklock.managers.ChunkEvaluator.ChunkValueData evaluation,
                                               me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement) {
        try {
            List<String> lines;
            
            // Check if we should use Vault economy or materials
            if (economyManager != null && economyManager.getCurrentType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT 
                && economyManager.isVaultAvailable()) {
                
                // Use money-based hologram with unified cost
                boolean canAfford = economyManager.canAfford(player, paymentRequirement);
                String formattedCost = economyManager.getVaultService().format(paymentRequirement.getVaultCost());
                
                lines = me.chunklock.hologram.util.HologramTextUtils.createChunkHologramLinesForMoney(
                    formattedCost, canAfford);
                
            } else {
                // Use material-based hologram (default) - use unified cost calculation
                // Convert PaymentRequirement to display format
                Material displayMaterial = paymentRequirement.getMaterial();
                int displayAmount = paymentRequirement.getMaterialAmount();
                
                // Check if player has required items using unified calculation
                boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);
                int playerItemCount = countPlayerItems(player, displayMaterial);
                
                lines = me.chunklock.hologram.util.HologramTextUtils.createChunkHologramLines(
                    me.chunklock.hologram.util.HologramTextUtils.formatMaterialName(displayMaterial),
                    hasItems, playerItemCount, displayAmount);
            }
            
            Location location = getOrComputeWallLocation(chunk, hologramId.getSide());
            createNewHologram(hologramId, location, lines);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error creating hologram with requirement for " + hologramId, e);
        }
    }
    
    /**
     * Create hologram with fallback/default cost (used when async calculation fails or times out)
     */
    private void createHologramWithFallback(HologramId hologramId, Player player, Chunk chunk,
                                           me.chunklock.managers.ChunkEvaluator.ChunkValueData evaluation) {
        try {
            // Use a simple fallback - show default material requirement
            Material defaultMaterial = Material.DIRT;
            int defaultAmount = 1;
            
            List<String> lines = me.chunklock.hologram.util.HologramTextUtils.createChunkHologramLines(
                me.chunklock.hologram.util.HologramTextUtils.formatMaterialName(defaultMaterial),
                false, 0, defaultAmount);
            
            Location location = getOrComputeWallLocation(chunk, hologramId.getSide());
            createNewHologram(hologramId, location, lines);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error creating fallback hologram for " + hologramId, e);
        }
    }
    
    /**
     * Update the content of an existing hologram to reflect current material counts
     */
    private void updateExistingHologramContent(HologramId hologramId, Player player) {
        try {
            World world = Bukkit.getWorld(hologramId.getWorldName());
            if (world == null) return;
            
            Chunk chunk = world.getChunkAt(hologramId.getChunkX(), hologramId.getChunkZ());
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
            
            // Calculate cost asynchronously to avoid blocking the main thread
            me.chunklock.services.AsyncCostCalculationService asyncService = 
                ChunklockPlugin.getInstance().getAsyncCostCalculationService();
            
            if (asyncService != null) {
                asyncService.getCostAsync(player, chunk).thenAccept(paymentRequirement -> {
                    Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                        if (paymentRequirement != null) {
                            updateHologramContentWithRequirement(hologramId, player, chunk, evaluation, paymentRequirement);
                        }
                    });
                }).exceptionally(throwable -> {
                    ChunklockPlugin.getInstance().getLogger().fine("Async cost update failed for hologram " + hologramId);
                    return null;
                });
            } else {
                // Fallback: calculate on async thread
                Bukkit.getScheduler().runTaskAsynchronously(ChunklockPlugin.getInstance(), () -> {
                    try {
                        var paymentRequirement = economyManager.calculateRequirement(player, chunk, evaluation.biome, evaluation);
                        Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                            updateHologramContentWithRequirement(hologramId, player, chunk, evaluation, paymentRequirement);
                        });
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().fine("Async cost update failed for hologram " + hologramId);
                    }
                });
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error updating hologram content for " + hologramId, e);
        }
    }
    
    /**
     * Update hologram content with calculated payment requirement
     */
    private void updateHologramContentWithRequirement(HologramId hologramId, Player player, Chunk chunk,
                                                      me.chunklock.managers.ChunkEvaluator.ChunkValueData evaluation,
                                                      me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement) {
        try {
            List<String> newLines;
            
            // Check if we should use Vault economy or materials
            if (economyManager != null && economyManager.getCurrentType() == me.chunklock.economy.EconomyManager.EconomyType.VAULT 
                && economyManager.isVaultAvailable()) {
                
                // Use money-based hologram with unified cost
                boolean canAfford = economyManager.canAfford(player, paymentRequirement);
                String formattedCost = economyManager.getVaultService().format(paymentRequirement.getVaultCost());
                
                newLines = me.chunklock.hologram.util.HologramTextUtils.createChunkHologramLinesForMoney(
                    formattedCost, canAfford);
                
            } else {
                // Use material-based hologram (default) - use unified cost calculation
                Material displayMaterial = paymentRequirement.getMaterial();
                int displayAmount = paymentRequirement.getMaterialAmount();
                boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);
                int playerItemCount = countPlayerItems(player, displayMaterial);
                
                newLines = me.chunklock.hologram.util.HologramTextUtils.createChunkHologramLines(
                    me.chunklock.hologram.util.HologramTextUtils.formatMaterialName(displayMaterial),
                    hasItems, playerItemCount, displayAmount);
            }
            
            // Update the hologram lines
            performUpdateLines(hologramId, newLines);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error updating hologram content for " + hologramId, e);
        }
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
    
    private void startBackgroundTasks() {
        // Distance culling task
        distanceCullingTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (worldManager.isWorldEnabled(player.getWorld())) {
                        updateActiveHologramsForPlayer(player);
                    }
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 20L, config.getCullingSweepPeriod());
        
        // Cleanup task for invalid holograms
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupInvalidHolograms();
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 200L, 1200L); // Every minute
    }
    
    private void cleanupInvalidHolograms() {
        // For now, no cleanup is needed as we don't have a way to validate hologram state
        // This could be extended to check hologram validity with the provider API
    }
    
    private void cleanupOrphanedHolograms() {
        // On startup, clean up any existing "chunklock:" holograms that might be orphaned
        ChunklockPlugin.getInstance().getLogger().info("Cleaning up orphaned holograms...");
        // Implementation would depend on FancyHolograms API to list existing holograms
    }
    
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }
    
    private int countPlayerItems(Player player, org.bukkit.Material material) {
        int count = 0;
        for (org.bukkit.inventory.ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    private long getCurrentTick() {
        return ChunklockPlugin.getInstance().getServer().getCurrentTick();
    }
    
    // Rest of the implementation (provider initialization, builder, etc.) stays similar...
    
    private HologramProvider initializeProvider() {
        if (!config.isEnabled()) {
            ChunklockPlugin.getInstance().getLogger().info(
                "Holograms disabled in configuration - hologram functionality disabled");
            return createNullProvider();
        }

        if (config.isProviderDisabled()) {
            ChunklockPlugin.getInstance().getLogger().info(
                "Hologram provider set to 'none' - hologram functionality disabled");
            return createNullProvider();
        }

        switch (config.getProvider().toLowerCase()) {
            case "fancyholograms":
                return new FancyHologramsProvider();
            case "cmi":
                return new CMIHologramsProvider();
            case "auto":
                // Auto-detect: try CMI first, then FancyHolograms
                // First check if plugin is loaded (lightweight) before attempting full initialization
                if (CMIHologramsProvider.isPluginAvailable()) {
                    CMIHologramsProvider cmiProvider = new CMIHologramsProvider();
                    // Constructor performs full reflection initialization
                    if (cmiProvider.isAvailable()) {
                        ChunklockPlugin.getInstance().getLogger().info("Auto-detected CMI hologram provider");
                        return cmiProvider;
                    }
                }
                if (FancyHologramsProvider.isPluginAvailable()) {
                    FancyHologramsProvider fancyProvider = new FancyHologramsProvider();
                    // Constructor performs full reflection initialization
                    if (fancyProvider.isAvailable()) {
                        ChunklockPlugin.getInstance().getLogger().info("Auto-detected FancyHolograms provider");
                        return fancyProvider;
                    }
                }
                ChunklockPlugin.getInstance().getLogger().warning(
                    "No hologram provider found - disabling holograms");
                return createNullProvider();
            default:
                ChunklockPlugin.getInstance().getLogger().warning(
                    "Unknown hologram provider '" + config.getProvider() + "' - disabling holograms");
                return createNullProvider();
        }
    }
    
    private HologramProvider createNullProvider() {
        return new HologramProvider() {
            @Override public String getProviderName() { return "None"; }
            @Override public boolean isAvailable() { return false; }
            @Override public Optional<me.chunklock.hologram.api.Hologram> createHologram(HologramData data) { return Optional.empty(); }
            @Override public boolean removeHologram(me.chunklock.hologram.api.Hologram hologram) { return false; }
            @Override public boolean updateHologram(me.chunklock.hologram.api.Hologram hologram, HologramData newData) { return false; }
            @Override public void cleanup() {}
            @Override public java.util.Map<String, Object> getStatistics() { return new HashMap<>(); }
        };
    }
    
    private void logInitializationStatus() {
        if (available) {
            ChunklockPlugin.getInstance().getLogger().info(
                "HologramService initialized with provider: " + provider.getProviderName());
            ChunklockPlugin.getInstance().getLogger().info(
                "HologramService enabled worlds: " + worldManager.getEnabledWorlds());
        } else {
            ChunklockPlugin.getInstance().getLogger().info(
                "HologramService disabled - provider unavailable or disabled in config");
        }
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("available", available);
        stats.put("totalHologramStates", hologramStates.size());
        stats.put("spawnedHolograms", spawnedHolograms.size());
        stats.put("activePlayers", activeHologramSets.size());
        stats.put("cachedWallLocations", cachedWallLocations.size());
        
        // Per-player stats
        Map<String, Integer> activeCountsPerPlayer = new HashMap<>();
        for (Map.Entry<UUID, Set<HologramId>> entry : activeHologramSets.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            String name = player != null ? player.getName() : entry.getKey().toString();
            activeCountsPerPlayer.put(name, entry.getValue().size());
        }
        stats.put("activeHologramsPerPlayer", activeCountsPerPlayer);
        stats.put("debouncer", debouncer.getStatistics());
        
        return stats;
    }
    
    static class Builder {
        ChunkLockManager chunkLockManager;
        BiomeUnlockRegistry biomeUnlockRegistry;
        WorldManager worldManager;
        me.chunklock.economy.EconomyManager economyManager;
        
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
        
        Builder economyManager(me.chunklock.economy.EconomyManager economyManager) {
            this.economyManager = economyManager;
            return this;
        }
        
        HologramService build() {
            return new HologramService(this);
        }
    }
}
