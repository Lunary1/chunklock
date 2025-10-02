package me.chunklock.hologram.core;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import me.chunklock.ChunklockPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Debouncing system that coalesces rapid hologram updates into single operations.
 * Prevents spam updates when player inventory changes rapidly.
 */
public final class HologramDebouncer {
    
    private final Map<HologramId, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();
    private final int debounceDelayTicks;
    private BukkitRunnable cleanupTask;
    
    public HologramDebouncer(int debounceDelayTicks) {
        this.debounceDelayTicks = debounceDelayTicks;
        startCleanupTask();
    }
    
    /**
     * Schedules a hologram update with debouncing.
     * If multiple updates for the same hologram ID arrive within the debounce window,
     * only the latest one will be executed.
     */
    public void scheduleUpdate(HologramId hologramId, Location location, List<String> lines, 
                             Runnable updateCallback) {
        long currentTick = getCurrentTick();
        PendingUpdate update = new PendingUpdate(location, lines, updateCallback, currentTick + debounceDelayTicks);
        
        pendingUpdates.put(hologramId, update);
        
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Scheduled debounced update for hologram " + hologramId);
        }
    }
    
    /**
     * Processes all pending updates that have exceeded their debounce delay.
     */
    public void processPendingUpdates() {
        if (pendingUpdates.isEmpty()) return;
        
        long currentTick = getCurrentTick();
        List<HologramId> toProcess = new ArrayList<>();
        
        for (Map.Entry<HologramId, PendingUpdate> entry : pendingUpdates.entrySet()) {
            if (entry.getValue().executionTick <= currentTick) {
                toProcess.add(entry.getKey());
            }
        }
        
        for (HologramId id : toProcess) {
            PendingUpdate update = pendingUpdates.remove(id);
            if (update != null) {
                try {
                    update.callback.run();
                    if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                        ChunklockPlugin.getInstance().getLogger().fine("Executed debounced update for hologram " + id);
                    }
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                        "Error executing debounced update for hologram " + id, e);
                }
            }
        }
    }
    
    /**
     * Cancels any pending update for the specified hologram ID.
     */
    public void cancelUpdate(HologramId hologramId) {
        pendingUpdates.remove(hologramId);
    }
    
    /**
     * Cancels all pending updates for the specified player.
     */
    public void cancelPlayerUpdates(UUID playerId) {
        pendingUpdates.entrySet().removeIf(entry -> entry.getKey().getPlayerId().equals(playerId));
    }
    
    public void cleanup() {
        pendingUpdates.clear();
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
    }
    
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                processPendingUpdates();
            }
        };
        cleanupTask.runTaskTimer(ChunklockPlugin.getInstance(), 1L, 1L); // Every tick
    }
    
    private long getCurrentTick() {
        return ChunklockPlugin.getInstance().getServer().getCurrentTick();
    }
    
    private static class PendingUpdate {
        final Runnable callback;
        final long executionTick;
        
        PendingUpdate(Location location, List<String> lines, Runnable callback, long executionTick) {
            // Store callback and execution time; location and lines are captured by the callback
            this.callback = callback;
            this.executionTick = executionTick;
        }
    }
    
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("pendingUpdates", pendingUpdates.size());
        stats.put("debounceDelayTicks", debounceDelayTicks);
        return stats;
    }
}
