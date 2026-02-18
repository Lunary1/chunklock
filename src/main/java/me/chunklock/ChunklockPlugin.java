package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.managers.*;
import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.config.ConfigManager;
import me.chunklock.api.ChunklockAPI;
import me.chunklock.api.container.ServiceContainer;
import me.chunklock.api.services.ServiceManager;
import me.chunklock.api.services.ServiceRegistration;
import me.chunklock.util.validation.ConfigValidator;
import me.chunklock.util.validation.DataMigrator;
import me.chunklock.util.validation.DependencyChecker;
import me.chunklock.listeners.*;
import me.chunklock.services.StartingChunkService;
import me.chunklock.ui.UnlockGui;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.chunklock.bstats.Metrics;

public class ChunklockPlugin extends JavaPlugin implements Listener {

    private static ChunklockPlugin instance;
    
    // Configuration
    private ConfigManager configManager;
    
    // Service Layer (Phase 2)
    private ServiceContainer serviceContainer;
    private ServiceManager serviceManager;
    private ServiceRegistration serviceRegistration;
    
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
    
    // Economy system
    private me.chunklock.economy.EconomyManager economyManager;
    private me.chunklock.services.AsyncCostCalculationService asyncCostCalculationService;
    private me.chunklock.services.ChunkCostDatabase costDatabase;
    
    // Database system
    private me.chunklock.services.ChunkStore chunkDatabase;
    private me.chunklock.services.PlayerStore playerDatabase;
    private me.chunklock.services.DataMigrationService dataMigrationService;
    private me.chunklock.services.MySqlConnectionProvider mySqlConnectionProvider;
    
    // Dependency checker
    private DependencyChecker dependencyChecker;

    @Override
    public void onEnable() {
        try {
            instance = this;
            
            // Modern startup header
            logStartupHeader();
            
            // Check plugin dependencies early
            dependencyChecker = new DependencyChecker(this);
            dependencyChecker.checkAndLogDependencies();
            
            // Initialize configuration manager
            logSection("Configuration", "⚙️");
            configManager = new ConfigManager(this);
            if (!configManager.validateConfiguration()) {
                getLogger().severe("❌ Configuration validation failed");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("✅ Configuration loaded and validated");
            
            // CRITICAL: Validate and ensure complete config before anything else
            ConfigValidator configValidator = new ConfigValidator(this);
            configValidator.validateAndEnsureComplete();
            reloadConfig();
            
            if (configValidator.isConfigComplete()) {
                getLogger().info("✅ Configuration integrity verified");
            } else {
                getLogger().severe("❌ Configuration validation failed");
            }
            
            // Analytics and bStats metrics
            try {
                initializeBStats();
                getLogger().info("📊 bStats metrics initialized");
            } catch (Exception e) {
                getLogger().info("📊 bStats metrics disabled");
            }
            
            // Perform migration
            try {
                new DataMigrator(this).migrate();
            } catch (Exception e) {
                if (isDebugMode()) {
                    getLogger().warning("Migration step failed: " + e.getMessage());
                }
            }
            
            // Initialize components
            if (!initializeComponents()) {
                getLogger().severe("❌ Failed to initialize core components - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Register event listeners
            if (!registerEventListeners()) {
                getLogger().severe("❌ Failed to register event listeners - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Register commands
            if (!registerCommands()) {
                getLogger().warning("⚠️ Failed to register commands - some functionality may be unavailable");
            }
            
            // Initialize API (should be last)
            try {
                ChunklockAPI.initialize(this);
                getLogger().info("✅ API interface ready");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to initialize ChunklockAPI", e);
            }
            
            // Success summary
            logSuccessFooter();
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "❌ Critical error during plugin enable", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Display modern startup header
     */
    private void logStartupHeader() {
        String version = getPluginMeta().getVersion();
        getLogger().info("╔════════════════════════════════════════════════════════════════╗");
        getLogger().info("║                    🎮 CHUNKLOCK v" + String.format("%-8s", version) + "                     ║");
        getLogger().info("║              Strategic Chunk Progression System              ║");
        getLogger().info("║                      Initializing...                         ║");
        getLogger().info("╚════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Display section header for organization
     */
    private void logSection(String sectionName, String icon) {
        if (isDebugMode()) {
            getLogger().info("═══════════════════════════════════════════════════════════════════");
            getLogger().info(icon + " " + sectionName + " Setup");
            getLogger().info("═══════════════════════════════════════════════════════════════════");
        } else {
            getLogger().info(icon + " Initializing " + sectionName + "...");
        }
    }

    /**
     * Display success footer
     */
    private void logSuccessFooter() {
        getLogger().info("╔════════════════════════════════════════════════════════════════╗");
        getLogger().info("║                    📝 STARTUP COMPLETE ✨                    ║");
        if (worldManager != null && !worldManager.getEnabledWorlds().isEmpty()) {
            String worldsText = "Active Worlds: " + String.join(", ", worldManager.getEnabledWorlds());
            getLogger().info("║  " + String.format("%-59s", worldsText) + "  ║");
        }
        getLogger().info("║             Ready for strategic chunk progression!           ║");
        getLogger().info("║               Players can use /chunklock start               ║");
        getLogger().info("╚════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Check if debug mode is enabled for detailed logging
     */
    private boolean isDebugMode() {
        try {
            // Use modular debug config
            me.chunklock.config.modular.DebugConfig debugConfig = getConfigManager().getDebugConfig();
            return debugConfig != null ? debugConfig.isEnabled() : false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Initialize all plugin components
     */
    public boolean initializeComponents() {
        try {
            logSection("Core Systems", "⚠️");
            
            // Log compatibility information only in debug mode
            if (isDebugMode()) {
                getLogger().info("⚙️ Server Compatibility Check:");
                getLogger().info("  " + me.chunklock.util.ServerCompatibility.getCompatibilitySummary().replace("\n", "\n  "));
            }
            
            // Initialize databases FIRST (before managers that depend on them)
            logSection("Database System", "💾");
            me.chunklock.services.StorageFactory.StorageSelection storageSelection =
                me.chunklock.services.StorageFactory.createStores(this);
            if (storageSelection.isStartupFailure()) {
                return false;
            }

            this.chunkDatabase = storageSelection.getChunkStore();
            this.playerDatabase = storageSelection.getPlayerStore();
            this.mySqlConnectionProvider = storageSelection.getMySqlConnectionProvider();

            if (chunkDatabase == null || playerDatabase == null) {
                getLogger().severe("❌ Storage backends were not created");
                return false;
            }

            if (!chunkDatabase.initialize()) {
                getLogger().severe("❌ Failed to initialize chunk storage - plugin cannot continue");
                return false;
            }

            if (!playerDatabase.initialize()) {
                getLogger().severe("❌ Failed to initialize player storage - plugin cannot continue");
                return false;
            }

            if (storageSelection.isMysqlMode()) {
                me.chunklock.services.StorageFactory.StorageSelection mapDbBootstrap =
                    me.chunklock.services.StorageFactory.createMapDbStores(this);
                me.chunklock.services.ChunkStore mapDbChunkStore = mapDbBootstrap.getChunkStore();
                me.chunklock.services.PlayerStore mapDbPlayerStore = mapDbBootstrap.getPlayerStore();
                try {
                    if (!mapDbChunkStore.initialize() || !mapDbPlayerStore.initialize()) {
                        getLogger().warning("⚠️ Failed to initialize temporary MapDB stores for migration bootstrap");
                        java.io.File chunksDb = new java.io.File(getDataFolder(), "chunks.db");
                        java.io.File playersDb = new java.io.File(getDataFolder(), "players.db");
                        if (chunksDb.exists() || playersDb.exists()) {
                            getLogger().severe("❌ Existing MapDB files detected but migration bootstrap failed. Aborting startup to prevent data loss.");
                            return false;
                        }
                    } else {
                        me.chunklock.services.DataMigrationService mapDbDataMigration =
                            new me.chunklock.services.DataMigrationService(this, mapDbChunkStore, mapDbPlayerStore);
                        if (mapDbDataMigration.needsMigration()) {
                            getLogger().info("📦 Migrating legacy YAML data into MapDB before MySQL import...");
                            if (!mapDbDataMigration.migrate()) {
                                getLogger().warning("⚠️ YAML to MapDB migration had issues - check logs");
                            }
                        }

                        me.chunklock.services.MapDbToMySqlMigrationService mapDbToMySqlMigration =
                            new me.chunklock.services.MapDbToMySqlMigrationService(
                                this,
                                mapDbChunkStore,
                                mapDbPlayerStore,
                                chunkDatabase,
                                playerDatabase
                            );
                        if (mapDbToMySqlMigration.needsMigration() && !mapDbToMySqlMigration.migrate()) {
                            getLogger().severe("❌ MapDB to MySQL migration failed");
                            return false;
                        }
                    }
                } finally {
                    mapDbChunkStore.close();
                    mapDbPlayerStore.close();
                }
            } else {
                // Run YAML -> MapDB migration for non-MySQL storage mode
                this.dataMigrationService = new me.chunklock.services.DataMigrationService(this, chunkDatabase, playerDatabase);
                if (dataMigrationService.needsMigration()) {
                    getLogger().info("📦 Migrating data from YAML to MapDB...");
                    if (!dataMigrationService.migrate()) {
                        getLogger().warning("⚠️ Data migration had issues - check logs");
                    }
                }
            }
            
            // Initialize in dependency order
            this.worldManager = new WorldManager(this);
            this.chunkValueRegistry = new ChunkValueRegistry(this);
            this.enhancedTeamManager = new EnhancedTeamManager(this);
            this.teamCommandHandler = new BasicTeamCommandHandler(enhancedTeamManager);
            this.teamManager = new TeamManager(this);
            this.progressTracker = new PlayerProgressTracker(this, teamManager);
            this.playerDataManager = new PlayerDataManager(this);
            this.biomeUnlockRegistry = new BiomeUnlockRegistry(this, progressTracker);
            this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry, getLogger());
            this.chunkLockManager = new ChunkLockManager(chunkEvaluator, this, teamManager);
            
            // NEW: Initialize chunk pre-allocation service BEFORE SingleWorldManager (it needs this service)
            this.chunkPreAllocationService = new me.chunklock.services.ChunkPreAllocationService(chunkLockManager, this);
            
            // Now safe to initialize SingleWorldManager since ChunkPreAllocationService is ready
            this.singleWorldManager = new me.chunklock.managers.SingleWorldManager(this);
            
            // Initialize economy manager after biome registry and chunk evaluator
            this.economyManager = new me.chunklock.economy.EconomyManager(this, biomeUnlockRegistry, progressTracker, chunkEvaluator);
            
            // Initialize chunk cost database for persistent caching
            this.costDatabase = new me.chunklock.services.ChunkCostDatabase(this);
            try {
                if (!costDatabase.initialize()) {
                    getLogger().warning("⚠️ Failed to initialize cost database - performance may be reduced");
                }
            } catch (Exception e) {
                getLogger().warning("⚠️ Failed to initialize cost database - performance may be reduced");
            }
            
            // Initialize async cost calculation service for improved GUI performance
            this.asyncCostCalculationService = new me.chunklock.services.AsyncCostCalculationService(this, economyManager, chunkLockManager);
            
            this.startingChunkService = new StartingChunkService(chunkLockManager, playerDataManager);
            this.startingChunkService.setPreAllocationService(chunkPreAllocationService);
            
            this.progressionValidationService = new me.chunklock.services.ProgressionValidationService(this);
            this.unlockGui = new UnlockGui(this, chunkLockManager, biomeUnlockRegistry, progressTracker, teamManager, economyManager);
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
            
            getLogger().info("✅ Core systems initialized");
            
            // NEW Phase 2: Initialize Service Layer
            initializeServiceLayer();
            
            // Start progression validation task for player worlds
            startProgressionValidationTask();
            
            getLogger().info("✅ Component initialization complete");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "❌ Error initializing components", e);
            return false;
        }
    }

    /**
     * Initialize the service layer (Phase 2 implementation)
     */
    private void initializeServiceLayer() {
        try {
            logSection("Service Layer", "⚙️");
            
            // Initialize service container
            ServiceContainer.initialize(getLogger());
            serviceContainer = ServiceContainer.getInstance();
            
            // Create service manager
            serviceManager = new ServiceManager(serviceContainer, getLogger());
            
            // Create service registration
            serviceRegistration = new ServiceRegistration(serviceContainer, this, getLogger());
            
            // Register services with their dependencies
            serviceRegistration.registerServices(
                chunkLockManager,
                chunkEvaluator,
                teamManager,
                enhancedTeamManager,
                progressTracker,
                economyManager,
                hologramService,
                unlockGui
            );
            
            // Initialize all services
            serviceManager.initializeServices();
            
            // Start all services
            serviceManager.startServices();
            
            getLogger().info("✅ Service layer ready (" + serviceManager.getServiceCount() + " services)");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "❌ Failed to initialize service layer", e);
            throw new RuntimeException("Service layer initialization failed", e);
        }
    }

    /**
     * Register all event listeners
     */
    public boolean registerEventListeners() {
        try {
            logSection("Event Listeners", "👂");
            
            Bukkit.getPluginManager().registerEvents(playerListener, this);
            Bukkit.getPluginManager().registerEvents(joinQuitListener, this);
            Bukkit.getPluginManager().registerEvents(unlockGuiListener, this);
            Bukkit.getPluginManager().registerEvents(blockProtectionListener, this);
            Bukkit.getPluginManager().registerEvents(borderListener, this);
            Bukkit.getPluginManager().registerEvents(teleportListener, this);
            Bukkit.getPluginManager().registerEvents(inventoryChangeListener, this);
            Bukkit.getPluginManager().registerEvents(this, this);
            
            getLogger().info("✅ Event system ready");
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "❌ Error registering event listeners", e);
            return false;
        }
    }

    /**
     * Register plugin commands
     */
    public boolean registerCommands() {
        try {
            logSection("Commands", "💬");
            
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
                    getLogger().info("✅ Command system ready");
                    return true;
                }
            }
            
            getLogger().severe("❌ Command registration failed - missing dependencies");
            return false;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "❌ Error during command registration", e);
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

            // Ensure reload is idempotent (avoid duplicate listeners/tasks)
            HandlerList.unregisterAll((org.bukkit.plugin.Plugin) this);
            Bukkit.getScheduler().cancelTasks(this);
            
            // Save all data
            saveAllData();
            
            // Reinitialize
            boolean success = initializeComponents() && registerEventListeners();
            
            if (success) {
                // Reload debug configurations for all components
                if (unlockGui != null) unlockGui.reloadConfiguration();
                if (startingChunkService != null) startingChunkService.reloadConfiguration();
                if (chunkPreAllocationService != null) chunkPreAllocationService.reloadConfiguration();
                if (economyManager != null) economyManager.reloadConfiguration();
                
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

    /**
     * Shutdown the service layer (Phase 2 implementation)
     */
    private void shutdownServiceLayer() {
        try {
            getLogger().info("⚙️ Phase 2: Shutting down Service Layer...");
            
            if (serviceManager != null) {
                serviceManager.stopServices();
                getLogger().info("✅ Services stopped successfully");
            }
            
            if (serviceContainer != null) {
                serviceContainer.shutdown();
                getLogger().info("✅ Service Container shutdown successfully");
            }
            
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error during service layer shutdown", e);
        }
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Disabling Chunklock plugin...");
            
            // Phase 2: Shutdown Service Layer
            shutdownServiceLayer();
            
            // Shutdown API
            ChunklockAPI.shutdown();
            
            if (hologramService != null) hologramService.cleanup();
            if (chunkBorderManager != null) chunkBorderManager.cleanup();
            if (chunkPreAllocationService != null) chunkPreAllocationService.stop(); // NEW: Stop pre-allocation service
            if (costDatabase != null) costDatabase.close(); // Close database connection
            
            saveAllData();
            instance = null;
            
            getLogger().info("Chunklock plugin disabled successfully");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable", e);
        }
    }

    private void saveAllData() {
        int saveErrors = 0;
        
        // Storage backends auto-save on write, but we ensure resources close cleanly
        
        try { if (teamManager != null) teamManager.saveAll(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving team data", e); saveErrors++; }
        
        try { if (enhancedTeamManager != null) enhancedTeamManager.saveTeams(); } 
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error saving enhanced team data", e); saveErrors++; }
        
        // Close databases
        try { if (chunkDatabase != null) chunkDatabase.close(); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error closing chunk store", e); saveErrors++; }
        
        try { if (playerDatabase != null) playerDatabase.close(); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error closing player store", e); saveErrors++; }

        try { if (mySqlConnectionProvider != null) mySqlConnectionProvider.close(); }
        catch (Exception e) { getLogger().log(Level.SEVERE, "Error closing MySQL connection pool", e); saveErrors++; }
        
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

    public me.chunklock.services.ChunkStore getChunkDatabase() {
        if (chunkDatabase == null) throw new IllegalStateException("ChunkStore not initialized");
        return chunkDatabase;
    }

    public me.chunklock.services.PlayerStore getPlayerDatabase() {
        if (playerDatabase == null) throw new IllegalStateException("PlayerStore not initialized");
        return playerDatabase;
    }

    public me.chunklock.services.MySqlConnectionProvider getMySqlConnectionProvider() {
        // Nullable - only present when MySQL mode is active
        return mySqlConnectionProvider;
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
    
    public me.chunklock.economy.EconomyManager getEconomyManager() {
        if (economyManager == null) throw new IllegalStateException("EconomyManager not initialized");
        return economyManager;
    }

    public me.chunklock.services.AsyncCostCalculationService getAsyncCostCalculationService() {
        if (asyncCostCalculationService == null) throw new IllegalStateException("AsyncCostCalculationService not initialized");
        return asyncCostCalculationService;
    }

    public me.chunklock.services.ChunkCostDatabase getCostDatabase() {
        if (costDatabase == null) throw new IllegalStateException("ChunkCostDatabase not initialized");
        return costDatabase;
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
    
    public DependencyChecker getDependencyChecker() {
        if (dependencyChecker == null) throw new IllegalStateException("DependencyChecker not initialized");
        return dependencyChecker;
    }

    // Phase 2: Service Layer Access Methods
    
    /**
     * Get a service instance from the service container.
     * @param serviceClass The service class to retrieve
     * @return The service instance
     * @throws IllegalStateException if services are not initialized
     */
    public <T extends me.chunklock.api.services.BaseService> T getService(Class<T> serviceClass) {
        if (serviceManager == null || !serviceManager.isInitialized()) {
            throw new IllegalStateException("Service layer not initialized");
        }
        return serviceManager.getService(serviceClass);
    }
    
    /**
     * Get the service manager for advanced service operations.
     * @return The service manager instance
     */
    public ServiceManager getServiceManager() {
        if (serviceManager == null) {
            throw new IllegalStateException("Service manager not initialized");
        }
        return serviceManager;
    }
    
    /**
     * Check if the service layer is healthy.
     * @return true if all services are healthy, false otherwise
     */
    public boolean isServiceLayerHealthy() {
        return serviceManager != null && serviceManager.isStarted() && serviceManager.checkHealth();
    }

    /**
     * Start the progression validation task (disabled for single world system)
     */
    private void startProgressionValidationTask() {
        // Progression validation not needed for single world system
        if (isDebugMode()) {
            getLogger().info("Progression validation disabled for single world system");
        }
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
    
    /**
     * Initialize bStats metrics for the plugin
     */
    private void initializeBStats() {
        // Plugin ID for Chunklock on bStats.org
        int pluginId = 26163; // Official Chunklock plugin ID
        
        // Simple bStats initialization - just basic metrics for now
        new Metrics(this, pluginId);
        
        getLogger().info("bStats metrics initialized successfully");
    }
    
    /**
     * Gets the configuration manager.
     * 
     * @return The configuration manager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
}
