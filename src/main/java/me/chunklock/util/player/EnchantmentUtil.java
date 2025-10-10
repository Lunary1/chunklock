package me.chunklock.util.player;

import org.bukkit.enchantments.Enchantment;

import java.lang.reflect.Field;

/**
 * Cross-version compatibility utility for enchantments.
 * Handles differences between Minecraft versions.
 */
public class EnchantmentUtil {
    
    private static Enchantment unbreakingEnchantment;
    
    static {
        // Try modern API first (1.20.6+), then fall back to legacy
        unbreakingEnchantment = getUnbreakingEnchantment();
    }
    
    /**
     * Gets the Unbreaking enchantment in a cross-version compatible way.
     * @return The Unbreaking enchantment
     */
    public static Enchantment getUnbreaking() {
        return unbreakingEnchantment;
    }
    
    private static Enchantment getUnbreakingEnchantment() {
        // Try modern Registry API (1.20.6+)
        try {
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            Object enchantmentRegistry = registryClass.getField("ENCHANTMENT").get(null);
            java.lang.reflect.Method getMethod = registryClass.getMethod("get", org.bukkit.NamespacedKey.class);
            Enchantment modern = (Enchantment) getMethod.invoke(enchantmentRegistry, 
                org.bukkit.NamespacedKey.minecraft("unbreaking"));
            if (modern != null) {
                return modern;
            }
        } catch (Exception ignored) {
            // Registry API not available
        }
        
        // Try getByKey method (available in some versions)
        try {
            java.lang.reflect.Method getByKeyMethod = Enchantment.class.getMethod("getByKey", org.bukkit.NamespacedKey.class);
            Enchantment byKey = (Enchantment) getByKeyMethod.invoke(null, org.bukkit.NamespacedKey.minecraft("unbreaking"));
            if (byKey != null) {
                return byKey;
            }
        } catch (Exception ignored) {
            // getByKey not available
        }
        
        // Try direct field access for older versions
        try {
            Field unbreakingField = Enchantment.class.getField("UNBREAKING");
            return (Enchantment) unbreakingField.get(null);
        } catch (Exception ignored) {
            // Field doesn't exist
        }
        
        // Try legacy name access (suppress deprecation warnings)
        try {
            java.lang.reflect.Method getByNameMethod = Enchantment.class.getMethod("getByName", String.class);
            return (Enchantment) getByNameMethod.invoke(null, "UNBREAKING");
        } catch (Exception ignored) {
            // Not available
        }
        
        // Try alternative legacy name
        try {
            java.lang.reflect.Method getByNameMethod = Enchantment.class.getMethod("getByName", String.class);
            return (Enchantment) getByNameMethod.invoke(null, "DURABILITY");
        } catch (Exception ignored) {
            // Not available
        }
        
        // Last resort: reflection on all enchantments
        try {
            java.lang.reflect.Method valuesMethod = Enchantment.class.getMethod("values");
            Enchantment[] enchantments = (Enchantment[]) valuesMethod.invoke(null);
            for (Enchantment enchantment : enchantments) {
                String name = enchantment.getKey().getKey().toLowerCase();
                if (name.contains("unbreaking") || name.contains("durability")) {
                    return enchantment;
                }
            }
        } catch (Exception ignored) {
            // Give up
        }
        
        // Return null if we can't find it - caller should handle gracefully
        return null;
    }
}
