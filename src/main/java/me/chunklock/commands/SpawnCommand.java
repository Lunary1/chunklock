// src/main/java/me/chunklock/commands/SpawnCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.LanguageKeys;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.util.message.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import me.chunklock.util.chunk.ChunkUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("error", "PlayerDataManager is not available");
                String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
                player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
                return true;
            }
            
            Location savedLoc = playerDataManager.getChunkSpawn(player.getUniqueId());
            if (savedLoc == null) {
                String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_NO_SPAWN_LOCATION);
                player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
                return true;
            }
            
            // Validate location
            if (savedLoc.getWorld() == null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("error", "Saved spawn location has invalid world");
                String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
                player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
                ChunklockPlugin.getInstance().getLogger().warning(
                    "Player " + player.getName() + " has invalid spawn location (null world)");
                return true;
            }
            
            // Ensure world is loaded
            org.bukkit.World world = savedLoc.getWorld();
            if (!world.getPlayers().contains(player) && !world.equals(player.getWorld())) {
                // Player is in different world, ensure world is loaded
                if (org.bukkit.Bukkit.getWorld(world.getName()) == null) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("error", "World " + world.getName() + " is not loaded");
                    String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
                    player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
                    return true;
                }
            }
            
            // Check for optional "center" argument
            boolean forceCenter = args.length > 0 && args[0].equalsIgnoreCase("center");
            
            // Send initial message
            player.sendMessage(Component.text("Teleporting to your starting chunk...")
                .color(NamedTextColor.YELLOW));
            
            // Use scheduler to ensure teleportation happens on main thread
            org.bukkit.Bukkit.getScheduler().runTask(ChunklockPlugin.getInstance(), () -> {
                try {
                    if (forceCenter) {
                        // Teleport to exact center
                        org.bukkit.Chunk chunk = savedLoc.getChunk();
                        // Ensure chunk is loaded
                        if (!chunk.isLoaded()) {
                            chunk.load(true); // Force load chunk
                        }
                        Location centerLoc = ChunkUtils.getChunkCenter(chunk);
                        if (centerLoc != null && centerLoc.getWorld() != null) {
                            player.teleport(centerLoc);
                            player.sendMessage(Component.text("Teleported to center of your starting chunk.")
                                .color(NamedTextColor.GREEN));
                        } else {
                            throw new IllegalStateException("Invalid center location calculated");
                        }
                    } else {
                        // Teleport to saved spawn location (may not be center)
                        // Ensure chunk is loaded
                        org.bukkit.Chunk chunk = savedLoc.getChunk();
                        if (!chunk.isLoaded()) {
                            chunk.load(true); // Force load chunk
                        }
                        if (savedLoc.getWorld() != null) {
                            player.teleport(savedLoc);
                            player.sendMessage(Component.text("Teleported to your starting chunk spawn point.")
                                .color(NamedTextColor.GREEN));
                            player.sendMessage(Component.text("Use '/chunklock spawn center' to go to the exact center.")
                                .color(NamedTextColor.GRAY));
                        } else {
                            throw new IllegalStateException("Saved spawn location has null world");
                        }
                    }
                } catch (Exception e) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("error", e.getMessage() != null ? e.getMessage() : "Teleportation failed");
                    String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
                    player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
                    ChunklockPlugin.getInstance().getLogger().warning(
                        "Error teleporting player " + player.getName() + " to spawn: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
            return true;
            
        } catch (Exception e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            String errorMsg = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
            player.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
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