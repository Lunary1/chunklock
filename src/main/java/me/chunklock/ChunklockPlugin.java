package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.managers.EnhancedTeamManager;
import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.util.DataMigrator;
import me.chunklock.commands.ChunklockCommandExecutor;
import me.chunklock.commands.ChunklockCommand;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.ChunkValueRegistry;
import me.chunklock.managers.HologramManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.managers.TickTask;
import me.chunklock.listeners.BlockProtectionListener;
import me.chunklock.listeners.BorderListener;
import me.chunklock.listeners.PlayerJoinQuitListener;
import me.chunklock.listeners.PlayerListener;
import me.chunklock.ui.UnlockGuiListener;
import me.chunklock.border.BorderRefreshService;
import me.chunklock.ui.UnlockGui;
import java.util.logging.Level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.logging.Level;

public class ChunklockPlugin extends JavaPlugin implements Listener {

    private static ChunklockPlugin instance;
    private ChunkLockManager chunkLockManager;
    private BiomeUnlockRegistry biomeUnlockRegistry;
    private PlayerProgressTracker progressTracker;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private ChunkValueRegistry chunkValueRegistry;
    private ChunkEvaluator chunkEvaluator;
    private UnlockGui unlockGui;
    private HologramManager hologramManager;
    private PlayerListener playerListener;
    
    // Keep track of active tasks for cleanup
    private TickTask activeTickTask;
    
    // Block protection listener
    private BlockProtectionListener blockProtectionListener;
    
    // Glass border system
    private ChunkBorderManager chunkBorderManager;
    private me.chunklock.listeners.BorderListener borderListener;
    private me.chunklock.listeners.PlayerJoinQuitListener joinQuitListener;
    private me.chunklock.ui.UnlockGuiListener unlockGuiListener;
    private me.chunklock.border.BorderRefreshService borderRefreshService;

    // Enhanced team system components
    private EnhancedTeamManager enhancedTeamManager;
    private BasicTeamCommandHandler teamCommandHandler;

    @Override
    public void onEnable() {
    try {
        int pluginId = 19876; // Get this from bStats
        //Metrics metrics = new Metrics(this, pluginId);
        getLogger().info("Analytics initialized successfully");
    } catch (Exception e) {
        getLogger().info("Analytics disabled or failed to initialize");
    }

    try {
        instance = this;
        
        getLogger().info("=== Starting Chunklock Plugin Initialization ===");
        
        getLogger().info("Phase 1: Configuration validation...");
        if (!validateConfiguration()) {
            getLogger().severe("Invalid configuration detected - disabling plugin");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("✓ Configuration validation passed");
        
        getLogger().info("Phase 2: Component initialization...");
        if (!initializeComponents()) {
            getLogger().severe("Failed to initialize core components - disabling plugin");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("✓ Component initialization completed");
        
        getLogger().info("Phase 3: Event listener registration...");
        if (!registerEventListeners()) {
            getLogger().severe("Failed to register event listeners - disabling plugin");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("✓ Event listeners registered");
        
        getLogger().info("Phase 4: Background task startup...");
        if (!startTasks()) {
            getLogger().warning("Some tasks failed to start - plugin may have reduced functionality");
        } else {
            getLogger().info("✓ Background tasks started");
        }
        
        getLogger().info("Phase 5: Command registration...");
        if (!registerCommands()) {
            getLogger().warning("Failed to register some commands - some functionality may be unavailable");
        } else {
            getLogger().info("✓ Commands registered successfully");
        }
        
        getLogger().info("=== Chunklock Plugin Initialization Complete ===");
        getLogger().info("Plugin enabled successfully with enhanced team system, protection, and glass borders!");
        
    } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Critical error during plugin enable", e);
        Bukkit.getPluginManager().disablePlugin(this);
    }
}

    /**
     * Validates plugin configuration before startup
     */
    private boolean validateConfiguration() {
        try {
            // Check if required config files can be created
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                getLogger().severe("Cannot create plugin data folder");
                return false;
            }

            // Validate server version compatibility
            String version = Bukkit.getVersion();
            if (!version.contains("1.21")) {
                getLogger().warning("This plugin was designed for Minecraft 1.21.4+. Current version: " + version);
                getLogger().warning("Some features may not work correctly");
            }

            // Check available memory
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            if (maxMemory < 1073741824) { // 1GB
                getLogger().warning("Low memory detected (" + (maxMemory / 1048576) + "MB). Consider increasing heap size for better performance");
            }

            getLogger().info("Configuration validation passed");

            // Migrate legacy configuration/data files if necessary
            try {
                new DataMigrator(this).migrate();
            } catch (Exception e) {
                getLogger().warning("Migration step failed: " + e.getMessage());
            }

            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during configuration validation", e);
            return false;
        }
    }

    /**
     * Performs a hot reload of the plugin without restarting the server
     */
    public boolean performReload(CommandSender sender) {
        try {
            sender.sendMessage(Component.text("Step 1/8: Cleaning up existing systems...").color(NamedTextColor.GRAY));
            
            // 1. Stop and cleanup existing systems
            if (hologramManager != null) {
                hologramManager.cleanup();
            }
            
            if (chunkBorderManager != null) {
                chunkBorderManager.cleanup();
            }
            
            if (activeTickTask != null && !activeTickTask.isCancelled()) {
                activeTickTask.cancel();
            }

            // 2. Save all current data before reload
            sender.sendMessage(Component.text("Step 2/8: Saving current data...").color(NamedTextColor.GRAY));
            saveAllData();

            // 3. Reinitialize core components
            sender.sendMessage(Component.text("Step 3/8: Reinitializing components...").color(NamedTextColor.GRAY));
            boolean componentsOk = initializeComponents();
            if (!componentsOk) {
                sender.sendMessage(Component.text("Warning: Some components failed to reinitialize").color(NamedTextColor.YELLOW));
            }

            // 3.5. Reload border configuration
            sender.sendMessage(Component.text("Step 3.5/8: Reloading border configuration...").color(NamedTextColor.GRAY));
            if (chunkBorderManager != null) {
                chunkBorderManager.reloadConfiguration();
            }

            // 4. Restart background tasks
            sender.sendMessage(Component.text("Step 4/8: Restarting background tasks...").color(NamedTextColor.GRAY));
            boolean tasksOk = startTasks();
            if (!tasksOk) {
                sender.sendMessage(Component.text("Warning: Some tasks failed to restart").color(NamedTextColor.YELLOW));
            }

            // 5. Restart visual effects for online players
            sender.sendMessage(Component.text("Step 5/8: Restarting visual effects...").color(NamedTextColor.GRAY));
            restartVisualEffects();

            // 6. Restart team system
            sender.sendMessage(Component.text("Step 6/8: Restarting team system...").color(NamedTextColor.GRAY));
            // Team system automatically reloads with component initialization

            // 7. Refresh borders for all players
            sender.sendMessage(Component.text("Step 7/8: Refreshing glass borders...").color(NamedTextColor.GRAY));
            if (chunkBorderManager != null && chunkBorderManager.isEnabled()) {
                chunkBorderManager.refreshAllBorders();
            }

            // 8. Validate reload success
            sender.sendMessage(Component.text("Step 8/8: Validating reload...").color(NamedTextColor.GRAY));
            
            boolean success = validateReload();
            
            // Notify all online players
            Component reloadMessage = Component.text("Chunklock plugin has been reloaded by an admin.").color(NamedTextColor.YELLOW);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(reloadMessage);
            }
            
            getLogger().info("Plugin reload completed " + (success ? "successfully" : "with warnings"));
            return success;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin reload", e);
            sender.sendMessage(Component.text("Reload failed: " + e.getMessage()).color(NamedTextColor.RED));
            return false;
        }
    }

    private void restartVisualEffects() {
        if (hologramManager != null) {
            // Restart holograms for all online players
            for (Player player : Bukkit.getOnlinePlayers()) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    hologramManager.startHologramDisplay(player);
                }, 20L); // 1 second delay
            }
        }
    }

    private boolean validateReload() {
        try {
            // Check that all core components are properly initialized
            boolean valid = chunkLockManager != null &&
                           biomeUnlockRegistry != null &&
                           progressTracker != null &&
                           teamManager != null &&
                           playerDataManager != null &&
                           chunkValueRegistry != null &&
                           chunkEvaluator != null &&
                           unlockGui != null &&
                           hologramManager != null &&
                           playerListener != null &&
                           blockProtectionListener != null &&
                           chunkBorderManager != null &&
                           enhancedTeamManager != null &&
                           teamCommandHandler != null;
            
            if (!valid) {
                getLogger().warning("Reload validation failed: Some components are null");
                return false;
            }
            
            // Test a basic operation
            int totalChunks = chunkLockManager.getTotalUnlockedChunks();
            
            // Test team system
            int totalTeams = enhancedTeamManager.getAllTeams().size();
            
            getLogger().info("Reload validation passed. Total unlocked chunks: " + totalChunks + ", Teams: " + totalTeams);
            
            return true;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Reload validation failed", e);
            return false;
        }
    }

    private boolean initializeComponents() {
    try {
        getLogger().info("=== Starting Component Initialization ===");
        
        // Initialize in dependency order with detailed logging
        getLogger().info("Step 1: Initializing ChunkValueRegistry...");
        this.chunkValueRegistry = new ChunkValueRegistry(this);
        getLogger().info("✓ ChunkValueRegistry initialized: " + (chunkValueRegistry != null));
        
        getLogger().info("Step 2: Initializing EnhancedTeamManager...");
        this.enhancedTeamManager = new EnhancedTeamManager(this);
        this.teamCommandHandler = new BasicTeamCommandHandler(enhancedTeamManager);
        getLogger().info("✓ Enhanced team system initialized: " + (enhancedTeamManager != null));
        
        getLogger().info("Step 3: Initializing TeamManager (legacy)...");
        this.teamManager = new TeamManager(this);
        getLogger().info("✓ TeamManager initialized: " + (teamManager != null));
        
        getLogger().info("Step 4: Initializing PlayerProgressTracker...");
        this.progressTracker = new PlayerProgressTracker(this, teamManager);
        getLogger().info("✓ PlayerProgressTracker initialized: " + (progressTracker != null));
        
        getLogger().info("Step 5: Initializing PlayerDataManager...");
        this.playerDataManager = new PlayerDataManager(this);
        getLogger().info("✓ PlayerDataManager initialized: " + (playerDataManager != null));
        
        getLogger().info("Step 6: Initializing BiomeUnlockRegistry...");
        this.biomeUnlockRegistry = new BiomeUnlockRegistry(this, progressTracker);
        getLogger().info("✓ BiomeUnlockRegistry initialized: " + (biomeUnlockRegistry != null));
        
        getLogger().info("Step 7: Initializing ChunkEvaluator...");
        this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry);
        getLogger().info("✓ ChunkEvaluator initialized: " + (chunkEvaluator != null));
        
        getLogger().info("Step 8: Initializing ChunkLockManager...");
        this.chunkLockManager = new ChunkLockManager(chunkEvaluator, this, teamManager);
        getLogger().info("✓ ChunkLockManager initialized: " + (chunkLockManager != null));
        
        getLogger().info("Step 9: Initializing UnlockGui...");
        this.unlockGui = new UnlockGui(chunkLockManager, biomeUnlockRegistry, progressTracker, teamManager);
        getLogger().info("✓ UnlockGui initialized: " + (unlockGui != null));
        
        getLogger().info("Step 10: Initializing HologramManager...");
        this.hologramManager = new HologramManager(chunkLockManager, biomeUnlockRegistry);
        getLogger().info("✓ HologramManager initialized: " + (hologramManager != null));
        
        getLogger().info("Step 11: Initializing PlayerListener...");
        this.playerListener = new PlayerListener(chunkLockManager, progressTracker, playerDataManager, unlockGui);
        getLogger().info("✓ PlayerListener initialized: " + (playerListener != null));

        getLogger().info("Step 12: Initializing ChunkBorderManager...");
        this.chunkBorderManager = new ChunkBorderManager(chunkLockManager, unlockGui, teamManager, progressTracker);
        getLogger().info("✓ ChunkBorderManager initialized: " + (chunkBorderManager != null));

        this.borderRefreshService = new me.chunklock.border.BorderRefreshService(chunkBorderManager);
        this.playerListener.setBorderRefreshService(borderRefreshService);
        this.joinQuitListener = new me.chunklock.listeners.PlayerJoinQuitListener(playerListener);
        this.unlockGuiListener = new me.chunklock.ui.UnlockGuiListener(unlockGui);
        this.borderListener = new me.chunklock.listeners.BorderListener(chunkBorderManager);

        getLogger().info("Step 13: Initializing BlockProtectionListener...");
        this.blockProtectionListener = new BlockProtectionListener(chunkLockManager, unlockGui, chunkBorderManager);
        getLogger().info("✓ BlockProtectionListener initialized: " + (blockProtectionListener != null));


        
        // Set up team integration in BiomeUnlockRegistry
        try {
            getLogger().info("Step 14: Setting up team integration...");
            biomeUnlockRegistry.setEnhancedTeamManager(enhancedTeamManager);
            getLogger().info("✓ Team integration enabled in BiomeUnlockRegistry");
        } catch (Exception e) {
            getLogger().warning("Failed to enable team integration in BiomeUnlockRegistry: " + e.getMessage());
        }
        
        getLogger().info("=== Component Initialization Summary ===");
        getLogger().info("progressTracker: " + (progressTracker != null ? "OK" : "NULL"));
        getLogger().info("chunkLockManager: " + (chunkLockManager != null ? "OK" : "NULL"));
        getLogger().info("unlockGui: " + (unlockGui != null ? "OK" : "NULL"));
        getLogger().info("teamManager: " + (teamManager != null ? "OK" : "NULL"));
        getLogger().info("biomeUnlockRegistry: " + (biomeUnlockRegistry != null ? "OK" : "NULL"));
        getLogger().info("playerDataManager: " + (playerDataManager != null ? "OK" : "NULL"));
        getLogger().info("teamCommandHandler: " + (teamCommandHandler != null ? "OK" : "NULL"));
        
        getLogger().info("All core components initialized successfully");
        return true;
        
    } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Error initializing components", e);
        return false;
    }
}

    private boolean registerEventListeners() {
        try {
            // Register all event listeners
            Bukkit.getPluginManager().registerEvents(playerListener, this);
            Bukkit.getPluginManager().registerEvents(joinQuitListener, this);
            Bukkit.getPluginManager().registerEvents(unlockGuiListener, this);
            
            // Register the block protection listener
            Bukkit.getPluginManager().registerEvents(blockProtectionListener, this);

            // Register the glass border listener
            Bukkit.getPluginManager().registerEvents(borderListener, this);
            
            // Register main plugin events (join/quit handlers)
            Bukkit.getPluginManager().registerEvents(this, this);
            
            getLogger().info("Event listeners registered successfully (including enhanced team, block protection, and glass borders)");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering event listeners", e);
            return false;
        }
    }

    private boolean startTasks() {
        try {
            // Cancel existing task if it exists
            if (activeTickTask != null && !activeTickTask.isCancelled()) {
                activeTickTask.cancel();
            }
            
            // Start new task
            activeTickTask = new TickTask(chunkLockManager, biomeUnlockRegistry);
            activeTickTask.runTaskTimer(this, 0L, 2L);
            
            getLogger().info("Background tasks started successfully");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error starting background tasks", e);
            return false;
        }
    }

// Update for ChunklockPlugin.java - replace the registerCommands() method

private boolean registerCommands() {
    try {
        getLogger().info("=== Starting Command Registration ===");
        
        // Count how many dependencies are available
        int availableDependencies = 0;
        int totalDependencies = 6;
        
        if (progressTracker != null) {
            getLogger().info("✓ PlayerProgressTracker available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ PlayerProgressTracker is NULL");
        }
        
        if (chunkLockManager != null) {
            getLogger().info("✓ ChunkLockManager available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ ChunkLockManager is NULL");
        }
        
        if (unlockGui != null) {
            getLogger().info("✓ UnlockGui available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ UnlockGui is NULL");
        }
        
        if (teamManager != null) {
            getLogger().info("✓ TeamManager available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ TeamManager is NULL");
        }
        
        if (biomeUnlockRegistry != null) {
            getLogger().info("✓ BiomeUnlockRegistry available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ BiomeUnlockRegistry is NULL");
        }
        
        if (playerDataManager != null) {
            getLogger().info("✓ PlayerDataManager available");
            availableDependencies++;
        } else {
            getLogger().severe("✗ PlayerDataManager is NULL");
        }
        
        getLogger().info("Dependencies available: " + availableDependencies + "/" + totalDependencies);
        
        if (availableDependencies == totalDependencies) {
            getLogger().info("All dependencies available. Using new command system...");
            
            // Create command executor with EXACT parameter order matching constructor
            var chunklockCmd = new me.chunklock.commands.ChunklockCommandExecutor(
                progressTracker,        // PlayerProgressTracker
                chunkLockManager,       // ChunkLockManager
                unlockGui,              // UnlockGui
                teamManager,            // TeamManager
                teamCommandHandler,     // BasicTeamCommandHandler
                biomeUnlockRegistry,    // BiomeUnlockRegistry
                playerDataManager);     // PlayerDataManager
            
            if (getCommand("chunklock") != null) {
                getCommand("chunklock").setExecutor(chunklockCmd);
                getCommand("chunklock").setTabCompleter(chunklockCmd);
                getLogger().info("✓ New command system registered successfully");
                return true;
            }
        } else {
            getLogger().warning("Not all dependencies available. Falling back to legacy command system...");
            
            // Fallback to legacy system if available
            try {
                var legacyCmd = new ChunklockCommand(progressTracker, chunkLockManager, unlockGui, teamManager, teamCommandHandler, biomeUnlockRegistry);
                
                if (getCommand("chunklock") != null) {
                    getCommand("chunklock").setExecutor(legacyCmd);
                    getCommand("chunklock").setTabCompleter(legacyCmd);
                    getLogger().warning("Using legacy command system due to missing dependencies");
                    return true;
                }
            } catch (Exception fallbackError) {
                getLogger().log(Level.SEVERE, "Legacy command system also failed", fallbackError);
            }
        }
        
        getLogger().severe("Both new and legacy command systems failed to initialize");
        return false;
        
    } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Critical error during command registration", e);
        return false;
    }
}

    // Enhanced hologram management events
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Start holograms for joining player after a short delay
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (event.getPlayer().isOnline() && hologramManager != null) {
                    hologramManager.startHologramDisplay(event.getPlayer());
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error starting holograms for joining player " + event.getPlayer().getName(), e);
            }
        }, 20L); // 1 second delay
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up holograms for leaving player
        try {
            if (hologramManager != null) {
                hologramManager.stopHologramDisplay(event.getPlayer());
            }
            
            // Clean up block protection listener data
            if (blockProtectionListener != null) {
                blockProtectionListener.cleanupPlayer(event.getPlayer().getUniqueId());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error cleaning up for leaving player " + event.getPlayer().getName(), e);
        }
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Disabling Chunklock plugin...");
            
            // Clean up visual effects
            if (hologramManager != null) {
                hologramManager.cleanup();
            }
            
            // Clean up glass borders
            if (chunkBorderManager != null) {
                chunkBorderManager.cleanup();
            }
            
            // Cancel background tasks
            if (activeTickTask != null && !activeTickTask.isCancelled()) {
                activeTickTask.cancel();
            }
            
            // Save all data
            saveAllData();
            
            // Clear static references
            instance = null;
            
            getLogger().info("Chunklock plugin disabled successfully");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable", e);
        }
    }

    private void saveAllData() {
        int saveErrors = 0;
        
        if (playerDataManager != null) {
            try {
                playerDataManager.saveAll();
                getLogger().info("Player data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving player data", e);
                saveErrors++;
            }
        }
        
        if (teamManager != null) {
            try {
                teamManager.saveAll();
                getLogger().info("Legacy team data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving legacy team data", e);
                saveErrors++;
            }
        }
        
        // Save enhanced team data
        if (enhancedTeamManager != null) {
            try {
                enhancedTeamManager.saveTeams();
                getLogger().info("Enhanced team data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving enhanced team data", e);
                saveErrors++;
            }
        }
        
        if (chunkLockManager != null) {
            try {
                chunkLockManager.saveAll();
                getLogger().info("Chunk data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving chunk data", e);
                saveErrors++;
            }
        }
        
        if (progressTracker != null) {
            try {
                progressTracker.saveAll();
                getLogger().info("Progress data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving progress data", e);
                saveErrors++;
            }
        }
        
        if (saveErrors > 0) {
            getLogger().warning("Encountered " + saveErrors + " errors while saving data");
        }
    }

    public static ChunklockPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ChunklockPlugin instance is null - plugin may not be loaded");
        }
        return instance;
    }
    
    // Safe getters with null checks and better error messages
    public ChunkLockManager getChunkLockManager() {
        if (chunkLockManager == null) {
            getLogger().warning("ChunkLockManager accessed before initialization");
            throw new IllegalStateException("ChunkLockManager not initialized");
        }
        return chunkLockManager;
    }
    
    public ChunkEvaluator getChunkEvaluator() {
        if (chunkEvaluator == null) {
            getLogger().warning("ChunkEvaluator accessed before initialization");
            throw new IllegalStateException("ChunkEvaluator not initialized");
        }
        return chunkEvaluator;
    }

    public UnlockGui getUnlockGui() {
        if (unlockGui == null) {
            getLogger().warning("UnlockGui accessed before initialization");
            throw new IllegalStateException("UnlockGui not initialized");
        }
        return unlockGui;
    }

    public PlayerDataManager getPlayerDataManager() {
        if (playerDataManager == null) {
            getLogger().warning("PlayerDataManager accessed before initialization");
            throw new IllegalStateException("PlayerDataManager not initialized");
        }
        return playerDataManager;
    }

    public TeamManager getTeamManager() {
        if (teamManager == null) {
            getLogger().warning("TeamManager accessed before initialization");
            throw new IllegalStateException("TeamManager not initialized");
        }
        return teamManager;
    }

    public HologramManager getHologramManager() {
        if (hologramManager == null) {
            getLogger().warning("HologramManager accessed before initialization");
            throw new IllegalStateException("HologramManager not initialized");
        }
        return hologramManager;
    }

    public TickTask getTickTask() {
        return activeTickTask;
    }

    public PlayerListener getPlayerListener() {
        if (playerListener == null) {
            getLogger().warning("PlayerListener accessed before initialization");
            throw new IllegalStateException("PlayerListener not initialized");
        }
        return playerListener;
    }

    public BlockProtectionListener getBlockProtectionListener() {
        if (blockProtectionListener == null) {
            getLogger().warning("BlockProtectionListener accessed before initialization");
            throw new IllegalStateException("BlockProtectionListener not initialized");
        }
        return blockProtectionListener;
    }

    public ChunkBorderManager getChunkBorderManager() {
        if (chunkBorderManager == null) {
            getLogger().warning("ChunkBorderManager accessed before initialization");
            throw new IllegalStateException("ChunkBorderManager not initialized");
        }
        return chunkBorderManager;
    }

    // Enhanced team system getters
    public EnhancedTeamManager getEnhancedTeamManager() {
        if (enhancedTeamManager == null) {
            getLogger().warning("EnhancedTeamManager accessed before initialization");
            throw new IllegalStateException("EnhancedTeamManager not initialized");
        }
        return enhancedTeamManager;
    }

    public BasicTeamCommandHandler getTeamCommandHandler() {
        if (teamCommandHandler == null) {
            getLogger().warning("BasicTeamCommandHandler accessed before initialization");
            throw new IllegalStateException("BasicTeamCommandHandler not initialized");
        }
        return teamCommandHandler;
    }

    /**
     * Get comprehensive plugin statistics for debugging
     */
    public String getPluginStats() {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("=== Chunklock Plugin Statistics ===\n");
            
            if (chunkLockManager != null) {
                stats.append("Total unlocked chunks: ").append(chunkLockManager.getTotalUnlockedChunks()).append("\n");
            }
            
            // Enhanced team statistics
            if (enhancedTeamManager != null) {
                var allTeams = enhancedTeamManager.getAllTeams();
                stats.append("Total teams: ").append(allTeams.size()).append("\n");
                int totalTeamMembers = allTeams.stream().mapToInt(team -> team.getTotalMembers()).sum();
                int totalTeamChunks = allTeams.stream().mapToInt(team -> team.getTotalChunksUnlocked()).sum();
                stats.append("Total team members: ").append(totalTeamMembers).append("\n");
                stats.append("Total team chunks unlocked: ").append(totalTeamChunks).append("\n");
            }
            
            if (activeTickTask != null) {
                var tickStats = activeTickTask.getCacheStats();
                stats.append("TickTask cache size: ").append(tickStats.get("cacheSize")).append("\n");
                stats.append("Particles spawned: ").append(tickStats.get("particlesSpawned")).append("\n");
                stats.append("Cache hits: ").append(tickStats.get("cacheHits")).append("\n");
            }
            
            if (playerListener != null) {
                var listenerStats = playerListener.getPlayerListenerStats();
                stats.append("Players with warning cooldown: ").append(listenerStats.get("playersWithWarningCooldown")).append("\n");
                stats.append("Players with unlock cooldown: ").append(listenerStats.get("playersWithUnlockCooldown")).append("\n");
            }
            
            if (blockProtectionListener != null) {
                var protectionStats = blockProtectionListener.getProtectionStats();
                stats.append("Players with protection warning cooldown: ").append(protectionStats.get("playersWithWarningCooldown")).append("\n");
            }
            
            // Glass border statistics
            if (chunkBorderManager != null) {
                var borderStats = chunkBorderManager.getBorderStats();
                stats.append("Players with glass borders: ").append(borderStats.get("playersWithBorders")).append("\n");
                stats.append("Total border blocks: ").append(borderStats.get("totalBorderBlocks")).append("\n");
                stats.append("Border system enabled: ").append(borderStats.get("enabled")).append("\n");
            }
            
            // Memory usage
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            stats.append("Memory usage: ").append(usedMemory / 1048576).append("MB / ")
                 .append(runtime.maxMemory() / 1048576).append("MB\n");
            
            stats.append("Online players: ").append(Bukkit.getOnlinePlayers().size()).append("\n");
            
            return stats.toString();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error generating plugin statistics", e);
            return "Error generating statistics: " + e.getMessage();
        }
    }
}