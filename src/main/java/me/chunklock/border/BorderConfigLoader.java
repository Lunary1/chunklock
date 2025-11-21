package me.chunklock.border;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.modular.BordersConfig;
import me.chunklock.config.modular.DebugConfig;
import me.chunklock.config.modular.PerformanceConfig;
import org.bukkit.plugin.java.JavaPlugin;

public class BorderConfigLoader {
    public BorderConfig load(JavaPlugin plugin) {
        // Use modular config system
        BordersConfig bordersConfig = null;
        DebugConfig debugConfig = null;
        PerformanceConfig performanceConfig = null;
        
        if (plugin instanceof ChunklockPlugin) {
            me.chunklock.config.ConfigManager configManager = ((ChunklockPlugin) plugin).getConfigManager();
            bordersConfig = configManager.getBordersConfig();
            debugConfig = configManager.getDebugConfig();
            performanceConfig = configManager.getPerformanceConfig();
        } else {
            bordersConfig = new BordersConfig(plugin);
            debugConfig = new DebugConfig(plugin);
            performanceConfig = new PerformanceConfig(plugin);
        }
        
        BorderConfig c = new BorderConfig();
        if (bordersConfig != null) {
            c.enabled = bordersConfig.isEnabled();
            c.useFullHeight = bordersConfig.isUseFullHeight();
            c.borderHeight = bordersConfig.getBorderHeight();
            c.minYOffset = bordersConfig.getMinYOffset();
            c.maxYOffset = bordersConfig.getMaxYOffset();
            c.scanRange = bordersConfig.getScanRange();
            c.updateDelay = bordersConfig.getUpdateDelay();
            c.updateCooldown = bordersConfig.getUpdateCooldown();
            c.showForBypassPlayers = bordersConfig.isShowForBypassPlayers();
            c.autoUpdateOnMovement = bordersConfig.isAutoUpdateOnMovement();
            c.restoreOriginalBlocks = bordersConfig.isRestoreOriginalBlocks();
            c.debugLogging = bordersConfig.isDebugLogging();
            c.borderMaterial = bordersConfig.getBorderMaterial();
        } else {
            // Fallback defaults
            c.enabled = true;
            c.useFullHeight = true;
            c.borderHeight = 3;
            c.minYOffset = -2;
            c.maxYOffset = 4;
            c.scanRange = 8;
            c.updateDelay = 20L;
            c.updateCooldown = 2000L;
            c.showForBypassPlayers = false;
            c.autoUpdateOnMovement = true;
            c.restoreOriginalBlocks = true;
            c.debugLogging = false;
            c.borderMaterial = org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS;
        }
        
        // Debug logging: check master debug switch first, then specific border debug setting
        if (debugConfig != null && debugConfig.isEnabled()) {
            c.debugLogging = debugConfig.isBordersDebug() || c.debugLogging;
        }
        
        // Performance settings
        if (performanceConfig != null) {
            c.borderUpdateDelayTicks = performanceConfig.getBorderUpdateDelay();
            c.maxBorderUpdatesPerTick = performanceConfig.getMaxBorderUpdatesPerTick();
        } else {
            c.borderUpdateDelayTicks = 2;
            c.maxBorderUpdatesPerTick = 10;
        }
        
        return c;
    }
}
