package me.chunklock.util.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import java.lang.reflect.Method;

/**
 * Utility class to safely handle Material operations across different Bukkit versions.
 * Provides safe access to Material names avoiding deprecated OldEnum.name() warnings.
 */
public final class MaterialUtil {
    
    private MaterialUtil() {}
    
    /**
     * Safely gets the enum name of a material, avoiding deprecated OldEnum.name() warnings.
     * 
     * @param material The material to get the name for
     * @return The enum name of the material (e.g., "DIAMOND", "IRON_INGOT")
     */
    public static String getMaterialName(Material material) {
        if (material == null) {
            return "UNKNOWN";
        }
        
        try {
            // Try to get the key first (modern approach)
            NamespacedKey key = material.getKey();
            return key.getKey().toUpperCase().replace("-", "_");
        } catch (Exception e) {
            // Fallback to enum name using reflection to avoid deprecation warnings
            try {
                Method nameMethod = material.getClass().getMethod("name");
                return (String) nameMethod.invoke(material);
            } catch (Exception ex) {
                // Final fallback to toString
                return material.toString().toUpperCase();
            }
        }
    }
    
    /**
     * Formats a material name for display (e.g., "DIAMOND" -> "diamond", "IRON_INGOT" -> "iron ingot").
     * 
     * @param material The material to format
     * @return The formatted display name
     */
    public static String formatMaterialName(Material material) {
        if (material == null) {
            return "unknown";
        }
        
        String name = getMaterialName(material);
        return name.toLowerCase().replace('_', ' ');
    }
}

