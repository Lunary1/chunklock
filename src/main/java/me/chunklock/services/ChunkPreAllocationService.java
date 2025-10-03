package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Pre-allocation service that calculates good spawn chunks in advance.
 * This prevents server lag during /chunklock start by having suitable chunks ready.
 */
public class ChunkPreAllocationService {
    
    private final ChunkLockManager chunkLockManager;
    private final ChunklockPlugin plugin;
    private final Random random = new Random();
    
    // Pre-calculated good spawn chunks (thread-safe queue)
    private final Queue<PreAllocatedChunk> availableChunks = new ConcurrentLinkedQueue<>();
    
    // Debug configuration
    private boolean debugLogging;
    
    // Configuration
    private static final int TARGET_POOL_SIZE = 20; // Keep 20 good chunks ready
    private static final int MAX_SCORE_THRESHOLD = 25;
    private static final int SEARCH_RADIUS = 30; // Larger radius for pre-allocation
    private static final int CHUNKS_PER_TICK = 2; // Process 2 chunks per tick to avoid lag
    private static final long SCAN_INTERVAL_TICKS = 100L; // Scan every 5 seconds
    
    // Task management
    private BukkitTask preAllocationTask;
    private boolean enabled = true;
    
    public ChunkPreAllocationService(ChunkLockManager chunkLockManager, ChunklockPlugin plugin) {
        this.chunkLockManager = chunkLockManager;
        this.plugin = plugin;
        loadDebugConfiguration();
    }
    
    /**
     * Load debug configuration from config.yml
     */
    private void loadDebugConfiguration() {
        var config = plugin.getConfig();
        boolean masterDebug = config.getBoolean("debug-mode.enabled", false);
        this.debugLogging = masterDebug && config.getBoolean("debug-mode.performance", false);
    }
    
    /**
     * Reload debug configuration (called during plugin reload)
     */
    public void reloadConfiguration() {
        loadDebugConfiguration();
    }
    
    /**
     * Start the pre-allocation service
     */
    public void start(World world) {
        if (preAllocationTask != null) {
            preAllocationTask.cancel();
        }
        
        if (debugLogging) {
            plugin.getLogger().info("[ChunkPreAllocation] Starting chunk pre-allocation service for world: " + world.getName());
        }
        
        preAllocationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!enabled) return;
            
            try {
                maintainChunkPool(world);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error in chunk pre-allocation", e);
            }
        }, 20L, SCAN_INTERVAL_TICKS);
    }
    
    /**
     * Stop the pre-allocation service
     */
    public void stop() {
        enabled = false;
        if (preAllocationTask != null) {
            preAllocationTask.cancel();
            preAllocationTask = null;
        }
        availableChunks.clear();
        if (debugLogging) {
            plugin.getLogger().info("[ChunkPreAllocation] Stopped chunk pre-allocation service");
        }
    }
    
    /**
     * Get a pre-allocated chunk instantly
     */
    public Chunk getPreAllocatedChunk() {
        PreAllocatedChunk preAllocated = availableChunks.poll();
        if (preAllocated != null) {
            if (debugLogging) {
                plugin.getLogger().info("[ChunkPreAllocation] Provided pre-allocated chunk at " + 
                    preAllocated.chunk.getX() + "," + preAllocated.chunk.getZ() + " (score: " + preAllocated.score + ")");
            }
            return preAllocated.chunk;
        }
        return null;
    }
    
    /**
     * Check if we have pre-allocated chunks available
     */
    public boolean hasAvailableChunks() {
        return !availableChunks.isEmpty();
    }
    
    /**
     * Get the number of pre-allocated chunks available
     */
    public int getAvailableChunkCount() {
        return availableChunks.size();
    }
    
    /**
     * Maintain the pool of pre-allocated chunks
     */
    private void maintainChunkPool(World world) {
        if (availableChunks.size() >= TARGET_POOL_SIZE) {
            return; // Pool is full
        }
        
        int chunksToFind = TARGET_POOL_SIZE - availableChunks.size();
        int attemptsPerCycle = CHUNKS_PER_TICK * 10; // Try to find chunks efficiently
        
        for (int attempt = 0; attempt < attemptsPerCycle && chunksToFind > 0; attempt++) {
            try {
                // Generate random coordinates within search radius
                int cx = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                int cz = random.nextInt(SEARCH_RADIUS * 2 + 1) - SEARCH_RADIUS;
                
                // Schedule chunk evaluation on main thread (required for world access)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        evaluateAndStoreChunk(chunk);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.FINE, "Error evaluating chunk during pre-allocation", e);
                    }
                });
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.FINE, "Error in chunk pre-allocation loop", e);
            }
        }
    }
    
    /**
     * Evaluate a chunk and store it if suitable
     */
    private void evaluateAndStoreChunk(Chunk chunk) {
        try {
            // Check if this chunk is already in use or in our pool
            if (isChunkAlreadyAllocated(chunk)) {
                return;
            }
            
            // Quick water check first
            ChunkEvaluator evaluator = ChunklockPlugin.getInstance().getChunkEvaluator();
            if (!evaluator.isChunkSuitableForSpawning(chunk)) {
                return;
            }
            
            // Full evaluation (using cached evaluation for performance)
            ChunkEvaluator.ChunkValueData evaluation = evaluator.evaluateChunkForCache(chunk);
            
            if (evaluation.score <= MAX_SCORE_THRESHOLD) {
                PreAllocatedChunk preAllocated = new PreAllocatedChunk(chunk, evaluation.score);
                availableChunks.offer(preAllocated);
                
                plugin.getLogger().fine("[ChunkPreAllocation] Added chunk " + chunk.getX() + "," + chunk.getZ() + 
                    " to pool (score: " + evaluation.score + "), pool size: " + availableChunks.size());
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Error evaluating chunk for pre-allocation", e);
        }
    }
    
    /**
     * Check if a chunk is already allocated or in our pool
     */
    private boolean isChunkAlreadyAllocated(Chunk chunk) {
        // Check if chunk is already claimed by any player
        if (!chunkLockManager.isLocked(chunk)) {
            return true; // Already unlocked by someone
        }
        
        // Check if chunk is already in our pool
        return availableChunks.stream()
            .anyMatch(preAllocated -> preAllocated.chunk.getX() == chunk.getX() && 
                                     preAllocated.chunk.getZ() == chunk.getZ());
    }
    
    /**
     * Get statistics for debugging
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("availableChunks", availableChunks.size());
        stats.put("targetPoolSize", TARGET_POOL_SIZE);
        stats.put("taskRunning", preAllocationTask != null && !preAllocationTask.isCancelled());
        return stats;
    }
    
    /**
     * Force refresh the chunk pool (admin command)
     */
    public void refreshPool(World world) {
        availableChunks.clear();
        if (debugLogging) {
            plugin.getLogger().info("[ChunkPreAllocation] Manually refreshing chunk pool for world: " + world.getName());
        }
        
        // Trigger immediate pool maintenance
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            maintainChunkPool(world);
        });
    }
    
    /**
     * Container for pre-allocated chunk data
     */
    private static class PreAllocatedChunk {
        final Chunk chunk;
        final int score;
        final long timestamp;
        
        PreAllocatedChunk(Chunk chunk, int score) {
            this.chunk = chunk;
            this.score = score;
            this.timestamp = System.currentTimeMillis();
        }
    }
}