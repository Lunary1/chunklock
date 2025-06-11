package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Level;

public class ChunklockPlugin extends JavaPlugin {

    private static ChunklockPlugin instance;
    private ChunkLockManager chunkLockManager;
    private BiomeUnlockRegistry biomeUnlockRegistry;
    private PlayerProgressTracker progressTracker;
    private TeamManager teamManager;
    private PlayerDataManager playerDataManager;
    private ChunkValueRegistry chunkValueRegistry;
    private ChunkEvaluator chunkEvaluator;
    private UnlockGui unlockGui;

    @Override
    public void onEnable() {
        try {
            instance = this;
            
            getLogger().info("Starting Chunklock plugin initialization...");
            
            // Initialize components with error handling
            if (!initializeComponents()) {
                getLogger().severe("Failed to initialize core components - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Register event listeners with error handling
            if (!registerEventListeners()) {
                getLogger().severe("Failed to register event listeners - disabling plugin");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
            
            // Start tasks with error handling
            if (!startTasks()) {
                getLogger().warning("Some tasks failed to start - plugin may have reduced functionality");
            }
            
            // Register commands with error handling
            if (!registerCommands()) {
                getLogger().warning("Failed to register some commands - some functionality may be unavailable");
            }
            
            getLogger().info("Chunklock plugin enabled successfully!");
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Critical error during plugin enable", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private boolean initializeComponents() {
        try {
            this.chunkValueRegistry = new ChunkValueRegistry(this);
            this.teamManager = new TeamManager(this);
            this.progressTracker = new PlayerProgressTracker(this, teamManager);
            this.biomeUnlockRegistry = new BiomeUnlockRegistry(this, progressTracker);
            this.playerDataManager = new PlayerDataManager(this);
            this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry);
            this.chunkLockManager = new ChunkLockManager(chunkEvaluator, this);
            this.unlockGui = new UnlockGui(chunkLockManager, biomeUnlockRegistry, progressTracker);
            
            getLogger().info("All core components initialized successfully");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error initializing components", e);
            return false;
        }
    }

    private boolean registerEventListeners() {
        try {
            Bukkit.getPluginManager().registerEvents(
                new PlayerListener(chunkLockManager, progressTracker, playerDataManager, unlockGui), this);
            Bukkit.getPluginManager().registerEvents(
                new UnlockItemListener(chunkLockManager, biomeUnlockRegistry, progressTracker), this);
            Bukkit.getPluginManager().registerEvents(unlockGui, this);
            
            getLogger().info("Event listeners registered successfully");
            return true;
            
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error registering event listeners", e);
            return false;
        }
    }

    private boolean startTasks() {
        try {
            new TickTask(chunkLockManager, biomeUnlockRegistry).runTaskTimer(this, 0L, 10L);
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

    @Override
    public void onDisable() {
        try {
            getLogger().info("Disabling Chunklock plugin...");
            
            // Save all data with error handling
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
    
    // Safe getters with null checks
    public ChunkLockManager getChunkLockManager() {
        if (chunkLockManager == null) {
            getLogger().warning("ChunkLockManager accessed before initialization");
        }
        return chunkLockManager;
    }
    
    public ChunkEvaluator getChunkEvaluator() {
        if (chunkEvaluator == null) {
            getLogger().warning("ChunkEvaluator accessed before initialization");
        }
        return chunkEvaluator;
    }

    public UnlockGui getUnlockGui() {
        if (unlockGui == null) {
            getLogger().warning("UnlockGui accessed before initialization");
        }
        return unlockGui;
    }

    public PlayerDataManager getPlayerDataManager() {
        if (playerDataManager == null) {
            getLogger().warning("PlayerDataManager accessed before initialization");
        }
        return playerDataManager;
    }

    public TeamManager getTeamManager() {
        if (teamManager == null) {
            getLogger().warning("TeamManager accessed before initialization");
        }
        return teamManager;
    }
}