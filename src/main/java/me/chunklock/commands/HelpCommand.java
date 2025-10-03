// src/main/java/me/chunklock/commands/HelpCommand.java
package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;

/**
 * Handles the help command - shows detailed help information.
 */
public class HelpCommand extends SubCommand {
    
    private final ChunklockCommandManager commandManager;
    
    public HelpCommand(ChunklockCommandManager commandManager) {
        super("help", "chunklock.use", false);
        this.commandManager = commandManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            // Show help for specific command
            return showSpecificCommandHelp(sender, args[0]);
        }
        
        // Show general help
        showGeneralHelp(sender);
        return true;
    }
    
    private boolean showSpecificCommandHelp(CommandSender sender, String commandName) {
        SubCommand subCommand = commandManager.getSubCommands().get(commandName.toLowerCase());
        
        if (subCommand == null) {
            sender.sendMessage(Component.text("Unknown command: " + commandName)
                .color(NamedTextColor.RED));
            return true;
        }
        
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to view help for this command.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        sender.sendMessage(Component.text("Help for: " + commandName)
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        sender.sendMessage(Component.text("Usage: " + subCommand.getUsage())
            .color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Description: " + subCommand.getDescription())
            .color(NamedTextColor.GRAY));
        
        if (subCommand.getPermission() != null) {
            sender.sendMessage(Component.text("Permission: " + subCommand.getPermission())
                .color(NamedTextColor.YELLOW));
        }
        
        return true;
    }
    
    private void showGeneralHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Chunklock Help")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.BOLD, true));
        
        sender.sendMessage(Component.text("═══ Basic Commands ═══")
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/chunklock status - View your unlocked chunks")
            .color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/chunklock spawn - Return to your starting chunk")
            .color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("/chunklock team - Team management commands")
            .color(NamedTextColor.WHITE));
        sender.sendMessage(Component.text("💡 Right-click a block to open the unlock GUI")
            .color(NamedTextColor.GOLD));
        
        if (sender.hasPermission("chunklock.admin")) {
            sender.sendMessage(Component.text("═══ Admin Commands ═══")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/chunklock unlock <player> - Force unlock chunk for player")
                .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/chunklock reset <player> - Reset player progress")
                .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/chunklock bypass [player] - Toggle bypass mode")
                .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/chunklock reload - Reload plugin configuration")
                .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("")
                .color(NamedTextColor.GRAY));
        }
        
        sender.sendMessage(Component.text("Use '/chunklock help <command>' for detailed help")
            .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text("💡 Teams share unlocked chunks and affect costs!")
            .color(NamedTextColor.GREEN));
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Complete with available command names
            String prefix = args[0].toLowerCase();
            return commandManager.getSubCommands().keySet().stream()
                .filter(name -> name.startsWith(prefix))
                .filter(name -> {
                    SubCommand cmd = commandManager.getSubCommands().get(name);
                    return cmd.hasPermission(sender) && cmd.isValidSender(sender);
                })
                .sorted()
                .toList();
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public String getUsage() {
        return "/chunklock help [command]";
    }
    
    @Override
    public String getDescription() {
        return "Show help information for commands";
    }
}