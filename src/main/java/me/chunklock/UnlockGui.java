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

public class UnlockGui implements Listener {
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;

    private static class PendingUnlock {
        final Chunk chunk;
        final Biome biome;
        final BiomeUnlockRegistry.UnlockRequirement requirement;

        PendingUnlock(Chunk chunk, Biome biome, BiomeUnlockRegistry.UnlockRequirement requirement) {
            this.chunk = chunk;
            this.biome = biome;
            this.requirement = requirement;
        }
    }

    private final Map<UUID, PendingUnlock> pending = new HashMap<>();
    private static final String GUI_TITLE = "Unlock Chunk";

    public UnlockGui(ChunkLockManager chunkLockManager,
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
    }

    public void open(Player player, Chunk chunk) {
        var eval = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
        Biome biome = eval.biome;
        var requirement = biomeUnlockRegistry.calculateRequirement(player, biome, eval.score);

        // Create larger inventory for better display
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE));

        // Add chunk info item
        addChunkInfoItem(inv, chunk, eval);
        
        // Add requirement display (improved for large amounts)
        addRequirementItems(inv, player, requirement);

        // Add unlock button
        addUnlockButton(inv, player, requirement);

        pending.put(player.getUniqueId(), new PendingUnlock(chunk, biome, requirement));
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

    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        event.setCancelled(true);
        
        // Only the unlock button (slot 22) should be clickable
        if (event.getRawSlot() != 22) return;

        PendingUnlock state = pending.get(player.getUniqueId());
        if (state == null) return;
        if (!chunkLockManager.isLocked(state.chunk)) {
            player.sendMessage(Component.text("Chunk already unlocked.").color(NamedTextColor.GRAY));
            return;
        }

        ItemStack requiredStack = new ItemStack(state.requirement.material(), state.requirement.amount());
        if (!player.getInventory().containsAtLeast(requiredStack, state.requirement.amount())) {
            // Play error sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            
            int playerHas = countPlayerItems(player, state.requirement.material());
            int needed = state.requirement.amount() - playerHas;
            
            player.sendMessage(Component.text("✗ Missing required items!")
                .color(NamedTextColor.RED));
            player.sendMessage(Component.text("Need " + needed + " more " + formatMaterialName(state.requirement.material()))
                .color(NamedTextColor.YELLOW));
            return;
        }

        // Remove items and unlock chunk
        player.getInventory().removeItem(requiredStack);
        chunkLockManager.unlockChunk(state.chunk);
        ChunklockPlugin.getInstance().getEnhancedTeamManager().recordChunkUnlock(player.getUniqueId(), BiomeUnlockRegistry.getBiomeDisplayName(state.biome));
        progressTracker.incrementUnlockedChunks(player.getUniqueId());

        int total = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        
        // Play unlock effects!
        UnlockEffectsManager.playUnlockEffects(player, state.chunk, total);
        
        // Update glass borders after unlocking
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            if (borderManager != null) {
                borderManager.onChunkUnlocked(player, state.chunk);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error updating borders after unlock: " + e.getMessage());
        }
        
        // Refresh holograms after unlocking
        try {
            HologramManager hologramManager = ChunklockPlugin.getInstance().getHologramManager();
            if (hologramManager != null) {
                // Immediate refresh
                hologramManager.refreshHologramsForPlayer(player);
                // Also refresh with a small delay to ensure chunk state is fully updated
                Bukkit.getScheduler().runTaskLater(ChunklockPlugin.getInstance(), () -> {
                    hologramManager.refreshHologramsForPlayer(player);
                }, 5L);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error refreshing holograms after unlock: " + e.getMessage());
        }
        
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}