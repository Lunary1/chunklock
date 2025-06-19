package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class UnlockGui implements Listener {
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;

    private static class PendingUnlock {
        final Chunk chunk;
        final Biome biome;
        final BiomeUnlockRegistry.UnlockRequirement requirement;
        final boolean contested;
        final long timestamp;

        PendingUnlock(Chunk chunk, Biome biome, BiomeUnlockRegistry.UnlockRequirement requirement, boolean contested) {
            this.chunk = chunk;
            this.biome = biome;
            this.requirement = requirement;
            this.contested = contested;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > 300000; // 5 minutes
        }
    }

    private final Map<UUID, PendingUnlock> pending = new HashMap<>();
    
    // 🔧 FIX: Use a more unique GUI title that's easier to match
    private static final String GUI_TITLE = "ChunkLock Unlock GUI";
    
    // 🔧 FIX: Track which inventories belong to our plugin
    private final Map<UUID, Inventory> activeGuis = new HashMap<>();

    private final TeamManager teamManager;

    public UnlockGui(ChunkLockManager chunkLockManager,
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker,
                     TeamManager teamManager) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
        this.teamManager = teamManager;
    }

    public void open(Player player, Chunk chunk) {
        UUID playerId = player.getUniqueId();
        
        // 🔧 FIX: Clean up any existing state first
        cleanupPlayer(playerId);
        
        var eval = chunkLockManager.evaluateChunk(playerId, chunk);
        Biome biome = eval.biome;
        UUID teamId = teamManager.getTeamLeader(playerId);
        boolean contested = chunkLockManager.isContestedChunk(chunk, teamId);

        var requirement = biomeUnlockRegistry.calculateRequirement(player, biome, eval.score);
        if (contested) {
            double mult = chunkLockManager.getContestedCostMultiplier();
            int amt = (int) Math.ceil(requirement.amount() * mult);
            requirement = new BiomeUnlockRegistry.UnlockRequirement(requirement.material(), amt);
            player.sendMessage("§cContested chunk! Cost x" + mult);
        }

        // Create larger inventory for better display
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));

        // Add chunk info item
        addChunkInfoItem(inv, chunk, eval);
        
        // Add requirement display (improved for large amounts)
        addRequirementItems(inv, player, requirement);

        // Add unlock button
        addUnlockButton(inv, player, requirement);

        // 🔧 FIX: Store both pending state and inventory reference
        PendingUnlock pendingUnlock = new PendingUnlock(chunk, biome, requirement, contested);
        pending.put(playerId, pendingUnlock);
        activeGuis.put(playerId, inv);
        
        // 🐛 DEBUG: Log GUI opening
        ChunklockPlugin.getInstance().getLogger().info("Opening unlock GUI for " + player.getName() + 
            " - chunk " + chunk.getX() + "," + chunk.getZ() + 
            " - required: " + requirement.amount() + "x " + requirement.material());
        
        player.openInventory(inv);
    }

    private void addChunkInfoItem(Inventory inv, Chunk chunk, ChunkEvaluator.ChunkValueData eval) {
        ItemStack chunkInfo = new ItemStack(Material.MAP);
        ItemMeta meta = chunkInfo.getItemMeta();
        
        meta.displayName(Component.text("Chunk Information")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Location: " + chunk.getX() + ", " + chunk.getZ())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Biome: " + BiomeUnlockRegistry.getBiomeDisplayName(eval.biome))
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Difficulty: " + eval.difficulty)
            .color(getDifficultyColor(eval.difficulty))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Score: " + eval.score)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        
        meta.lore(lore);
        chunkInfo.setItemMeta(meta);
        inv.setItem(4, chunkInfo);
    }

    private void addRequirementItems(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        int requiredAmount = requirement.amount();
        int playerHas = countPlayerItems(player, requirement.material());
        boolean hasEnough = playerHas >= requiredAmount;
        
        // Calculate how many stacks to show
        if (requiredAmount <= 64) {
            // Single stack - simple case
            addSingleRequirementItem(inv, 10, requirement, playerHas, hasEnough);
        } else {
            // Multiple stacks needed - show breakdown
            addMultipleRequirementItems(inv, requirement, playerHas, hasEnough);
        }
    }

    private void addSingleRequirementItem(Inventory inv, int slot, BiomeUnlockRegistry.UnlockRequirement requirement, 
                                        int playerHas, boolean hasEnough) {
        ItemStack stack = new ItemStack(requirement.material(), Math.min(64, requirement.amount()));
        ItemMeta meta = stack.getItemMeta();
        
        // Set name based on whether player has enough
        Component displayName;
        if (hasEnough) {
            displayName = Component.text("✓ " + formatMaterialName(requirement.material()))
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false);
        } else {
            displayName = Component.text("✗ " + formatMaterialName(requirement.material()))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
        }
        meta.displayName(displayName);
        
        // Add detailed lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Required: " + requirement.amount())
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("You have: " + playerHas)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        
        if (!hasEnough) {
            int needed = requirement.amount() - playerHas;
            lore.add(Component.text("Still need: " + needed)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    private void addMultipleRequirementItems(Inventory inv, BiomeUnlockRegistry.UnlockRequirement requirement, 
                                           int playerHas, boolean hasEnough) {
        int requiredAmount = requirement.amount();
        
        // Calculate stack breakdown
        int fullStacks = requiredAmount / 64;
        int remainder = requiredAmount % 64;
        
        // Show stack breakdown in slots 9, 10, 11
        int slot = 9;
        
        // Add full stack representations
        for (int i = 0; i < Math.min(fullStacks, 2); i++) { // Limit to 2 full stacks shown
            ItemStack stack = new ItemStack(requirement.material(), 64);
            ItemMeta meta = stack.getItemMeta();
            
            meta.displayName(Component.text("Stack " + (i + 1) + " (64 items)")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Part of total requirement")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            
            stack.setItemMeta(meta);
            inv.setItem(slot++, stack);
        }
        
        // Add remainder stack if exists
        if (remainder > 0 && slot <= 11) {
            ItemStack stack = new ItemStack(requirement.material(), remainder);
            ItemMeta meta = stack.getItemMeta();
            
            String stackLabel = fullStacks > 2 ? "... + " + remainder + " more" : "Final stack (" + remainder + " items)";
            meta.displayName(Component.text(stackLabel)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            
            stack.setItemMeta(meta);
            inv.setItem(slot, stack);
        }
        
        // Add summary item in center
        ItemStack summary = new ItemStack(requirement.material(), Math.min(64, requiredAmount));
        ItemMeta summaryMeta = summary.getItemMeta();
        
        Component displayName;
        if (hasEnough) {
            displayName = Component.text("✓ " + formatMaterialName(requirement.material()) + " (TOTAL)")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        } else {
            displayName = Component.text("✗ " + formatMaterialName(requirement.material()) + " (TOTAL)")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        }
        summaryMeta.displayName(displayName);
        
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.text("Total Required: " + requiredAmount)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        summaryLore.add(Component.text("You have: " + playerHas)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        
        if (!hasEnough) {
            int needed = requiredAmount - playerHas;
            summaryLore.add(Component.text("Still need: " + needed)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            
            // Add helpful stack information
            int neededFullStacks = needed / 64;
            int neededRemainder = needed % 64;
            if (neededFullStacks > 0) {
                String stackInfo = neededFullStacks + " full stack" + (neededFullStacks > 1 ? "s" : "");
                if (neededRemainder > 0) {
                    stackInfo += " + " + neededRemainder + " more";
                }
                summaryLore.add(Component.text("(" + stackInfo + ")")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            }
        }
        
        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inv.setItem(13, summary); // Center slot
    }

    private void addUnlockButton(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        boolean hasEnough = countPlayerItems(player, requirement.material()) >= requirement.amount();
        
        ItemStack unlock;
        if (hasEnough) {
            unlock = new ItemStack(Material.EMERALD_BLOCK);
            ItemMeta meta = unlock.getItemMeta();
            meta.displayName(Component.text("✓ Click to Unlock Chunk!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("You have all required items")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Click to consume items and unlock")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            
            unlock.setItemMeta(meta);
        } else {
            unlock = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta meta = unlock.getItemMeta();
            meta.displayName(Component.text("✗ Cannot Unlock Yet")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("You don't have enough items")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Gather the required materials first")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            
            unlock.setItemMeta(meta);
        }
        
        inv.setItem(22, unlock); // Bottom right area
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    private NamedTextColor getDifficultyColor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case IMPOSSIBLE -> NamedTextColor.DARK_PURPLE;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // 🔧 FIX: Check if this is our GUI using multiple methods
        boolean isOurGUI = false;
        
        // Method 1: Check if we have an active GUI for this player
        Inventory activeGui = activeGuis.get(playerId);
        if (activeGui != null && activeGui.equals(event.getInventory())) {
            isOurGUI = true;
            ChunklockPlugin.getInstance().getLogger().fine("GUI identified by active GUI tracking");
        }
        
        // Method 2: Check if player has pending unlock state
        if (!isOurGUI && pending.containsKey(playerId)) {
            isOurGUI = true;
            ChunklockPlugin.getInstance().getLogger().fine("GUI identified by pending state");
        }
        
        // Method 3: Check inventory title as fallback
        if (!isOurGUI) {
            try {
                String inventoryTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.getView().title());
                if (GUI_TITLE.equals(inventoryTitle)) {
                    isOurGUI = true;
                    ChunklockPlugin.getInstance().getLogger().fine("GUI identified by title: " + inventoryTitle);
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Title check failed: " + e.getMessage());
            }
        }
        
        if (!isOurGUI) {
            return;
        }

        event.setCancelled(true);
        
        // 🐛 DEBUG: Log click event with detailed info
        ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
            " clicked slot " + event.getRawSlot() + " in unlock GUI. Pending state exists: " + pending.containsKey(playerId));
        
        // Only the unlock button (slot 22) should be clickable
        if (event.getRawSlot() != 22) {
            ChunklockPlugin.getInstance().getLogger().fine("Click was not on unlock button (slot 22)");
            return;
        }

        // 🔧 FIX: Enhanced state validation with debugging
        PendingUnlock state = pending.get(playerId);
        if (state == null) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            ChunklockPlugin.getInstance().getLogger().warning("No pending unlock state for player " + player.getName() + 
                ". Active GUIs: " + activeGuis.containsKey(playerId) + 
                ", Pending size: " + pending.size());
            return;
        }

        UUID teamId = teamManager.getTeamLeader(playerId);

        // Check if state is expired
        if (state.isExpired()) {
            player.sendMessage(Component.text("❌ Unlock session expired. Please try again.")
                .color(NamedTextColor.RED));
            player.closeInventory();
            cleanupPlayer(playerId);
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
            cleanupPlayer(playerId);
            return;
        }

        try {
            // 🔧 FIX: Enhanced item validation with detailed feedback
            int playerHas = countPlayerItems(player, state.requirement.material());
            int required = state.requirement.amount();

            if (state.contested && !progressTracker.canClaimContested(teamId, chunkLockManager.getMaxContestedClaimsPerDay())) {
                player.sendMessage(Component.text("❌ Contested claim limit reached for today.").color(NamedTextColor.RED));
                return;
            }
            
            ChunklockPlugin.getInstance().getLogger().info("Item validation: player " + player.getName() + 
                " has " + playerHas + " " + state.requirement.material() + ", needs " + required);
            
            if (playerHas < required) {
                // Play error sound
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                
                int needed = required - playerHas;
                
                player.sendMessage(Component.text("❌ Missing required items!")
                    .color(NamedTextColor.RED));
                player.sendMessage(Component.text("Need " + needed + " more " + 
                    formatMaterialName(state.requirement.material()))
                    .color(NamedTextColor.YELLOW));
                
                ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
                    " missing items: has " + playerHas + ", needs " + required);
                return;
            }

            // 🔧 FIX: Use BiomeUnlockRegistry's consume method for consistency
            try {
                // Get current evaluation (might have changed)
                var evaluation = chunkLockManager.evaluateChunk(playerId, state.chunk);
                
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

            // 🔧 FIX: Unlock chunk with proper error handling
            chunkLockManager.unlockChunk(state.chunk, teamId);
            if (state.contested) {
                progressTracker.incrementContestedClaims(teamId);
            }
            ChunklockPlugin.getInstance().getLogger().info("Unlocked chunk " + state.chunk.getX() + 
                "," + state.chunk.getZ() + " for player " + player.getName());

            // 🔧 FIX: Safe team recording with error handling
            try {
                var enhancedTeamManager = ChunklockPlugin.getInstance().getEnhancedTeamManager();
                if (enhancedTeamManager != null) {
                    enhancedTeamManager.recordChunkUnlock(playerId, 
                        BiomeUnlockRegistry.getBiomeDisplayName(state.biome));
                    ChunklockPlugin.getInstance().getLogger().fine("Recorded team chunk unlock");
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Failed to record team chunk unlock: " + e.getMessage());
                // Continue anyway - this is not critical
            }

            // Update progress
            progressTracker.incrementUnlockedChunks(playerId);
            int total = progressTracker.getUnlockedChunkCount(playerId);
            
            ChunklockPlugin.getInstance().getLogger().info("Player " + player.getName() + 
                " now has " + total + " unlocked chunks");

            // 🎉 SUCCESS: Play unlock effects
            try {
                UnlockEffectsManager.playUnlockEffects(player, state.chunk, total);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Failed to play unlock effects: " + e.getMessage());
                // Still show basic success message
                player.sendMessage(Component.text("🎉 Chunk unlocked successfully!")
                    .color(NamedTextColor.GREEN));
            }
            
            // 🔧 FIX: Safe border and hologram updates
            updateSystemsAfterUnlock(player, state.chunk);
            
            // Clean up and close
            cleanupPlayer(playerId);
            player.closeInventory();
            
            // Final success sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        } catch (Exception e) {
            // 🔧 FIX: Comprehensive error handling
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Critical error during chunk unlock for player " + player.getName(), e);
            
            player.sendMessage(Component.text("❌ An error occurred during unlock. Please try again.")
                .color(NamedTextColor.RED));
            player.sendMessage(Component.text("If this persists, contact an administrator.")
                .color(NamedTextColor.GRAY));
            
            // Play error sound
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f);
            
            cleanupPlayer(playerId);
            player.closeInventory();
        }
    }

    // 🔧 FIX: Only clean up on manual close, not during unlock process
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        UUID playerId = player.getUniqueId();
        
        // Check if this was our GUI
        Inventory activeGui = activeGuis.get(playerId);
        if (activeGui != null && activeGui.equals(event.getInventory())) {
            // Small delay to prevent cleanup during successful unlock
            Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                // Only cleanup if player still has the same GUI tracked (prevents cleanup during unlock)
                if (activeGuis.get(playerId) == activeGui) {
                    ChunklockPlugin.getInstance().getLogger().fine("Cleaning up GUI state for " + player.getName() + " after inventory close");
                    cleanupPlayer(playerId);
                }
            }, 1L);
        }
    }

    // 🔧 NEW: Centralized cleanup method
    private void cleanupPlayer(UUID playerId) {
        pending.remove(playerId);
        activeGuis.remove(playerId);
    }

    // 🔧 NEW: Separate method for safe system updates
    private void updateSystemsAfterUnlock(Player player, Chunk unlockedChunk) {
        // Update glass borders
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            if (borderManager != null) {
                borderManager.onChunkUnlocked(player, unlockedChunk);
                ChunklockPlugin.getInstance().getLogger().fine("Updated borders after unlock");
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error updating borders after unlock: " + e.getMessage());
        }
        
        // Refresh holograms
        try {
            HologramManager hologramManager = ChunklockPlugin.getInstance().getHologramManager();
            if (hologramManager != null) {
                // Immediate refresh
                hologramManager.refreshHologramsForPlayer(player);
                
                // Delayed refresh to ensure chunk state is fully updated
                Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                    if (player.isOnline()) {
                        hologramManager.refreshHologramsForPlayer(player);
                    }
                }, 5L);
                
                ChunklockPlugin.getInstance().getLogger().fine("Refreshed holograms after unlock");
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error refreshing holograms after unlock: " + e.getMessage());
        }
    }

    // 🔧 ENHANCED: Better item counting method
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
            
            // Also check off-hand
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() == material) {
                count += offHand.getAmount();
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error counting items for " + player.getName() + ": " + e.getMessage());
        }
        
        return count;
    }
}