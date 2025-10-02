package me.chunklock.hologram.compute;

import me.chunklock.ChunklockPlugin;
import me.chunklock.hologram.core.HologramId;
import me.chunklock.hologram.util.HologramLocationUtils;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Handles asynchronous computation of hologram eligibility and placement calculations.
 * All heavy work is done off the main thread, with results applied on the main thread.
 */
public class HologramComputeService {
    
    private final ExecutorService computeExecutor;
    private final ChunkLockManager chunkLockManager;
    private final AtomicLong jobCounter = new AtomicLong(0);
    private final Map<UUID, Long> latestJobIds = new ConcurrentHashMap<>();
    
    public HologramComputeService(ChunkLockManager chunkLockManager) {
        this.chunkLockManager = chunkLockManager;
        
        // Create a single-threaded executor for compute jobs
        this.computeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HologramCompute");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Computes eligible hologram IDs for a player asynchronously.
     * Only considers unlocked chunks and their frontier neighbors.
     * 
     * @param player the player
     * @param maxDistance maximum distance for hologram visibility
     * @param resultCallback callback executed on main thread with results
     */
    public void computeEligibleHologramsAsync(Player player, double maxDistance, 
                                            Consumer<EligibilityResult> resultCallback) {
        UUID playerId = player.getUniqueId();
        long jobId = jobCounter.incrementAndGet();
        
        // Track this as the latest job for this player
        latestJobIds.put(playerId, jobId);
        
        CompletableFuture.supplyAsync(() -> {
            // Check if this job is still relevant
            if (!isJobStillRelevant(playerId, jobId)) {
                return null;
            }
            
            return computeEligibleHolograms(player, maxDistance);
        }, computeExecutor).thenAcceptAsync(result -> {
            // Execute callback on main thread
            if (result != null && isJobStillRelevant(playerId, jobId)) {
                resultCallback.accept(result);
            }
        }, runnable -> Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), runnable));
    }
    
    /**
     * Computes wall locations for a set of chunks asynchronously.
     * 
     * @param chunks the chunks to compute wall locations for
     * @param resultCallback callback executed on main thread with results
     */
    public void computeWallLocationsAsync(Set<Chunk> chunks, 
                                        Consumer<Map<String, Map<HologramLocationUtils.WallSide, Location>>> resultCallback) {
        CompletableFuture.supplyAsync(() -> {
            Map<String, Map<HologramLocationUtils.WallSide, Location>> wallLocations = new HashMap<>();
            
            for (Chunk chunk : chunks) {
                String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
                Map<HologramLocationUtils.WallSide, Location> chunkWalls = new EnumMap<>(HologramLocationUtils.WallSide.class);
                
                for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.getOrderedSides()) {
                    Location location = HologramLocationUtils.calculateWallHologramLocation(
                        chunk, side, 1.0, 8.0, 2.0, 64);
                    chunkWalls.put(side, location);
                }
                
                wallLocations.put(chunkKey, chunkWalls);
            }
            
            return wallLocations;
        }, computeExecutor).thenAcceptAsync(result -> {
            // Execute callback on main thread
            resultCallback.accept(result);
        }, runnable -> Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), runnable));
    }
    
    /**
     * Synchronous computation of eligible holograms (called from async context).
     */
    private EligibilityResult computeEligibleHolograms(Player player, double maxDistance) {
        try {
            Set<HologramId> eligibleIds = new HashSet<>();
            Set<String> unlockedChunks = new HashSet<>();
            Set<String> frontierChunks = new HashSet<>();
            
            UUID playerId = player.getUniqueId();
            World world = player.getWorld();
            Location playerLocation = player.getLocation();
            
            // Get all unlocked chunks for this player by checking chunk ownership
            Set<String> allUnlockedChunks = chunkLockManager.getUnlockedChunks();
            Set<String> playerUnlockedChunks = new HashSet<>();
            
            for (String chunkKey : allUnlockedChunks) {
                String[] parts = chunkKey.split(":");
                if (parts.length >= 3 && parts[0].equals(world.getName())) {
                    try {
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                        
                        UUID chunkOwner = chunkLockManager.getChunkOwner(chunk);
                        if (playerId.equals(chunkOwner)) {
                            playerUnlockedChunks.add(chunkKey);
                        }
                    } catch (NumberFormatException e) {
                        // Skip malformed chunk keys
                        continue;
                    }
                }
            }
            
            unlockedChunks.addAll(playerUnlockedChunks);
            
            // Find frontier chunks (N/E/S/W neighbors of unlocked chunks)
            Set<String> frontierSet = new HashSet<>();
            for (String chunkKey : playerUnlockedChunks) {
                String[] parts = chunkKey.split(":");
                if (parts.length >= 3) {
                    try {
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        
                        // Add N/E/S/W neighbors (no diagonals)
                        frontierSet.add(world.getName() + ":" + chunkX + ":" + (chunkZ - 1)); // North
                        frontierSet.add(world.getName() + ":" + (chunkX + 1) + ":" + chunkZ); // East  
                        frontierSet.add(world.getName() + ":" + chunkX + ":" + (chunkZ + 1)); // South
                        frontierSet.add(world.getName() + ":" + (chunkX - 1) + ":" + chunkZ); // West
                    } catch (NumberFormatException e) {
                        // Skip malformed chunk keys
                        continue;
                    }
                }
            }
            
            // Remove already unlocked chunks from frontier
            frontierSet.removeAll(playerUnlockedChunks);
            frontierChunks.addAll(frontierSet);
            
            // Combine unlocked and frontier for eligibility
            Set<String> eligibleChunkKeys = new HashSet<>();
            eligibleChunkKeys.addAll(unlockedChunks);
            eligibleChunkKeys.addAll(frontierChunks);
            
            // Generate hologram IDs for eligible chunks with distance culling
            for (String chunkKey : eligibleChunkKeys) {
                String[] parts = chunkKey.split(":");
                if (parts.length >= 3) {
                    try {
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        
                        for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.getOrderedSides()) {
                            // Quick distance check using chunk center
                            double chunkCenterX = chunkX * 16 + 8;
                            double chunkCenterZ = chunkZ * 16 + 8;
                            double distanceSquared = Math.pow(playerLocation.getX() - chunkCenterX, 2) + 
                                                   Math.pow(playerLocation.getZ() - chunkCenterZ, 2);
                            
                            if (distanceSquared <= maxDistance * maxDistance) {
                                HologramId hologramId = HologramId.create(playerId, world, chunkX, chunkZ, side);
                                eligibleIds.add(hologramId);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // Skip malformed chunk keys
                        continue;
                    }
                }
            }
            
            return new EligibilityResult(eligibleIds, unlockedChunks.size(), frontierChunks.size());
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error computing eligible holograms for player " + player.getName() + ": " + e.getMessage());
            return new EligibilityResult(new HashSet<>(), 0, 0);
        }
    }
    
    /**
     * Checks if a compute job is still relevant (hasn't been superseded by a newer job).
     */
    private boolean isJobStillRelevant(UUID playerId, long jobId) {
        Long latestJobId = latestJobIds.get(playerId);
        return latestJobId != null && latestJobId == jobId;
    }
    
    /**
     * Cancels any pending jobs for a player.
     */
    public void cancelPlayerJobs(UUID playerId) {
        latestJobIds.remove(playerId);
    }
    
    /**
     * Cleanup method to shutdown the executor.
     */
    public void cleanup() {
        latestJobIds.clear();
        computeExecutor.shutdown();
        try {
            if (!computeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                computeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            computeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Result of eligibility computation.
     */
    public static class EligibilityResult {
        private final Set<HologramId> eligibleIds;
        private final int unlockedCount;
        private final int frontierCount;
        
        public EligibilityResult(Set<HologramId> eligibleIds, int unlockedCount, int frontierCount) {
            this.eligibleIds = eligibleIds;
            this.unlockedCount = unlockedCount;
            this.frontierCount = frontierCount;
        }
        
        public Set<HologramId> getEligibleIds() { return eligibleIds; }
        public int getUnlockedCount() { return unlockedCount; }
        public int getFrontierCount() { return frontierCount; }
        public int getEligibleCount() { return eligibleIds.size(); }
    }
}
