package me.chunklock.hologram.state;

import me.chunklock.ChunklockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Manages player world readiness states and provides callbacks when players become location stable.
 */
public class WorldReadyManager {
    
    private final Map<UUID, PlayerWorldReadyState> playerStates = new ConcurrentHashMap<>();
    private final Map<UUID, Consumer<Player>> stabilityCallbacks = new ConcurrentHashMap<>();
    private BukkitTask updateTask;
    
    public WorldReadyManager() {
        startUpdateTask();
    }
    
    /**
     * Registers a player as transitioning to a new world.
     * 
     * @param playerId the player's UUID
     * @param targetWorldName the name of the world they're transitioning to
     */
    public void registerPlayerWorldTransition(UUID playerId, String targetWorldName) {
        PlayerWorldReadyState state = new PlayerWorldReadyState(playerId, targetWorldName);
        playerStates.put(playerId, state);
        
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("WorldReady: Registered player " + playerId + 
                " for world transition to " + targetWorldName);
        }
    }
    
    /**
     * Sets a callback to be executed when the player becomes location stable.
     * 
     * @param playerId the player's UUID
     * @param callback the callback to execute
     */
    public void setStabilityCallback(UUID playerId, Consumer<Player> callback) {
        stabilityCallbacks.put(playerId, callback);
    }
    
    /**
     * Manually marks a player as location stable (e.g., after known world setup completion).
     * 
     * @param playerId the player's UUID
     */
    public void forcePlayerLocationStable(UUID playerId) {
        PlayerWorldReadyState state = playerStates.get(playerId);
        if (state != null) {
            state.forceLocationStable();
            
            // Execute callback if available
            Consumer<Player> callback = stabilityCallbacks.get(playerId);
            if (callback != null) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    callback.accept(player);
                }
            }
        }
    }
    
    /**
     * Checks if a player is ready for hologram operations.
     * 
     * @param playerId the player's UUID
     * @return true if the player is location stable
     */
    public boolean isPlayerReady(UUID playerId) {
        PlayerWorldReadyState state = playerStates.get(playerId);
        return state != null && state.isLocationStable();
    }
    
    /**
     * Gets the current readiness state for a player.
     * 
     * @param playerId the player's UUID
     * @return the readiness state, or null if not tracked
     */
    public PlayerWorldReadyState getPlayerState(UUID playerId) {
        return playerStates.get(playerId);
    }
    
    /**
     * Marks that a hologram refresh is pending for a player.
     * 
     * @param playerId the player's UUID
     */
    public void setPendingRefresh(UUID playerId) {
        PlayerWorldReadyState state = playerStates.get(playerId);
        if (state != null) {
            state.setPendingRefresh(true);
        }
    }
    
    /**
     * Removes tracking for a player.
     * 
     * @param playerId the player's UUID
     */
    public void removePlayer(UUID playerId) {
        playerStates.remove(playerId);
        stabilityCallbacks.remove(playerId);
    }
    
    /**
     * Cleanup method to stop all tasks.
     */
    public void cleanup() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
        playerStates.clear();
        stabilityCallbacks.clear();
    }
    
    /**
     * Starts the background task that updates player states.
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, PlayerWorldReadyState> entry : playerStates.entrySet()) {
                    UUID playerId = entry.getKey();
                    PlayerWorldReadyState state = entry.getValue();
                    
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null || !player.isOnline()) {
                        continue;
                    }
                    
                    // Update state and check if player just became stable
                    boolean justBecameStable = state.updateState(player);
                    
                    if (justBecameStable) {
                        // Execute callback if available
                        Consumer<Player> callback = stabilityCallbacks.get(playerId);
                        if (callback != null) {
                            try {
                                callback.accept(player);
                            } catch (Exception e) {
                                ChunklockPlugin.getInstance().getLogger().warning(
                                    "Error executing stability callback for player " + player.getName() + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 1L, 1L); // Run every tick
    }
    
    /**
     * Gets the number of players currently being tracked.
     * 
     * @return the number of tracked players
     */
    public int getTrackedPlayerCount() {
        return playerStates.size();
    }
}
