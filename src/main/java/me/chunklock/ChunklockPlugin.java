package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import me.chunklock.UnlockGui;

public class ChunklockPlugin extends JavaPlugin {

    private static ChunklockPlugin instance;
    private ChunkLockManager chunkLockManager;
    private BiomeUnlockRegistry biomeUnlockRegistry;
    private PlayerProgressTracker progressTracker;
    private PlayerDataManager playerDataManager;
    private ChunkValueRegistry chunkValueRegistry;
    private ChunkEvaluator chunkEvaluator;
    private UnlockGui unlockGui;

    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize registries first
        this.chunkValueRegistry = new ChunkValueRegistry(this);
        this.biomeUnlockRegistry = new BiomeUnlockRegistry(this);
        this.progressTracker = new PlayerProgressTracker(this);
        this.playerDataManager = new PlayerDataManager(this);
        
        // Initialize evaluator and manager
        this.chunkEvaluator = new ChunkEvaluator(playerDataManager, chunkValueRegistry);
        this.chunkLockManager = new ChunkLockManager(chunkEvaluator);

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(chunkLockManager, progressTracker, playerDataManager), this);
        Bukkit.getPluginManager().registerEvents(new UnlockItemListener(chunkLockManager, biomeUnlockRegistry, progressTracker), this);
        this.unlockGui = new UnlockGui(chunkLockManager, biomeUnlockRegistry, progressTracker);
        Bukkit.getPluginManager().registerEvents(unlockGui, this);

        // Start tick task for visual effects
        new TickTask(chunkLockManager, biomeUnlockRegistry).runTaskTimer(this, 0L, 10L);
        
        // Register commands
        var chunklockCmd = new ChunklockCommand(progressTracker, chunkLockManager, unlockGui);
        getCommand("chunklock").setExecutor(chunklockCmd);
        getCommand("chunklock").setTabCompleter(chunklockCmd);
        
        getLogger().info("Chunklock plugin enabled with ChunkEvaluator integration!");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAll();
        }
        getLogger().info("Chunklock plugin disabled.");
    }

    public static ChunklockPlugin getInstance() {
        return instance;
    }
    
    // Getters for other classes to access components
    public ChunkLockManager getChunkLockManager() {
        return chunkLockManager;
    }
    
    public ChunkEvaluator getChunkEvaluator() {
        return chunkEvaluator;
    }

    public UnlockGui getUnlockGui() {
        return unlockGui;
    }
}