package me.chunklock.api.services;

import me.chunklock.api.container.ServiceContainer;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Manages the lifecycle of all services in the Chunklock plugin.
 * Handles initialization, startup, health monitoring, and shutdown.
 */
public class ServiceManager {
    
    private final ServiceContainer container;
    private final Logger logger;
    private final Map<Class<?>, BaseService> services = new ConcurrentHashMap<>();
    private boolean initialized = false;
    private boolean started = false;
    
    public ServiceManager(ServiceContainer container, Logger logger) {
        this.container = container;
        this.logger = logger;
    }
    
    /**
     * Initialize all registered services.
     * This should be called after all services are registered.
     */
    public void initializeServices() {
        if (initialized) {
            logger.warning("Services already initialized");
            return;
        }
        
        logger.info("Initializing services...");
        
        for (Class<?> serviceClass : container.getRegisteredServices()) {
            try {
                Object service = container.getService(serviceClass);
                if (service instanceof BaseService) {
                    BaseService baseService = (BaseService) service;
                    services.put(serviceClass, baseService);
                    baseService.initialize();
                    logger.info("Initialized service: " + baseService.getServiceName());
                }
            } catch (Exception e) {
                logger.severe("Failed to initialize service: " + serviceClass.getSimpleName() + " - " + e.getMessage());
                throw new RuntimeException("Service initialization failed", e);
            }
        }
        
        initialized = true;
        logger.info("All services initialized successfully");
    }
    
    /**
     * Start all initialized services.
     * This should be called after initializeServices().
     */
    public void startServices() {
        if (!initialized) {
            throw new IllegalStateException("Services must be initialized before starting");
        }
        
        if (started) {
            logger.warning("Services already started");
            return;
        }
        
        logger.info("Starting services...");
        
        for (BaseService service : services.values()) {
            try {
                service.start();
                logger.info("Started service: " + service.getServiceName());
            } catch (Exception e) {
                logger.severe("Failed to start service: " + service.getServiceName() + " - " + e.getMessage());
                throw new RuntimeException("Service startup failed", e);
            }
        }
        
        started = true;
        logger.info("All services started successfully");
    }
    
    /**
     * Stop all services in reverse order of startup.
     */
    public void stopServices() {
        if (!started) {
            logger.info("Services not started, nothing to stop");
            return;
        }
        
        logger.info("Stopping services...");
        
        List<BaseService> serviceList = new ArrayList<>(services.values());
        
        // Stop services in reverse order
        for (int i = serviceList.size() - 1; i >= 0; i--) {
            BaseService service = serviceList.get(i);
            try {
                service.stop();
                logger.info("Stopped service: " + service.getServiceName());
            } catch (Exception e) {
                logger.warning("Error stopping service: " + service.getServiceName() + " - " + e.getMessage());
            }
        }
        
        services.clear();
        started = false;
        initialized = false;
        
        logger.info("All services stopped");
    }
    
    /**
     * Get a service instance by class.
     */
    public <T extends BaseService> T getService(Class<T> serviceClass) {
        if (!initialized) {
            throw new IllegalStateException("Services not initialized");
        }
        return container.getService(serviceClass);
    }
    
    /**
     * Check the health of all services.
     */
    public boolean checkHealth() {
        if (!started) {
            return false;
        }
        
        boolean allHealthy = true;
        
        for (BaseService service : services.values()) {
            try {
                if (!service.isHealthy()) {
                    logger.warning("Unhealthy service detected: " + service.getServiceName() + " - " + service.getHealthStatus());
                    allHealthy = false;
                }
            } catch (Exception e) {
                logger.warning("Error checking health for service: " + service.getServiceName() + " - " + e.getMessage());
                allHealthy = false;
            }
        }
        
        return allHealthy;
    }
    
    /**
     * Get health status report for all services.
     */
    public Map<String, String> getHealthReport() {
        Map<String, String> report = new ConcurrentHashMap<>();
        
        for (BaseService service : services.values()) {
            try {
                report.put(service.getServiceName(), service.getHealthStatus());
            } catch (Exception e) {
                report.put(service.getServiceName(), "Error: " + e.getMessage());
            }
        }
        
        return report;
    }
    
    /**
     * Check if services are initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Check if services are started.
     */
    public boolean isStarted() {
        return started;
    }
    
    /**
     * Get the number of registered services.
     */
    public int getServiceCount() {
        return services.size();
    }
}