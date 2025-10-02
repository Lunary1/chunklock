package me.chunklock.hologram.state;

import me.chunklock.ChunklockPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Represents the state of a player's readiness in their world for hologram operations.
 * Tracks the progression from world join to location stability.
 */
public class PlayerWorldReadyState {
    
    /**
     * The different states a player can be in regarding world readiness.
     */
    public enum WorldReadyStatus {
        /**
         * Player has started/teleported but their target world is not yet confirmed.
         */
        WAITING_FOR_WORLD_JOIN,
        
        /**
         * Player's world has been confirmed but location is not yet stable.
         */
        WORLD_CONFIRMED,
        
        /**
         * Player's location is stable and holograms can be safely processed.
         */
        LOCATION_STABLE
    }
    
    private final UUID playerId;
    private final String targetWorldName;
    private WorldReadyStatus status;
    private Location lastLocation;
    private long lastLocationChangeTime;
    private int stableLocationTicks;
    private boolean pendingRefresh;
    
    // Configuration constants
    private static final double LOCATION_STABILITY_THRESHOLD = 0.5; // blocks
    private static final int REQUIRED_STABLE_TICKS = 8; // ticks to consider location stable
    
    public PlayerWorldReadyState(UUID playerId, String targetWorldName) {
        this.playerId = playerId;
        this.targetWorldName = targetWorldName;
        this.status = WorldReadyStatus.WAITING_FOR_WORLD_JOIN;
        this.lastLocationChangeTime = System.currentTimeMillis();
        this.stableLocationTicks = 0;
        this.pendingRefresh = false;
    }
    
    /**
     * Updates the state based on player's current world and location.
     * 
     * @param player the player to check
     * @return true if state changed to LOCATION_STABLE
     */
    public boolean updateState(Player player) {
        WorldReadyStatus previousStatus = this.status;
        
        // Check world confirmation
        if (status == WorldReadyStatus.WAITING_FOR_WORLD_JOIN) {
            if (player.getWorld().getName().equals(targetWorldName)) {
                status = WorldReadyStatus.WORLD_CONFIRMED;
                lastLocation = player.getLocation().clone();
                lastLocationChangeTime = System.currentTimeMillis();
                stableLocationTicks = 0;
                
                if (ChunklockPlugin.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
                    ChunklockPlugin.getInstance().getLogger().fine("WorldReady: " + player.getName() + 
                        " world confirmed: " + targetWorldName);
                }
            }
        }
        
        // Check location stability
        if (status == WorldReadyStatus.WORLD_CONFIRMED) {
            Location currentLocation = player.getLocation();
            
            if (lastLocation == null) {
                lastLocation = currentLocation.clone();
                stableLocationTicks = 0;
            } else {
                double distance = currentLocation.distance(lastLocation);
                
                if (distance < LOCATION_STABILITY_THRESHOLD) {
                    stableLocationTicks++;
                    
                    if (stableLocationTicks >= REQUIRED_STABLE_TICKS) {
                        status = WorldReadyStatus.LOCATION_STABLE;
                        
                        if (ChunklockPlugin.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
                            ChunklockPlugin.getInstance().getLogger().fine("WorldReady: " + player.getName() + 
                                " location stabilized after " + stableLocationTicks + " ticks");
                        }
                    }
                } else {
                    // Location changed significantly, reset stability counter
                    lastLocation = currentLocation.clone();
                    stableLocationTicks = 0;
                    lastLocationChangeTime = System.currentTimeMillis();
                }
            }
        }
        
        // Return true if we just became stable
        return previousStatus != WorldReadyStatus.LOCATION_STABLE && status == WorldReadyStatus.LOCATION_STABLE;
    }
    
    /**
     * Forces the state to location stable (e.g., after known world setup completion).
     */
    public void forceLocationStable() {
        if (status != WorldReadyStatus.LOCATION_STABLE) {
            status = WorldReadyStatus.LOCATION_STABLE;
            
            if (ChunklockPlugin.getInstance().getLogger().isLoggable(java.util.logging.Level.FINE)) {
                ChunklockPlugin.getInstance().getLogger().fine("WorldReady: " + 
                    "forced location stable for player " + playerId);
            }
        }
    }
    
    /**
     * Marks that a hologram refresh is pending for when the player becomes ready.
     */
    public void setPendingRefresh(boolean pending) {
        this.pendingRefresh = pending;
    }
    
    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getTargetWorldName() { return targetWorldName; }
    public WorldReadyStatus getStatus() { return status; }
    public boolean isLocationStable() { return status == WorldReadyStatus.LOCATION_STABLE; }
    public boolean hasPendingRefresh() { return pendingRefresh; }
    public long getLastLocationChangeTime() { return lastLocationChangeTime; }
    public int getStableLocationTicks() { return stableLocationTicks; }
    
    @Override
    public String toString() {
        return String.format("PlayerWorldReadyState{player=%s, world=%s, status=%s, stableTicks=%d, pending=%s}", 
            playerId, targetWorldName, status, stableLocationTicks, pendingRefresh);
    }
}
