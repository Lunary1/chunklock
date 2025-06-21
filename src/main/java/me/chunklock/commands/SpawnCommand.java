// src/main/java/me/chunklock/commands/SpawnCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the spawn command - teleports players to their starting chunk.
 */
public class SpawnCommand extends SubCommand {
    
    private final PlayerDataManager playerDataManager;
    
    public SpawnCommand(PlayerDataManager playerDataManager) {
        super("spawn", "chunklock.use", true);
        
        if (playerDataManager == null) {
            throw new IllegalArgumentException("PlayerDataManager cannot be null");
        }
        
        this.playerDataManager = playerDataManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return false; // Should not happen due to requiresPlayer = true
        }
        
        try {
            // Validate PlayerDataManager is still available
            if (playerDataManager == null) {
                player.sendMessage(Component.text("Error: Player data manager not available.")
                    .color(NamedTextColor.RED));
                return true;
            }
            
            Location savedLoc = playerDataManager.getChunkSpawn(player.getUniqueId());
            if (savedLoc == null) {
                player.sendMessage(Component.text("No starting chunk recorded.")
                    .color(NamedTextColor.RED));
                return true;
            }
            
            // Check for optional "center" argument
            boolean forceCenter = args.length > 0 && args[0].equalsIgnoreCase("center");
            
            if (forceCenter) {
                // Teleport to exact center
                Location centerLoc = getCenterLocationOfChunk(savedLoc.getChunk());
                player.teleport(centerLoc);
                player.sendMessage(Component.text("Teleported to center of your starting chunk.")
                    .color(NamedTextColor.GREEN));
            } else {
                // Teleport to saved spawn location (may not be center)
                player.teleport(savedLoc);
                player.sendMessage(Component.text("Teleported to your starting chunk spawn point.")
                    .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("Use '/chunklock spawn center' to go to the exact center.")
                    .color(NamedTextColor.GRAY));
            }
            
            return true;
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error during teleportation.")
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error in spawn command for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    /**
     * Calculates the exact center location of a chunk with improved error handling.
     * This is extracted from the original ChunklockCommand.java
     */
    private Location getCenterLocationOfChunk(Chunk chunk) {
        if (chunk == null) {
            throw new IllegalArgumentException("Chunk cannot be null");
        }
        
        World world = chunk.getWorld();
        if (world == null) {
            throw new IllegalStateException("Chunk world is null");
        }

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Calculate exact center coordinates
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        
        // Get the highest solid block at center with better error handling
        int centerY;
        try {
            centerY = world.getHighestBlockAt(centerX, centerZ).getY();
            // Add 1 to place player on top of the block, not inside it
            centerY += 1;
            
            // Ensure Y is within world bounds
            centerY = Math.max(world.getMinHeight() + 1, 
                      Math.min(centerY, world.getMaxHeight() - 2));
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error getting highest block at chunk center (" + centerX + "," + centerZ + 
                "), using fallback Y: " + e.getMessage());
            centerY = Math.max(world.getMinHeight() + 10, world.getSpawnLocation().getBlockY());
        }
        
        // Return center location with 0.5 offset for perfect centering
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("center".startsWith(prefix)) {
                completions.add("center");
            }
        }
        
        return completions;
    }
    
    @Override
    public String getUsage() {
        return "/chunklock spawn [center]";
    }
    
    @Override
    public String getDescription() {
        return "Teleport to your starting chunk";
    }
}