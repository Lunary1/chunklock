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
 * Handles the setup command - generates and configures the single Chunklock world.
 * Admin command: /chunklock setup <diameter>
 */
public class SetupCommand extends SubCommand {
    
    private final SingleWorldManager singleWorldManager;
    
    public SetupCommand(SingleWorldManager singleWorldManager) {
        super("setup", "chunklock.admin", true);
        
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
        
        // Check if player has admin permission
        if (!player.hasPermission("chunklock.admin")) {
            player.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Validate arguments
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /chunklock setup <diameter>")
                .color(NamedTextColor.RED));
            player.sendMessage(Component.text("Example: /chunklock setup 30000")
                .color(NamedTextColor.GRAY));
            return true;
        }
        
        // Parse diameter
        int diameter;
        try {
            diameter = Integer.parseInt(args[0]);
            if (diameter < 1000) {
                player.sendMessage(Component.text("Diameter must be at least 1000 blocks.")
                    .color(NamedTextColor.RED));
                return true;
            }
            if (diameter > 100000) {
                player.sendMessage(Component.text("Diameter cannot exceed 100,000 blocks for performance reasons.")
                    .color(NamedTextColor.RED));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid diameter. Please enter a number.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Check if world already exists
        if (singleWorldManager.isChunklockWorldSetup()) {
            player.sendMessage(Component.text("Chunklock world already exists!")
                .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Current world: " + singleWorldManager.getChunklockWorldName())
                .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("Current diameter: " + singleWorldManager.getWorldDiameter() + " blocks")
                .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("Use '/chunklock setup-force <diameter>' to recreate the world.")
                .color(NamedTextColor.GRAY));
            return true;
        }
        
        // Start world setup
        player.sendMessage(Component.text("Starting Chunklock world setup...")
            .color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Diameter: " + diameter + " blocks")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("This may take several minutes depending on the size.")
            .color(NamedTextColor.YELLOW));
        
        // Execute setup asynchronously
        singleWorldManager.setupChunklockWorld(diameter, player).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("✅ Chunklock world setup completed successfully!")
                    .color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("World name: " + singleWorldManager.getChunklockWorldName())
                    .color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Players can now use '/chunklock start' to begin!")
                    .color(NamedTextColor.AQUA));
            } else {
                player.sendMessage(Component.text("❌ Failed to setup Chunklock world. Check console for details.")
                    .color(NamedTextColor.RED));
            }
        }).exceptionally(throwable -> {
            player.sendMessage(Component.text("❌ Error during world setup: " + throwable.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().severe("Failed to setup Chunklock world: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
        
        return true;
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Suggest common diameter values
            String prefix = args[0].toLowerCase();
            String[] suggestions = {"5000", "10000", "15000", "20000", "25000", "30000"};
            
            for (String suggestion : suggestions) {
                if (suggestion.startsWith(prefix)) {
                    completions.add(suggestion);
                }
            }
        }
        
        return completions;
    }
    
    @Override
    public String getDescription() {
        return "Setup the Chunklock world with specified diameter (Admin only)";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock setup <diameter>";
    }
}