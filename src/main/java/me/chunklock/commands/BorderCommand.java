// src/main/java/me/chunklock/commands/BorderCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkBorderManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles border management commands for the glass border system.
 * Allows admins to refresh, inspect, and manage chunk borders.
 */
public class BorderCommand extends SubCommand {

    public BorderCommand() {
        super("borders", "chunklock.admin", false);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showBorderHelp(sender);
            return true;
        }

        String action = args[0].toLowerCase();
        
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            if (borderManager == null) {
                sender.sendMessage(Component.text("❌ Border manager not available.")
                    .color(NamedTextColor.RED));
                return true;
            }

            switch (action) {
                case "refresh" -> handleRefresh(sender, args, borderManager);
                case "info" -> handleInfo(sender, borderManager);
                case "clear" -> handleClear(sender, borderManager);
                case "help" -> showBorderHelp(sender);
                default -> {
                    sender.sendMessage(Component.text("Unknown border action: " + action)
                        .color(NamedTextColor.RED));
                    showBorderHelp(sender);
                }
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("❌ Error managing borders: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error in border command by " + sender.getName(), e);
        }

        return true;
    }

    /**
     * Handle border refresh command.
     */
    private void handleRefresh(CommandSender sender, String[] args, ChunkBorderManager borderManager) {
        if (!borderManager.isEnabled()) {
            sender.sendMessage(Component.text("⚠️ Glass border system is disabled in configuration.")
                .color(NamedTextColor.YELLOW));
            return;
        }

        if (args.length >= 2) {
            // Refresh borders for specific player
            String playerName = args[1];
            Player target = Bukkit.getPlayer(playerName);
            
            if (target == null) {
                sender.sendMessage(Component.text("❌ Player '" + playerName + "' not found or not online.")
                    .color(NamedTextColor.RED));
                return;
            }

            sender.sendMessage(Component.text("🔄 Refreshing glass borders for " + target.getName() + "...")
                .color(NamedTextColor.YELLOW));

            borderManager.refreshBordersForPlayer(target);
            
            sender.sendMessage(Component.text("✅ Refreshed glass borders for " + target.getName() + ".")
                .color(NamedTextColor.GREEN));

            // Notify the target player
            target.sendMessage(Component.text("🔄 Your glass borders have been refreshed by an admin.")
                .color(NamedTextColor.AQUA));

        } else {
            // Refresh borders for all players
            int onlinePlayerCount = Bukkit.getOnlinePlayers().size();
            
            if (onlinePlayerCount == 0) {
                sender.sendMessage(Component.text("ℹ️ No online players to refresh borders for.")
                    .color(NamedTextColor.GRAY));
                return;
            }

            sender.sendMessage(Component.text("🔄 Refreshing glass borders for all " + onlinePlayerCount + " online players...")
                .color(NamedTextColor.YELLOW));

            borderManager.refreshAllBorders();
            
            sender.sendMessage(Component.text("✅ Refreshed glass borders for all online players.")
                .color(NamedTextColor.GREEN));

            // Notify all players
            Component notification = Component.text("🔄 Glass borders have been refreshed by an admin.")
                .color(NamedTextColor.AQUA);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(notification);
            }
        }
    }

    /**
     * Handle border info command.
     */
    private void handleInfo(CommandSender sender, ChunkBorderManager borderManager) {
        try {
            Map<String, Object> stats = borderManager.getBorderStats();
            
            sender.sendMessage(Component.text("=== Glass Border System Information ===")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));

            // System status
            boolean enabled = (Boolean) stats.get("enabled");
            sender.sendMessage(Component.text("🔧 System Status: " + (enabled ? "ENABLED" : "DISABLED"))
                .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED));

            if (!enabled) {
                sender.sendMessage(Component.text("ℹ️ Enable borders in config.yml under 'glass-borders.enabled'")
                    .color(NamedTextColor.GRAY));
                return;
            }

            // Configuration details
            sender.sendMessage(Component.text("⚙️ Configuration:")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  Border Material: " + stats.get("borderMaterial"))
                .color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  Scan Range: " + stats.get("scanRange") + " chunks")
                .color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  Use Full Height: " + stats.get("useFullHeight"))
                .color(NamedTextColor.WHITE));
            sender.sendMessage(Component.text("  Border Height: " + stats.get("borderHeight") + " blocks")
                .color(NamedTextColor.WHITE));

            // Runtime statistics
            sender.sendMessage(Component.text("📊 Runtime Statistics:")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  Players with Borders: " + stats.get("playersWithBorders"))
                .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("  Total Border Blocks: " + stats.get("totalBorderBlocks"))
                .color(NamedTextColor.GREEN));
            sender.sendMessage(Component.text("  Border-to-Chunk Mappings: " + stats.get("borderToChunkMappings"))
                .color(NamedTextColor.GREEN));

            // Performance information
            sender.sendMessage(Component.text("⚡ Performance:")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("  Update Queue: " + stats.get("updateQueue"))
                .color(NamedTextColor.WHITE));

            // Configuration details from nested config
            @SuppressWarnings("unchecked")
            Map<String, Object> config = (Map<String, Object>) stats.get("config");
            if (config != null) {
                sender.sendMessage(Component.text("🔧 Advanced Settings:")
                    .color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("  Auto-Update on Movement: " + config.get("autoUpdateOnMovement"))
                    .color(NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Show for Bypass Players: " + config.get("showForBypassPlayers"))
                    .color(NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Restore Original Blocks: " + config.get("restoreOriginalBlocks"))
                    .color(NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Update Cooldown: " + config.get("updateCooldown"))
                    .color(NamedTextColor.WHITE));
            }

            // Online players with borders
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            if (onlinePlayers > 0) {
                sender.sendMessage(Component.text("👥 Online Players: " + onlinePlayers)
                    .color(NamedTextColor.AQUA));
                
                // List online players if reasonable number
                if (onlinePlayers <= 10) {
                    StringBuilder playerList = new StringBuilder("  ");
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (playerList.length() > 2) playerList.append(", ");
                        playerList.append(player.getName());
                    }
                    sender.sendMessage(Component.text(playerList.toString())
                        .color(NamedTextColor.GRAY));
                }
            }

        } catch (Exception e) {
            sender.sendMessage(Component.text("❌ Error retrieving border information: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error getting border stats", e);
        }
    }

    /**
     * Handle border clear command.
     */
    private void handleClear(CommandSender sender, ChunkBorderManager borderManager) {
        try {
            int onlinePlayerCount = Bukkit.getOnlinePlayers().size();
            
            if (onlinePlayerCount == 0) {
                sender.sendMessage(Component.text("ℹ️ No online players have borders to clear.")
                    .color(NamedTextColor.GRAY));
                return;
            }

            sender.sendMessage(Component.text("🧹 Clearing all glass borders for " + onlinePlayerCount + " online players...")
                .color(NamedTextColor.YELLOW));

            // Clear borders for all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                borderManager.removeBordersForPlayer(player);
            }

            sender.sendMessage(Component.text("✅ Cleared all glass borders.")
                .color(NamedTextColor.GREEN));

            // Notify all players
            Component notification = Component.text("🧹 Glass borders have been cleared by an admin.")
                .color(NamedTextColor.AQUA);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(notification);
            }

            // Suggest refresh command
            sender.sendMessage(Component.text("💡 Use '/chunklock borders refresh' to recreate borders.")
                .color(NamedTextColor.GOLD));

        } catch (Exception e) {
            sender.sendMessage(Component.text("❌ Error clearing borders: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error clearing borders", e);
        }
    }

    /**
     * Show help for border commands.
     */
    private void showBorderHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Glass Border Commands ===")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));

        sender.sendMessage(Component.text("Available commands:")
            .color(NamedTextColor.YELLOW));

        sender.sendMessage(Component.text("/chunklock borders refresh [player]")
            .color(NamedTextColor.WHITE)
            .append(Component.text(" - Refresh borders for player or all players")
                .color(NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("/chunklock borders info")
            .color(NamedTextColor.WHITE)
            .append(Component.text(" - Show detailed border system information")
                .color(NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("/chunklock borders clear")
            .color(NamedTextColor.WHITE)
            .append(Component.text(" - Remove all borders (can be refreshed)")
                .color(NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("/chunklock borders help")
            .color(NamedTextColor.WHITE)
            .append(Component.text(" - Show this help message")
                .color(NamedTextColor.GRAY)));

        sender.sendMessage(Component.text("💡 Tip: Borders automatically update when players move between chunks.")
            .color(NamedTextColor.GOLD));
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // First argument: border action
            String prefix = args[0].toLowerCase();
            List<String> actions = List.of("refresh", "info", "clear", "help");
            
            for (String action : actions) {
                if (action.startsWith(prefix)) {
                    completions.add(action);
                }
            }
            
        } else if (args.length == 2) {
            // Second argument: player name for refresh command
            String action = args[0].toLowerCase();
            
            if ("refresh".equals(action)) {
                String prefix = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(player.getName());
                    }
                }
            }
        }
        
        return completions;
    }

    @Override
    public String getUsage() {
        return "/chunklock borders <refresh [player]|info|clear|help>";
    }

    @Override
    public String getDescription() {
        return "Admin: Manage glass border system";
    }
}