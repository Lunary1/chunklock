package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UnlockGui implements Listener {
    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;

    private final Map<UUID, Chunk> pending = new HashMap<>();

    private static final String GUI_TITLE = "Unlock Chunk";

    public UnlockGui(ChunkLockManager chunkLockManager,
                     BiomeUnlockRegistry biomeUnlockRegistry,
                     PlayerProgressTracker progressTracker) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
    }

    public void open(Player player, Chunk chunk) {
        Biome biome = chunk.getBlock(8, player.getLocation().getBlockY(), 8).getBiome();
        List<Material> items = biomeUnlockRegistry.getRequiredItems(biome);

        Inventory inv = Bukkit.createInventory(null, 9, Component.text(GUI_TITLE));

        int slot = 0;
        for (Material mat : items) {
            ItemStack stack = new ItemStack(mat);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text(mat.name()).color(NamedTextColor.YELLOW));
            stack.setItemMeta(meta);
            inv.setItem(slot++, stack);
        }

        ItemStack unlock = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = unlock.getItemMeta();
        meta.displayName(Component.text("Click to Unlock").color(NamedTextColor.GREEN));
        unlock.setItemMeta(meta);
        inv.setItem(8, unlock);

        pending.put(player.getUniqueId(), chunk);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        event.setCancelled(true);
        if (event.getRawSlot() != 8) return;

        Chunk chunk = pending.get(player.getUniqueId());
        if (chunk == null) return;
        if (!chunkLockManager.isLocked(chunk)) {
            player.sendMessage(Component.text("Chunk already unlocked.").color(NamedTextColor.GRAY));
            return;
        }

        Biome biome = chunk.getBlock(8, player.getLocation().getBlockY(), 8).getBiome();
        if (!biomeUnlockRegistry.hasRequiredItems(player, biome)) {
            player.sendMessage(Component.text("Missing required items: " + biomeUnlockRegistry.getRequiredItems(biome)).color(NamedTextColor.RED));
            return;
        }

        biomeUnlockRegistry.consumeRequiredItem(player, biome);
        chunkLockManager.unlockChunk(chunk);
        progressTracker.incrementUnlockedChunks(player.getUniqueId());

        int total = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        player.sendMessage(Component.text("Chunk unlocked! Total unlocked chunks: " + total).color(NamedTextColor.GREEN));
        player.closeInventory();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
