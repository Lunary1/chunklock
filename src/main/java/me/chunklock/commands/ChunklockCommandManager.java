// src/main/java/me/chunklock/commands/ChunklockCommandManager.java
package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command manager for the chunklock plugin.
 * Routes commands to appropriate subcommand handlers.
 */
public class ChunklockCommandManager implements CommandExecutor, TabCompleter {
    
    private final Map<String, SubCommand> subCommands = new HashMap<>();
    private final Map<String, String> aliases = new HashMap<>();
    
    public ChunklockCommandManager() {
        // Don't call registerSubCommands() here - let subclasses control when to register
        registerAliases();
    }
    
    /**
     * Initialize the command manager by registering subcommands.
     * This should be called after the subclass constructor has finished.
     */
    public void initialize() {
        registerSubCommands();
    }
    
    /**
     * Register a subcommand with the manager.
     * 
     * @param subCommand The subcommand to register
     */
    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }
    
    /**
     * Register command aliases.
     */
    private void registerAliases() {
        aliases.put("c", "chat");  // For team chat
        aliases.put("info", "status");
    }
    
    /**
     * Register all subcommands. Override this method to add subcommands.
     */
    protected void registerSubCommands() {
        // Subcommands will be registered here
        // This method will be overridden in the concrete implementation
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            showMainHelp(sender);
            return true;
        }
        
        String subCommandName = args[0].toLowerCase();
        
        // Check for aliases
        if (aliases.containsKey(subCommandName)) {
            subCommandName = aliases.get(subCommandName);
        }
        
        SubCommand subCommand = subCommands.get(subCommandName);
        
        if (subCommand == null) {
            sender.sendMessage(Component.text("Unknown command. Use '/chunklock help' for available commands.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Check permissions
        if (!subCommand.hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Check if player is required
        if (!subCommand.isValidSender(sender)) {
            sender.sendMessage(Component.text("Only players can use this command.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        // Execute subcommand with remaining args
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            sender.sendMessage(Component.text("An error occurred while executing the command.")
                .color(NamedTextColor.RED));
            // Log the error
            e.printStackTrace();
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Complete subcommand names
            String prefix = args[0].toLowerCase();
            
            for (String subCommandName : subCommands.keySet()) {
                SubCommand subCommand = subCommands.get(subCommandName);
                if (subCommand.hasPermission(sender) && 
                    subCommand.isValidSender(sender) && 
                    subCommandName.startsWith(prefix)) {
                    completions.add(subCommandName);
                }
            }
            
            // Add aliases
            for (Map.Entry<String, String> aliasEntry : aliases.entrySet()) {
                if (aliasEntry.getKey().startsWith(prefix)) {
                    SubCommand targetCommand = subCommands.get(aliasEntry.getValue());
                    if (targetCommand != null && targetCommand.hasPermission(sender) && 
                        targetCommand.isValidSender(sender)) {
                        completions.add(aliasEntry.getKey());
                    }
                }
            }
            
        } else if (args.length > 1) {
            // Get tab completions from the subcommand
            String subCommandName = args[0].toLowerCase();
            
            if (aliases.containsKey(subCommandName)) {
                subCommandName = aliases.get(subCommandName);
            }
            
            SubCommand subCommand = subCommands.get(subCommandName);
            if (subCommand != null && 
                subCommand.hasPermission(sender) && 
                subCommand.isValidSender(sender)) {
                
                String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
                return subCommand.getTabCompletions(sender, subArgs);
            }
        }
        
        return completions;
    }
    
    /**
     * Show the main help message with available commands.
     * 
     * @param sender The command sender
     */
    private void showMainHelp(CommandSender sender) {
        sender.sendMessage(Component.text("Chunklock Commands:")
            .color(NamedTextColor.AQUA));
        
        // Group commands by permission level
        List<SubCommand> userCommands = new ArrayList<>();
        List<SubCommand> adminCommands = new ArrayList<>();
        
        for (SubCommand subCommand : subCommands.values()) {
            if (!subCommand.hasPermission(sender)) continue;
            if (!subCommand.isValidSender(sender)) continue;
            
            if (subCommand.getPermission() != null && 
                subCommand.getPermission().contains("admin")) {
                adminCommands.add(subCommand);
            } else {
                userCommands.add(subCommand);
            }
        }
        
        // Show user commands
        if (!userCommands.isEmpty()) {
            for (SubCommand cmd : userCommands) {
                sender.sendMessage(Component.text(cmd.getUsage() + " - " + cmd.getDescription())
                    .color(NamedTextColor.WHITE));
            }
        }
        
        // Show admin commands if sender has admin permissions
        if (!adminCommands.isEmpty()) {
            sender.sendMessage(Component.text("=== Admin Commands ===")
                .color(NamedTextColor.YELLOW));
            for (SubCommand cmd : adminCommands) {
                sender.sendMessage(Component.text(cmd.getUsage() + " - " + cmd.getDescription())
                    .color(NamedTextColor.GRAY));
            }
        }
    }
    
    /**
     * Get all registered subcommands.
     * 
     * @return Map of subcommand name to SubCommand instance
     */
    public Map<String, SubCommand> getSubCommands() {
        return Collections.unmodifiableMap(subCommands);
    }
}