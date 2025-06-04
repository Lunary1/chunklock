package me.chunklock;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunklockPlugin extends JavaPlugin {

    private static ChunklockPlugin instance;
    private ChunkLockManager chunkLockManager;
    private BiomeUnlockRegistry biomeUnlockRegistry;
    private PlayerProgressTracker progressTracker;
    private PlayerDataManager playerDataManager;

    @Override
    public void onEnable() {
        instance = this;
        this.chunkLockManager = new ChunkLockManager();
        this.biomeUnlockRegistry = new BiomeUnlockRegistry(this);
        this.progressTracker = new PlayerProgressTracker();
        this.playerDataManager = new PlayerDataManager(this);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(chunkLockManager, progressTracker, playerDataManager), this);
        Bukkit.getPluginManager().registerEvents(new UnlockItemListener(chunkLockManager, biomeUnlockRegistry, progressTracker), this);

        new TickTask(chunkLockManager, biomeUnlockRegistry).runTaskTimer(this, 0L, 10L); // every 10 ticks
        getLogger().info("Chunklock plugin enabled.");
        getCommand("chunklock").setExecutor(new ChunklockCommand(progressTracker));
        
    }

    @Override
    public void onDisable() {
        playerDataManager.saveAll();
        getLogger().info("Chunklock plugin disabled.");
    }

    public static ChunklockPlugin getInstance() {
        return instance;
    }
}