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
        c.debugLogging = config.getBoolean("glass-borders.debug-logging", false);
        String materialName = config.getString("glass-borders.border-material", "LIGHT_GRAY_STAINED_GLASS");
        try {
            c.borderMaterial = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "', using LIGHT_GRAY_STAINED_GLASS");
            c.borderMaterial = Material.LIGHT_GRAY_STAINED_GLASS;
        }
        c.skipValuableOres = config.getBoolean("glass-borders.skip-valuable-ores", true);
        c.skipFluids = config.getBoolean("glass-borders.skip-fluids", true);
        c.skipImportantBlocks = config.getBoolean("glass-borders.skip-important-blocks", true);
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
