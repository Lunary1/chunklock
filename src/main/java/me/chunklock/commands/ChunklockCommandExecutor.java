// src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java
package me.chunklock.commands;

import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.commands.DiagnosticCommand;
import me.chunklock.commands.HelpCommand;
import me.chunklock.commands.SpawnCommand;
import me.chunklock.commands.StatusCommand;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGui;
import me.chunklock.ChunklockPlugin;

/**
 * Main command executor for the chunklock plugin.
 * Registers and routes all subcommands.
 * 
 * This replaces the original ChunklockCommand.java with a cleaner modular approach.
 */
public class ChunklockCommandExecutor extends ChunklockCommandManager {
    
    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final TeamManager teamManager;
    private final BasicTeamCommandHandler teamCommandHandler;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final PlayerDataManager playerDataManager;
    
    public ChunklockCommandExecutor(PlayerProgressTracker progressTracker,
                                   ChunkLockManager chunkLockManager,
                                   UnlockGui unlockGui,
                                   TeamManager teamManager,
                                   BasicTeamCommandHandler teamCommandHandler,
                                   BiomeUnlockRegistry biomeUnlockRegistry,
                                   PlayerDataManager playerDataManager) {
        super();
        
        // Validate dependencies
        if (progressTracker == null) {
            throw new IllegalArgumentException("PlayerProgressTracker cannot be null");
        }
        if (chunkLockManager == null) {
            throw new IllegalArgumentException("ChunkLockManager cannot be null");
        }
        if (unlockGui == null) {
            throw new IllegalArgumentException("UnlockGui cannot be null");
        }
        if (teamManager == null) {
            throw new IllegalArgumentException("TeamManager cannot be null");
        }
        if (biomeUnlockRegistry == null) {
            throw new IllegalArgumentException("BiomeUnlockRegistry cannot be null");
        }
        if (playerDataManager == null) {
            throw new IllegalArgumentException("PlayerDataManager cannot be null");
        }
        if (teamCommandHandler == null) {
            throw new IllegalArgumentException("BasicTeamCommandHandler cannot be null");
        }
        
        // Set all dependencies FIRST
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.teamManager = teamManager;
        this.teamCommandHandler = teamCommandHandler;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.playerDataManager = playerDataManager;
        
        // NOW register subcommands (after all fields are set)
        this.initialize();
    }
    
    @Override
    protected void registerSubCommands() {
        ChunklockPlugin plugin = ChunklockPlugin.getInstance();
        
        try {
            plugin.getLogger().info("=== Starting SubCommand Registration ===");
            plugin.getLogger().info("Verifying dependencies are available:");
            plugin.getLogger().info("  progressTracker: " + (progressTracker != null ? "OK" : "NULL"));
            plugin.getLogger().info("  chunkLockManager: " + (chunkLockManager != null ? "OK" : "NULL"));
            plugin.getLogger().info("  unlockGui: " + (unlockGui != null ? "OK" : "NULL"));
            plugin.getLogger().info("  teamManager: " + (teamManager != null ? "OK" : "NULL"));
            plugin.getLogger().info("  biomeUnlockRegistry: " + (biomeUnlockRegistry != null ? "OK" : "NULL"));
            plugin.getLogger().info("  playerDataManager: " + (playerDataManager != null ? "OK" : "NULL"));
            plugin.getLogger().info("  teamCommandHandler: " + (teamCommandHandler != null ? "OK" : "NULL"));
            
            // Add diagnostic command first to test system
            registerSubCommand(new DiagnosticCommand());
            plugin.getLogger().info("✓ Registered DiagnosticCommand");
            
            // Basic user commands with null checks
            if (progressTracker != null && chunkLockManager != null) {
                registerSubCommand(new StatusCommand(progressTracker, chunkLockManager));
                plugin.getLogger().info("✓ Registered StatusCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register StatusCommand - dependencies are null: " +
                    "progressTracker=" + (progressTracker != null ? "OK" : "NULL") + 
                    ", chunkLockManager=" + (chunkLockManager != null ? "OK" : "NULL"));
            }
            
            // Spawn command
            if (playerDataManager != null) {
                registerSubCommand(new SpawnCommand(playerDataManager));
                plugin.getLogger().info("✓ Registered SpawnCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register SpawnCommand - PlayerDataManager is null");
            }
            
            registerSubCommand(new HelpCommand(this));
            plugin.getLogger().info("✓ Registered HelpCommand");
            
            // TODO: Add more subcommands as they are extracted from the original ChunklockCommand
            
            // For now, we'll create a legacy command wrapper for commands not yet extracted
            registerSubCommand(new LegacyCommandWrapper(
                progressTracker, chunkLockManager, unlockGui, teamManager, 
                teamCommandHandler, biomeUnlockRegistry, playerDataManager));
            plugin.getLogger().info("✓ Registered LegacyCommandWrapper");
            
            plugin.getLogger().info("SubCommand registration completed. Total commands: " + getSubCommands().size());
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during subcommand registration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Temporary wrapper for commands that haven't been extracted yet.
     * This allows gradual migration from the old command system.
     */
    private static class LegacyCommandWrapper extends SubCommand {
        
        private final PlayerProgressTracker progressTracker;
        private final ChunkLockManager chunkLockManager;
        private final UnlockGui unlockGui;
        private final TeamManager teamManager;
        private final BasicTeamCommandHandler teamCommandHandler;
        private final BiomeUnlockRegistry biomeUnlockRegistry;
        private final PlayerDataManager playerDataManager;
        
        public LegacyCommandWrapper(PlayerProgressTracker progressTracker,
                                   ChunkLockManager chunkLockManager,
                                   UnlockGui unlockGui,
                                   TeamManager teamManager,
                                   BasicTeamCommandHandler teamCommandHandler,
                                   BiomeUnlockRegistry biomeUnlockRegistry,
                                   PlayerDataManager playerDataManager) {
            super("*", null, false); // Special wildcard command
            this.progressTracker = progressTracker;
            this.chunkLockManager = chunkLockManager;
            this.unlockGui = unlockGui;
            this.teamManager = teamManager;
            this.teamCommandHandler = teamCommandHandler;
            this.biomeUnlockRegistry = biomeUnlockRegistry;
            this.playerDataManager = playerDataManager;
        }
        
        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String[] args) {
            // This would contain the original command logic for commands not yet extracted
            // For now, just show a message
            sender.sendMessage("§eThis command is not yet migrated to the new system.");
            sender.sendMessage("§7Please use the original plugin version until migration is complete.");
            return true;
        }
        
        @Override
        public java.util.List<String> getTabCompletions(org.bukkit.command.CommandSender sender, String[] args) {
            return java.util.Collections.emptyList();
        }
        
        @Override
        public String getUsage() {
            return "/chunklock <legacy command>";
        }
        
        @Override
        public String getDescription() {
            return "Legacy command wrapper (temporary)";
        }
        
        @Override
        public boolean hasPermission(org.bukkit.command.CommandSender sender) {
            return true; // Allow access to determine if command exists
        }
    }
}