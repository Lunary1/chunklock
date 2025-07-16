package me.chunklock.hologram.display;

import me.chunklock.hologram.api.Hologram;
import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.config.HologramConfiguration;
import me.chunklock.hologram.tracking.HologramState;
import me.chunklock.hologram.tracking.HologramTracker;
import me.chunklock.hologram.util.HologramLocationUtils;
import me.chunklock.hologram.util.HologramTextUtils;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service responsible for creating, updating, and managing hologram display logic.
 * Handles the business logic of when and how holograms should be shown.
 */
public final class HologramDisplayService {

    private final HologramProvider provider;
    private final HologramTracker tracker;
    private final HologramConfiguration config;
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final WorldManager worldManager;

    public HologramDisplayService(HologramProvider provider, 
                                HologramTracker tracker,
                                HologramConfiguration config,
                                ChunkLockManager chunkLockManager,
                                BiomeUnlockRegistry biomeUnlockRegistry,
                                WorldManager worldManager) {
        this.provider = provider;
        this.tracker = tracker;
        this.config = config;
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.worldManager = worldManager;
    }

    /**
     * Updates holograms for all locked chunks around a player.
     */
    public void updateHologramsForPlayer(Player player) {
        if (!provider.isAvailable() || !isWorldEnabled(player.getWorld())) {
            removeAllPlayerHolograms(player);
            return;
        }

        if (chunkLockManager.isBypassing(player)) {
            removeAllPlayerHolograms(player);
            return;
        }

        try {
            Location playerLoc = player.getLocation();
            Chunk playerChunk = playerLoc.getChunk();
            Set<String> validChunkKeys = new HashSet<>();

            // Check 3x3 grid around player
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Chunk checkChunk = player.getWorld().getChunkAt(
                        playerChunk.getX() + dx, 
                        playerChunk.getZ() + dz
                    );

                    if (!isWorldEnabled(checkChunk.getWorld())) {
                        continue;
                    }

                    chunkLockManager.initializeChunk(checkChunk, player.getUniqueId());
                    String chunkKey = HologramLocationUtils.generateChunkKey(checkChunk);

                    if (chunkLockManager.isLocked(checkChunk)) {
                        Map<HologramLocationUtils.WallSide, Location> locations = 
                            calculateWallHologramLocations(player, checkChunk);
                        
                        if (!locations.isEmpty()) {
                            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), checkChunk);
                            updateHologramForChunk(player, checkChunk, evaluation, locations);
                            validChunkKeys.add(chunkKey);
                        } else {
                            removeHologramForChunk(player, checkChunk);
                        }
                    } else {
                        removeHologramForChunk(player, checkChunk);
                    }
                }
            }

            // Clean up invalid holograms
            cleanupInvalidHolograms(player, validChunkKeys);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error updating holograms for " + player.getName(), e);
        }
    }

    /**
     * Updates hologram for a specific chunk.
     */
    private void updateHologramForChunk(Player player, Chunk chunk, 
                                      ChunkEvaluator.ChunkValueData evaluation,
                                      Map<HologramLocationUtils.WallSide, Location> locations) {
        try {
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);
            int playerItemCount = countPlayerItems(player, requirement.material());
            String materialName = HologramTextUtils.formatMaterialName(requirement.material());

            List<String> lines = HologramTextUtils.createChunkHologramLines(
                materialName, hasItems, playerItemCount, requirement.amount());
            String hologramText = String.join("\n", lines);

            for (Map.Entry<HologramLocationUtils.WallSide, Location> entry : locations.entrySet()) {
                String hologramKey = HologramLocationUtils.generateHologramKey(
                    player.getUniqueId(), chunk, entry.getKey().name());
                Location hologramLocation = entry.getValue();

                // Check view distance
                double distance = player.getLocation().distance(hologramLocation);
                if (distance > config.getViewDistance()) {
                    removeHologramByKey(hologramKey);
                    continue;
                }

                // Check if update is needed
                HologramState currentState = tracker.getState(hologramKey);
                if (currentState != null && !currentState.needsUpdate(
                    hologramText, hologramLocation, materialName, 
                    playerItemCount, requirement.amount(), hasItems)) {
                    continue; // No update needed
                }

                // Remove existing and create new (with delay for smooth transition)
                if (tracker.isTracked(hologramKey)) {
                    removeHologramByKey(hologramKey);
                    // Small delay to prevent overlap
                    CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .execute(() -> createNewHologram(hologramKey, hologramLocation, lines, 
                                                       entry.getKey(), hologramText, materialName, 
                                                       playerItemCount, requirement.amount(), hasItems));
                } else {
                    createNewHologram(hologramKey, hologramLocation, lines, entry.getKey(), 
                                    hologramText, materialName, playerItemCount, 
                                    requirement.amount(), hasItems);
                }
            }

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error updating hologram for chunk " + chunk.getX() + "," + chunk.getZ(), e);
        }
    }

    /**
     * Creates a new hologram with the specified parameters.
     */
    private void createNewHologram(String hologramKey, Location location, List<String> lines,
                                 HologramLocationUtils.WallSide wallSide, String hologramText,
                                 String materialName, int itemCount, int requiredCount, boolean hasItems) {
        try {
            float yaw = HologramLocationUtils.getWallFacingYaw(wallSide);
            
            HologramData hologramData = HologramData.builder(hologramKey, location)
                .lines(lines)
                .viewDistance(config.getViewDistance())
                .persistent(false)
                .rotation(yaw, 0.0f)
                .build();

            var hologramOpt = provider.createHologram(hologramData);
            if (hologramOpt.isPresent()) {
                HologramState state = new HologramState(hologramText, location, materialName, 
                                                      itemCount, requiredCount, hasItems);
                tracker.trackHologram(hologramKey, hologramOpt.get(), state);
            }

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error creating hologram: " + hologramKey, e);
        }
    }

    /**
     * Removes a hologram by key.
     */
    private boolean removeHologramByKey(String hologramKey) {
        Hologram hologram = tracker.untrackHologram(hologramKey);
        if (hologram != null) {
            return provider.removeHologram(hologram);
        }
        return false;
    }

    /**
     * Removes all holograms for a player.
     */
    public void removeAllPlayerHolograms(Player player) {
        Map<String, Hologram> playerHolograms = tracker.removePlayerHolograms(player);
        for (Hologram hologram : playerHolograms.values()) {
            provider.removeHologram(hologram);
        }
    }

    /**
     * Removes holograms for a specific chunk.
     */
    public void removeHologramForChunk(Player player, Chunk chunk) {
        String prefix = HologramLocationUtils.generateHologramKey(player.getUniqueId(), chunk, null);
        Set<String> keysToRemove = tracker.getHologramKeysWithPrefix(prefix);
        
        for (String key : keysToRemove) {
            removeHologramByKey(key);
        }
    }

    /**
     * Calculates hologram locations for chunk walls that border unlocked chunks.
     */
    private Map<HologramLocationUtils.WallSide, Location> calculateWallHologramLocations(Player player, Chunk chunk) {
        Map<HologramLocationUtils.WallSide, Location> locations = new HashMap<>();
        
        if (!isWorldEnabled(chunk.getWorld())) {
            return locations;
        }

        for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.values()) {
            try {
                Chunk neighbor = chunk.getWorld().getChunkAt(
                    chunk.getX() + side.dx, 
                    chunk.getZ() + side.dz
                );
                
                chunkLockManager.initializeChunk(neighbor, player.getUniqueId());
                
                if (!chunkLockManager.isLocked(neighbor)) {
                    Location location = HologramLocationUtils.calculateWallHologramLocation(
                        chunk, side, config.getWallOffset(), config.getCenterOffset(),
                        config.getGroundClearance(), config.getMinHeight()
                    );
                    locations.put(side, location);
                }

            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine(
                    "Error checking " + side + " wall for chunk: " + e.getMessage());
            }
        }

        return locations;
    }

    /**
     * Cleans up holograms that should no longer exist.
     */
    private void cleanupInvalidHolograms(Player player, Set<String> validChunkKeys) {
        Set<String> playerKeys = tracker.getPlayerHologramKeys(player);
        
        for (String hologramKey : playerKeys) {
            String[] parts = hologramKey.split("_");
            if (parts.length >= 4) {
                try {
                    String worldName = parts[1];
                    int chunkX = Integer.parseInt(parts[2]);
                    int chunkZ = Integer.parseInt(parts[3]);
                    String chunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                    
                    // Check if world is still enabled
                    World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null || !isWorldEnabled(world)) {
                        removeHologramByKey(hologramKey);
                        continue;
                    }
                    
                    // Check if chunk should still have hologram
                    if (!validChunkKeys.contains(chunkKey)) {
                        removeHologramByKey(hologramKey);
                    }
                    
                } catch (NumberFormatException e) {
                    // Invalid key format, remove it
                    removeHologramByKey(hologramKey);
                }
            }
        }
    }

    /**
     * Counts how many of a specific material the player has.
     */
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        
        // Check off-hand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            count += offHand.getAmount();
        }
        
        return count;
    }

    /**
     * Checks if the world is enabled for holograms.
     */
    private boolean isWorldEnabled(World world) {
        if (world == null) return false;
        
        try {
            return worldManager.isWorldEnabled(world);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Could not check world status for holograms: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleanup all managed holograms.
     */
    public void cleanup() {
        Map<String, Hologram> allHolograms = tracker.clear();
        for (Hologram hologram : allHolograms.values()) {
            provider.removeHologram(hologram);
        }
    }

    /**
     * Gets display service statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.putAll(tracker.getStatistics());
        stats.putAll(provider.getStatistics());
        stats.put("worldCheckingEnabled", true);
        
        try {
            stats.put("enabledWorlds", worldManager.getEnabledWorlds());
        } catch (Exception e) {
            stats.put("worldCheckError", e.getMessage());
        }
        
        return stats;
    }
}
