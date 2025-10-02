package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the start command - creates or loads a player's private world and teleports them there.
 */
public class StartCommand extends SubCommand {
    
    private final WorldManager worldManager;
    
    public StartCommand(WorldManager worldManager) {
        super("start", "chunklock.use", true);
        
        if (worldManager == null) {
            throw new IllegalArgumentException("WorldManager cannot be null");
        }
        
        this.worldManager = worldManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return false; // Should not happen due to requiresPlayer = true
        }
        
        // Check if per-player worlds are enabled
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            player.sendMessage(Component.text("Per-player worlds are not enabled on this server.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Check if player already has a world
        String existingWorld = worldManager.getPlayerWorldName(player.getUniqueId());
        if (existingWorld != null) {
            player.sendMessage(Component.text("You already have a private world! Teleporting you there...")
                .color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Creating your private world, please wait...")
                .color(NamedTextColor.GREEN));
        }
        
        // Get or create the player's world and teleport them
        worldManager.teleportToPlayerWorld(player).thenAccept(success -> {
            if (success) {
                if (existingWorld == null) {
                    player.sendMessage(Component.text("Welcome to your private Chunklock world!")
                        .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("Use '/chunklock unlock' to begin unlocking chunks.")
                        .color(NamedTextColor.GRAY));
                    player.sendMessage(Component.text("Invite team members with '/chunklock team invite <player>'")
                        .color(NamedTextColor.GRAY));
                } else {
                    player.sendMessage(Component.text("Welcome back to your private world!")
                        .color(NamedTextColor.GREEN));
                }
            } else {
                player.sendMessage(Component.text("Failed to create or load your private world. Please try again later.")
                    .color(NamedTextColor.RED));
            }
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("An error occurred while setting up your world: " + throwable.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning("Failed to setup world for " + player.getName() + ": " + throwable.getMessage());
            return null;
        });
        
        return true;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>(); // No tab completion needed
    }
    
    @Override
    public String getDescription() {
        return "Create or load your private Chunklock world";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock start";
    }
}
