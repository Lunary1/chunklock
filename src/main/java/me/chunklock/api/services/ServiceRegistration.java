package me.chunklock.api.services;

import me.chunklock.api.container.ServiceContainer;
import me.chunklock.api.impl.ChunkServiceImpl;
import me.chunklock.managers.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;

/**
 * Handles registration of all services in the dependency injection container.
 * This centralizes service configuration and dependency wiring.
 */
public class ServiceRegistration {
    
    private final ServiceContainer container;
    private final Logger logger;
    
    public ServiceRegistration(ServiceContainer container, JavaPlugin plugin, Logger logger) {
        this.container = container;
        this.logger = logger;
    }
    
    /**
     * Register all services with their dependencies.
     * This method should be called during plugin initialization, after managers are created.
     */
    public void registerServices(ChunkLockManager chunkLockManager,
                                ChunkEvaluator chunkEvaluator,
                                TeamManager teamManager,
                                EnhancedTeamManager enhancedTeamManager,
                                PlayerProgressTracker progressTracker,
                                me.chunklock.economy.EconomyManager economyManager,
                                me.chunklock.hologram.HologramService hologramService,
                                me.chunklock.ui.UnlockGui unlockGui) {
        
        logger.info("Registering services...");
        
        // Register ChunkService
        container.registerSingleton(ChunkService.class, () -> 
            new ChunkServiceImpl(chunkLockManager, chunkEvaluator, logger));
        
        // Phase 2: Register existing managers as services temporarily
        // This allows gradual migration from managers to services
        registerManager(ChunkLockManager.class, chunkLockManager);
        registerManager(ChunkEvaluator.class, chunkEvaluator);
        registerManager(TeamManager.class, teamManager);
        registerManager(EnhancedTeamManager.class, enhancedTeamManager);
        registerManager(PlayerProgressTracker.class, progressTracker);
        
        // Register other service implementations will be added incrementally
        // For now, we're establishing the service layer foundation
        
        logger.info("Service registration complete");
    }
    
    /**
     * Register a manager as a service directly.
     * This is a transitional approach while we migrate from managers to services.
     */
    public <T> void registerManager(Class<T> serviceClass, T managerInstance) {
        container.registerInstance(serviceClass, managerInstance);
        logger.info("Registered manager as service: " + serviceClass.getSimpleName());
    }
}