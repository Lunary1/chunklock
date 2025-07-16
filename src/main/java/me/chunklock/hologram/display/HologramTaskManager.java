package me.chunklock.hologram.display;

import me.chunklock.hologram.config.HologramConfiguration;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages scheduled tasks for updating player holograms.
 * Handles starting, stopping, and cleanup of per-player hologram update tasks.
 */
public final class HologramTaskManager {

    private final HologramDisplayService displayService;
    private final HologramConfiguration config;
    private final WorldManager worldManager;
    private final Map<UUID, BukkitTask> playerTasks = new ConcurrentHashMap<>();

    public HologramTaskManager(HologramDisplayService displayService, 
                              HologramConfiguration config,
                              WorldManager worldManager) {
        this.displayService = displayService;
        this.config = config;
        this.worldManager = worldManager;
    }

    /**
     * Starts hologram display task for a player.
     */
    public void startPlayerTask(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Check if player is in an enabled world
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Player " + player.getName() + " is in disabled world " + 
                player.getWorld().getName() + " - skipping hologram display");
            return;
        }

        stopPlayerTask(player); // Clean up any existing task

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    playerTasks.remove(player.getUniqueId());
                    return;
                }

                // Re-check world status on each update
                if (!isWorldEnabled(player)) {
                    // Player moved to disabled world, remove holograms and stop task
                    displayService.removeAllPlayerHolograms(player);
                    cancel();
                    playerTasks.remove(player.getUniqueId());
                    return;
                }

                try {
                    displayService.updateHologramsForPlayer(player);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                        "Error updating holograms for " + player.getName(), e);
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 0L, config.getUpdateInterval());

        playerTasks.put(player.getUniqueId(), task);

        ChunklockPlugin.getInstance().getLogger().fine(
            "Started hologram display task for " + player.getName());
    }

    /**
     * Stops hologram display task for a player.
     */
    public void stopPlayerTask(Player player) {
        if (player == null) return;

        BukkitTask task = playerTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Remove holograms for this player
        displayService.removeAllPlayerHolograms(player);

        ChunklockPlugin.getInstance().getLogger().fine(
            "Stopped hologram display task for " + player.getName());
    }

    /**
     * Force refresh holograms for a specific player.
     */
    public void refreshPlayerHolograms(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        // Check if player is in enabled world before refreshing
        if (!isWorldEnabled(player)) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Player " + player.getName() + " is in disabled world " + 
                player.getWorld().getName() + " - removing holograms instead of refreshing");
            displayService.removeAllPlayerHolograms(player);
            return;
        }

        try {
            // Remove existing holograms first
            displayService.removeAllPlayerHolograms(player);
            
            // Update holograms immediately
            displayService.updateHologramsForPlayer(player);
            
            ChunklockPlugin.getInstance().getLogger().fine(
                "Refreshed holograms for " + player.getName());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error refreshing holograms for " + player.getName(), e);
        }
    }

    /**
     * Checks if the player's world is enabled for holograms.
     */
    private boolean isWorldEnabled(Player player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }

        try {
            return worldManager.isWorldEnabled(player.getWorld());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine(
                "Could not check world status for holograms: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cleanup all tasks and holograms.
     */
    public void cleanup() {
        ChunklockPlugin.getInstance().getLogger().info("Cleaning up hologram tasks...");

        // Cancel all tasks
        for (BukkitTask task : playerTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        playerTasks.clear();

        // Cleanup display service
        displayService.cleanup();

        ChunklockPlugin.getInstance().getLogger().info("Hologram task cleanup completed");
    }

    /**
     * Gets task manager statistics.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("activeTasks", playerTasks.size());
        stats.put("updateInterval", config.getUpdateInterval());
        
        // Add display service stats
        stats.putAll(displayService.getStatistics());
        
        return stats;
    }

    /**
     * Gets the number of active player tasks.
     */
    public int getActiveTaskCount() {
        return playerTasks.size();
    }

    /**
     * Checks if a player has an active task.
     */
    public boolean hasActiveTask(Player player) {
        return player != null && playerTasks.containsKey(player.getUniqueId());
    }
}
