package me.chunklock.border;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class BorderConfigLoader {
    public BorderConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        BorderConfig c = new BorderConfig();
        c.enabled = config.getBoolean("glass-borders.enabled", true);
        c.useFullHeight = config.getBoolean("glass-borders.use-full-height", true);
        c.borderHeight = config.getInt("glass-borders.border-height", 3);
        c.minYOffset = config.getInt("glass-borders.min-y-offset", -2);
        c.maxYOffset = config.getInt("glass-borders.max-y-offset", 4);
        c.scanRange = config.getInt("glass-borders.scan-range", 8);
        c.updateDelay = config.getLong("glass-borders.update-delay", 20L);
        c.updateCooldown = config.getLong("glass-borders.update-cooldown", 2000L);
        c.showForBypassPlayers = config.getBoolean("glass-borders.show-for-bypass-players", false);
        c.autoUpdateOnMovement = config.getBoolean("glass-borders.auto-update-on-movement", true);
        c.restoreOriginalBlocks = config.getBoolean("glass-borders.restore-original-blocks", true);
        
        // Debug logging: check master debug switch first, then specific border debug setting
        boolean masterDebug = config.getBoolean("debug-mode.enabled", false);
        if (masterDebug) {
            c.debugLogging = config.getBoolean("debug-mode.borders", false);
        } else {
            c.debugLogging = config.getBoolean("glass-borders.debug-logging", false);
        }
        String materialName = config.getString("glass-borders.border-material", "LIGHT_GRAY_STAINED_GLASS");
        try {
            c.borderMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "', using LIGHT_GRAY_STAINED_GLASS");
            c.borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS;
        }
        if (config.isConfigurationSection("performance")) {
            var perf = config.getConfigurationSection("performance");
            c.borderUpdateDelayTicks = perf.getInt("border-update-delay", 2);
            c.maxBorderUpdatesPerTick = perf.getInt("max-border-updates-per-tick", 10);
        } else {
            c.borderUpdateDelayTicks = 2;
            c.maxBorderUpdatesPerTick = 10;
        }
        return c;
    }
}
