package me.chunklock.border;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles queued border updates for performance optimization.
 * Processes border update tasks at a controlled rate to prevent lag.
 */
public class BorderUpdateQueue {
    
    private final JavaPlugin plugin;
    private final Queue<Runnable> updateQueue = new ConcurrentLinkedQueue<>();
    private final int maxUpdatesPerTick;
    private final BorderUpdateHandler updateHandler;
    private BukkitTask processingTask;
    
    @FunctionalInterface
    public interface BorderUpdateHandler {
        void updateBordersForPlayer(Player player);
    }
    
    public BorderUpdateQueue(JavaPlugin plugin, int maxUpdatesPerTick, BorderUpdateHandler updateHandler) {
        this.plugin = plugin;
        this.maxUpdatesPerTick = maxUpdatesPerTick;
        this.updateHandler = updateHandler;
        
        // Start the processing task
        this.processingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processQueue, 1L, 1L);
    }
    
    /**
     * Schedule a border update for a player.
     * The update will be processed asynchronously at a controlled rate.
     */
    public void scheduleBorderUpdate(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        updateQueue.add(() -> {
            try {
                if (player.isOnline()) {
                    updateHandler.updateBordersForPlayer(player);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error updating borders for player " + player.getName() + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * Schedule a custom border update task.
     * Use this for more complex update operations.
     */
    public void scheduleCustomUpdate(Runnable updateTask) {
        if (updateTask != null) {
            updateQueue.add(updateTask);
        }
    }
    
    /**
     * Process queued border updates at a controlled rate.
     */
    private void processQueue() {
        int processed = 0;
        while (processed < maxUpdatesPerTick) {
            Runnable task = updateQueue.poll();
            if (task == null) {
                break; // No more tasks in queue
            }
            
            try {
                task.run();
            } catch (Exception e) {
                plugin.getLogger().warning("Error processing border update: " + e.getMessage());
            }
            
            processed++;
        }
    }
    
    /**
     * Get the current queue size for monitoring.
     */
    public int getQueueSize() {
        return updateQueue.size();
    }
    
    /**
     * Check if the queue is empty.
     */
    public boolean isEmpty() {
        return updateQueue.isEmpty();
    }
    
    /**
     * Clear all pending updates from the queue.
     */
    public void clearQueue() {
        updateQueue.clear();
    }
    
    /**
     * Shutdown the update queue and cancel the processing task.
     * Call this during plugin disable.
     */
    public void shutdown() {
        if (processingTask != null && !processingTask.isCancelled()) {
            processingTask.cancel();
        }
        updateQueue.clear();
    }
    
    /**
     * Get statistics about the update queue for debugging.
     */
    public QueueStats getStats() {
        return new QueueStats(updateQueue.size(), maxUpdatesPerTick);
    }
    
    public static class QueueStats {
        public final int queueSize;
        public final int maxUpdatesPerTick;
        
        public QueueStats(int queueSize, int maxUpdatesPerTick) {
            this.queueSize = queueSize;
            this.maxUpdatesPerTick = maxUpdatesPerTick;
        }
        
        @Override
        public String toString() {
            return "QueueStats{size=" + queueSize + ", maxPerTick=" + maxUpdatesPerTick + "}";
        }
    }
}