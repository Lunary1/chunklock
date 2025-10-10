package me.chunklock.util.world;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import me.chunklock.ChunklockPlugin;

/**
 * Utility class to safely handle Biome operations across different Bukkit versions.
 * Provides compatibility for deprecated Biome.valueOf() and Biome.values() methods.
 */
public final class BiomeUtil {
    
    private BiomeUtil() {}
    
    /**
     * Safely gets a biome from a string with cross-version compatibility.
     * 
     * @param biomeKey The biome key/name to parse
     * @return The biome if found, null otherwise
     */
    public static Biome getBiomeFromString(String biomeKey) {
        if (biomeKey == null || biomeKey.trim().isEmpty()) {
            return null;
        }
        
        String cleanKey = biomeKey.toUpperCase().trim();
        
        // Method 1: Try Registry approach first (modern approach for newer versions)
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Object biomeRegistry = registryClass.getField("BIOME").get(null);
            
            // Try to get biome by NamespacedKey
            NamespacedKey key = NamespacedKey.minecraft(biomeKey.toLowerCase().trim());
            Object biome = registryClass.getMethod("get", NamespacedKey.class).invoke(biomeRegistry, key);
            
            if (biome instanceof Biome) {
                return (Biome) biome;
            }
        } catch (Exception e) {
            // Registry approach failed, continue
        }
        
        // Method 2: Try direct enum name match with reflection (avoids deprecation warnings)
        try {
            Method valueOfMethod = Biome.class.getMethod("valueOf", String.class);
            return (Biome) valueOfMethod.invoke(null, cleanKey);
        } catch (Exception e) {
            // Method failed, continue to next approach
        }
        
        // Method 3: Manual iteration through available biomes (most compatible)
        try {
            for (Biome biome : getAllBiomes()) {
                // Check enum name match using reflection to avoid deprecation
                try {
                    Method nameMethod = biome.getClass().getMethod("name");
                    String biomeName = (String) nameMethod.invoke(biome);
                    if (biomeName.equalsIgnoreCase(cleanKey)) {
                        return biome;
                    }
                } catch (Exception ex) {
                    // Try toString as fallback
                    if (biome.toString().equalsIgnoreCase(cleanKey)) {
                        return biome;
                    }
                }
                
                // Check key match if available
                try {
                    NamespacedKey key = biome.getKey();
                    if (key.getKey().equalsIgnoreCase(biomeKey.trim()) || 
                        key.toString().equalsIgnoreCase(biomeKey.trim())) {
                        return biome;
                    }
                } catch (Exception ex) {
                    // Some biomes might not have keys or getKey() might fail
                    continue;
                }
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error searching for biome: " + biomeKey, e);
        }
        
        return null;
    }
    
    /**
     * Safely gets all available biomes with cross-version compatibility.
     * 
     * @return List of all available biomes
     */
    public static List<Biome> getAllBiomes() {
        // Method 1: Try Registry approach first (newer versions)
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Object biomeRegistry = registryClass.getField("BIOME").get(null);
            
            @SuppressWarnings("unchecked")
            Iterable<Biome> biomes = (Iterable<Biome>) registryClass.getMethod("iterator").invoke(biomeRegistry);
            
            return java.util.stream.StreamSupport.stream(biomes.spliterator(), false)
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            // Registry approach failed
        }
        
        // Method 2: Try values() method using reflection (avoids deprecation warnings)
        try {
            Method valuesMethod = Biome.class.getMethod("values");
            Biome[] biomes = (Biome[]) valuesMethod.invoke(null);
            return Arrays.asList(biomes);
        } catch (Exception e) {
            // values() method failed
        }
        
        // Method 3: Fallback to hardcoded common biomes (last resort)
        try {
            java.util.List<Biome> fallbackBiomes = new java.util.ArrayList<>();
            
            // Try to add some common biomes that should exist in most versions
            String[] commonBiomes = {
                "PLAINS", "FOREST", "DESERT", "OCEAN", "SWAMP", "MOUNTAINS", 
                "TAIGA", "JUNGLE", "NETHER_WASTES", "THE_END", "BIRCH_FOREST",
                "DARK_FOREST", "SAVANNA", "ICE_SPIKES", "BADLANDS", "RIVER",
                "BEACH", "MUSHROOM_FIELDS", "DEEP_OCEAN", "FROZEN_OCEAN"
            };
            
            Method valueOfMethod = Biome.class.getMethod("valueOf", String.class);
            for (String biomeName : commonBiomes) {
                try {
                    Biome biome = (Biome) valueOfMethod.invoke(null, biomeName);
                    fallbackBiomes.add(biome);
                } catch (Exception ex) {
                    // Skip biomes that don't exist in this version
                }
            }
            
            if (!fallbackBiomes.isEmpty()) {
                ChunklockPlugin.getInstance().getLogger().warning(
                    "Using fallback biome list due to API compatibility issues. " +
                    "Some biomes may not be available.");
                return fallbackBiomes;
            }
        } catch (Exception e) {
            // Even fallback failed
        }
        
        ChunklockPlugin.getInstance().getLogger().severe(
            "Unable to retrieve biomes due to API compatibility issues. " +
            "Plugin functionality may be limited.");
        
        return java.util.Collections.emptyList();
    }
    
    /**
     * Safely checks if a biome exists.
     * 
     * @param biomeKey The biome key to check
     * @return true if the biome exists, false otherwise
     */
    public static boolean biomeExists(String biomeKey) {
        return getBiomeFromString(biomeKey) != null;
    }
    
    /**
     * Gets the display name of a biome in a safe way.
     * 
     * @param biome The biome to get the name for
     * @return The display name of the biome
     */
    public static String getBiomeDisplayName(Biome biome) {
        if (biome == null) {
            return "Unknown";
        }
        
        try {
            // Try to get the key for a cleaner name
            NamespacedKey key = biome.getKey();
            String keyName = key.getKey();
            
            // Convert snake_case to Title Case
            return Arrays.stream(keyName.split("_"))
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                    .collect(java.util.stream.Collectors.joining(" "));
        } catch (Exception e) {
            // Fallback to enum name using reflection
            try {
                Method nameMethod = biome.getClass().getMethod("name");
                String name = (String) nameMethod.invoke(biome);
                return Arrays.stream(name.split("_"))
                        .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                        .collect(java.util.stream.Collectors.joining(" "));
            } catch (Exception ex) {
                // Final fallback to toString
                try {
                    String name = biome.toString();
                    if (name.contains("_")) {
                        return Arrays.stream(name.split("_"))
                                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                                .collect(java.util.stream.Collectors.joining(" "));
                    }
                    return name;
                } catch (Exception finalEx) {
                    return "Unknown Biome";
                }
            }
        }
    }
}
