package me.chunklock.hologram;

import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.config.HologramConfiguration;
import me.chunklock.hologram.display.HologramDisplayService;
import me.chunklock.hologram.display.HologramTaskManager;
import me.chunklock.hologram.provider.FancyHologramsProvider;
import me.chunklock.hologram.tracking.HologramTracker;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.WorldManager;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

/**
 * Main hologram service that coordinates all hologram-related functionality.
 * This is the primary interface for the rest of the plugin to interact with holograms.
 */
public final class HologramService {

    private final HologramConfiguration config;
    private final HologramProvider provider;
    private final HologramTracker tracker;
    private final HologramDisplayService displayService;
    private final HologramTaskManager taskManager;
    private final boolean available;

    private HologramService(Builder builder) {
        this.config = new HologramConfiguration(ChunklockPlugin.getInstance());
        this.provider = initializeProvider();
        this.tracker = new HologramTracker();
        this.displayService = new HologramDisplayService(
            provider, tracker, config, 
            builder.chunkLockManager, builder.biomeUnlockRegistry, builder.worldManager);
        this.taskManager = new HologramTaskManager(displayService, config, builder.worldManager);
        this.available = provider.isAvailable();
        
        logInitializationStatus();
    }

    /**
     * Creates a new HologramService instance.
     */
    public static HologramService create(ChunkLockManager chunkLockManager,
                                       BiomeUnlockRegistry biomeUnlockRegistry,
                                       WorldManager worldManager) {
        return new Builder()
            .chunkLockManager(chunkLockManager)
            .biomeUnlockRegistry(biomeUnlockRegistry)
            .worldManager(worldManager)
            .build();
    }

    /**
     * Checks if hologram functionality is available and enabled.
     */
    public boolean isAvailable() {
        return available && config.isEnabled();
    }

    /**
     * Starts hologram display for a player.
     */
    public void startHologramDisplay(Player player) {
        if (!isAvailable()) {
            return;
        }
        taskManager.startPlayerTask(player);
    }

    /**
     * Stops hologram display for a player.
     */
    public void stopHologramDisplay(Player player) {
        if (!isAvailable()) {
            return;
        }
        taskManager.stopPlayerTask(player);
    }

    /**
     * Force cleanup of holograms for a specific chunk when it's unlocked.
     */
    public void forceCleanupChunk(Player player, Chunk chunk) {
        if (!isAvailable()) {
            return;
        }
        displayService.removeHologramForChunk(player, chunk);
    }

    /**
     * Force refresh holograms for a specific player.
     */
    public void refreshHologramsForPlayer(Player player) {
        if (!isAvailable()) {
            return;
        }
        taskManager.refreshPlayerHolograms(player);
    }

    /**
     * Cleanup all holograms and tasks.
     */
    public void cleanup() {
        taskManager.cleanup();
        if (provider != null) {
            provider.cleanup();
        }
    }

    /**
     * Gets comprehensive hologram statistics for debugging.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = taskManager.getStatistics();
        stats.put("hologramService", Map.of(
            "available", isAvailable(),
            "enabled", config.isEnabled(),
            "provider", provider != null ? provider.getProviderName() : "None",
            "configuredProvider", config.getProvider()
        ));
        return stats;
    }

    /**
     * Gets the current hologram configuration.
     */
    public HologramConfiguration getConfiguration() {
        return config;
    }

    /**
     * Initializes the appropriate hologram provider based on configuration.
     */
    private HologramProvider initializeProvider() {
        if (!config.isEnabled()) {
            ChunklockPlugin.getInstance().getLogger().info(
                "Holograms disabled in configuration - hologram functionality disabled");
            return createNullProvider();
        }

        if (config.isProviderDisabled()) {
            ChunklockPlugin.getInstance().getLogger().info(
                "Hologram provider set to 'none' - hologram functionality disabled");
            return createNullProvider();
        }

        // Try FancyHolograms
        HologramProvider fancyProvider = new FancyHologramsProvider();
        if (fancyProvider.isAvailable()) {
            return fancyProvider;
        }

        // No providers available
        if (config.isAutoDetection()) {
            ChunklockPlugin.getInstance().getLogger().warning(
                "No supported hologram providers found. Install FancyHolograms for hologram functionality.");
        } else {
            ChunklockPlugin.getInstance().getLogger().warning(
                "Configured hologram provider '" + config.getProvider() + 
                "' is not available. Install FancyHolograms for hologram functionality.");
        }

        return createNullProvider();
    }

    /**
     * Creates a null provider that does nothing.
     */
    private HologramProvider createNullProvider() {
        return new HologramProvider() {
            @Override
            public String getProviderName() { return "None"; }
            
            @Override
            public boolean isAvailable() { return false; }
            
            @Override
            public Optional<me.chunklock.hologram.api.Hologram> createHologram(
                me.chunklock.hologram.display.HologramData hologramData) {
                return Optional.empty();
            }
            
            @Override
            public boolean removeHologram(me.chunklock.hologram.api.Hologram hologram) { 
                return false; 
            }
            
            @Override
            public boolean updateHologram(me.chunklock.hologram.api.Hologram hologram, 
                                        me.chunklock.hologram.display.HologramData newData) { 
                return false; 
            }
            
            @Override
            public void cleanup() {}
            
            @Override
            public Map<String, Object> getStatistics() { 
                return Map.of("provider", "None", "available", false); 
            }
        };
    }

    /**
     * Logs the initialization status.
     */
    private void logInitializationStatus() {
        ChunklockPlugin.getInstance().getLogger().info("🔍 Hologram Service Initialization:");
        ChunklockPlugin.getInstance().getLogger().info("  ├─ Configuration Enabled: " + config.isEnabled());
        ChunklockPlugin.getInstance().getLogger().info("  ├─ Configured Provider: " + config.getProvider());
        ChunklockPlugin.getInstance().getLogger().info("  ├─ Active Provider: " + provider.getProviderName());
        ChunklockPlugin.getInstance().getLogger().info("  ├─ Provider Available: " + provider.isAvailable());
        ChunklockPlugin.getInstance().getLogger().info("  └─ Service Available: " + isAvailable());

        if (isAvailable()) {
            ChunklockPlugin.getInstance().getLogger().info("✅ Hologram system: ACTIVE (" + provider.getProviderName() + ")");
        } else {
            ChunklockPlugin.getInstance().getLogger().info("🔇 Hologram system: DISABLED");
        }
    }

    /**
     * Builder for HologramService.
     */
    public static final class Builder {
        private ChunkLockManager chunkLockManager;
        private BiomeUnlockRegistry biomeUnlockRegistry;
        private WorldManager worldManager;

        public Builder chunkLockManager(ChunkLockManager chunkLockManager) {
            this.chunkLockManager = chunkLockManager;
            return this;
        }

        public Builder biomeUnlockRegistry(BiomeUnlockRegistry biomeUnlockRegistry) {
            this.biomeUnlockRegistry = biomeUnlockRegistry;
            return this;
        }

        public Builder worldManager(WorldManager worldManager) {
            this.worldManager = worldManager;
            return this;
        }

        public HologramService build() {
            if (chunkLockManager == null || biomeUnlockRegistry == null || worldManager == null) {
                throw new IllegalStateException("All dependencies must be provided");
            }
            return new HologramService(this);
        }
    }
}
