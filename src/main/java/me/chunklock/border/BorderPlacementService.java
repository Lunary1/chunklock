// Update the BorderPlacementService.java file

package me.chunklock.border;

import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.TeamManager;
import me.chunklock.util.chunk.ChunkCoordinate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;

public class BorderPlacementService {
    private final ChunkLockManager chunkLockManager;
    private final BorderConfig config;

    public BorderPlacementService(ChunkLockManager chunkLockManager, TeamManager teamManager, BorderConfig config) {
        this.chunkLockManager = chunkLockManager;
        this.config = config;
    }

    public void createBordersForChunk(Player player, Chunk chunk, BorderStateManager borderState) {
        EnumSet<BorderDirection> sides = getSidesTouchingLockedChunks(chunk, player);
        if (sides.isEmpty()) return;

        UUID id = player.getUniqueId();

        for (BorderDirection dir : sides) {
            int lockedX = chunk.getX() + dir.dx;
            int lockedZ = chunk.getZ() + dir.dz;
            ChunkCoordinate lockedCoord = new ChunkCoordinate(lockedX, lockedZ, chunk.getWorld().getName());

            for (Location loc : getBorderLocationsForSide(chunk, dir, player)) {
                try {
                    Block block = loc.getBlock();
                    if (shouldSkipBlock(block)) continue;
                    
                    // CHANGED: Only check for the single border material from config
                    if (block.getType() == config.borderMaterial) continue;

                    // Use BorderStateManager instead of direct map access
                    borderState.addBorderBlock(id, loc, block.getBlockData().clone(), lockedCoord);

                    // CHANGED: Always use the same material from config regardless of ownership
                    block.setType(config.borderMaterial);
                    
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void removeSharedBorders(Chunk chunk, Player player, BorderStateManager borderState) {
        if (!borderState.hasPlayerBorders(player.getUniqueId())) return;

        World world = chunk.getWorld();
        UUID id = player.getUniqueId();

        for (BorderDirection dir : BorderDirection.values()) {
            try {
                Chunk neighbor = world.getChunkAt(chunk.getX() + dir.dx, chunk.getZ() + dir.dz);
                chunkLockManager.initializeChunk(neighbor, id);
                if (!chunkLockManager.isLocked(neighbor)) {
                    for (Location loc : getBorderLocationsForSide(chunk, dir, player)) {
                        BlockData data = borderState.removeBorderBlock(id, loc);
                        if (data != null) {
                            Block block = loc.getBlock();
                            // CHANGED: Only check for the single border material from config
                            if (block.getType() == config.borderMaterial) {
                                if (config.restoreOriginalBlocks) {
                                    block.setBlockData(data);
                                } else {
                                    block.setType(Material.AIR);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private EnumSet<BorderDirection> getSidesTouchingLockedChunks(Chunk chunk, Player player) {
        EnumSet<BorderDirection> sides = EnumSet.noneOf(BorderDirection.class);
        World world = chunk.getWorld();
        UUID id = player.getUniqueId();

        for (BorderDirection dir : BorderDirection.values()) {
            try {
                Chunk neighbor = world.getChunkAt(chunk.getX() + dir.dx, chunk.getZ() + dir.dz);
                chunkLockManager.initializeChunk(neighbor, id);
                if (chunkLockManager.isLocked(neighbor)) {
                    sides.add(dir);
                }
            } catch (Exception ignored) {
            }
        }
        return sides;
    }

    private List<Location> getBorderLocationsForSide(Chunk chunk, BorderDirection dir, Player player) {
        List<Location> locations = new ArrayList<>();
        World world = chunk.getWorld();
        
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;
        
        int xOffset = 0;
        int zOffset = 0;
        
        if (dir == BorderDirection.NORTH) {
            zOffset = -1;
        } else if (dir == BorderDirection.SOUTH) {
            zOffset = 16;
        } else if (dir == BorderDirection.WEST) {
            xOffset = -1;
        } else if (dir == BorderDirection.EAST) {
            xOffset = 16;
        }
        
        for (int i = 0; i < 16; i++) {
            int x, z;
            if (dir == BorderDirection.NORTH || dir == BorderDirection.SOUTH) {
                x = startX + i;
                z = startZ + zOffset;
            } else {
                x = startX + xOffset;
                z = startZ + i;
            }
            
            if (config.useFullHeight) {
                int minY = world.getMinHeight();
                int maxY = world.getMaxHeight() - 1;
                for (int y = minY; y <= maxY; y++) {
                    locations.add(new Location(world, x, y, z));
                }
            } else {
                int baseY = (int) player.getLocation().getY();
                int minY = Math.max(world.getMinHeight(), baseY + config.minYOffset);
                int maxY = Math.min(world.getMaxHeight() - 1, baseY + config.maxYOffset);
                
                for (int y = minY; y <= minY + config.borderHeight - 1 && y <= maxY; y++) {
                    locations.add(new Location(world, x, y, z));
                }
            }
        }
        
        return locations;
    }

    private boolean shouldSkipBlock(Block block) {
        Material type = block.getType();
        
        // Only skip absolutely essential blocks that would break the game
        if (type == Material.BEDROCK ||
            type == Material.SPAWNER ||
            type == Material.END_PORTAL ||
            type == Material.END_PORTAL_FRAME ||
            type == Material.NETHER_PORTAL) {
            return true;
        }
        
        // Skip if it's already a border material
        if (type == config.borderMaterial) {
            return true;
        }
        
        return false;
    }

    public enum BorderDirection {
        NORTH(0, -1),
        EAST(1, 0),
        SOUTH(0, 1),
        WEST(-1, 0);
        final int dx;
        final int dz;
        BorderDirection(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }
}