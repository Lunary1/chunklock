package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final Map<String, ArmorStand> activeHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playerHologramTasks = new HashMap<>();
    
    private static final double HOLOGRAM_HEIGHT_OFFSET = 3.0;
    private static final int HOLOGRAM_UPDATE_INTERVAL = 20; // 1 second
    private static final int HOLOGRAM_VIEW_DISTANCE = 32; // blocks

    public HologramManager(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    /**
     * Starts hologram display task for a player
     */
    public void startHologramDisplay(Player player) {
        stopHologramDisplay(player); // Clean up any existing task
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                updateHologramsForPlayer(player);
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 0L, HOLOGRAM_UPDATE_INTERVAL);
        
        playerHologramTasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops hologram display for a player
     */
    public void stopHologramDisplay(Player player) {
        BukkitTask task = playerHologramTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        
        // Remove holograms visible to this player
        removeHologramsForPlayer(player);
    }

    /**
     * Updates holograms around a player
     */
    private void updateHologramsForPlayer(Player player) {
        try {
            if (chunkLockManager.isBypassing(player)) {
                return; // Don't show holograms for bypassing players
            }

            Location playerLoc = player.getLocation();
            Chunk playerChunk = playerLoc.getChunk();
            
            // Check adjacent chunks for locked status
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip current chunk
                    
                    Chunk adjacentChunk = player.getWorld().getChunkAt(
                        playerChunk.getX() + dx, 
                        playerChunk.getZ() + dz
                    );
                    
                    chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                    
                    if (chunkLockManager.isLocked(adjacentChunk)) {
                        // Check if player has required items
                        var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), adjacentChunk);
                        if (biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score)) {
                            showHologramForChunk(player, adjacentChunk, evaluation);
                        } else {
                            removeHologramForChunk(player, adjacentChunk);
                        }
                    } else {
                        removeHologramForChunk(player, adjacentChunk);
                    }
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error updating holograms for " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Shows hologram for a specific chunk
     */
    private void showHologramForChunk(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation) {
        String hologramKey = getHologramKey(player, chunk);
        
        // Don't recreate if hologram already exists
        if (activeHolograms.containsKey(hologramKey)) {
            return;
        }

        try {
            Location hologramLocation = getHologramLocation(chunk);
            
            // Check distance to player
            if (player.getLocation().distance(hologramLocation) > HOLOGRAM_VIEW_DISTANCE) {
                return;
            }

            ArmorStand hologram = (ArmorStand) chunk.getWorld().spawnEntity(hologramLocation, EntityType.ARMOR_STAND);
            
            // Configure armor stand as hologram
            hologram.setVisible(false);
            hologram.setGravity(false);
            hologram.setCanPickupItems(false);
            hologram.setInvulnerable(true);
            hologram.setMarker(true);
            hologram.setSmall(true);
            
            // Set hologram text
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
            
            Component hologramText = Component.text()
                .append(Component.text("🔓 UNLOCKABLE", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(biomeName, NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("Required: " + requirement.amount() + "x", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(requirement.material().name().replace("_", " "), NamedTextColor.AQUA))
                .build();
            
            hologram.customName(hologramText);
            hologram.setCustomNameVisible(true);
            
            activeHolograms.put(hologramKey, hologram);
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error creating hologram: " + e.getMessage());
        }
    }

    /**
     * Removes hologram for a specific chunk
     */
    private void removeHologramForChunk(Player player, Chunk chunk) {
        String hologramKey = getHologramKey(player, chunk);
        ArmorStand hologram = activeHolograms.remove(hologramKey);
        
        if (hologram != null && hologram.isValid()) {
            hologram.remove();
        }
    }

    /**
     * Removes all holograms for a player
     */
    private void removeHologramsForPlayer(Player player) {
        activeHolograms.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(player.getUniqueId().toString())) {
                ArmorStand hologram = entry.getValue();
                if (hologram != null && hologram.isValid()) {
                    hologram.remove();
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Gets hologram location (center of chunk, elevated)
     */
    private Location getHologramLocation(Chunk chunk) {
        World world = chunk.getWorld();
        int centerX = chunk.getX() * 16 + 8;
        int centerZ = chunk.getZ() * 16 + 8;
        int centerY = world.getHighestBlockYAt(centerX, centerZ) + (int)HOLOGRAM_HEIGHT_OFFSET;
        
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    /**
     * Generates unique key for player-chunk hologram
     */
    private String getHologramKey(Player player, Chunk chunk) {
        return player.getUniqueId() + "_" + chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
    }

    /**
     * Cleanup all holograms
     */
    public void cleanup() {
        // Cancel all tasks
        for (BukkitTask task : playerHologramTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        playerHologramTasks.clear();
        
        // Remove all holograms
        for (ArmorStand hologram : activeHolograms.values()) {
            if (hologram != null && hologram.isValid()) {
                hologram.remove();
            }
        }
        activeHolograms.clear();
    }
}