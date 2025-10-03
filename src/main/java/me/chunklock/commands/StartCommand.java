package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.SingleWorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the start command - teleports players to the single Chunklock world and assigns them a starting chunk.
 */
public class StartCommand extends SubCommand {
    
    private final SingleWorldManager singleWorldManager;
    
    public StartCommand(SingleWorldManager singleWorldManager) {
        super("start", "chunklock.use", true);
        
        if (singleWorldManager == null) {
            throw new IllegalArgumentException("SingleWorldManager cannot be null");
        }
        
        this.singleWorldManager = singleWorldManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return false; // Should not happen due to requiresPlayer = true
        }
        
        // Check if the Chunklock world is setup
        if (!singleWorldManager.isChunklockWorldSetup()) {
            player.sendMessage(Component.text("The Chunklock world has not been set up yet!")
                .color(NamedTextColor.RED));
            player.sendMessage(Component.text("Ask an administrator to run '/chunklock setup <diameter>' first.")
                .color(NamedTextColor.GRAY));
            return true;
        }
        
        // Check if player already has a claim
        if (singleWorldManager.hasPlayerClaim(player.getUniqueId())) {
            player.sendMessage(Component.text("Teleporting you to your starting chunk...")
                .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Finding you a starting chunk in the Chunklock world...")
                .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Please wait while we assign you a suitable location.")
                .color(NamedTextColor.GRAY));
        }
        
        // Teleport to the Chunklock world
        singleWorldManager.teleportPlayerToChunklockWorld(player).thenAccept(success -> {
            if (success) {
                if (singleWorldManager.hasPlayerClaim(player.getUniqueId())) {
                    if (args.length == 0 || !args[0].equals("silent")) {
                        player.sendMessage(Component.text("✅ Welcome to the Chunklock world!")
                            .color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("Your starting chunk: X=" + 
                            singleWorldManager.getPlayerClaim(player.getUniqueId()).getX() + 
                            ", Z=" + singleWorldManager.getPlayerClaim(player.getUniqueId()).getZ())
                            .color(NamedTextColor.GRAY));
                        player.sendMessage(Component.text("Use the GUI to unlock adjacent chunks and explore!")
                            .color(NamedTextColor.AQUA));
                        player.sendMessage(Component.text("Total players in world: " + 
                            singleWorldManager.getTotalClaims())
                            .color(NamedTextColor.GRAY));
                    }
                } else {
                    player.sendMessage(Component.text("Welcome back to your starting chunk!")
                        .color(NamedTextColor.GREEN));
                }
            } else {
                player.sendMessage(Component.text("❌ Failed to enter the Chunklock world.")
                    .color(NamedTextColor.RED));
                player.sendMessage(Component.text("This could be because:")
                    .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• The world is not set up properly")
                    .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• No suitable starting chunks are available")
                    .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("• The world is full (too many players)")
                    .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Contact an administrator for assistance.")
                    .color(NamedTextColor.YELLOW));
            }
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("❌ An error occurred: " + throwable.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning("Failed to teleport " + player.getName() + 
                " to Chunklock world: " + throwable.getMessage());
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
        return "Enter the Chunklock world and get your starting chunk";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock start";
    }
}
