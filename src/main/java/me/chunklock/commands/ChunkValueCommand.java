package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.util.chunk.ChunkNeighborUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin command to inspect chunk values, neighbors, and multipliers.
 * Usage: /chunklock chunkvalue <x> <z> [world]
 */
public class ChunkValueCommand extends SubCommand {

    private final ChunkLockManager chunkLockManager;

    public ChunkValueCommand(ChunkLockManager chunkLockManager) {
        super("chunkvalue", "chunklock.admin", false);
        this.chunkLockManager = chunkLockManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players")
                .color(NamedTextColor.RED));
            return true;
        }

        Chunk targetChunk;
        World targetWorld;

        if (args.length >= 2) {
            // Specific coordinates provided
            try {
                int chunkX = Integer.parseInt(args[0]);
                int chunkZ = Integer.parseInt(args[1]);
                
                if (args.length >= 3) {
                    targetWorld = Bukkit.getWorld(args[2]);
                    if (targetWorld == null) {
                        sender.sendMessage(Component.text("World '" + args[2] + "' not found")
                            .color(NamedTextColor.RED));
                        return true;
                    }
                } else {
                    targetWorld = player.getWorld();
                }
                
                targetChunk = targetWorld.getChunkAt(chunkX, chunkZ);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates. Use integers for x and z")
                    .color(NamedTextColor.RED));
                return true;
            }
        } else {
            // Use player's current chunk
            targetChunk = player.getLocation().getChunk();
            targetWorld = player.getWorld();
        }

        // Display chunk value information
        try {
            displayChunkInfo(sender, targetChunk);
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error retrieving chunk information: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning("Error in chunkvalue command: " + e.getMessage());
        }

        return true;
    }

    private void displayChunkInfo(CommandSender sender, Chunk chunk) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══════════════════════════════════")
            .color(NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("   📊 CHUNK VALUE INFORMATION")
            .color(NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
        sender.sendMessage(Component.text("═══════════════════════════════════")
            .color(NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.empty());

        // Basic chunk info
        sender.sendMessage(Component.text("Location: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text("(" + chunk.getX() + ", " + chunk.getZ() + ")")
                .color(NamedTextColor.WHITE))
            .append(Component.text(" in " + chunk.getWorld().getName())
                .color(NamedTextColor.WHITE)));

        // Lock status
        boolean isLocked = chunkLockManager.isLocked(chunk);
        sender.sendMessage(Component.text("Status: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(isLocked ? "LOCKED" : "UNLOCKED")
                .color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN)));

        // Base value
        double baseValue = chunkLockManager.getBaseValue(chunk);
        sender.sendMessage(Component.text("Base Value: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(String.format("%.2f", baseValue))
                .color(NamedTextColor.WHITE)));

        // Neighbors
        List<Chunk> neighbors = ChunkNeighborUtils.getCardinalNeighbors(chunk);
        sender.sendMessage(Component.text("Neighbors: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(neighbors.size() + " found")
                .color(NamedTextColor.WHITE)));

        if (!neighbors.isEmpty()) {
            sender.sendMessage(Component.text("  Neighbor Values:")
                .color(NamedTextColor.DARK_GRAY));
            double neighborSum = 0.0;
            int validNeighbors = 0;
            
            for (Chunk neighbor : neighbors) {
                double neighborValue = chunkLockManager.getBaseValue(neighbor);
                if (neighborValue > 0.0) {
                    neighborSum += neighborValue;
                    validNeighbors++;
                }
                sender.sendMessage(Component.text("    (" + neighbor.getX() + ", " + neighbor.getZ() + "): ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(String.format("%.2f", neighborValue))
                        .color(NamedTextColor.WHITE)));
            }
            
            if (validNeighbors > 0) {
                double avgNeighborValue = neighborSum / validNeighbors;
                sender.sendMessage(Component.text("  Average Neighbor Value: ")
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(String.format("%.2f", avgNeighborValue))
                        .color(NamedTextColor.WHITE)));
                
                // Calculate multiplier
                if (avgNeighborValue > 0.0 && baseValue > 0.0) {
                    double multiplier = baseValue / avgNeighborValue;
                    sender.sendMessage(Component.text("  Calculated Multiplier: ")
                        .color(NamedTextColor.DARK_GRAY)
                        .append(Component.text(String.format("%.2f", multiplier))
                            .color(NamedTextColor.YELLOW)));
                }
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("═══════════════════════════════════")
            .color(NamedTextColor.DARK_GRAY));
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 3) {
            // Complete world names
            String prefix = args[2].toLowerCase();
            for (World world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(world.getName());
                }
            }
        }
        
        return completions;
    }

    @Override
    public String getUsage() {
        return "/chunklock chunkvalue [x] [z] [world]";
    }

    @Override
    public String getDescription() {
        return "Admin: Inspect chunk base values, neighbors, and multipliers";
    }
}

