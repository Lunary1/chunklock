package me.chunklock.api.services;

/**
 * Base interface for all services in the Chunklock plugin.
 * Provides lifecycle management and health monitoring capabilities.
 */
public interface BaseService {
    
    /**
     * Initialize the service. Called after dependency injection.
     */
    default void initialize() {
        // Default implementation - no action required
    }
    
    /**
     * Start the service. Called after all services are initialized.
     */
    default void start() {
        // Default implementation - no action required
    }
    
    /**
     * Stop the service. Called during plugin shutdown.
     */
    default void stop() {
        // Default implementation - no action required
    }
    
    /**
     * Check if the service is healthy and operational.
     * @return true if the service is healthy, false otherwise
     */
    default boolean isHealthy() {
        return true;
    }
    
    /**
     * Get the service name for logging and identification purposes.
     * @return the service name
     */
    default String getServiceName() {
        return getClass().getSimpleName();
    }
    
    /**
     * Get service-specific health information.
     * @return health status details
     */
    default String getHealthStatus() {
        return isHealthy() ? "Healthy" : "Unhealthy";
    }
}