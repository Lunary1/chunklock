// src/main/java/me/chunklock/commands/ChunklockCommandManager.java
package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command manager for the chunklock plugin.
 * Routes commands to appropriate subcommand handlers with enhanced world validation.
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
        
        // ========================================
        // ENHANCED WORLD VALIDATION WRAPPER
        // ========================================
        
        // Skip world validation for console-friendly and global commands
        if (!shouldValidateWorld(subCommandName, sender)) {
            return executeCommand(subCommand, sender, args);
        }
        
        // For player commands, validate the world
        if (sender instanceof Player) {
            Player player = (Player) sender;
            WorldValidationResult validation = validatePlayerWorld(player, subCommandName);
            
            switch (validation.getResult()) {
                case ALLOWED:
                    // World validation passed, execute command
                    return executeCommand(subCommand, sender, args);
                    
                case BLOCKED:
                    // Show error and block execution
                    showWorldValidationError(player, validation);
                    return true;
                    
                case ADMIN_WARNING:
                    // Admin in disabled world - show warning but allow execution
                    showAdminWarning(player, validation);
                    return executeCommand(subCommand, sender, args);
            }
        }
        
        // Fallback - execute command
        return executeCommand(subCommand, sender, args);
    }
    
    /**
     * Execute a subcommand with proper error handling.
     */
    private boolean executeCommand(SubCommand subCommand, CommandSender sender, String[] args) {
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        try {
            return subCommand.execute(sender, subArgs);
        } catch (Exception e) {
            sender.sendMessage(Component.text("An error occurred while executing the command.")
                .color(NamedTextColor.RED));
            
            // Log with more detail for debugging
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error executing command '" + subCommand.getName() + "' for " + sender.getName() + ": " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }
    
    /**
     * Validate if a player can use commands in their current world.
     */
    private WorldValidationResult validatePlayerWorld(Player player, String commandName) {
        if (player == null || player.getWorld() == null) {
            return new WorldValidationResult(ValidationResult.BLOCKED, "Invalid player or world", null, null);
        }
        
        try {
            WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
            String worldName = player.getWorld().getName();
            boolean worldEnabled = worldManager.isWorldEnabled(player.getWorld());
            List<String> enabledWorlds = worldManager.getEnabledWorlds();
            
            if (worldEnabled) {
                return new WorldValidationResult(ValidationResult.ALLOWED, null, worldName, enabledWorlds);
            }
            
            // World is disabled - check if player is admin
            boolean isAdmin = player.hasPermission("chunklock.admin");
            boolean isAdminCommand = isAdminCommand(commandName);
            
            if (isAdmin && isAdminCommand) {
                // Admin using admin command in disabled world - allow with warning
                return new WorldValidationResult(ValidationResult.ADMIN_WARNING, 
                    "Admin override for command '" + commandName + "' in disabled world", worldName, enabledWorlds);
            }
            
            // Regular player or non-admin command in disabled world - block
            return new WorldValidationResult(ValidationResult.BLOCKED, 
                "ChunkLock not active in world: " + worldName, worldName, enabledWorlds);
                
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error during world validation for " + player.getName() + ": " + e.getMessage());
            
            return new WorldValidationResult(ValidationResult.BLOCKED, 
                "Could not verify world status", player.getWorld().getName(), null);
        }
    }
    
    /**
     * Show error message when world validation fails.
     */
    private void showWorldValidationError(Player player, WorldValidationResult validation) {
        player.sendMessage(Component.text("❌ " + validation.getMessage())
            .color(NamedTextColor.RED));
        
        List<String> enabledWorlds = validation.getEnabledWorlds();
        if (enabledWorlds != null && !enabledWorlds.isEmpty()) {
            player.sendMessage(Component.text("✅ ChunkLock is active in: " + String.join(", ", enabledWorlds))
                .color(NamedTextColor.GREEN));
                
            // Suggest teleportation to first available enabled world
            String firstEnabledWorld = enabledWorlds.get(0);
            if (Bukkit.getWorld(firstEnabledWorld) != null) {
                player.sendMessage(Component.text("💡 Tip: Use /mv tp " + firstEnabledWorld + " to access ChunkLock")
                    .color(NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text("Contact an administrator - no worlds are configured for ChunkLock")
                .color(NamedTextColor.GRAY));
        }
    }
    
    /**
     * Show warning to admin users when they override world validation.
     */
    private void showAdminWarning(Player player, WorldValidationResult validation) {
        player.sendMessage(Component.text("⚠️ Admin Override: " + validation.getMessage())
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("This command may not work as expected in disabled worlds.")
            .color(NamedTextColor.GRAY));
    }
    
    /**
     * Check if a command name represents an admin command.
     */
    private boolean isAdminCommand(String commandName) {
        Set<String> adminCommands = Set.of(
            "unlock",       // Force unlock chunks
            "reset",        // Reset player progress
            "bypass",       // Toggle bypass mode
            "reload",       // Reload config
            "resetall"      // Reset all players
        );
        
        return adminCommands.contains(commandName);
    }
    
    /**
     * Determines whether a command should have world validation applied.
     */
    private boolean shouldValidateWorld(String subCommandName, CommandSender sender) {
        // Always skip validation for console commands
        if (!(sender instanceof Player)) {
            return false;
        }
        
        // Commands that should work from any world (truly global commands)
        Set<String> globalCommands = Set.of(
            "help",         // Help should always be available
            "reload",       // Reload should work from anywhere (admin override will handle warnings)
            "start",        // Start command creates/loads player world - should work from any world
            "setup"         // Setup command creates the ChunkLock world - should work from any world
        );
        
        return !globalCommands.contains(subCommandName);
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
        sender.sendMessage(Component.text("=== ChunkLock Commands ===")
            .color(NamedTextColor.AQUA));
            
        if (sender instanceof Player) {
            Player player = (Player) sender;
            
            // Check if player is in an enabled world and show appropriate message
            try {
                WorldManager worldManager = ChunklockPlugin.getInstance().getWorldManager();
                boolean inEnabledWorld = worldManager.isWorldEnabled(player.getWorld());
                
                if (!inEnabledWorld) {
                    sender.sendMessage(Component.text("⚠️ You are currently in a world where ChunkLock is not active")
                        .color(NamedTextColor.YELLOW));
                    List<String> enabledWorlds = worldManager.getEnabledWorlds();
                    if (!enabledWorlds.isEmpty()) {
                        sender.sendMessage(Component.text("Active worlds: " + String.join(", ", enabledWorlds))
                            .color(NamedTextColor.GREEN));
                    }
                    sender.sendMessage(Component.text(""));
                }
            } catch (Exception e) {
                // Ignore - just show help normally
            }
        }
        
        sender.sendMessage(Component.text("Available commands:")
            .color(NamedTextColor.WHITE));
            
        // Show commands that the sender has permission for
        List<String> commandList = new ArrayList<>();
        for (SubCommand subCommand : subCommands.values()) {
            if (subCommand.hasPermission(sender) && subCommand.isValidSender(sender)) {
                commandList.add("/chunklock " + subCommand.getName() + " - " + subCommand.getDescription());
            }
        }
        
        Collections.sort(commandList);
        for (String commandInfo : commandList) {
            sender.sendMessage(Component.text(commandInfo)
                .color(NamedTextColor.GRAY));
        }
        
        sender.sendMessage(Component.text("Use '/chunklock help <command>' for detailed help")
            .color(NamedTextColor.YELLOW));
    }
    
    /**
     * Get all registered subcommands (for help system).
     * 
     * @return Map of subcommand names to SubCommand objects
     */
    public Map<String, SubCommand> getSubCommands() {
        return Collections.unmodifiableMap(subCommands);
    }
    
    // ========================================
    // VALIDATION RESULT CLASSES
    // ========================================
    
    /**
     * Enum for validation results.
     */
    private enum ValidationResult {
        ALLOWED,        // Command allowed to execute
        BLOCKED,        // Command blocked - show error
        ADMIN_WARNING   // Admin override - show warning but execute
    }
    
    /**
     * Result of world validation check.
     */
    private static class WorldValidationResult {
        private final ValidationResult result;
        private final String message;
        private final String currentWorld;
        private final List<String> enabledWorlds;
        
        public WorldValidationResult(ValidationResult result, String message, String currentWorld, List<String> enabledWorlds) {
            this.result = result;
            this.message = message;
            this.currentWorld = currentWorld;
            this.enabledWorlds = enabledWorlds;
        }
        
        public ValidationResult getResult() { return result; }
        public String getMessage() { return message; }
        public String getCurrentWorld() { return currentWorld; }
        public List<String> getEnabledWorlds() { return enabledWorlds; }
    }
}