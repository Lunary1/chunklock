package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class TickTask extends BukkitRunnable {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private int tickCounter = 0;
    
    private static final int PARTICLE_UPDATE_INTERVAL = 5; // Every 5 ticks (0.25 seconds)
    private static final int PARTICLES_PER_SIDE = 8; // Particles per chunk border side
    private static final double PARTICLE_HEIGHT_RANGE = 5.0; // Vertical range for particles

    public TickTask(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    @Override
    public void run() {
        tickCounter++;
        
        // Update particles every few ticks for performance
        if (tickCounter % PARTICLE_UPDATE_INTERVAL != 0) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (chunkLockManager.isBypassing(player)) {
                continue;
            }
            
            try {
                updatePlayerEffects(player);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Error updating effects for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void updatePlayerEffects(Player player) {
        Chunk center = player.getLocation().getChunk();
        chunkLockManager.initializeChunk(center, player.getUniqueId());

        // Check adjacent chunks
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk chunk = center.getWorld().getChunkAt(center.getX() + dx, center.getZ() + dz);
                chunkLockManager.initializeChunk(chunk, player.getUniqueId());
                
                if (chunkLockManager.isLocked(chunk)) {
                    var eval = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                    boolean canUnlock = biomeUnlockRegistry.hasRequiredItems(player, eval.biome, eval.score);
                    
                    drawEnhancedChunkBorder(player, chunk, eval.difficulty, canUnlock);
                }
            }
        }
    }

    private void drawEnhancedChunkBorder(Player player, Chunk chunk, Difficulty difficulty, boolean canUnlock) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        // Determine particle type and color based on difficulty and unlock status
        Particle particleType;
        Color color = null;
        
        if (canUnlock) {
            particleType = Particle.HAPPY_VILLAGER;
        } else {
            switch (difficulty) {
                case EASY -> {
                    particleType = Particle.DUST;
                    color = Color.GREEN;
                }
                case NORMAL -> {
                    particleType = Particle.DUST;
                    color = Color.YELLOW;
                }
                case HARD -> {
                    particleType = Particle.DUST;
                    color = Color.RED;
                }
                case IMPOSSIBLE -> {
                    particleType = Particle.DUST;
                    color = Color.PURPLE;
                }
                default -> {
                    particleType = Particle.SMOKE; // Fallback particle
                }
            }
        }

        // Calculate Y range around player
        int playerY = player.getLocation().getBlockY();
        int minY = Math.max(world.getMinHeight(), playerY - (int)PARTICLE_HEIGHT_RANGE);
        int maxY = Math.min(world.getMaxHeight() - 1, playerY + (int)PARTICLE_HEIGHT_RANGE);

        // Draw animated border
        double animationOffset = (tickCounter * 0.1) % 16; // Moving animation
        
        for (int y = minY; y <= maxY; y += 2) {
            // North and South borders
            for (int i = 0; i < PARTICLES_PER_SIDE; i++) {
                double offset = (16.0 / PARTICLES_PER_SIDE) * i + animationOffset;
                if (offset >= 16) offset -= 16;
                
                spawnBorderParticle(player, particleType, color, chunkX + offset, y, chunkZ, canUnlock);
                spawnBorderParticle(player, particleType, color, chunkX + offset, y, chunkZ + 15, canUnlock);
            }
            
            // East and West borders
            for (int i = 0; i < PARTICLES_PER_SIDE; i++) {
                double offset = (16.0 / PARTICLES_PER_SIDE) * i + animationOffset;
                if (offset >= 16) offset -= 16;
                
                spawnBorderParticle(player, particleType, color, chunkX, y, chunkZ + offset, canUnlock);
                spawnBorderParticle(player, particleType, color, chunkX + 15, y, chunkZ + offset, canUnlock);
            }
        }
    }

    private void spawnBorderParticle(Player player, Particle particleType, Color color, 
                                   double x, double y, double z, boolean canUnlock) {
        try {
            if (particleType == Particle.DUST && color != null) {
                Particle.DustOptions dustOptions = new Particle.DustOptions(color, canUnlock ? 2.0f : 1.0f);
                player.spawnParticle(particleType, x, y, z, 1, 0, 0, 0, 0, dustOptions);
            } else {
                int count = canUnlock ? 2 : 1;
                player.spawnParticle(particleType, x, y, z, count, 0.1, 0.1, 0.1, 0);
            }
        } catch (Exception e) {
            // Silently ignore particle errors to prevent spam
        }
    }
}