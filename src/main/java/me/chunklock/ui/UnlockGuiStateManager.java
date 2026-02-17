package me.chunklock.ui;

import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.inventory.Inventory;
import me.chunklock.managers.BiomeUnlockRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the state of active unlock GUIs and pending unlock operations.
 * Separates state management from UI logic and event handling.
 */
public class UnlockGuiStateManager {
    
    /**
     * Represents a pending unlock operation with expiration tracking.
     */
    public static class PendingUnlock {
        public final Chunk chunk;
        public final Biome biome;
        public final BiomeUnlockRegistry.UnlockRequirement requirement;
        public final boolean contested;
        public final long timestamp;
        private me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement;
        
        public PendingUnlock(Chunk chunk, Biome biome, BiomeUnlockRegistry.UnlockRequirement requirement, boolean contested) {
            this.chunk = chunk;
            this.biome = biome;
            this.requirement = requirement;
            this.contested = contested;
            this.timestamp = System.currentTimeMillis();
        }
        
        public void setPaymentRequirement(me.chunklock.economy.EconomyManager.PaymentRequirement paymentRequirement) {
            this.paymentRequirement = paymentRequirement;
        }
        
        public me.chunklock.economy.EconomyManager.PaymentRequirement getPaymentRequirement() {
            return paymentRequirement;
        }
        
        public boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > 300000; // 5 minutes
        }
    }
    
    // State tracking
    private final Map<UUID, PendingUnlock> pendingUnlocks = new HashMap<>();
    private final Map<UUID, Inventory> activeGuis = new HashMap<>();
    
    /**
     * Store a pending unlock operation for a player.
     */
    public void setPendingUnlock(UUID playerId, PendingUnlock pendingUnlock) {
        pendingUnlocks.put(playerId, pendingUnlock);
    }
    
    /**
     * Get the pending unlock operation for a player.
     */
    public PendingUnlock getPendingUnlock(UUID playerId) {
        return pendingUnlocks.get(playerId);
    }
    
    /**
     * Check if a player has a pending unlock operation.
     */
    public boolean hasPendingUnlock(UUID playerId) {
        return pendingUnlocks.containsKey(playerId);
    }
    
    /**
     * Store an active GUI for a player.
     */
    public void setActiveGui(UUID playerId, Inventory inventory) {
        activeGuis.put(playerId, inventory);
    }
    
    /**
     * Get the active GUI for a player.
     */
    public Inventory getActiveGui(UUID playerId) {
        return activeGuis.get(playerId);
    }
    
    /**
     * Check if a player has an active GUI.
     */
    public boolean hasActiveGui(UUID playerId) {
        return activeGuis.containsKey(playerId);
    }
    
    /**
     * Check if an inventory belongs to a player's active GUI.
     */
    public boolean isPlayerGui(UUID playerId, Inventory inventory) {
        Inventory activeGui = activeGuis.get(playerId);
        return activeGui != null && activeGui.equals(inventory);
    }
    
    /**
     * Clean up all state for a player (both pending unlock and active GUI).
     */
    public void cleanupPlayer(UUID playerId) {
        pendingUnlocks.remove(playerId);
        activeGuis.remove(playerId);
    }
    
    /**
     * Clean up only the pending unlock for a player (keep GUI active).
     */
    public void clearPendingUnlock(UUID playerId) {
        pendingUnlocks.remove(playerId);
    }
    
    /**
     * Clean up only the active GUI for a player (keep pending unlock).
     */
    public void clearActiveGui(UUID playerId) {
        activeGuis.remove(playerId);
    }
    
    /**
     * Get statistics about the current state.
     */
    public StateStats getStats() {
        int expiredPending = 0;
        for (PendingUnlock pending : pendingUnlocks.values()) {
            if (pending.isExpired()) {
                expiredPending++;
            }
        }
        
        return new StateStats(
            pendingUnlocks.size(),
            activeGuis.size(),
            expiredPending
        );
    }
    
    /**
     * Clean up expired pending unlocks.
     */
    public int cleanupExpired() {
        final int[] cleaned = {0}; // Use array to make it effectively final
        pendingUnlocks.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired();
            if (expired) cleaned[0]++;
            return expired;
        });
        return cleaned[0];
    }
    
    /**
     * Statistics about the GUI state.
     */
    public static class StateStats {
        public final int pendingUnlocks;
        public final int activeGuis;
        public final int expiredPending;
        
        public StateStats(int pendingUnlocks, int activeGuis, int expiredPending) {
            this.pendingUnlocks = pendingUnlocks;
            this.activeGuis = activeGuis;
            this.expiredPending = expiredPending;
        }
        
        @Override
        public String toString() {
            return "GuiState{pending=" + pendingUnlocks + 
                   ", active=" + activeGuis + 
                   ", expired=" + expiredPending + "}";
        }
    }
}