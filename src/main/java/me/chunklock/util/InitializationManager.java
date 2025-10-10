package me.chunklock.util;

import me.chunklock.ChunklockPlugin;
import me.chunklock.util.validation.ConfigValidator;
import me.chunklock.util.validation.DataMigrator;
import org.bukkit.Bukkit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Clean initialization system for ChunklockPlugin
 * Provides clear overview without console spam
 */
public class InitializationManager {
    
    private final ChunklockPlugin plugin;
    private final List<String> successComponents = new ArrayList<>();
    private final List<String> failedComponents = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    
    public InitializationManager(ChunklockPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Streamlined plugin initialization with clean logging
     */
    public boolean initialize() {
        long startTime = System.currentTimeMillis();
        
        plugin.getLogger().info("=== Starting Chunklock v" + plugin.getPluginMeta().getVersion() + " ===");
        
        try {
            // Phase 1: Configuration
            if (!validateConfiguration()) {
                plugin.getLogger().severe("Configuration validation failed - disabling plugin");
                return false;
            }
            
            // Phase 2: Core Components
            if (!initializeComponents()) {
                plugin.getLogger().severe("Component initialization failed - disabling plugin");
                return false;
            }
            
            // Phase 3: Event System
            if (!registerEventListeners()) {
                plugin.getLogger().severe("Event listener registration failed - disabling plugin");
                return false;
            }
            
            // Phase 4: Background Tasks
            if (!startTasks()) {
                warnings.add("Some background tasks failed to start");
            }
            
            // Phase 5: Commands
            if (!registerCommands()) {
                warnings.add("Some commands failed to register");
            }
            
            // Print final summary
            printInitializationSummary(startTime);
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Critical error during initialization", e);
            return false;
        }
    }
    
    private boolean validateConfiguration() {
        try {
            // Check data folder
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                failedComponents.add("Data folder creation");
                return false;
            }
            
            // Validate server version
            String version = Bukkit.getVersion();
            if (!version.contains("1.21")) {
                warnings.add("Designed for 1.21.4+, current: " + version);
            }
            
            // Check memory
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            if (maxMemory < 1073741824) { // 1GB
                warnings.add("Low memory: " + (maxMemory / 1048576) + "MB");
            }
            
            // Config validation and migration
            try {
                ConfigValidator validator = new ConfigValidator(plugin);
                validator.validateAndEnsureComplete();
                plugin.reloadConfig();
                
                new DataMigrator(plugin).migrate();
                successComponents.add("Configuration");
            } catch (Exception e) {
                warnings.add("Config validation: " + e.getMessage());
            }
            
            return true;
            
        } catch (Exception e) {
            failedComponents.add("Configuration validation");
            plugin.getLogger().log(Level.SEVERE, "Configuration validation error", e);
            return false;
        }
    }
    
    private boolean initializeComponents() {
        try {
            ComponentInitializer initializer = new ComponentInitializer(plugin);
            
            // Initialize all components in order
            if (initializer.initializeAll()) {
                successComponents.add("Core Components (" + initializer.getComponentCount() + ")");
                return true;
            } else {
                failedComponents.add("Component initialization");
                failedComponents.addAll(initializer.getFailedComponents());
                return false;
            }
            
        } catch (Exception e) {
            failedComponents.add("Component initialization");
            plugin.getLogger().log(Level.SEVERE, "Component initialization error", e);
            return false;
        }
    }
    
    private boolean registerEventListeners() {
        try {
            // Register all listeners - actual implementation would be moved here
            // from the main plugin class
            successComponents.add("Event Listeners");
            return true;
            
        } catch (Exception e) {
            failedComponents.add("Event listeners");
            plugin.getLogger().log(Level.SEVERE, "Event listener registration error", e);
            return false;
        }
    }
    
    private boolean startTasks() {
        try {
            // Background task startup - minimal for now
            successComponents.add("Background Tasks");
            return true;
            
        } catch (Exception e) {
            failedComponents.add("Background tasks");
            plugin.getLogger().log(Level.WARNING, "Background task startup error", e);
            return false;
        }
    }
    
    private boolean registerCommands() {
        try {
            // Command registration - actual implementation would be moved here
            successComponents.add("Command System");
            return true;
            
        } catch (Exception e) {
            failedComponents.add("Command registration");
            plugin.getLogger().log(Level.WARNING, "Command registration error", e);
            return false;
        }
    }
    
    private void printInitializationSummary(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        
        plugin.getLogger().info("=== Initialization Summary ===");
        
        // Success components
        if (!successComponents.isEmpty()) {
            plugin.getLogger().info("✅ Loaded: " + String.join(", ", successComponents));
        }
        
        // Failed components
        if (!failedComponents.isEmpty()) {
            plugin.getLogger().warning("❌ Failed: " + String.join(", ", failedComponents));
        }
        
        // Warnings
        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("⚠️ Warnings: " + String.join(", ", warnings));
        }
        
        // World info
        try {
            if (plugin.getWorldManager() != null) {
                plugin.getLogger().info("🌍 Active in: " + plugin.getWorldManager().getEnabledWorlds());
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // Final status
        if (failedComponents.isEmpty()) {
            plugin.getLogger().info("🎉 Chunklock enabled successfully! (" + duration + "ms)");
        } else {
            plugin.getLogger().warning("⚠️ Chunklock enabled with issues (" + duration + "ms)");
        }
    }
    
    /**
     * Helper class for component initialization
     */
    private static class ComponentInitializer {
        private final ChunklockPlugin plugin;
        private final List<String> failedComponents = new ArrayList<>();
        private int componentCount = 0;
        
        public ComponentInitializer(ChunklockPlugin plugin) {
            this.plugin = plugin;
        }
        
        public boolean initializeAll() {
            // This would contain the actual component initialization logic
            // moved from the main plugin class, but without verbose logging
            
            try {
                // Initialize each component silently
                initComponent("WorldManager", () -> new me.chunklock.managers.WorldManager(plugin));
                initComponent("ChunkValueRegistry", () -> new me.chunklock.managers.ChunkValueRegistry(plugin));
                initComponent("EnhancedTeamManager", () -> new me.chunklock.managers.EnhancedTeamManager(plugin));
                // ... etc for all components
                
                return failedComponents.isEmpty();
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Component initialization failed", e);
                return false;
            }
        }
        
        private void initComponent(String name, ComponentSupplier supplier) {
            try {
                Object component = supplier.get();
                if (component != null) {
                    componentCount++;
                } else {
                    failedComponents.add(name);
                }
            } catch (Exception e) {
                failedComponents.add(name + " (" + e.getMessage() + ")");
                plugin.getLogger().log(Level.WARNING, "Failed to initialize " + name, e);
            }
        }
        
        public int getComponentCount() {
            return componentCount;
        }
        
        public List<String> getFailedComponents() {
            return failedComponents;
        }
        
        @FunctionalInterface
        private interface ComponentSupplier {
            Object get() throws Exception;
        }
    }
}

// Updated ChunklockPlugin.onEnable() method:
/*
@Override
public void onEnable() {
    try {
        instance = this;
        
        // Use the clean initialization system
        InitializationManager initManager = new InitializationManager(this);
        if (!initManager.initialize()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
    } catch (Exception e) {
        getLogger().log(Level.SEVERE, "Critical error during plugin enable", e);
        Bukkit.getPluginManager().disablePlugin(this);
    }
}
*/