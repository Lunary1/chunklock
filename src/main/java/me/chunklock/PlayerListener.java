package me.chunklock;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

public class PlayerListener implements Listener {

    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final Map<UUID, Long> lastWarned = new HashMap<>();
    private final Random random = new Random();
    private static final long COOLDOWN_MS = 2000L;

    public PlayerListener(ChunkLockManager chunkLockManager, PlayerProgressTracker progressTracker, PlayerDataManager playerDataManager) {
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!playerDataManager.hasChunk(player.getUniqueId())) {
            World world = player.getWorld();
            int radius = 16;
            int cx = random.nextInt(radius * 2) - radius;
            int cz = random.nextInt(radius * 2) - radius;

            Chunk chunk = world.getChunkAt(cx, cz);
            chunkLockManager.unlockChunk(chunk);

            Location spawn = new Location(world, cx * 16 + 8, world.getHighestBlockYAt(cx * 16 + 8, cz * 16 + 8) + 2, cz * 16 + 8);
            playerDataManager.setChunk(player.getUniqueId(), spawn);
            player.teleport(spawn);
            player.setRespawnLocation(spawn, true);
            player.sendMessage("§aYou have been assigned a starting chunk at " + cx + ", " + cz);
        } else {
            Location savedSpawn = playerDataManager.getChunkSpawn(player.getUniqueId());
            if (savedSpawn != null) {
                player.teleport(savedSpawn);
                player.setRespawnLocation(savedSpawn, true);
                player.sendMessage("§aWelcome back! You've been returned to your starting chunk.");
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Chunk from = event.getFrom().getChunk();
        Chunk to = event.getTo().getChunk();

        if (!from.equals(to)) {
            chunkLockManager.initializeChunk(to);
            if (chunkLockManager.isLocked(to)) {
                Player player = event.getPlayer();
                long now = System.currentTimeMillis();
                long last = lastWarned.getOrDefault(player.getUniqueId(), 0L);

                if (now - last >= COOLDOWN_MS) {
                    player.sendMessage("§cThis chunk is locked! Difficulty: " + chunkLockManager.getDifficulty(to));
                    lastWarned.put(player.getUniqueId(), now);
                }

                event.setCancelled(true);
            }
        }
    }
}
