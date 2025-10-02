// src/main/java/me/chunklock/commands/ChunklockCommandExecutor.java
package me.chunklock.commands;

import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGui;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkBorderManager;

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
            
            // Diagnostic command (always first)
            registerSubCommand(new DiagnosticCommand());
            plugin.getLogger().info("✓ Registered DiagnosticCommand");
            
            // Status command
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

            // Start command (per-player worlds)
            try {
                me.chunklock.managers.WorldManager worldManager = plugin.getWorldManager();
                if (worldManager != null) {
                    registerSubCommand(new me.chunklock.commands.StartCommand(worldManager));
                    plugin.getLogger().info("✓ Registered StartCommand with WorldManager");
                    
                    // Also register WorldInfoCommand for admins
                    registerSubCommand(new me.chunklock.commands.WorldInfoCommand(worldManager));
                    plugin.getLogger().info("✓ Registered WorldInfoCommand with WorldManager");
                } else {
                    plugin.getLogger().warning("✗ Cannot register StartCommand/WorldInfoCommand - WorldManager is null");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("✗ Cannot register StartCommand/WorldInfoCommand - WorldManager error: " + e.getMessage());
            }

            // Team command
            if (teamCommandHandler != null) {
                registerSubCommand(new TeamCommand(teamCommandHandler));
                plugin.getLogger().info("✓ Registered TeamCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register TeamCommand - BasicTeamCommandHandler is null");
            }

            // Bypass command
            if (chunkLockManager != null) {
                registerSubCommand(new BypassCommand(chunkLockManager));
                plugin.getLogger().info("✓ Registered BypassCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register BypassCommand - ChunkLockManager is null");
            }
            
            // Reset command
            if (progressTracker != null && chunkLockManager != null && playerDataManager != null) {
                registerSubCommand(new ResetCommand(progressTracker, chunkLockManager, playerDataManager));
                plugin.getLogger().info("✓ Registered ResetCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register ResetCommand - dependencies are null: " +
                    "progressTracker=" + (progressTracker != null ? "OK" : "NULL") + 
                    ", chunkLockManager=" + (chunkLockManager != null ? "OK" : "NULL") +
                    ", playerDataManager=" + (playerDataManager != null ? "OK" : "NULL"));
            }
            
            // Help command
            registerSubCommand(new HelpCommand(this));
            plugin.getLogger().info("✓ Registered HelpCommand");

            // RELOAD COMMAND - NO DEPENDENCIES NEEDED
            registerSubCommand(new ReloadCommand());
            plugin.getLogger().info("✓ Registered ReloadCommand - NO DEPENDENCIES REQUIRED");

            // BORDER COMMAND - NEEDS ChunkBorderManager
            try {
                ChunkBorderManager borderManager = plugin.getChunkBorderManager();
                if (borderManager != null) {
                    registerSubCommand(new BorderCommand());
                    plugin.getLogger().info("✓ Registered BorderCommand with ChunkBorderManager");
                } else {
                    plugin.getLogger().warning("✗ Cannot register BorderCommand - ChunkBorderManager is null");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("✗ Cannot register BorderCommand - ChunkBorderManager error: " + e.getMessage());
            }

            // Debug command
            if (chunkLockManager != null && biomeUnlockRegistry != null && unlockGui != null) {
                registerSubCommand(new DebugCommand(chunkLockManager, biomeUnlockRegistry, unlockGui));
                plugin.getLogger().info("✓ Registered DebugCommand with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register DebugCommand - dependencies are null: " +
                    "chunkLockManager=" + (chunkLockManager != null ? "OK" : "NULL") + 
                    ", biomeUnlockRegistry=" + (biomeUnlockRegistry != null ? "OK" : "NULL") +
                    ", unlockGui=" + (unlockGui != null ? "OK" : "NULL"));
            }

            // Unlock command (admin-only)
            if (chunkLockManager != null && progressTracker != null && teamManager != null) {
                registerSubCommand(new UnlockCommand(chunkLockManager, progressTracker, teamManager));
                plugin.getLogger().info("✓ Registered UnlockCommand (admin-only) with valid dependencies");
            } else {
                plugin.getLogger().severe("✗ Cannot register UnlockCommand - dependencies are null: " +
                    "chunkLockManager=" + (chunkLockManager != null ? "OK" : "NULL") + 
                    ", progressTracker=" + (progressTracker != null ? "OK" : "NULL") +
                    ", teamManager=" + (teamManager != null ? "OK" : "NULL"));
            }
            
            plugin.getLogger().info("SubCommand registration completed. Total commands: " + getSubCommands().size());
            
        } catch (Exception e) {
            plugin.getLogger().severe("Error during subcommand registration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}