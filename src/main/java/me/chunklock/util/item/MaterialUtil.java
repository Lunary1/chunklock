package me.chunklock.util.item;

import org.bukkit.Material;

/**
 * Utility class for material name formatting and manipulation.
 * Provides methods for getting material names in different formats.
 */
public final class MaterialUtil {
    
    private MaterialUtil() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Gets the enum name of a material (e.g., "OAK_LOG", "DIAMOND_ORE").
     * Useful for string matching and comparisons.
     * 
     * @param material The material to get the name for
     * @return The enum name of the material, or "AIR" if null
     */
    public static String getMaterialName(Material material) {
        if (material == null) {
            return "AIR";
        }
        return material.name();
    }
    
    /**
     * Formats a material name for display (e.g., "OAK_LOG" -> "Oak Log").
     * Converts enum names to human-readable format.
     * 
     * @param material The material to format
     * @return The formatted material name, or "Unknown Item" if null
     */
    public static String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }
        
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            if (!word.isEmpty()) {
                formatted.append(word.substring(0, 1).toUpperCase())
                         .append(word.substring(1));
            }
        }
        
        return formatted.toString();
    }
}

