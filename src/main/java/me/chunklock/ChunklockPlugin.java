package me.chunklock;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

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
    
    // New: Block protection listener
    private BlockProtectionListener blockProtectionListener;

    @Override
    public void onEnable() {
        try {
            int pluginId = 19876; // Get this from bStats
            Metrics metrics = new Metrics(this, pluginId);
            getLogger().info("Analytics initialized successfully");
        } catch (Exception e) {
            getLogger().info("Analytics disabled or failed to initialize");
        }

        try {
            instance = this;
            
            getLogger().info("Starting Chunklock plugin initialization...");
            
            if (!validateConfiguration()) {
                getLogger().severe("Invalid configuration detected - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            if (!initializeComponents()) {
                getLogger().severe("Failed to initialize core components - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            if (!registerEventListeners()) {
                getLogger().severe("Failed to register event listeners - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            if (!startTasks()) {
                getLogger().warning("Some tasks failed to start - plugin may have reduced functionality");
            }
            
            if (!registerCommands()) {
                getLogger().warning("Failed to register some commands - some functionality may be unavailable");
            }
            
            getLogger().info("Chunklock plugin enabled successfully with enhanced protection and visual effects!");
            
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
            sender.sendMessage(Component.text("Step 1/6: Cleaning up existing systems...").color(NamedTextColor.GRAY));
            
            // 1. Stop and cleanup existing systems
            if (hologramManager != null) {
                hologramManager.cleanup();
            }
            
            if (activeTickTask != null && !activeTickTask.isCancelled()) {
                activeTickTask.cancel();
            }

            // 2. Save all current data before reload
            sender.sendMessage(Component.text("Step 2/6: Saving current data...").color(NamedTextColor.GRAY));
            saveAllData();

            // 3. Reinitialize core components
            sender.sendMessage(Component.text("Step 3/6: Reinitializing components...").color(NamedTextColor.GRAY));
            boolean componentsOk = initializeComponents();
            if (!componentsOk) {
                sender.sendMessage(Component.text("Warning: Some components failed to reinitialize").color(NamedTextColor.YELLOW));
            }

            // 4. Restart background tasks
            sender.sendMessage(Component.text("Step 4/6: Restarting background tasks...").color(NamedTextColor.GRAY));
            boolean tasksOk = startTasks();
            if (!tasksOk) {
                sender.sendMessage(Component.text("Warning: Some tasks failed to restart").color(NamedTextColor.YELLOW));
            }

            // 5. Restart visual effects for online players
            sender.sendMessage(Component.text("Step 5/6: Restarting visual effects...").color(NamedTextColor.GRAY));
            restartVisualEffects();

            // 6. Validate reload success
            sender.sendMessage(Component.text("Step 6/6: Validating reload...").color(NamedTextColor.GRAY));
            
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
                           blockProtectionListener != null; // New component check
            
            if (!valid) {
                getLogger().warning("Reload validation failed: Some components are null");
                return false;
            }
            
            // Test a basic operation
            int totalChunks = chunkLockManager.getTotalUnlockedChunks();
            getLogger().info("Reload validation passed. Total unlocked chunks: " + totalChunks);
            
            return true;
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Reload validation failed", e);
            return false;
        }
    }

    private boolean initializeComponents() {
        try {
            // Initialize in dependency order
            this.chunkValueRegistry = new ChunkValueRegistry(this);
            this.teamManager = new TeamManager(this);
            this.progressTracker = new PlayerProgressTracker(this, teamManager);
            this.biomeUnlockRegistry = new BiomeUnlockRegistry(this, progressTracker);
            this.playerDataManager = new PlayerDataManager(this);
            this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry);
            this.chunkLockManager = new ChunkLockManager(chunkEvaluator, this);
            this.unlockGui = new UnlockGui(chunkLockManager, biomeUnlockRegistry, progressTracker);
            this.hologramManager = new HologramManager(chunkLockManager, biomeUnlockRegistry);
            this.playerListener = new PlayerListener(chunkLockManager, progressTracker, playerDataManager, unlockGui);
            
            // New: Initialize block protection listener
            this.blockProtectionListener = new BlockProtectionListener(chunkLockManager, unlockGui);
            
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
            Bukkit.getPluginManager().registerEvents(
                new UnlockItemListener(chunkLockManager, biomeUnlockRegistry, progressTracker), this);
            Bukkit.getPluginManager().registerEvents(unlockGui, this);
            
            // Register the new block protection listener
            Bukkit.getPluginManager().registerEvents(blockProtectionListener, this);
            
            // Register main plugin events (join/quit handlers)
            Bukkit.getPluginManager().registerEvents(this, this);
            
            getLogger().info("Event listeners registered successfully (including block protection)");
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

    private boolean registerCommands() {
        try {
            var chunklockCmd = new ChunklockCommand(progressTracker, chunkLockManager, unlockGui, teamManager);
            
            if (getCommand("chunklock") != null) {
                getCommand("chunklock").setExecutor(chunklockCmd);
                getCommand("chunklock").setTabCompleter(chunklockCmd);
                getLogger().info("Commands registered successfully");
                return true;
            } else {
                getLogger().warning("chunklock command not found in plugin.yml");
                return false;
            }
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error registering commands", e);
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
                getLogger().info("Team data saved successfully");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Error saving team data", e);
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

    // New getter for block protection listener
    public BlockProtectionListener getBlockProtectionListener() {
        if (blockProtectionListener == null) {
            getLogger().warning("BlockProtectionListener accessed before initialization");
            throw new IllegalStateException("BlockProtectionListener not initialized");
        }
        return blockProtectionListener;
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