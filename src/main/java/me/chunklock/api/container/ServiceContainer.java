package me.chunklock.api.container;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Simple dependency injection container for managing services in Chunklock.
 * Provides singleton and transient service registration with lifecycle management.
 */
public class ServiceContainer {
    
    private static ServiceContainer instance;
    
    private final Map<Class<?>, ServiceRegistration<?>> services = new ConcurrentHashMap<>();
    private final Map<Class<?>, Object> singletonInstances = new ConcurrentHashMap<>();
    private final Set<Class<?>> initializingServices = new HashSet<>();
    private final Logger logger;
    
    private ServiceContainer(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Initialize the global service container instance
     */
    public static void initialize(Logger logger) {
        if (instance == null) {
            instance = new ServiceContainer(logger);
        }
    }
    
    /**
     * Get the global service container instance
     */
    public static ServiceContainer getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ServiceContainer not initialized. Call initialize() first.");
        }
        return instance;
    }
    
    /**
     * Register a singleton service
     */
    public <T> void registerSingleton(Class<T> serviceClass, Supplier<T> factory) {
        services.put(serviceClass, new ServiceRegistration<>(factory, ServiceLifecycle.SINGLETON));
        logger.info("Registered singleton service: " + serviceClass.getSimpleName());
    }
    
    /**
     * Register a transient service (new instance each time)
     */
    public <T> void registerTransient(Class<T> serviceClass, Supplier<T> factory) {
        services.put(serviceClass, new ServiceRegistration<>(factory, ServiceLifecycle.TRANSIENT));
        logger.info("Registered transient service: " + serviceClass.getSimpleName());
    }
    
    /**
     * Register a singleton service with an existing instance
     */
    public <T> void registerInstance(Class<T> serviceClass, T instance) {
        singletonInstances.put(serviceClass, instance);
        services.put(serviceClass, new ServiceRegistration<>(() -> instance, ServiceLifecycle.SINGLETON));
        logger.info("Registered service instance: " + serviceClass.getSimpleName());
    }
    
    /**
     * Get a service instance
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> serviceClass) {
        ServiceRegistration<T> registration = (ServiceRegistration<T>) services.get(serviceClass);
        
        if (registration == null) {
            throw new IllegalArgumentException("Service not registered: " + serviceClass.getName());
        }
        
        if (registration.lifecycle == ServiceLifecycle.SINGLETON) {
            return getSingletonInstance(serviceClass, registration);
        } else {
            return createTransientInstance(registration);
        }
    }
    
    /**
     * Check if a service is registered
     */
    public boolean isRegistered(Class<?> serviceClass) {
        return services.containsKey(serviceClass);
    }
    
    /**
     * Get all registered service classes
     */
    public Set<Class<?>> getRegisteredServices() {
        return new HashSet<>(services.keySet());
    }
    
    @SuppressWarnings("unchecked")
    private <T> T getSingletonInstance(Class<T> serviceClass, ServiceRegistration<T> registration) {
        // Check if instance already exists
        T existingInstance = (T) singletonInstances.get(serviceClass);
        if (existingInstance != null) {
            return existingInstance;
        }
        
        // Prevent circular dependencies
        if (initializingServices.contains(serviceClass)) {
            throw new IllegalStateException("Circular dependency detected for service: " + serviceClass.getName());
        }
        
        try {
            initializingServices.add(serviceClass);
            T instance = registration.factory.get();
            singletonInstances.put(serviceClass, instance);
            logger.fine("Created singleton instance: " + serviceClass.getSimpleName());
            return instance;
        } finally {
            initializingServices.remove(serviceClass);
        }
    }
    
    private <T> T createTransientInstance(ServiceRegistration<T> registration) {
        T instance = registration.factory.get();
        logger.fine("Created transient instance: " + instance.getClass().getSimpleName());
        return instance;
    }
    
    /**
     * Shutdown the container and cleanup resources
     */
    public void shutdown() {
        logger.info("Shutting down ServiceContainer...");
        
        // Cleanup singletons that implement AutoCloseable
        for (Object instance : singletonInstances.values()) {
            if (instance instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) instance).close();
                } catch (Exception e) {
                    logger.warning("Error closing service instance: " + e.getMessage());
                }
            }
        }
        
        singletonInstances.clear();
        services.clear();
        initializingServices.clear();
        
        instance = null;
        logger.info("ServiceContainer shutdown complete");
    }
    
    private static class ServiceRegistration<T> {
        final Supplier<T> factory;
        final ServiceLifecycle lifecycle;
        
        ServiceRegistration(Supplier<T> factory, ServiceLifecycle lifecycle) {
            this.factory = factory;
            this.lifecycle = lifecycle;
        }
    }
    
    private enum ServiceLifecycle {
        SINGLETON,
        TRANSIENT
    }
}