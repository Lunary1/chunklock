package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.managers.*;
import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.util.ConfigValidator;
import me.chunklock.util.DataMigrator;
import me.chunklock.listeners.*;
import me.chunklock.services.StartingChunkService;
import me.chunklock.ui.UnlockGui;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ChunklockPlugin extends JavaPlugin implements Listener {

    private static ChunklockPlugin instance;
    
    // Core managers
    private ChunkLockManager chunkLockManager;
    private BiomeUnlockRegistry biomeUnlockRegistry;
    private PlayerProgressTracker progressTracker;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private ChunkValueRegistry chunkValueRegistry;
    private ChunkEvaluator chunkEvaluator;
    private WorldManager worldManager;
    private me.chunklock.managers.SingleWorldManager singleWorldManager;
    
    // UI and services
    private UnlockGui unlockGui;
    private me.chunklock.hologram.HologramService hologramService;
    private StartingChunkService startingChunkService;
    private me.chunklock.services.ChunkPreAllocationService chunkPreAllocationService; // NEW: Performance optimization
    private me.chunklock.services.ProgressionValidationService progressionValidationService;
    
    // Listeners
    private PlayerListener playerListener;
    private BlockProtectionListener blockProtectionListener;
    private TeleportListener teleportListener;
    private me.chunklock.listeners.InventoryChangeListener inventoryChangeListener;
    
    // Border system
    private ChunkBorderManager chunkBorderManager;
    private me.chunklock.listeners.BorderListener borderListener;
    private me.chunklock.listeners.PlayerJoinQuitListener joinQuitListener;
    private me.chunklock.ui.UnlockGuiListener unlockGuiListener;
    private me.chunklock.border.BorderRefreshService borderRefreshService;

    // Enhanced team system
    private EnhancedTeamManager enhancedTeamManager;
    private BasicTeamCommandHandler teamCommandHandler;

    @Override
    public void onEnable() {
        try {
            instance = this;
            
            getLogger().info("=== Starting Chunklock v" + getPluginMeta().getVersion() + " ===");
            
            // CRITICAL: Validate and ensure complete config before anything else
            ConfigValidator configValidator = new ConfigValidator(this);
            configValidator.validateAndEnsureComplete();
            reloadConfig();
            
            if (configValidator.isConfigComplete()) {
                getLogger().info("✅ Configuration validation passed");
            } else {
                getLogger().severe("❌ Configuration validation failed");
            }
            
            // Analytics (optional)
            try {
                // Metrics disabled for now
                //int pluginId = 19876;
                //Metrics metrics = new Metrics(this, pluginId);
                getLogger().info("Analytics initialized successfully");
            } catch (Exception e) {
                getLogger().info("Analytics disabled or failed to initialize");
            }
            
            // Perform migration
            try {
                new DataMigrator(this).migrate();
            } catch (Exception e) {
                getLogger().warning("Migration step failed: " + e.getMessage());
            }
            
            // Initialize components
            if (!initializeComponents()) {
                getLogger().severe("Failed to initialize core components - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Register event listeners
            if (!registerEventListeners()) {
                getLogger().severe("Failed to register event listeners - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Register commands
            if (!registerCommands()) {
                getLogger().warning("Failed to register commands - some functionality may be unavailable");
            }
            
            // Final summary
            getLogger().info("=== Initialization Complete ===");
            getLogger().info("✅ Core systems: Loaded");
            if (worldManager != null) {
                getLogger().info("🌍 Active worlds: " + worldManager.getEnabledWorlds());
            }
            getLogger().info("🎉 Chunklock enabled successfully!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin enable", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Initialize all plugin components
     */
    public boolean initializeComponents() {
        try {
            getLogger().info("Initializing core components...");
            
            // Log compatibility information
            getLogger().info("🔧 Server Compatibility Check:");
            getLogger().info("  " + me.chunklock.util.ServerCompatibility.getCompatibilitySummary().replace("\n", "\n  "));
            
            // Initialize in dependency order
            this.singleWorldManager = new me.chunklock.managers.SingleWorldManager(this);
            this.worldManager = new WorldManager(this);
            this.chunkValueRegistry = new ChunkValueRegistry(this);
            this.enhancedTeamManager = new EnhancedTeamManager(this);
            this.teamCommandHandler = new BasicTeamCommandHandler(enhancedTeamManager);
            this.teamManager = new TeamManager(this);
            this.progressTracker = new PlayerProgressTracker(this, teamManager);
            this.playerDataManager = new PlayerDataManager(this);
            this.biomeUnlockRegistry = new BiomeUnlockRegistry(this, progressTracker);
            this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry);
            this.chunkLockManager = new ChunkLockManager(chunkEvaluator, this, teamManager);
            this.startingChunkService = new StartingChunkService(chunkLockManager, playerDataManager);
            
            // NEW: Initialize chunk pre-allocation service for performance
            this.chunkPreAllocationService = new me.chunklock.services.ChunkPreAllocationService(chunkLockManager, this);
            this.startingChunkService.setPreAllocationService(chunkPreAllocationService);
            
            this.progressionValidationService = new me.chunklock.services.ProgressionValidationService(this);
            this.unlockGui = new UnlockGui(chunkLockManager, biomeUnlockRegistry, progressTracker, teamManager);
            this.hologramService = me.chunklock.hologram.HologramService.create(chunkLockManager, biomeUnlockRegistry, worldManager);
            this.playerListener = new PlayerListener(chunkLockManager, progressTracker, playerDataManager, unlockGui);
            this.chunkBorderManager = new ChunkBorderManager(chunkLockManager, unlockGui, teamManager, progressTracker);
            this.borderRefreshService = new me.chunklock.border.BorderRefreshService(chunkBorderManager);
            this.playerListener.setBorderRefreshService(borderRefreshService);
            this.joinQuitListener = new me.chunklock.listeners.PlayerJoinQuitListener(playerListener);
            this.unlockGuiListener = new me.chunklock.ui.UnlockGuiListener(unlockGui);
            this.borderListener = new me.chunklock.listeners.BorderListener(chunkBorderManager);
            this.blockProtectionListener = new BlockProtectionListener(chunkLockManager, unlockGui, chunkBorderManager);
            this.teleportListener = new TeleportListener(worldManager, playerDataManager, startingChunkService);
            this.inventoryChangeListener = new me.chunklock.listeners.InventoryChangeListener(this);
            
            // Set up team integration
            biomeUnlockRegistry.setEnhancedTeamManager(enhancedTeamManager);
            
            // Start progression validation task for player worlds
            startProgressionValidationTask();
            
            getLogger().info("✅ All components initialized successfully");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing components", e);
            return false;
        }
    }

    /**
     * Register all event listeners
     */
    public boolean registerEventListeners() {
        try {
            getLogger().info("Registering event listeners...");
            
            Bukkit.getPluginManager().registerEvents(playerListener, this);
            Bukkit.getPluginManager().registerEvents(joinQuitListener, this);
            Bukkit.getPluginManager().registerEvents(unlockGuiListener, this);
            Bukkit.getPluginManager().registerEvents(blockProtectionListener, this);
            Bukkit.getPluginManager().registerEvents(borderListener, this);
            Bukkit.getPluginManager().registerEvents(teleportListener, this);
            Bukkit.getPluginManager().registerEvents(inventoryChangeListener, this);
            Bukkit.getPluginManager().registerEvents(this, this);
            
            getLogger().info("✅ Event listeners registered successfully");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering event listeners", e);
            return false;
        }
    }

    /**
     * Register plugin commands
     */
    public boolean registerCommands() {
        try {
            getLogger().info("Registering commands...");
            
            // Verify all dependencies are available
            if (progressTracker != null && chunkLockManager != null && unlockGui != null && 
                teamManager != null && biomeUnlockRegistry != null && playerDataManager != null &&
                singleWorldManager != null) {
                
                var chunklockCmd = new me.chunklock.commands.ChunklockCommandExecutor(
                    progressTracker, chunkLockManager, unlockGui, teamManager, 
                    teamCommandHandler, biomeUnlockRegistry, playerDataManager, singleWorldManager);
                
                if (getCommand("chunklock") != null) {
                    getCommand("chunklock").setExecutor(chunklockCmd);
                    getCommand("chunklock").setTabCompleter(chunklockCmd);
                    getLogger().info("✅ Commands registered successfully");
                    return true;
                }
            }
            
            getLogger().severe("Command registration failed - missing dependencies");
            return false;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during command registration", e);
            return false;
        }
    }

    /**
     * Performs a hot reload of the plugin without restarting the server
     */
    public boolean performReload(CommandSender sender) {
        try {
            sender.sendMessage(Component.text("🔄 Reloading Chunklock...").color(NamedTextColor.YELLOW));
            
            // Cleanup existing systems
            if (hologramService != null) hologramService.cleanup();
            if (chunkBorderManager != null) chunkBorderManager.cleanup();
            
            // Save all data
            saveAllData();
            
            // Reinitialize
            boolean success = initializeComponents() && registerEventListeners();
            
            if (success) {
                // Reload debug configurations for all components
                if (unlockGui != null) unlockGui.reloadConfiguration();
                if (startingChunkService != null) startingChunkService.reloadConfiguration();
                if (chunkPreAllocationService != null) chunkPreAllocationService.reloadConfiguration();
                
                // Restart visual effects
                restartVisualEffects();
                
                // Refresh borders
                if (chunkBorderManager != null && chunkBorderManager.isEnabled()) {
                    chunkBorderManager.refreshAllBorders();
                }
                
                sender.sendMessage(Component.text("✅ Reload completed successfully!").color(NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("⚠️ Reload completed with warnings").color(NamedTextColor.YELLOW));
            }
            
            return success;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin reload", e);
            sender.sendMessage(Component.text("❌ Reload failed: " + e.getMessage()).color(NamedTextColor.RED));
            return false;
        }
    }

    private void restartVisualEffects() {
        if (hologramService != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    hologramService.updateActiveHologramsForPlayer(player);
                }, 20L);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!worldManager.isWorldEnabled(event.getPlayer().getWorld())) {
            return;
        }
        
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                if (event.getPlayer().isOnline() && hologramService != null) {
                    hologramService.updateActiveHologramsForPlayer(event.getPlayer());
                }
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error starting holograms for " + event.getPlayer().getName(), e);
            }
        }, 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            if (hologramService != null) {
                hologramService.despawnPlayerHolograms(event.getPlayer());
            }
            if (blockProtectionListener != null) {
                blockProtectionListener.cleanupPlayer(event.getPlayer().getUniqueId());
            }
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error cleaning up for " + event.getPlayer().getName(), e);
        }
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Disabling Chunklock plugin...");
            
            if (hologramService != null) hologramService.cleanup();
            if (chunkBorderManager != null) chunkBorderManager.cleanup();
            if (chunkPreAllocationService != null) chunkPreAllocationService.stop(); // NEW: Stop pre-allocation service
            
            saveAllData();
            instance = null;
            
            getLogger().info("Chunklock plugin disabled successfully");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable", e);
        }
    }

    private void saveAllData() {
        int saveErrors = 0;
        
        try { if (playerDataManager != null) playerDataManager.saveAll(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving player data", e); saveErrors++; }
        
        try { if (teamManager != null) teamManager.saveAll(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving team data", e); saveErrors++; }
        
        try { if (enhancedTeamManager != null) enhancedTeamManager.saveTeams(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving enhanced team data", e); saveErrors++; }
        
        try { if (chunkLockManager != null) chunkLockManager.saveAll(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving chunk data", e); saveErrors++; }
        
        try { if (progressTracker != null) progressTracker.saveAll(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving progress data", e); saveErrors++; }
        
        if (saveErrors > 0) {
            getLogger().warning("Encountered " + saveErrors + " errors while saving data");
        }
    }

    // Static getters and component accessors
    public static ChunklockPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ChunklockPlugin instance is null - plugin may not be loaded");
        }
        return instance;
    }
    
    // Safe getters with null checks
    public ChunkLockManager getChunkLockManager() {
        if (chunkLockManager == null) throw new IllegalStateException("ChunkLockManager not initialized");
        return chunkLockManager;
    }
    
    public ChunkEvaluator getChunkEvaluator() {
        if (chunkEvaluator == null) throw new IllegalStateException("ChunkEvaluator not initialized");
        return chunkEvaluator;
    }

    public UnlockGui getUnlockGui() {
        if (unlockGui == null) throw new IllegalStateException("UnlockGui not initialized");
        return unlockGui;
    }

    public PlayerDataManager getPlayerDataManager() {
        if (playerDataManager == null) throw new IllegalStateException("PlayerDataManager not initialized");
        return playerDataManager;
    }

    public TeamManager getTeamManager() {
        if (teamManager == null) throw new IllegalStateException("TeamManager not initialized");
        return teamManager;
    }

    public me.chunklock.hologram.HologramService getHologramService() {
        if (hologramService == null) throw new IllegalStateException("HologramService not initialized");
        return hologramService;
    }

    public PlayerListener getPlayerListener() {
        if (playerListener == null) throw new IllegalStateException("PlayerListener not initialized");
        return playerListener;
    }

    public BlockProtectionListener getBlockProtectionListener() {
        if (blockProtectionListener == null) throw new IllegalStateException("BlockProtectionListener not initialized");
        return blockProtectionListener;
    }

    public ChunkBorderManager getChunkBorderManager() {
        if (chunkBorderManager == null) throw new IllegalStateException("ChunkBorderManager not initialized");
        return chunkBorderManager;
    }

    public me.chunklock.services.ChunkPreAllocationService getChunkPreAllocationService() {
        if (chunkPreAllocationService == null) throw new IllegalStateException("ChunkPreAllocationService not initialized");
        return chunkPreAllocationService;
    }

    public WorldManager getWorldManager() {
        if (worldManager == null) throw new IllegalStateException("WorldManager not initialized");
        return worldManager;
    }

    public me.chunklock.managers.SingleWorldManager getSingleWorldManager() {
        if (singleWorldManager == null) throw new IllegalStateException("SingleWorldManager not initialized");
        return singleWorldManager;
    }

    public StartingChunkService getStartingChunkService() {
        if (startingChunkService == null) throw new IllegalStateException("StartingChunkService not initialized");
        return startingChunkService;
    }

    public EnhancedTeamManager getEnhancedTeamManager() {
        if (enhancedTeamManager == null) throw new IllegalStateException("EnhancedTeamManager not initialized");
        return enhancedTeamManager;
    }

    public BasicTeamCommandHandler getTeamCommandHandler() {
        if (teamCommandHandler == null) throw new IllegalStateException("BasicTeamCommandHandler not initialized");
        return teamCommandHandler;
    }

    public BiomeUnlockRegistry getBiomeUnlockRegistry() {
        if (biomeUnlockRegistry == null) throw new IllegalStateException("BiomeUnlockRegistry not initialized");
        return biomeUnlockRegistry;
    }

    public me.chunklock.services.ProgressionValidationService getProgressionValidationService() {
        if (progressionValidationService == null) throw new IllegalStateException("ProgressionValidationService not initialized");
        return progressionValidationService;
    }

    /**
     * Start the progression validation task (disabled for single world system)
     */
    private void startProgressionValidationTask() {
        // Progression validation not needed for single world system
        getLogger().info("Progression validation disabled for single world system");
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
            
            if (enhancedTeamManager != null) {
                var allTeams = enhancedTeamManager.getAllTeams();
                stats.append("Total teams: ").append(allTeams.size()).append("\n");
                int totalTeamMembers = allTeams.stream().mapToInt(team -> team.getTotalMembers()).sum();
                int totalTeamChunks = allTeams.stream().mapToInt(team -> team.getTotalChunksUnlocked()).sum();
                stats.append("Total team members: ").append(totalTeamMembers).append("\n");
                stats.append("Total team chunks unlocked: ").append(totalTeamChunks).append("\n");
            }
            
            if (worldManager != null) {
                var enabledWorlds = worldManager.getEnabledWorlds();
                stats.append("Enabled worlds: ").append(enabledWorlds).append("\n");
                stats.append("Auto-assign on world change: ").append(worldManager.isAutoAssignOnWorldChangeEnabled()).append("\n");
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
            
            if (chunkBorderManager != null) {
                var borderStats = chunkBorderManager.getBorderStats();
                stats.append("Players with glass borders: ").append(borderStats.get("playersWithBorders")).append("\n");
                stats.append("Total border blocks: ").append(borderStats.get("totalBorderBlocks")).append("\n");
                stats.append("Border system enabled: ").append(borderStats.get("enabled")).append("\n");
            }
            
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