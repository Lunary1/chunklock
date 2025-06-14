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
    private final ChunkOwnershipManager ownershipManager;

    private static class PendingUnlock {
        final Chunk chunk;
        final Biome biome;
        final BiomeUnlockRegistry.UnlockRequirement requirement;
        final boolean isOverclaim;
        final double overclaimMultiplier;
        final BiomeUnlockRegistry.UnlockRequirement overclaimRequirement;

        PendingUnlock(Chunk chunk, Biome biome, BiomeUnlockRegistry.UnlockRequirement requirement, 
                     boolean isOverclaim, double overclaimMultiplier, BiomeUnlockRegistry.UnlockRequirement overclaimRequirement) {
            this.chunk = chunk;
            this.biome = biome;
            this.requirement = requirement;
            this.isOverclaim = isOverclaim;
            this.overclaimMultiplier = overclaimMultiplier;
            this.overclaimRequirement = overclaimRequirement;
        }
    }

    private final Map<UUID, PendingUnlock> pending = new HashMap<>();
    private static final String GUI_TITLE = "Unlock Chunk";

    public UnlockGui(ChunkLockManager chunkLockManager,
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker,
                     ChunkOwnershipManager ownershipManager) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
        this.ownershipManager = ownershipManager;
    }

    public void open(Player player, Chunk chunk) {
        var eval = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
        Biome biome = eval.biome;
        var baseRequirement = biomeUnlockRegistry.calculateRequirement(player, biome, eval.score);
        
        // Check if this is an overclaim scenario
        ChunkOwnershipManager.ChunkOwnership ownership = ownershipManager.getChunkOwnership(chunk);
        boolean isOverclaim = ownership != null && !ownership.getOwnerId().equals(player.getUniqueId());
        
        double overclaimMultiplier = 1.0;
        BiomeUnlockRegistry.UnlockRequirement overclaimRequirement = baseRequirement;
        
        if (isOverclaim && ownershipManager.isOverclaimEnabled()) {
            // Check if overclaim is allowed
            var overclaimResult = ownershipManager.attemptOverclaim(chunk, player);
            if (!overclaimResult.isAllowed()) {
                player.sendMessage(Component.text("Cannot overclaim: " + overclaimResult.getMessage()).color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
            
            overclaimMultiplier = ownershipManager.calculateOverclaimMultiplier(chunk, player.getUniqueId());
            int overclaimAmount = (int) Math.ceil(baseRequirement.amount() * overclaimMultiplier);
            overclaimRequirement = new BiomeUnlockRegistry.UnlockRequirement(baseRequirement.material(), overclaimAmount);
        }

        // Create GUI based on unlock type
        Inventory inv = createUnlockInventory(player, chunk, eval, baseRequirement, isOverclaim, overclaimMultiplier, overclaimRequirement, ownership);
        
        pending.put(player.getUniqueId(), new PendingUnlock(chunk, biome, baseRequirement, isOverclaim, overclaimMultiplier, overclaimRequirement));
        player.openInventory(inv);
    }

    private Inventory createUnlockInventory(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData eval,
                                          BiomeUnlockRegistry.UnlockRequirement baseRequirement, boolean isOverclaim,
                                          double overclaimMultiplier, BiomeUnlockRegistry.UnlockRequirement actualRequirement,
                                          ChunkOwnershipManager.ChunkOwnership ownership) {
        
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE)); // 3 rows for more info
        
        // Row 1: Chunk Information
        addChunkInfoItems(inv, chunk, eval, ownership);
        
        // Row 2: Requirements and Costs
        addRequirementItems(inv, baseRequirement, actualRequirement, isOverclaim, overclaimMultiplier);
        
        // Row 3: Action Buttons
        addActionButtons(inv, player, isOverclaim, actualRequirement);
        
        return inv;
    }

    private void addChunkInfoItems(Inventory inv, Chunk chunk, ChunkEvaluator.ChunkValueData eval, ChunkOwnershipManager.ChunkOwnership ownership) {
        // Chunk location info
        ItemStack locationInfo = new ItemStack(Material.MAP);
        ItemMeta meta = locationInfo.getItemMeta();
        meta.displayName(Component.text("Chunk Information").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Location: " + chunk.getX() + ", " + chunk.getZ()).color(NamedTextColor.WHITE));
        lore.add(Component.text("Biome: " + BiomeUnlockRegistry.getBiomeDisplayName(eval.biome)).color(NamedTextColor.GREEN));
        lore.add(Component.text("Difficulty: " + eval.difficulty).color(getDifficultyColor(eval.difficulty)));
        lore.add(Component.text("Score: " + eval.score).color(NamedTextColor.GRAY));
        meta.lore(lore);
        
        locationInfo.setItemMeta(meta);
        inv.setItem(1, locationInfo);

        // Ownership info
        ItemStack ownershipInfo = new ItemStack(ownership != null ? Material.PLAYER_HEAD : Material.BARRIER);
        meta = ownershipInfo.getItemMeta();
        
        if (ownership != null) {
            meta.displayName(Component.text("Owned by " + ownership.getOwnerName()).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            
            lore = new ArrayList<>();
            lore.add(Component.text("Owner: " + ownership.getOwnerName()).color(NamedTextColor.WHITE));
            
            if (ownership.isOverclaimed()) {
                lore.add(Component.text("⚠ Previously Overclaimed").color(NamedTextColor.RED));
            }
            
            long hoursOwned = (System.currentTimeMillis() - ownership.getUnlockTime()) / (60 * 60 * 1000L);
            lore.add(Component.text("Owned for: " + hoursOwned + " hours").color(NamedTextColor.GRAY));
            
        } else {
            meta.displayName(Component.text("Unowned Chunk").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore = new ArrayList<>();
            lore.add(Component.text("This chunk has no owner").color(NamedTextColor.WHITE));
        }
        
        meta.lore(lore);
        ownershipInfo.setItemMeta(meta);
        inv.setItem(7, ownershipInfo);
    }

    private void addRequirementItems(Inventory inv, BiomeUnlockRegistry.UnlockRequirement baseRequirement,
                                   BiomeUnlockRegistry.UnlockRequirement actualRequirement, boolean isOverclaim,
                                   double overclaimMultiplier) {
        
        // Base requirement display
        ItemStack baseReq = new ItemStack(baseRequirement.material(), Math.min(baseRequirement.amount(), 64));
        ItemMeta meta = baseReq.getItemMeta();
        meta.displayName(Component.text("Base Cost").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(baseRequirement.amount() + "x " + baseRequirement.material().name().replace("_", " ")).color(NamedTextColor.WHITE));
        meta.lore(lore);
        baseReq.setItemMeta(meta);
        inv.setItem(10, baseReq);
        
        if (isOverclaim) {
            // Overclaim multiplier display
            ItemStack multiplierItem = new ItemStack(Material.REDSTONE);
            meta = multiplierItem.getItemMeta();
            meta.displayName(Component.text("Overclaim Multiplier").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            
            lore = new ArrayList<>();
            lore.add(Component.text("x" + String.format("%.1f", overclaimMultiplier)).color(NamedTextColor.RED));
            lore.add(Component.text("Taking from another player").color(NamedTextColor.GRAY));
            lore.add(Component.text("costs more resources!").color(NamedTextColor.GRAY));
            meta.lore(lore);
            multiplierItem.setItemMeta(meta);
            inv.setItem(12, multiplierItem);
            
            // Arrow
            ItemStack arrow = new ItemStack(Material.ARROW);
            meta = arrow.getItemMeta();
            meta.displayName(Component.text("=").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            arrow.setItemMeta(meta);
            inv.setItem(13, arrow);
        }
        
        // Final cost display
        ItemStack finalCost = new ItemStack(actualRequirement.material(), Math.min(actualRequirement.amount(), 64));
        meta = finalCost.getItemMeta();
        meta.displayName(Component.text(isOverclaim ? "Overclaim Cost" : "Total Cost").color(isOverclaim ? NamedTextColor.RED : NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        
        lore = new ArrayList<>();
        lore.add(Component.text(actualRequirement.amount() + "x " + actualRequirement.material().name().replace("_", " ")).color(NamedTextColor.WHITE));
        if (isOverclaim) {
            lore.add(Component.text("⚠ Overclaim Price!").color(NamedTextColor.RED));
        }
        meta.lore(lore);
        finalCost.setItemMeta(meta);
        inv.setItem(isOverclaim ? 16 : 13, finalCost);
    }

    private void addActionButtons(Inventory inv, Player player, boolean isOverclaim, BiomeUnlockRegistry.UnlockRequirement actualRequirement) {
        // Check if player has required items
        boolean hasItems = player.getInventory().containsAtLeast(new ItemStack(actualRequirement.material()), actualRequirement.amount());
        
        // Cancel button
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta meta = cancel.getItemMeta();
        meta.displayName(Component.text("Cancel").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        cancel.setItemMeta(meta);
        inv.setItem(18, cancel);
        
        // Confirm button
        ItemStack confirm = new ItemStack(hasItems ? Material.EMERALD_BLOCK : Material.RED_CONCRETE);
        meta = confirm.getItemMeta();
        
        if (hasItems) {
            meta.displayName(Component.text(isOverclaim ? "Confirm Overclaim" : "Confirm Unlock").color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Click to proceed").color(NamedTextColor.WHITE));
            if (isOverclaim) {
                lore.add(Component.text("⚠ This will take the chunk").color(NamedTextColor.YELLOW));
                lore.add(Component.text("from another player!").color(NamedTextColor.YELLOW));
                
                // Show remaining overclaims
                int remaining = ownershipManager.getRemainingOverclaims(player.getUniqueId());
                lore.add(Component.text("Remaining today: " + remaining).color(NamedTextColor.GRAY));
            }
            meta.lore(lore);
        } else {
            meta.displayName(Component.text("Insufficient Items").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("You need more items!").color(NamedTextColor.WHITE));
            int needed = actualRequirement.amount();
            int has = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == actualRequirement.material()) {
                    has += item.getAmount();
                }
            }
            lore.add(Component.text("Have: " + has + " / Need: " + needed).color(NamedTextColor.GRAY));
            meta.lore(lore);
        }
        
        confirm.setItemMeta(meta);
        inv.setItem(26, confirm);
        
        // Info button
        ItemStack info = new ItemStack(Material.BOOK);
        meta = info.getItemMeta();
        meta.displayName(Component.text("Overclaim Info").color(NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        if (ownershipManager.isOverclaimEnabled()) {
            lore.add(Component.text("Overclaiming lets you take").color(NamedTextColor.WHITE));
            lore.add(Component.text("chunks from other players").color(NamedTextColor.WHITE));
            lore.add(Component.text("at increased cost.").color(NamedTextColor.WHITE));
            lore.add(Component.empty());
            lore.add(Component.text("Protection: New chunks are").color(NamedTextColor.GRAY));
            lore.add(Component.text("protected for 24 hours.").color(NamedTextColor.GRAY));
            lore.add(Component.empty());
            int remaining = ownershipManager.getRemainingOverclaims(player.getUniqueId());
            lore.add(Component.text("Daily limit: " + remaining + " remaining").color(NamedTextColor.YELLOW));
        } else {
            lore.add(Component.text("Overclaiming is disabled").color(NamedTextColor.RED));
            lore.add(Component.text("on this server.").color(NamedTextColor.RED));
        }
        meta.lore(lore);
        info.setItemMeta(meta);
        inv.setItem(22, info);
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
        
        int slot = event.getRawSlot();
        
        // Handle cancel button
        if (slot == 18) {
            player.closeInventory();
            player.sendMessage(Component.text("Cancelled chunk unlock.").color(NamedTextColor.GRAY));
            return;
        }
        
        // Handle confirm button
        if (slot != 26) return;

        PendingUnlock state = pending.get(player.getUniqueId());
        if (state == null) return;
        
        // Verify chunk is still locked
        if (!chunkLockManager.isLocked(state.chunk)) {
            player.sendMessage(Component.text("Chunk is already unlocked.").color(NamedTextColor.GRAY));
            player.closeInventory();
            return;
        }

        // Check if this is an overclaim that's no longer valid
        if (state.isOverclaim) {
            var overclaimResult = ownershipManager.attemptOverclaim(state.chunk, player);
            if (!overclaimResult.isAllowed()) {
                player.sendMessage(Component.text("Overclaim no longer allowed: " + overclaimResult.getMessage()).color(NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                player.closeInventory();
                return;
            }
        }

        // Verify player still has required items
        ItemStack requiredStack = new ItemStack(state.overclaimRequirement.material(), state.overclaimRequirement.amount());
        if (!player.getInventory().containsAtLeast(requiredStack, state.overclaimRequirement.amount())) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            player.sendMessage(Component.text("Missing required items: " + state.overclaimRequirement.amount() + " " + 
                state.overclaimRequirement.material().name()).color(NamedTextColor.RED));
            return;
        }

        // Remove items and unlock chunk
        player.getInventory().removeItem(requiredStack);
        chunkLockManager.unlockChunk(state.chunk);
        
        // Handle ownership
        if (state.isOverclaim) {
            ownershipManager.completeOverclaim(state.chunk, player.getUniqueId(), player.getName());
            
            // Special overclaim effects and messages
            player.sendMessage(Component.text("⚔ Chunk Overclaimed! ⚔").color(NamedTextColor.RED).decoration(TextDecoration.BOLD, true));
            player.sendMessage(Component.text("You have taken chunk " + state.chunk.getX() + "," + state.chunk.getZ() + 
                " from another player!").color(NamedTextColor.YELLOW));
            
            // Play dramatic overclaim sound
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f);
            
        } else {
            ownershipManager.setChunkOwner(state.chunk, player.getUniqueId(), player.getName());
        }
        
        progressTracker.incrementUnlockedChunks(player.getUniqueId());
        int total = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        
        // Play unlock effects (modified for overclaim)
        if (state.isOverclaim) {
            UnlockEffectsManager.playOverclaimEffects(player, state.chunk, total);
        } else {
            UnlockEffectsManager.playUnlockEffects(player, state.chunk, total);
        }
        
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}