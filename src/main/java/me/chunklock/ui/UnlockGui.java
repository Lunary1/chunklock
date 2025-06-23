package me.chunklock.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGuiBuilder;
import me.chunklock.ui.UnlockGuiStateManager;
import me.chunklock.ui.UnlockGuiStateManager.PendingUnlock;
import me.chunklock.ChunklockPlugin;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Main coordinator for the chunk unlock GUI system.
 * Handles opening GUIs, coordinating with other systems, and managing the unlock process.
 */
public class UnlockGui {
    
    // Core dependencies
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;
    private final TeamManager teamManager;
    
    // UI components
    private final UnlockGuiBuilder builder;
    private final UnlockGuiStateManager stateManager;
    
    // Constants
    public static final String GUI_TITLE = "ChunkLock Unlock GUI";
    
    public UnlockGui(ChunkLockManager chunkLockManager,
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker,
                     TeamManager teamManager) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
        this.teamManager = teamManager;
        this.builder = new UnlockGuiBuilder();
        this.stateManager = new UnlockGuiStateManager();
    }
    
    /**
     * Open the unlock GUI for a player looking at a specific chunk.
     */
    public void open(Player player, Chunk chunk) {
        UUID playerId = player.getUniqueId();
        
        // Clean up any existing state first
        stateManager.cleanupPlayer(playerId);
        
        // Evaluate chunk and calculate requirements
        var evaluation = chunkLockManager.evaluateChunk(playerId, chunk);
        Biome biome = evaluation.biome;
        UUID teamId = teamManager.getTeamLeader(playerId);
        boolean contested = chunkLockManager.isContestedChunk(chunk, teamId);

        var requirement = biomeUnlockRegistry.calculateRequirement(player, biome, evaluation.score);
        
        // Apply contested chunk cost multiplier
        if (contested) {
            double multiplier = chunkLockManager.getContestedCostMultiplier();
            int adjustedAmount = (int) Math.ceil(requirement.amount() * multiplier);
            requirement = new BiomeUnlockRegistry.UnlockRequirement(requirement.material(), adjustedAmount);
            player.sendMessage("§cContested chunk! Cost x" + multiplier);
        }

        // Build and open the GUI
        Inventory inventory = builder.build(player, chunk, evaluation, requirement);
        
        // Store state
        PendingUnlock pendingUnlock = new PendingUnlock(chunk, biome, requirement, contested);
        stateManager.setPendingUnlock(playerId, pendingUnlock);
        stateManager.setActiveGui(playerId, inventory);
        
        // Debug logging
        ChunklockPlugin.getInstance().getLogger().info("Opening unlock GUI for " + player.getName() + 
            " - chunk " + chunk.getX() + "," + chunk.getZ() + 
            " - required: " + requirement.amount() + "x " + requirement.material());
        
        player.openInventory(inventory);
    }
    
    /**
     * Handle inventory click events.
     */
    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // Check if this is our GUI
        if (!isOurGui(playerId, event.getInventory())) {
            return;
        }

        event.setCancelled(true);
        
        // Debug logging
        ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
            " clicked slot " + event.getRawSlot() + " in unlock GUI. Pending state exists: " + 
            stateManager.hasPendingUnlock(playerId));
        
        // Only the unlock button (slot 22) should be clickable
        if (event.getRawSlot() != 22) {
            ChunklockPlugin.getInstance().getLogger().fine("Click was not on unlock button (slot 22)");
            return;
        }

        // Process the unlock attempt
        processUnlockAttempt(player);
    }
    
    /**
     * Handle inventory close events.
     */
    public void handleInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // Clean up GUI state when inventory is closed
        if (stateManager.hasActiveGui(playerId)) {
            ChunklockPlugin.getInstance().getLogger().fine("Cleaning up GUI state for " + player.getName());
            stateManager.clearActiveGui(playerId);
            
            // Optionally clean up pending unlock after a delay to allow reopening
            // For now, we'll keep the pending state in case they reopen quickly
        }
    }
    
    /**
     * Check if an inventory belongs to our GUI system.
     */
    private boolean isOurGui(UUID playerId, Inventory inventory) {
        // Method 1: Check if we have an active GUI for this player
        if (stateManager.isPlayerGui(playerId, inventory)) {
            ChunklockPlugin.getInstance().getLogger().fine("GUI identified by active GUI tracking");
            return true;
        }
        
        // Method 2: Check if player has pending unlock state
        if (stateManager.hasPendingUnlock(playerId)) {
            ChunklockPlugin.getInstance().getLogger().fine("GUI identified by pending state");
            return true;
        }
        
        // Method 3: Check inventory title as fallback
        try {
            String inventoryTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(inventory.viewers().get(0).getOpenInventory().title());
            if (GUI_TITLE.equals(inventoryTitle)) {
                ChunklockPlugin.getInstance().getLogger().fine("GUI identified by title: " + inventoryTitle);
                return true;
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Title check failed: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Process an unlock attempt from a player.
     */
    private void processUnlockAttempt(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Get pending unlock state
        PendingUnlock state = stateManager.getPendingUnlock(playerId);
        if (state == null) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            ChunklockPlugin.getInstance().getLogger().warning("No pending unlock state for player " + player.getName());
            return;
        }

        UUID teamId = teamManager.getTeamLeader(playerId);

        // Check if state is expired
        if (state.isExpired()) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            stateManager.cleanupPlayer(playerId);
            ChunklockPlugin.getInstance().getLogger().warning("Expired unlock state for player " + player.getName());
            return;
        }
        
        ChunklockPlugin.getInstance().getLogger().info("Processing unlock for " + player.getName() + 
            " - chunk " + state.chunk.getX() + "," + state.chunk.getZ());

        // Check if chunk is still locked
        if (!chunkLockManager.isLocked(state.chunk)) {
            player.sendMessage(Component.text("✅ Chunk already unlocked!")
                .color(NamedTextColor.GREEN));
            player.closeInventory();
            stateManager.cleanupPlayer(playerId);
            return;
        }

        try {
            // Validate contested chunk claims
            if (state.contested && !progressTracker.canClaimContested(teamId, chunkLockManager.getMaxContestedClaimsPerDay())) {
                player.sendMessage(Component.text("❌ Contested claim limit reached for today.").color(NamedTextColor.RED));
                return;
            }
            
            // Check if player has required items
            int playerHas = countPlayerItems(player, state.requirement.material());
            int required = state.requirement.amount();
            
            ChunklockPlugin.getInstance().getLogger().info("Item validation: player " + player.getName() + 
                " has " + playerHas + " " + state.requirement.material() + ", needs " + required);
            
            if (playerHas < required) {
                handleInsufficientItems(player, playerHas, required, state.requirement.material());
                return;
            }

            // Execute the unlock
            executeUnlock(player, state, teamId);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Error processing unlock for " + player.getName(), e);
            player.sendMessage(Component.text("❌ An error occurred while unlocking the chunk.")
                .color(NamedTextColor.RED));
        }
    }
    
    /**
     * Handle case where player doesn't have enough items.
     */
    private void handleInsufficientItems(Player player, int playerHas, int required, Material material) {
        // Play error sound
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        
        int needed = required - playerHas;
        
        player.sendMessage(Component.text("❌ Missing required items!")
            .color(NamedTextColor.RED));
        player.sendMessage(Component.text("Need " + needed + " more " + 
            formatMaterialName(material))
            .color(NamedTextColor.YELLOW));
        
        ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
            " missing items: has " + playerHas + ", needs " + required);
    }
    
    /**
     * Execute the actual unlock process.
     */
    private void executeUnlock(Player player, PendingUnlock state, UUID teamId) {
        try {
            // Get current evaluation (might have changed)
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), state.chunk);
            
            // Use the registry's consumption method which handles team integration
            biomeUnlockRegistry.consumeRequiredItem(player, state.biome, evaluation.score);
            
            ChunklockPlugin.getInstance().getLogger().info("Consumed items for " + player.getName() + 
                " using BiomeUnlockRegistry method");
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("BiomeUnlockRegistry consumption failed, using fallback: " + e.getMessage());
            
            // Fallback: Manual item removal
            ItemStack requiredStack = new ItemStack(state.requirement.material(), state.requirement.amount());
            player.getInventory().removeItem(requiredStack);
        }

        // Unlock chunk
        chunkLockManager.unlockChunk(state.chunk, teamId);
        if (state.contested) {
            progressTracker.incrementContestedClaims(teamId);
        }
        ChunklockPlugin.getInstance().getLogger().info("Unlocked chunk " + state.chunk.getX() + 
            "," + state.chunk.getZ() + " for player " + player.getName());

        // Record team statistics if available
        try {
            var enhancedTeamManager = ChunklockPlugin.getInstance().getEnhancedTeamManager();
            if (enhancedTeamManager != null) {
                enhancedTeamManager.recordChunkUnlock(player.getUniqueId(), 
                    BiomeUnlockRegistry.getBiomeDisplayName(state.biome));
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Team statistics recording failed: " + e.getMessage());
        }

        // Play success sound and effects
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        player.sendMessage(Component.text("🎉 Chunk unlocked successfully!")
            .color(NamedTextColor.GREEN));

        // Clean up and close GUI
        player.closeInventory();
        stateManager.cleanupPlayer(player.getUniqueId());

        // Trigger related systems
        notifyUnlockSystems(player, state.chunk);
    }
    
    /**
     * Notify other systems that a chunk was unlocked.
     */
    private void notifyUnlockSystems(Player player, Chunk chunk) {
        try {
            // Update borders
            var borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            if (borderManager != null) {
                borderManager.onChunkUnlocked(player, chunk);
            }
            
            // Update holograms
            var hologramManager = ChunklockPlugin.getInstance().getHologramManager();
            if (hologramManager != null) {
                hologramManager.updateHologramsForPlayer(player);
            }
            
            // Trigger unlock effects
            var effectsManager = ChunklockPlugin.getInstance().getUnlockEffectsManager();
            if (effectsManager != null) {
                effectsManager.playUnlockEffects(player, chunk);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error notifying unlock systems", e);
        }
    }
    
    /**
     * Count how many of a specific material the player has.
     */
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }
    
    /**
     * Format material name for display.
     */
    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
    
    /**
     * Get statistics about the GUI system.
     */
    public UnlockGuiStateManager.StateStats getStats() {
        return stateManager.getStats();
    }
    
    /**
     * Clean up expired state.
     */
    public int cleanupExpired() {
        return stateManager.cleanupExpired();
    }
}