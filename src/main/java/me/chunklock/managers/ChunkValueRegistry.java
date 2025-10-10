package me.chunklock.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

public class ChunkValueRegistry {

    private final Map<Biome, Integer> biomeWeights = new java.util.HashMap<Biome, Integer>();
    private final Map<Material, Integer> blockWeights = new EnumMap<Material, Integer>(Material.class);
    private final Map<String, Integer> thresholds = new java.util.HashMap<String, Integer>();
    private final JavaPlugin plugin;

    public ChunkValueRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    private void loadConfiguration() {
        plugin.getLogger().info("[ChunkValueRegistry] Loading config.yml (chunk-values section)...");
        File file = new File(plugin.getDataFolder(), "config.yml");

        if (!file.exists()) {
            plugin.getLogger().info("[ChunkValueRegistry] config.yml not found, generating default...");
            try {
                plugin.saveResource("config.yml", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save default config.yml", e);
                loadDefaults();
                return;
            }
        }

        FileConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection config = root.getConfigurationSection("chunk-values");
        if (config == null) {
            plugin.getLogger().severe("Missing chunk-values section in config.yml, using defaults");
            loadDefaults();
            return;
        }

        loadThresholds(config);
        loadBiomeWeights(config);
        loadBlockWeights(config);
        
        plugin.getLogger().info("[ChunkValueRegistry] Configuration loaded successfully: " +
            biomeWeights.size() + " biomes, " + blockWeights.size() + " blocks, " + thresholds.size() + " thresholds");
    }

    private boolean validateConfiguration(ConfigurationSection config) {
        try {
            // Validate thresholds section
            if (!config.isConfigurationSection("thresholds")) {
                plugin.getLogger().severe("Missing 'thresholds' section in config.yml (chunk-values)");
                return false;
            }
            
            // Validate threshold values are positive and in order
            int easy = config.getInt("thresholds.easy", -1);
            int normal = config.getInt("thresholds.normal", -1);
            int hard = config.getInt("thresholds.hard", -1);
            
            if (easy <= 0 || normal <= 0 || hard <= 0) {
                plugin.getLogger().severe("Threshold values must be positive integers");
                return false;
            }
            
            if (easy >= normal || normal >= hard) {
                plugin.getLogger().severe("Thresholds must be in ascending order: easy < normal < hard");
                return false;
            }
            
            // Validate biomes section exists
            if (!config.isConfigurationSection("biomes")) {
                plugin.getLogger().warning("Missing 'biomes' section in config.yml (chunk-values)");
            }
            
            // Validate blocks section exists
            if (!config.isConfigurationSection("blocks")) {
                plugin.getLogger().warning("Missing 'blocks' section in config.yml (chunk-values)");
            }
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error validating configuration", e);
            return false;
        }
    }

    private void loadThresholds(ConfigurationSection config) {
        try {
            if (config.isConfigurationSection("thresholds")) {
                plugin.getLogger().info("[ChunkValueRegistry] Loading difficulty thresholds...");
                
                int easy = config.getInt("thresholds.easy", 25);
                int normal = config.getInt("thresholds.normal", 50);
                int hard = config.getInt("thresholds.hard", 80);
                
                thresholds.put("easy", easy);
                thresholds.put("normal", normal);
                thresholds.put("hard", hard);
                
                plugin.getLogger().info("[ChunkValueRegistry] Thresholds loaded: Easy=" + easy + ", Normal=" + normal + ", Hard=" + hard);
            } else {
                loadDefaultThresholds();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading thresholds, using defaults", e);
            loadDefaultThresholds();
        }
    }

    private void loadBiomeWeights(ConfigurationSection config) {
        try {
            if (config.isConfigurationSection("biomes")) {
                plugin.getLogger().info("[ChunkValueRegistry] Loading biome weights...");
                int loadedBiomes = 0;
                int errorBiomes = 0;
                
                for (String key : config.getConfigurationSection("biomes").getKeys(false)) {
                    try {
                        Biome biome = getBiomeFromString(key);
                        if (biome != null) {
                            int weight = config.getInt("biomes." + key, 8);
                            if (weight < 0) {
                                plugin.getLogger().warning("Negative weight for biome " + key + ", using 0");
                                weight = 0;
                            }
                            biomeWeights.put(biome, weight);
                            loadedBiomes++;
                        } else {
                            plugin.getLogger().warning("Invalid biome in config.yml (chunk-values): " + key);
                            errorBiomes++;
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error loading biome weight for: " + key + " - " + e.getMessage());
                        errorBiomes++;
                    }
                }
                
                plugin.getLogger().info("[ChunkValueRegistry] Loaded " + loadedBiomes + " biome weights" + 
                    (errorBiomes > 0 ? " (" + errorBiomes + " errors)" : ""));
            } else {
                loadDefaultBiomeWeights();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading biome weights, using defaults", e);
            loadDefaultBiomeWeights();
        }
    }

    private void loadBlockWeights(ConfigurationSection config) {
        try {
            if (config.isConfigurationSection("blocks")) {
                plugin.getLogger().info("[ChunkValueRegistry] Loading block weights...");
                int loadedBlocks = 0;
                int errorBlocks = 0;
                
                for (String key : config.getConfigurationSection("blocks").getKeys(false)) {
                    try {
                        Material material = Material.valueOf(key.toUpperCase());
                        int weight = config.getInt("blocks." + key, 1);
                        if (weight < 0) {
                            plugin.getLogger().warning("Negative weight for block " + key + ", using 0");
                            weight = 0;
                        }
                        blockWeights.put(material, weight);
                        loadedBlocks++;
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid block in config.yml (chunk-values): " + key);
                        errorBlocks++;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error loading block weight for: " + key + " - " + e.getMessage());
                        errorBlocks++;
                    }
                }
                
                plugin.getLogger().info("[ChunkValueRegistry] Loaded " + loadedBlocks + " block weights" + 
                    (errorBlocks > 0 ? " (" + errorBlocks + " errors)" : ""));
            } else {
                loadDefaultBlockWeights();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error loading block weights, using defaults", e);
            loadDefaultBlockWeights();
        }
    }

    private void loadDefaults() {
        plugin.getLogger().info("[ChunkValueRegistry] Loading default values...");
        loadDefaultThresholds();
        loadDefaultBiomeWeights();
        loadDefaultBlockWeights();
    }

    private void loadDefaultThresholds() {
        thresholds.clear();
        thresholds.put("easy", 25);
        thresholds.put("normal", 50);
        thresholds.put("hard", 80);
        plugin.getLogger().info("[ChunkValueRegistry] Loaded default thresholds");
    }

    private void loadDefaultBiomeWeights() {
        biomeWeights.clear();
        // Add some common biomes with default weights
        biomeWeights.put(Biome.PLAINS, 5);
        biomeWeights.put(Biome.FOREST, 8);
        biomeWeights.put(Biome.DESERT, 10);
        biomeWeights.put(Biome.JUNGLE, 20);
        biomeWeights.put(Biome.OCEAN, 15);
        biomeWeights.put(Biome.SWAMP, 11);
        biomeWeights.put(Biome.TAIGA, 10);
        plugin.getLogger().info("[ChunkValueRegistry] Loaded default biome weights for " + biomeWeights.size() + " biomes");
    }

    private void loadDefaultBlockWeights() {
        blockWeights.clear();
        // Add some common blocks with default weights
        blockWeights.put(Material.STONE, 1);
        blockWeights.put(Material.DIRT, 1);
        blockWeights.put(Material.GRASS_BLOCK, 1);
        blockWeights.put(Material.OAK_LOG, 6);
        blockWeights.put(Material.COAL_ORE, 5);
        blockWeights.put(Material.IRON_ORE, 10);
        blockWeights.put(Material.DIAMOND_ORE, 25);
        blockWeights.put(Material.WATER, 2);
        plugin.getLogger().info("[ChunkValueRegistry] Loaded default block weights for " + blockWeights.size() + " blocks");
    }

    private Biome getBiomeFromString(String biomeKey) {
        return me.chunklock.util.world.BiomeUtil.getBiomeFromString(biomeKey);
    }

    public int getBiomeWeight(Biome biome) {
        if (biome == null) {
            plugin.getLogger().fine("Null biome passed to getBiomeWeight, using default");
            return 8; // default fallback weight
        }
        return biomeWeights.getOrDefault(biome, 8); // default fallback weight
    }

    public int getBlockWeight(Material material) {
        if (material == null) {
            plugin.getLogger().fine("Null material passed to getBlockWeight, using default");
            return 1; // default fallback weight
        }
        return blockWeights.getOrDefault(material, 1); // default fallback weight
    }

    public int getThreshold(String level) {
        if (level == null || level.trim().isEmpty()) {
            plugin.getLogger().warning("Invalid threshold level requested: " + level);
            return 60; // default normal threshold
        }
        
        return thresholds.getOrDefault(level.toLowerCase().trim(), switch (level.toLowerCase().trim()) {
            case "easy" -> 25;
            case "normal" -> 50;
            case "hard" -> 80;
            default -> {
                plugin.getLogger().warning("Unknown threshold level: " + level + ", using default");
                yield 60;
            }
        });
    }

    // Helper method to get biome display name
    public static String getBiomeDisplayName(Biome biome) {
        if (biome == null) return "Unknown";
        try {
            NamespacedKey key = biome.getKey();
            return key.getKey().replace("_", " ");
        } catch (Exception e) {
            return biome.name().replace("_", " ");
        }
    }

    /**
     * Get configuration statistics for debugging
     */
    public Map<String, Object> getConfigStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("biomeCount", biomeWeights.size());
        stats.put("blockCount", blockWeights.size());
        stats.put("thresholdCount", thresholds.size());
        stats.put("thresholds", new java.util.HashMap<>(thresholds));
        return stats;
    }

    /**
     * Reload configuration from file
     */
    public boolean reloadConfiguration() {
        try {
            // Clear existing data
            biomeWeights.clear();
            blockWeights.clear();
            thresholds.clear();
            
            // Reload from file
            loadConfiguration();
            
            return !biomeWeights.isEmpty() && !thresholds.isEmpty();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error reloading configuration", e);
            return false;
        }
    }
}