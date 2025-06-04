package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TickTask extends BukkitRunnable {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;

    public TickTask(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Chunk center = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(center);
            maybeDrawBorder(player, center);

            for (Chunk adjacent : getAdjacentChunks(center)) {
                chunkLockManager.initializeChunk(adjacent);
                maybeDrawBorder(player, adjacent);
            }
        }
    }

    private Chunk[] getAdjacentChunks(Chunk center) {
        return new Chunk[] {
            center.getWorld().getChunkAt(center.getX() + 1, center.getZ()),
            center.getWorld().getChunkAt(center.getX() - 1, center.getZ()),
            center.getWorld().getChunkAt(center.getX(), center.getZ() + 1),
            center.getWorld().getChunkAt(center.getX(), center.getZ() - 1)
        };
    }

    private void maybeDrawBorder(Player player, Chunk chunk) {
        if (!chunkLockManager.isLocked(chunk)) return;

        // Check if player is eligible to unlock this chunk based on biome
        Biome biome = chunk.getBlock(8, player.getLocation().getBlockY(), 8).getBiome(); // sample center of chunk
        if (!biomeUnlockRegistry.hasRequiredItems(player, biome)) return;

        drawChunkBorder(player, chunk);
    }

    private void drawChunkBorder(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        int minY = Math.max(world.getMinHeight(), player.getLocation().getBlockY() - 10);
        int maxY = Math.min(world.getMaxHeight(), player.getLocation().getBlockY() + 10);

        for (int y = minY; y <= maxY; y += 2) {
            for (int i = 0; i < 16; i += 2) {
                player.spawnParticle(Particle.PORTAL, chunkX + i, y, chunkZ, 1);
                player.spawnParticle(Particle.PORTAL, chunkX + i, y, chunkZ + 15, 1);
                player.spawnParticle(Particle.PORTAL, chunkX, y, chunkZ + i, 1);
                player.spawnParticle(Particle.PORTAL, chunkX + 15, y, chunkZ + i, 1);
            }
        }
    }
}
