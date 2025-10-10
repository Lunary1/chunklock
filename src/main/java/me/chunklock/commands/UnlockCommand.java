// src/main/java/me/chunklock/commands/UnlockCommand.java
package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.managers.ChunkBorderManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Admin command to force unlock chunks for players.
 * Regular players should use the GUI to unlock their chunks.
 */
public class UnlockCommand extends SubCommand {
    
    private final ChunkLockManager chunkLockManager;
    private final PlayerProgressTracker progressTracker;
    // Note: teamManager kept for potential future team-based unlocking features
    
    public UnlockCommand(ChunkLockManager chunkLockManager, 
                        PlayerProgressTracker progressTracker, 
                        TeamManager teamManager) {
        super("unlock", "chunklock.admin", false); // Admin-only, works from console too
        this.chunkLockManager = chunkLockManager;
        this.progressTracker = progressTracker;
        // Note: teamManager parameter kept for API compatibility but not stored
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /chunklock unlock <player> [x] [z] [world]")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  - If x, z, world not specified, unlocks player's current chunk")
                .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  - Use 'here' as player name to unlock your current chunk")
                .color(NamedTextColor.GRAY));
            return true;
        }
        
        // Handle special "here" case for admin convenience
        if (args[0].equalsIgnoreCase("here")) {
            if (!(sender instanceof Player adminPlayer)) {
                sender.sendMessage(Component.text("Console cannot use 'here' - specify coordinates")
                    .color(NamedTextColor.RED));
                return true;
            }
            return unlockCurrentChunk(sender, adminPlayer);
        }
        
        // Find target player
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            sender.sendMessage(Component.text("Player '" + args[0] + "' not found or offline")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Determine which chunk to unlock
        Chunk targetChunk;
        
        if (args.length >= 4) {
            // Specific coordinates provided: /chunklock unlock <player> <x> <z> <world>
            try {
                int chunkX = Integer.parseInt(args[1]);
                int chunkZ = Integer.parseInt(args[2]);
                String worldName = args[3];
                
                var world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage(Component.text("World '" + worldName + "' not found")
                        .color(NamedTextColor.RED));
                    return true;
                }
                
                targetChunk = world.getChunkAt(chunkX, chunkZ);
                
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid coordinates. Use integers for x and z")
                    .color(NamedTextColor.RED));
                return true;
            }
        } else {
            // No coordinates specified - use player's current chunk
            targetChunk = targetPlayer.getLocation().getChunk();
        }
        
        // Perform the unlock
        return performUnlock(sender, targetPlayer, targetChunk);
    }
    
    /**
     * Unlock the current chunk for the specified player.
     */
    private boolean unlockCurrentChunk(CommandSender sender, Player player) {
        Chunk currentChunk = player.getLocation().getChunk();
        return performUnlock(sender, player, currentChunk);
    }
    
    /**
     * Perform the actual chunk unlock operation.
     */
    private boolean performUnlock(CommandSender sender, Player targetPlayer, Chunk chunk) {
        try {
            // Check if chunk is already unlocked
            if (!chunkLockManager.isLocked(chunk)) {
                sender.sendMessage(Component.text("Chunk " + chunk.getX() + "," + chunk.getZ() + 
                    " in " + chunk.getWorld().getName() + " is already unlocked")
                    .color(NamedTextColor.YELLOW));
                return true;
            }
            
            // Get player ID for chunk ownership
            // Use individual player ID, not team leader, to ensure proper ownership
            UUID playerId = targetPlayer.getUniqueId();
            
            // Force unlock the chunk
            chunkLockManager.unlockChunk(chunk, playerId);
            
            // Update progress tracking if the chunk was contested
            Location chunkCenter = new Location(chunk.getWorld(), 
                chunk.getX() * 16 + 8, 64, chunk.getZ() * 16 + 8);
            // Note: We don't increment contested claims here since this is an admin unlock
            
            // Update borders if border manager is available
            try {
                ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                if (borderManager != null) {
                    borderManager.onChunkUnlocked(targetPlayer, chunk);
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Failed to update borders after admin unlock: " + e.getMessage());
            }
            
            // Send success messages
            String chunkInfo = chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName();
            
            sender.sendMessage(Component.text("✓ Successfully unlocked chunk " + chunkInfo + " for " + targetPlayer.getName())
                .color(NamedTextColor.GREEN));
            
            targetPlayer.sendMessage(Component.text("🎉 Chunk " + chunkInfo + " has been unlocked by an admin!")
                .color(NamedTextColor.GREEN));
            
            // Log the action
            ChunklockPlugin.getInstance().getLogger().info("Admin " + sender.getName() + 
                " force-unlocked chunk " + chunkInfo + " for player " + targetPlayer.getName());
            
            return true;
            
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error unlocking chunk: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning("Error in admin unlock command: " + e.getMessage());
            return true;
        }
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Complete player names + "here"
            String prefix = args[0].toLowerCase();
            
            completions.add("here");
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 4) {
            // Complete world names for the 4th argument
            String prefix = args[3].toLowerCase();
            for (var world : Bukkit.getWorlds()) {
                if (world.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(world.getName());
                }
            }
        }
        // For args 2 and 3 (coordinates), no completion needed
        
        return completions;
    }
    
    @Override
    public String getUsage() {
        return "/chunklock unlock <player|here> [x] [z] [world]";
    }
    
    @Override
    public String getDescription() {
        return "Admin: Force unlock a chunk for a player";
    }
}