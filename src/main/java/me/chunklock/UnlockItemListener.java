package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class UnlockItemListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerProgressTracker progressTracker;

    public UnlockItemListener(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry, PlayerProgressTracker progressTracker) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.progressTracker = progressTracker;
    }

    @EventHandler
    public void onUseUnlockItem(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Only allow right-click with item
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        // Check if player is holding an unlock item (optional)
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() != Material.NETHER_STAR) return;

        // Get the block player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage(Component.text("No valid block in sight to unlock a chunk.").color(NamedTextColor.RED));
            return;
        }

        Chunk chunk = targetBlock.getChunk();

        if (!chunkLockManager.isLocked(chunk)) {
            player.sendMessage(Component.text("This chunk is already unlocked.").color(NamedTextColor.GRAY));
            return;
        }

        Biome biome = targetBlock.getBiome();
        if (!biomeUnlockRegistry.hasRequiredItems(player, biome)) {
            player.sendMessage(Component.text("You do not have the required items to unlock this biome: " + biome.name()).color(NamedTextColor.RED));
            player.sendMessage(Component.text("Required items: " + biomeUnlockRegistry.getRequiredItems(biome)).color(NamedTextColor.YELLOW));
            return;
        }

        // Consume the required item(s)
        biomeUnlockRegistry.consumeRequiredItem(player, biome);

        // Unlock the chunk and track progress
        chunkLockManager.unlockChunk(chunk);
        progressTracker.incrementUnlockedChunks(player.getUniqueId());

        int totalUnlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        player.sendMessage(Component.text("Chunk unlocked! Total unlocked chunks: " + totalUnlocked).color(NamedTextColor.GREEN));
    }
}
