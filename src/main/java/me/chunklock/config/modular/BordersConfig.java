package me.chunklock.config.modular;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Configuration handler for borders.yml
 * Manages glass border system settings.
 */
public class BordersConfig {
    private final Plugin plugin;
    private FileConfiguration config;

    public BordersConfig(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "borders.yml");
        if (!file.exists()) {
            plugin.saveResource("borders.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isEnabled() {
        return config.getBoolean("enabled", true);
    }

    public boolean isUseFullHeight() {
        return config.getBoolean("use-full-height", true);
    }

    public int getBorderHeight() {
        return config.getInt("border-height", 3);
    }

    public int getMinYOffset() {
        return config.getInt("min-y-offset", -2);
    }

    public int getMaxYOffset() {
        return config.getInt("max-y-offset", 4);
    }

    public int getScanRange() {
        return config.getInt("scan-range", 8);
    }

    public long getUpdateDelay() {
        return config.getLong("update-delay", 20L);
    }

    public long getUpdateCooldown() {
        return config.getLong("update-cooldown", 2000L);
    }

    public boolean isShowForBypassPlayers() {
        return config.getBoolean("show-for-bypass-players", false);
    }

    public boolean isAutoUpdateOnMovement() {
        return config.getBoolean("auto-update-on-movement", true);
    }

    public boolean isRestoreOriginalBlocks() {
        return config.getBoolean("restore-original-blocks", true);
    }

    public boolean isDebugLogging() {
        return config.getBoolean("debug-logging", false);
    }

    public Material getBorderMaterial() {
        String materialName = config.getString("border-material", "LIGHT_GRAY_STAINED_GLASS");
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid border material '" + materialName + "', using LIGHT_GRAY_STAINED_GLASS");
            return Material.LIGHT_GRAY_STAINED_GLASS;
        }
    }

    public FileConfiguration getRawConfig() {
        return config;
    }
}

