package me.chunklock.util.validation;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Utility class for checking plugin dependencies and integration status.
 * Provides comprehensive dependency validation for Chunklock.
 */
public class DependencyChecker {
    
    private final JavaPlugin plugin;
    
    public DependencyChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Check and log all plugin dependencies during startup
     */
    public void checkAndLogDependencies() {
        plugin.getLogger().info("=== Dependency Check ===");
        
        List<DependencyInfo> dependencies = getAllDependencies();
        
        for (DependencyInfo dep : dependencies) {
            logDependencyStatus(dep);
        }
        
        plugin.getLogger().info("=== Dependency Check Complete ===");
    }
    
    /**
     * Specifically test if Vault economy integration is working
     * This can be called from commands or other parts of the plugin
     */
    public boolean testVaultIntegration() {
        DependencyInfo vaultInfo = checkVault();
        
        plugin.getLogger().info("=== Vault Integration Test ===");
        logDependencyStatus(vaultInfo);
        
        boolean isWorking = vaultInfo.status == DependencyStatus.AVAILABLE;
        
        if (isWorking) {
            plugin.getLogger().info("✅ Vault integration is working properly");
        } else {
            plugin.getLogger().warning("❌ Vault integration is not working: " + vaultInfo.details);
        }
        
        plugin.getLogger().info("=== Vault Integration Test Complete ===");
        return isWorking;
    }
    
    /**
     * Get comprehensive information about all dependencies
     */
    private List<DependencyInfo> getAllDependencies() {
        List<DependencyInfo> deps = new ArrayList<>();
        
        // Check FancyHolograms
        deps.add(checkFancyHolograms());
        
        // Check Vault
        deps.add(checkVault());
        
        return deps;
    }
    
    /**
     * Check FancyHolograms plugin availability and functionality
     */
    private DependencyInfo checkFancyHolograms() {
        DependencyInfo info = new DependencyInfo("FancyHolograms", DependencyType.OPTIONAL, "Hologram display functionality");
        
        Plugin fancyPlugin = Bukkit.getPluginManager().getPlugin("FancyHolograms");
        if (fancyPlugin == null) {
            info.status = DependencyStatus.NOT_FOUND;
            info.details = "Plugin not installed - hologram features will be disabled";
            return info;
        }
        
        if (!fancyPlugin.isEnabled()) {
            info.status = DependencyStatus.DISABLED;
            info.details = "Plugin found but disabled - hologram features will be disabled";
            return info;
        }
        
        // Test if we can access the API
        try {
            Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
            Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
            Class.forName("de.oliver.fancyholograms.api.HologramManager");
            
            info.status = DependencyStatus.AVAILABLE;
            info.version = fancyPlugin.getPluginMeta().getVersion();
            info.details = "API accessible - hologram features enabled";
        } catch (ClassNotFoundException e) {
            info.status = DependencyStatus.INCOMPATIBLE;
            info.version = fancyPlugin.getPluginMeta().getVersion();
            info.details = "Incompatible version - API classes not found: " + e.getMessage();
        }
        
        return info;
    }
    
    /**
     * Check Vault plugin and economy integration
     */
    private DependencyInfo checkVault() {
        DependencyInfo info = new DependencyInfo("Vault", DependencyType.OPTIONAL, "Economy integration for money-based chunk unlocking");
        
        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin("Vault");
        if (vaultPlugin == null) {
            info.status = DependencyStatus.NOT_FOUND;
            info.details = "Plugin not installed - economy features will use materials only";
            return info;
        }
        
        if (!vaultPlugin.isEnabled()) {
            info.status = DependencyStatus.DISABLED;
            info.details = "Plugin found but disabled - economy features will use materials only";
            return info;
        }
        
        info.version = vaultPlugin.getPluginMeta().getVersion();
        
        // Check if economy service is available
        try {
            var servicesManager = Bukkit.getServer().getServicesManager();
            var economyRegistration = servicesManager.getRegistration(net.milkbowl.vault.economy.Economy.class);
            
            if (economyRegistration == null) {
                info.status = DependencyStatus.MISCONFIGURED;
                info.details = "No economy plugin found - install an economy plugin (like EssentialsX) for money features";
                return info;
            }
            
            var economy = economyRegistration.getProvider();
            if (economy == null) {
                info.status = DependencyStatus.MISCONFIGURED;
                info.details = "Economy service provider is null";
                return info;
            }
            
            info.status = DependencyStatus.AVAILABLE;
            info.details = "Economy integration available with " + economy.getName();
            info.extraInfo = "Economy Provider: " + economy.getName();
            
        } catch (Exception e) {
            info.status = DependencyStatus.ERROR;
            info.details = "Error checking economy service: " + e.getMessage();
        }
        
        return info;
    }
    
    /**
     * Log the status of a dependency
     */
    private void logDependencyStatus(DependencyInfo info) {
        String statusIcon = getStatusIcon(info.status);
        String typeLabel = info.type == DependencyType.REQUIRED ? "[REQUIRED]" : "[OPTIONAL]";
        
        Level logLevel = getLogLevel(info.status, info.type);
        
        StringBuilder message = new StringBuilder()
            .append(statusIcon).append(" ")
            .append(info.name).append(" ")
            .append(typeLabel);
        
        if (info.version != null) {
            message.append(" v").append(info.version);
        }
        
        message.append(" - ").append(info.details);
        
        if (info.extraInfo != null) {
            message.append(" (").append(info.extraInfo).append(")");
        }
        
        plugin.getLogger().log(logLevel, message.toString());
    }
    
    /**
     * Get appropriate status icon for dependency status
     */
    private String getStatusIcon(DependencyStatus status) {
        switch (status) {
            case AVAILABLE: return "✅";
            case NOT_FOUND: return "❌";
            case DISABLED: return "⚠️";
            case INCOMPATIBLE: return "🔴";
            case MISCONFIGURED: return "⚠️";
            case ERROR: return "❌";
            default: return "❓";
        }
    }
    
    /**
     * Get appropriate log level for dependency status
     */
    private Level getLogLevel(DependencyStatus status, DependencyType type) {
        if (type == DependencyType.REQUIRED) {
            return status == DependencyStatus.AVAILABLE ? Level.INFO : Level.SEVERE;
        } else {
            return status == DependencyStatus.AVAILABLE ? Level.INFO : Level.WARNING;
        }
    }
    
    /**
     * Information about a plugin dependency
     */
    public static class DependencyInfo {
        public final String name;
        public final DependencyType type;
        public final String description;
        public DependencyStatus status;
        public String version;
        public String details;
        public String extraInfo;
        
        public DependencyInfo(String name, DependencyType type, String description) {
            this.name = name;
            this.type = type;
            this.description = description;
        }
    }
    
    /**
     * Type of dependency
     */
    public enum DependencyType {
        REQUIRED,
        OPTIONAL
    }
    
    /**
     * Status of a dependency
     */
    public enum DependencyStatus {
        AVAILABLE,      // Plugin found, enabled, and functional
        NOT_FOUND,      // Plugin not installed
        DISABLED,       // Plugin installed but disabled
        INCOMPATIBLE,   // Plugin installed but wrong version/API
        MISCONFIGURED,  // Plugin available but missing configuration
        ERROR           // Error during check
    }
}