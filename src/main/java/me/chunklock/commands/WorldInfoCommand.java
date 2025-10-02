package me.chunklock.commands;

import me.chunklock.managers.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the worldinfo command - shows information about player worlds and world management.
 */
public class WorldInfoCommand extends SubCommand {
    
    private final WorldManager worldManager;
    
    public WorldInfoCommand(WorldManager worldManager) {
        super("worldinfo", "chunklock.admin", false);
        
        if (worldManager == null) {
            throw new IllegalArgumentException("WorldManager cannot be null");
        }
        
        this.worldManager = worldManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        
        // Check if per-player worlds are enabled
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            sender.sendMessage(Component.text("Per-player worlds are disabled on this server.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Show different info based on arguments
        if (args.length == 0) {
            showGeneralInfo(sender);
        } else if (args.length == 1) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "stats":
                    showWorldStats(sender);
                    break;
                case "loaded":
                    showLoadedWorlds(sender);
                    break;
                case "enabled":
                    showEnabledWorlds(sender);
                    break;
                default:
                    showUsage(sender);
                    break;
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            showPlayerWorldInfo(sender, args[1]);
        } else {
            showUsage(sender);
        }
        
        return true;
    }
    
    private void showGeneralInfo(CommandSender sender) {
        sender.sendMessage(Component.text("=== World Management Info ===").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Per-player worlds: " + (worldManager.isPerPlayerWorldsEnabled() ? "Enabled" : "Disabled"))
            .color(NamedTextColor.GREEN));
        
        Map<String, Object> stats = worldManager.getPlayerWorldStats();
        sender.sendMessage(Component.text("Total player worlds: " + stats.get("totalPlayerWorlds"))
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Currently loaded: " + stats.get("loadedPlayerWorlds"))
            .color(NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Max allowed: " + stats.get("maxPlayerWorlds"))
            .color(NamedTextColor.YELLOW));
        
        sender.sendMessage(Component.text("Use '/chunklock worldinfo stats' for detailed statistics")
            .color(NamedTextColor.GRAY));
    }
    
    private void showWorldStats(CommandSender sender) {
        Map<String, Object> stats = worldManager.getPlayerWorldStats();
        
        sender.sendMessage(Component.text("=== Detailed World Statistics ===").color(NamedTextColor.GOLD));
        
        for (Map.Entry<String, Object> entry : stats.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            Component line = Component.text(key + ": " + value).color(NamedTextColor.WHITE);
            sender.sendMessage(line);
        }
        
        // Additional server information
        sender.sendMessage(Component.text("=== Server World Info ===").color(NamedTextColor.BLUE));
        sender.sendMessage(Component.text("Total loaded worlds: " + Bukkit.getWorlds().size())
            .color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Online players: " + Bukkit.getOnlinePlayers().size())
            .color(NamedTextColor.WHITE));
    }
    
    private void showLoadedWorlds(CommandSender sender) {
        sender.sendMessage(Component.text("=== Currently Loaded Worlds ===").color(NamedTextColor.GOLD));
        
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            boolean isPlayerWorld = worldManager.isPlayerWorld(worldName);
            boolean isEnabled = worldManager.isWorldEnabled(world);
            int playerCount = world.getPlayers().size();
            
            NamedTextColor color = isEnabled ? NamedTextColor.GREEN : NamedTextColor.RED;
            String type = isPlayerWorld ? "[PLAYER]" : "[SERVER]";
            
            sender.sendMessage(Component.text(type + " " + worldName + " (Players: " + playerCount + ")")
                .color(color));
        }
    }
    
    private void showEnabledWorlds(CommandSender sender) {
        sender.sendMessage(Component.text("=== Chunklock Enabled Worlds ===").color(NamedTextColor.GOLD));
        
        List<String> enabledWorlds = worldManager.getEnabledWorlds();
        for (String worldName : enabledWorlds) {
            World world = Bukkit.getWorld(worldName);
            boolean isLoaded = world != null;
            boolean isPlayerWorld = worldManager.isPlayerWorld(worldName);
            
            NamedTextColor color = isLoaded ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            String status = isLoaded ? "[LOADED]" : "[UNLOADED]";
            String type = isPlayerWorld ? "[PLAYER]" : "[SERVER]";
            
            sender.sendMessage(Component.text(status + " " + type + " " + worldName).color(color));
        }
    }
    
    private void showPlayerWorldInfo(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName).color(NamedTextColor.RED));
            return;
        }
        
        String worldName = worldManager.getPlayerWorldName(target.getUniqueId());
        
        sender.sendMessage(Component.text("=== Player World Info: " + playerName + " ===").color(NamedTextColor.GOLD));
        
        if (worldName == null) {
            sender.sendMessage(Component.text("Player has no private world").color(NamedTextColor.YELLOW));
        } else {
            World world = Bukkit.getWorld(worldName);
            boolean isLoaded = world != null;
            
            sender.sendMessage(Component.text("World name: " + worldName).color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("Status: " + (isLoaded ? "Loaded" : "Unloaded"))
                .color(isLoaded ? NamedTextColor.GREEN : NamedTextColor.RED));
            
            if (isLoaded && world != null) {
                sender.sendMessage(Component.text("Players in world: " + world.getPlayers().size())
                    .color(NamedTextColor.WHITE));
                sender.sendMessage(Component.text("World spawn: " + 
                    world.getSpawnLocation().getBlockX() + ", " +
                    world.getSpawnLocation().getBlockY() + ", " + 
                    world.getSpawnLocation().getBlockZ()).color(NamedTextColor.WHITE));
            }
            
            // Check if sender can access the world
            if (sender instanceof Player) {
                Player senderPlayer = (Player) sender;
                boolean hasAccess = worldManager.hasWorldAccess(senderPlayer.getUniqueId(), worldName);
                sender.sendMessage(Component.text("Your access: " + (hasAccess ? "Allowed" : "Denied"))
                    .color(hasAccess ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
        }
    }
    
    private void showUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage:").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  /chunklock worldinfo - General information").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /chunklock worldinfo stats - Detailed statistics").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /chunklock worldinfo loaded - Currently loaded worlds").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /chunklock worldinfo enabled - Chunklock enabled worlds").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /chunklock worldinfo player <name> - Player's world info").color(NamedTextColor.GRAY));
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("stats");
            completions.add("loaded");
            completions.add("enabled");
            completions.add("player");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("player")) {
            // Add online player names
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        
        return completions;
    }
    
    @Override
    public String getDescription() {
        return "Show information about world management and player worlds";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock worldinfo [stats|loaded|enabled|player <name>]";
    }
}
