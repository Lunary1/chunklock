package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.HashMap;
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

        Inventory inv = Bukkit.createInventory(null, 9, Component.text(GUI_TITLE));

        ItemStack stack = new ItemStack(requirement.material(), requirement.amount());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(requirement.material().name()).color(NamedTextColor.YELLOW));
        stack.setItemMeta(meta);
        inv.setItem(4, stack);

        ItemStack unlock = new ItemStack(Material.EMERALD_BLOCK);
        meta = unlock.getItemMeta();
        meta.displayName(Component.text("Click to Unlock").color(NamedTextColor.GREEN));
        unlock.setItemMeta(meta);
        inv.setItem(8, unlock);

        pending.put(player.getUniqueId(), new PendingUnlock(chunk, biome, requirement));
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        event.setCancelled(true);
        if (event.getRawSlot() != 8) return;

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
            player.sendMessage(Component.text("Missing required items: " + state.requirement.amount() + " " + state.requirement.material().name()).color(NamedTextColor.RED));
            return;
        }

        // Remove items and unlock chunk
        player.getInventory().removeItem(requiredStack);
        chunkLockManager.unlockChunk(state.chunk);
        progressTracker.incrementUnlockedChunks(player.getUniqueId());

        int total = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        
        // Play unlock effects!
        UnlockEffectsManager.playUnlockEffects(player, state.chunk, total);
        
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}