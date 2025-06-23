package me.chunklock.border;

import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.TeamManager;
import me.chunklock.util.ChunkCoordinate;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.*;

public class BorderPlacementService {
    private final ChunkLockManager chunkLockManager;
    private final TeamManager teamManager;
    private final BorderConfig config;
    private final Material ownBorderMaterial = Material.LIME_STAINED_GLASS;
    private final Material enemyBorderMaterial = Material.RED_STAINED_GLASS;

    public BorderPlacementService(ChunkLockManager chunkLockManager, TeamManager teamManager, BorderConfig config) {
        this.chunkLockManager = chunkLockManager;
        this.teamManager = teamManager;
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
                    if (block.getType() == config.borderMaterial || block.getType() == ownBorderMaterial || block.getType() == enemyBorderMaterial) continue;

                    // Use BorderStateManager instead of direct map access
                    borderState.addBorderBlock(id, loc, block.getBlockData().clone(), lockedCoord);

                    Chunk neighbor = chunk.getWorld().getChunkAt(lockedX, lockedZ);
                    UUID owner = chunkLockManager.getChunkOwner(neighbor);
                    UUID teamId = teamManager.getTeamLeader(player.getUniqueId());
                    Material mat = config.borderMaterial;
                    if (owner != null) {
                        mat = owner.equals(teamId) ? ownBorderMaterial : enemyBorderMaterial;
                    }

                    block.setType(mat);
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

    private List<Location> getBorderLocationsForSide(Chunk chunk, BorderDirection side, Player player) {
        World world = chunk.getWorld();
        ChunkCoordinate coord = new ChunkCoordinate(chunk.getX(), chunk.getZ(), world.getName());
        int baseY = getBaseYForBorder(world, coord, player);
        List<Location> list = new ArrayList<>();
        int startX = chunk.getX() * 16;
        int startZ = chunk.getZ() * 16;

        switch (side) {
            case NORTH -> {
                for (int x = startX; x <= startX + 15; x++) {
                    addBorderColumn(list, world, x, baseY, startZ);
                }
            }
            case SOUTH -> {
                for (int x = startX; x <= startX + 15; x++) {
                    addBorderColumn(list, world, x, baseY, startZ + 15);
                }
            }
            case WEST -> {
                for (int z = startZ; z <= startZ + 15; z++) {
                    addBorderColumn(list, world, startX, baseY, z);
                }
            }
            case EAST -> {
                for (int z = startZ; z <= startZ + 15; z++) {
                    addBorderColumn(list, world, startX + 15, baseY, z);
                }
            }
        }
        return list;
    }

    private void addBorderColumn(List<Location> locations, World world, int x, int baseY, int z) {
        if (config.useFullHeight) {
            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            for (int y = minY; y <= maxY; y++) {
                locations.add(new Location(world, x, y, z));
            }
        } else {
            int startY = baseY + config.minYOffset;
            int endY = baseY + config.maxYOffset;
            int height = Math.max(1, config.borderHeight);
            for (int i = 0; i < height; i++) {
                int y = startY + i;
                if (y >= world.getMinHeight() && y <= world.getMaxHeight()) {
                    locations.add(new Location(world, x, y, z));
                }
            }
        }
    }

    private int getBaseYForBorder(World world, ChunkCoordinate chunkCoord, Player player) {
        try {
            int centerX = chunkCoord.x * 16 + 8;
            int centerZ = chunkCoord.z * 16 + 8;
            int surfaceY = world.getHighestBlockYAt(centerX, centerZ);
            int playerY = player.getLocation().getBlockY();
            int baseY = Math.max(surfaceY, Math.min(playerY, surfaceY + 10));
            return Math.max(baseY, world.getMinHeight() + 10);
        } catch (Exception e) {
            return Math.max(64, world.getMinHeight() + 10);
        }
    }

    private boolean shouldSkipBlock(Block block) {
        Material type = block.getType();
        if (type == Material.BEDROCK ||
            type == Material.SPAWNER ||
            type == Material.END_PORTAL ||
            type == Material.END_PORTAL_FRAME ||
            type == Material.NETHER_PORTAL) {
            return true;
        }
        if (config.skipImportantBlocks) {
            if (type == Material.BEACON ||
                type == Material.CONDUIT ||
                type == Material.CHEST ||
                type == Material.TRAPPED_CHEST ||
                type == Material.SHULKER_BOX) {
                return true;
            }
        }
        if (config.skipValuableOres) {
            if (type == Material.DIAMOND_ORE ||
                type == Material.EMERALD_ORE ||
                type == Material.ANCIENT_DEBRIS ||
                type == Material.DEEPSLATE_DIAMOND_ORE ||
                type == Material.DEEPSLATE_EMERALD_ORE ||
                type == Material.GOLD_ORE ||
                type == Material.DEEPSLATE_GOLD_ORE) {
                return true;
            }
        }
        if (config.skipFluids) {
            if (type == Material.WATER || type == Material.LAVA) {
                return true;
            }
        }
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