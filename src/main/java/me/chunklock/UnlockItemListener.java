package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() != Material.NETHER_STAR) return;

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
        var eval = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
        var requirement = biomeUnlockRegistry.calculateRequirement(player, biome, eval.score);

        ItemStack requiredStack = new ItemStack(requirement.material(), requirement.amount());
        if (!player.getInventory().containsAtLeast(requiredStack, requirement.amount())) {
            // Use helper method instead of deprecated name()
            String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(biome);
            player.sendMessage(Component.text("You do not have the required items to unlock this biome: " + biomeName).color(NamedTextColor.RED));
            player.sendMessage(Component.text("Required: " + requirement.amount() + " " + requirement.material().name()).color(NamedTextColor.YELLOW));
            return;
        }

        player.getInventory().removeItem(requiredStack);
        chunkLockManager.unlockChunk(chunk);
        progressTracker.incrementUnlockedChunks(player.getUniqueId());

        int totalUnlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        player.sendMessage(Component.text("Chunk unlocked! Total unlocked chunks: " + totalUnlocked).color(NamedTextColor.GREEN));
    }
}