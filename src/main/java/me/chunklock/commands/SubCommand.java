// src/main/java/me/chunklock/commands/SubCommand.java
package me.chunklock.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Base interface for all subcommands in the chunklock system.
 * Provides a consistent structure for command execution and tab completion.
 */
public abstract class SubCommand {
    
    protected final String name;
    protected final String permission;
    protected final boolean requiresPlayer;
    
    protected SubCommand(String name, String permission, boolean requiresPlayer) {
        this.name = name;
        this.permission = permission;
        this.requiresPlayer = requiresPlayer;
    }
    
    /**
     * Execute the subcommand.
     * 
     * @param sender The command sender
     * @param args Command arguments (excluding the subcommand name)
     * @return true if command was handled successfully
     */
    public abstract boolean execute(CommandSender sender, String[] args);
    
    /**
     * Get tab completions for this subcommand.
     * 
     * @param sender The command sender
     * @param args Current arguments
     * @return List of possible completions
     */
    public abstract List<String> getTabCompletions(CommandSender sender, String[] args);
    
    /**
     * Get the usage string for this command.
     * 
     * @return Usage string
     */
    public abstract String getUsage();
    
    /**
     * Get the description of this command.
     * 
     * @return Command description
     */
    public abstract String getDescription();
    
    /**
     * Check if the sender has permission to use this command.
     * 
     * @param sender The command sender
     * @return true if sender has permission
     */
    public boolean hasPermission(CommandSender sender) {
        return permission == null || sender.hasPermission(permission);
    }
    
    /**
     * Check if the sender is a player (when required).
     * 
     * @param sender The command sender
     * @return true if valid, false if player required but sender is not player
     */
    public boolean isValidSender(CommandSender sender) {
        return !requiresPlayer || sender instanceof Player;
    }
    
    /**
     * Safely cast sender to player if needed.
     * 
     * @param sender The command sender
     * @return Player instance or null if not a player
     */
    protected Player asPlayer(CommandSender sender) {
        return sender instanceof Player ? (Player) sender : null;
    }
    
    // Getters
    public String getName() { return name; }
    public String getPermission() { return permission; }
    public boolean isRequiresPlayer() { return requiresPlayer; }
}