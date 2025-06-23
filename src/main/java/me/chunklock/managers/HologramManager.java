package me.chunklock.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.models.Difficulty;
import me.chunklock.util.ChunkUtils;
import me.chunklock.ChunklockPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HologramManager {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final Map<String, ArmorStand> activeHolograms = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> playerHologramTasks = new HashMap<>();

    private static final double HOLOGRAM_HEIGHT_OFFSET = 3.0;
    private static final int HOLOGRAM_UPDATE_INTERVAL = 20; // 1 second
    private static final int HOLOGRAM_VIEW_DISTANCE = 48; // blocks (3 chunks)

    private enum WallSide {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        final int dx;
        final int dz;

        WallSide(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    public HologramManager(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
    }

    /**
     * Starts hologram display task for a player
     */
    public void startHologramDisplay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        stopHologramDisplay(player); // Clean up any existing task
        
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                
                try {
                    updateHologramsForPlayer(player);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                        "Error updating holograms for " + player.getName(), e);
                }
            }
        }.runTaskTimer(ChunklockPlugin.getInstance(), 0L, HOLOGRAM_UPDATE_INTERVAL);
        
        playerHologramTasks.put(player.getUniqueId(), task);
        
        ChunklockPlugin.getInstance().getLogger().fine("Started hologram display for " + player.getName());
    }

    /**
     * Stops hologram display for a player
     */
    public void stopHologramDisplay(Player player) {
        if (player == null) return;
        
        BukkitTask task = playerHologramTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        
        // Remove holograms visible to this player
        removeHologramsForPlayer(player);
        
        ChunklockPlugin.getInstance().getLogger().fine("Stopped hologram display for " + player.getName());
    }

    /**
     * Updates holograms around a player - shows holograms for ALL locked adjacent chunks
     */
private void updateHologramsForPlayer(Player player) {
        try {
            if (chunkLockManager.isBypassing(player)) {
                // Remove all holograms for bypassing players
                removeHologramsForPlayer(player);
                return;
            }

            Location playerLoc = player.getLocation();
            Chunk playerChunk = playerLoc.getChunk();
            
            // Track which chunks should have holograms
            Set<String> chunksWithHolograms = new HashSet<>();
            
            // Check all chunks in a 3x3 grid around the player
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    try {
                        Chunk checkChunk = player.getWorld().getChunkAt(
                            playerChunk.getX() + dx, 
                            playerChunk.getZ() + dz
                        );
                        
                        // Initialize chunk if needed
                        chunkLockManager.initializeChunk(checkChunk, player.getUniqueId());
                        
                        String chunkKey = getChunkKey(checkChunk);

                        if (chunkLockManager.isLocked(checkChunk)) {
                            Map<WallSide, Location> locs = getWallHologramLocations(player, checkChunk);
                            if (!locs.isEmpty()) {
                                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), checkChunk);
                                showHologramForChunk(player, checkChunk, evaluation, locs);
                                chunksWithHolograms.add(chunkKey);
                            } else {
                                removeHologramForChunk(player, checkChunk);
                            }
                        } else {
                            // Chunk is unlocked - definitely remove any hologram
                            removeHologramForChunk(player, checkChunk);
                            ChunklockPlugin.getInstance().getLogger().fine(
                                "Removed hologram for unlocked chunk " + checkChunk.getX() + "," + checkChunk.getZ() + " for player " + player.getName());
                        }
                    } catch (Exception e) {
                        ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                            "Error checking chunk at offset " + dx + "," + dz + " for player " + player.getName(), e);
                    }
                }
            }
            
            // FIX: Additional cleanup - remove any remaining holograms that shouldn't exist
            String playerPrefix = player.getUniqueId().toString() + "_";
            activeHolograms.entrySet().removeIf(entry -> {
                if (entry.getKey().startsWith(playerPrefix)) {
                    // Extract chunk coordinates from hologram key (format: playerUUID_world_x_z)
                    String[] parts = entry.getKey().split("_");
                    if (parts.length >= 4) {
                        try {
                            String worldName = parts[1];
                            int chunkX = Integer.parseInt(parts[2]);
                            int chunkZ = Integer.parseInt(parts[3]);
                            String chunkKey = worldName + ":" + chunkX + ":" + chunkZ;
                            
                            // If this chunk should not have a hologram, remove it
                            if (!chunksWithHolograms.contains(chunkKey)) {
                                ArmorStand hologram = entry.getValue();
                                if (hologram != null && hologram.isValid()) {
                                    hologram.remove();
                                    ChunklockPlugin.getInstance().getLogger().info(
                                        "Cleaned up stale hologram for chunk " + chunkX + "," + chunkZ + " for player " + player.getName());
                                }
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            // Invalid hologram key format, remove it
                            ArmorStand hologram = entry.getValue();
                            if (hologram != null && hologram.isValid()) {
                                hologram.remove();
                            }
                            return true;
                        }
                    }
                }
                return false;
            });
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error updating holograms for " + player.getName(), e);
        }
    }

    /**
     * Helper method to get chunk key from chunk
     */
    private String getChunkKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    /**
     * Checks if a locked chunk is adjacent to any unlocked chunk
     */
    private boolean isAdjacentToUnlockedChunk(Player player, Chunk lockedChunk) {
        try {
            // Check all 8 adjacent chunks (N, S, E, W, NE, NW, SE, SW)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip the center chunk itself
                    
                    try {
                        Chunk adjacentChunk = player.getWorld().getChunkAt(
                            lockedChunk.getX() + dx,
                            lockedChunk.getZ() + dz
                        );
                        
                        // Initialize adjacent chunk if needed
                        chunkLockManager.initializeChunk(adjacentChunk, player.getUniqueId());
                        
                        // If any adjacent chunk is unlocked, this locked chunk should show hologram
                        if (!chunkLockManager.isLocked(adjacentChunk)) {
                            return true;
                        }
                    } catch (Exception e) {
                        // Skip chunks that can't be checked
                        continue;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                "Error checking adjacent chunks for " + lockedChunk.getX() + "," + lockedChunk.getZ(), e);
            return false;
        }
    }

    /**
     * Calculates hologram spawn locations for each wall of the locked chunk that
     * borders an unlocked chunk.
     */
    private Map<WallSide, Location> getWallHologramLocations(Player player, Chunk chunk) {
        Map<WallSide, Location> map = new HashMap<>();
        World world = chunk.getWorld();
        int startX = chunk.getX() * 16;
        int startZ = chunk.getZ() * 16;

        for (WallSide side : WallSide.values()) {
            try {
                Chunk neighbor = world.getChunkAt(chunk.getX() + side.dx, chunk.getZ() + side.dz);
                chunkLockManager.initializeChunk(neighbor, player.getUniqueId());
                if (chunkLockManager.isLocked(neighbor)) {
                    continue; // neighbor also locked
                }

                int x = startX + 8;
                int z = startZ + 8;
                if (side == WallSide.NORTH) {
                    z = startZ;
                } else if (side == WallSide.SOUTH) {
                    z = startZ + 15;
                } else if (side == WallSide.EAST) {
                    x = startX + 15;
                } else if (side == WallSide.WEST) {
                    x = startX;
                }

                int y = getHighestSolidY(world, x, z) + 2;
                if (y < 64) y = 64;

                map.put(side, new Location(world, x + 0.5, y, z + 0.5));
            } catch (Exception ignored) {
            }
        }

        return map;
    }

    /**
     * Finds the highest solid block at the given coordinates, ignoring glass or barrier blocks.
     */
    private int getHighestSolidY(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        if (y > world.getMaxHeight()) y = world.getMaxHeight();
        while (y > world.getMinHeight()) {
            Block block = world.getBlockAt(x, y, z);
            var type = block.getType();
            if (type != org.bukkit.Material.BARRIER && !type.name().contains("GLASS") && type.isSolid()) {
                return y;
            }
            y--;
        }
        return world.getMinHeight();
    }

    /**
     * Shows hologram for a specific chunk
     */
    private void showHologramForChunk(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData evaluation, Map<WallSide, Location> locations) {
        if (locations == null || locations.isEmpty()) {
            return;
        }

        try {
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            String biomeName = BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome);
            boolean hasItems = biomeUnlockRegistry.hasRequiredItems(player, evaluation.biome, evaluation.score);

            Component hologramText;
            if (hasItems) {
                hologramText = Component.text()
                    .append(Component.text("🔓 UNLOCKABLE", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(biomeName, NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Required: " + requirement.amount() + "x", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(requirement.material().name().replace("_", " "), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("✓ Items available!", NamedTextColor.GREEN))
                .build();
            } else {
                hologramText = Component.text()
                    .append(Component.text("🔒 LOCKED", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(biomeName, NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Difficulty: " + evaluation.difficulty, getDifficultyColor(evaluation.difficulty)))
                .append(Component.newline())
                .append(Component.text("Need: " + requirement.amount() + "x", NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(requirement.material().name().replace("_", " "), NamedTextColor.GRAY))
                .build();
        }

            for (Map.Entry<WallSide, Location> entry : locations.entrySet()) {
                String hologramKey = getHologramKey(player, chunk, entry.getKey().name());

                if (activeHolograms.containsKey(hologramKey)) {
                    continue;
                }

            Location hologramLocation = entry.getValue();
            if (player.getLocation().distance(hologramLocation) > HOLOGRAM_VIEW_DISTANCE) {
                continue;
            }

            ArmorStand hologram = (ArmorStand) chunk.getWorld().spawnEntity(hologramLocation, EntityType.ARMOR_STAND);

            hologram.setVisible(false);
            hologram.setGravity(false);
            hologram.setCanPickupItems(false);
            hologram.setInvulnerable(true);
            hologram.setMarker(true);
            hologram.setSmall(true);

            hologram.customName(hologramText);
            hologram.setCustomNameVisible(true);

            activeHolograms.put(hologramKey, hologram);

                ChunklockPlugin.getInstance().getLogger().fine("Created hologram for chunk " +
                    chunk.getX() + "," + chunk.getZ() + " (" + entry.getKey() + ") for player " + player.getName());

            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING,
                "Error creating hologram for chunk " + chunk.getX() + "," + chunk.getZ() +
                " for player " + player.getName(), e);
        }
    }

    /**
     * Gets color for difficulty level
     */
    private NamedTextColor getDifficultyColor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case IMPOSSIBLE -> NamedTextColor.DARK_PURPLE;
        };
    }

    /**
     * Removes hologram for a specific chunk
     */
    private void removeHologramForChunk(Player player, Chunk chunk) {
        String prefix = getHologramKey(player, chunk);
        activeHolograms.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
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
     * Removes all holograms for a player
     */
    private void removeHologramsForPlayer(Player player) {
        String playerPrefix = player.getUniqueId().toString() + "_";
        
        activeHolograms.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(playerPrefix)) {
                ArmorStand hologram = entry.getValue();
                if (hologram != null && hologram.isValid()) {
                    hologram.remove();
                }
                return true;
            }
            return false;
        });
        
        ChunklockPlugin.getInstance().getLogger().fine("Removed all holograms for player " + player.getName());
    }

    /**
     * Gets hologram location (center of chunk, elevated)
     */
    private Location getHologramLocation(Chunk chunk) {
        try {
            World world = chunk.getWorld();
            int centerX = ChunkUtils.getChunkCenterX(chunk);
            int centerZ = ChunkUtils.getChunkCenterZ(chunk);
            
            // Get surface height at center
            int centerY = world.getHighestBlockYAt(centerX, centerZ) + (int)HOLOGRAM_HEIGHT_OFFSET;
            
            // Ensure Y is within world bounds
            centerY = Math.max(world.getMinHeight() + 5, 
                      Math.min(centerY, world.getMaxHeight() - 5));
            
            return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error getting hologram location for chunk " + chunk.getX() + "," + chunk.getZ(), e);
            
            // Fallback location
            World world = chunk.getWorld();
            return new Location(world, chunk.getX() * 16 + 8.5, 70, chunk.getZ() * 16 + 8.5);
        }
    }

    /**
     * Generates unique key for player-chunk hologram
     */
    private String getHologramKey(Player player, Chunk chunk) {
        return getHologramKey(player, chunk, null);
    }

    private String getHologramKey(Player player, Chunk chunk, String suffix) {
        String base = player.getUniqueId() + "_" + chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
        if (suffix != null) {
            return base + "_" + suffix;
        }
        return base;
    }

    /**
     * Cleanup all holograms and tasks
     */
    public void cleanup() {
        try {
            ChunklockPlugin.getInstance().getLogger().info("Cleaning up HologramManager...");
            
            // Cancel all tasks
            for (BukkitTask task : playerHologramTasks.values()) {
                if (task != null && !task.isCancelled()) {
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
            
            ChunklockPlugin.getInstance().getLogger().info("HologramManager cleanup completed");
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error during hologram cleanup", e);
        }
    }

    /**
     * Get hologram statistics for debugging
     */
    public Map<String, Object> getHologramStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeHolograms", activeHolograms.size());
        stats.put("activeTasks", playerHologramTasks.size());
        stats.put("onlinePlayers", playerHologramTasks.keySet().size());
        return stats;
    }

    /**
     * Force refresh holograms for a specific player
     */
    public void refreshHologramsForPlayer(Player player) {
        if (player == null || !player.isOnline()) return;
        
        try {
            // Remove existing holograms
            removeHologramsForPlayer(player);
            
            // Update holograms immediately
            updateHologramsForPlayer(player);
            
            ChunklockPlugin.getInstance().getLogger().fine("Refreshed holograms for " + player.getName());
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error refreshing holograms for " + player.getName(), e);
        }
    }
}