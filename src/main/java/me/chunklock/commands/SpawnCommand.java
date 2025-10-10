// src/main/java/me/chunklock/commands/SpawnCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import me.chunklock.util.chunk.ChunkUtils;
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
                Location centerLoc = ChunkUtils.getChunkCenter(savedLoc.getChunk());
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