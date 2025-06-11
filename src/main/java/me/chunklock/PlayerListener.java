package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import me.chunklock.UnlockGui;

import java.util.*;

public class PlayerListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final UnlockGui unlockGui;
    private final Map<UUID, Long> lastWarned = new HashMap<>();
    private final Random random = new Random();
    private static final long COOLDOWN_MS = 2000L;
    private static final int MAX_SPAWN_ATTEMPTS = 50;
    private static final int START_RANGE = 16;
    private static final int FALLBACK_RANGE = 10;

    public PlayerListener(ChunkLockManager chunkLockManager, PlayerProgressTracker progressTracker, PlayerDataManager playerDataManager, UnlockGui unlockGui) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
        this.unlockGui = unlockGui;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!playerDataManager.hasChunk(player.getUniqueId())) {
            assignStartingChunk(player);
        } else {
            Location savedSpawn = playerDataManager.getChunkSpawn(player.getUniqueId());
            if (savedSpawn != null) {
                player.teleport(savedSpawn);
                player.setRespawnLocation(savedSpawn, true);
                player.sendMessage("§aWelcome back! You've been returned to your starting chunk.");
            } else {
                // Fallback if saved spawn is invalid
                assignStartingChunk(player);
            }
        }
    }

    /**
     * Locate a suitable starting chunk for the player and teleport them there.
     */
    private void assignStartingChunk(Player player) {
        World world = player.getWorld();
        Location bestSpawn = null;
        int bestScore = Integer.MAX_VALUE;

        // Try to find a good starting chunk (prefer easier chunks near spawn)
        for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
            int cx = random.nextInt(START_RANGE * 2) - START_RANGE;
            int cz = random.nextInt(START_RANGE * 2) - START_RANGE;
            
            Chunk chunk = world.getChunkAt(cx, cz);
            
            // Evaluate this chunk
            ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
            
            // Prefer easier chunks for starting
            if (evaluation.difficulty == Difficulty.EASY && evaluation.score < bestScore) {
                int worldX = cx * 16 + 8;
                int worldZ = cz * 16 + 8;
                int y = world.getHighestBlockAt(worldX, worldZ).getY();
                
                // Check if it's a safe spawn location
                if (isSafeSpawnLocation(world, worldX, y, worldZ)) {
                    bestSpawn = new Location(world, worldX + 0.5, y + 1, worldZ + 0.5);
                    bestScore = evaluation.score;
                }
            }
        }

        // Fallback to any safe location if no easy chunk found
        if (bestSpawn == null) {
            for (int attempt = 0; attempt < MAX_SPAWN_ATTEMPTS; attempt++) {
                int cx = random.nextInt(FALLBACK_RANGE * 2) - FALLBACK_RANGE;
                int cz = random.nextInt(FALLBACK_RANGE * 2) - FALLBACK_RANGE;
                int worldX = cx * 16 + 8;
                int worldZ = cz * 16 + 8;
                int y = world.getHighestBlockAt(worldX, worldZ).getY();
                
                if (isSafeSpawnLocation(world, worldX, y, worldZ)) {
                    bestSpawn = new Location(world, worldX + 0.5, y + 1, worldZ + 0.5);
                    break;
                }
            }
        }

        // Final fallback to world spawn
        if (bestSpawn == null) {
            bestSpawn = world.getSpawnLocation();
        }

        // Unlock the starting chunk and set player spawn
        Chunk startChunk = bestSpawn.getChunk();
        chunkLockManager.unlockChunk(startChunk);
        
        playerDataManager.setChunk(player.getUniqueId(), bestSpawn);
        player.teleport(bestSpawn);
        player.setRespawnLocation(bestSpawn, true);
        
        ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), startChunk);
        player.sendMessage("§aYou have been assigned a starting chunk at " + startChunk.getX() + ", " + startChunk.getZ());
        player.sendMessage("§7Chunk difficulty: " + evaluation.difficulty + " | Biome: " + evaluation.biome.name());
    }

    private boolean isSafeSpawnLocation(World world, int x, int y, int z) {
        // Check if the spawn location is safe (not in water, lava, or void)
        if (y < 1 || y > world.getMaxHeight() - 10) return false;
        
        var blockAt = world.getBlockAt(x, y, z);
        var blockBelow = world.getBlockAt(x, y - 1, z);
        
        // Check for solid ground and air above
        return blockBelow.getType().isSolid() && 
               blockAt.getType().isAir() && 
               world.getBlockAt(x, y + 1, z).getType().isAir() &&
               !blockBelow.isLiquid();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo().getChunk();

        if (!from.equals(to)) {
            chunkLockManager.initializeChunk(to, event.getPlayer().getUniqueId());
            Player player = event.getPlayer();
            if (chunkLockManager.isBypassing(player)) {
                return;
            }

            if (chunkLockManager.isLocked(to)) {
                long now = System.currentTimeMillis();
                long last = lastWarned.getOrDefault(player.getUniqueId(), 0L);

                if (now - last >= COOLDOWN_MS) {
                    ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), to);
                    player.sendMessage("§cThis chunk is locked!");
                    player.sendMessage("§7Difficulty: " + evaluation.difficulty + " | Score: " + evaluation.score + " | Biome: " + evaluation.biome.name());
                    lastWarned.put(player.getUniqueId(), now);
                    unlockGui.open(player, to);
                }

                event.setCancelled(true);
            }
        }
    }
}