package me.chunklock;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

public class ChunkValueRegistry {

    private final Map<Biome, Integer> biomeWeights = new java.util.HashMap<Biome, Integer>();
    private final Map<Material, Integer> blockWeights = new EnumMap<Material, Integer>(Material.class);
    private final Map<String, Integer> thresholds = new java.util.HashMap<String, Integer>();

    public ChunkValueRegistry(JavaPlugin plugin) {
        plugin.getLogger().info("[ChunkValueRegistry] Loading chunk_values.yml...");
        File file = new File(plugin.getDataFolder(), "chunk_values.yml");
        if (!file.exists()) {
            plugin.getLogger().info("[ChunkValueRegistry] chunk_values.yml not found, generating default...");
            plugin.saveResource("chunk_values.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.isConfigurationSection("biomes")) {
            plugin.getLogger().info("[ChunkValueRegistry] Loading biome weights...");
            for (String key : config.getConfigurationSection("biomes").getKeys(false)) {
                try {
                    Biome biome = Biome.valueOf(key.toUpperCase());
                    int weight = config.getInt("biomes." + key);
                    biomeWeights.put(biome, weight);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid biome in chunk_values.yml: " + key);
                }
            }
        }

        if (config.isConfigurationSection("blocks")) {
            plugin.getLogger().info("[ChunkValueRegistry] Loading block weights...");
            for (String key : config.getConfigurationSection("blocks").getKeys(false)) {
                try {
                    Material material = Material.valueOf(key.toUpperCase());
                    int weight = config.getInt("blocks." + key);
                    blockWeights.put(material, weight);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid block in chunk_values.yml: " + key);
                }
            }
        }

        if (config.isConfigurationSection("thresholds")) {
            plugin.getLogger().info("[ChunkValueRegistry] Loading difficulty thresholds...");
            thresholds.put("easy", config.getInt("thresholds.easy", 30));
            thresholds.put("normal", config.getInt("thresholds.normal", 60));
            thresholds.put("hard", config.getInt("thresholds.hard", 90));
        }
    }

    public int getBiomeWeight(Biome biome) {
        return biomeWeights.getOrDefault(biome, 8); // default fallback weight
    }

    public int getBlockWeight(Material material) {
        return blockWeights.getOrDefault(material, 1); // default fallback weight
    }

    public int getThreshold(String level) {
        return thresholds.getOrDefault(level, switch (level.toLowerCase()) {
            case "easy" -> 30;
            case "normal" -> 60;
            case "hard" -> 90;
            default -> 120;
        });
    }
}
