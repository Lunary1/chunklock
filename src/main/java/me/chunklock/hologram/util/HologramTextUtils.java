package me.chunklock.hologram.util;

import org.bukkit.Material;

/**
 * Utility class for formatting and text manipulation in holograms.
 */
public final class HologramTextUtils {

    private HologramTextUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats a material name for display in holograms.
     * Converts MATERIAL_NAME to Material Name format.
     */
    public static String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }

        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder formatted = new StringBuilder();

        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (capitalizeNext) {
                formatted.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else if (c == ' ') {
                formatted.append(c);
                capitalizeNext = true;
            } else {
                formatted.append(c);
            }
        }

        return formatted.toString();
    }

    /**
     * Creates colored status text based on item availability.
     */
    public static String createStatusText(boolean hasItems, int playerCount, int requiredCount) {
        String statusColor = hasItems ? "§a" : "§c";
        String statusSymbol = hasItems ? "✓" : "✗";
        return statusColor + statusSymbol + " §a§l" + playerCount + "§7/" + "§l" + requiredCount;
    }

    /**
     * Creates the standard hologram text lines for a locked chunk.
     */
    public static java.util.List<String> createChunkHologramLines(String materialName, 
                                                                 boolean hasItems, 
                                                                 int playerCount, 
                                                                 int requiredCount) {
        return java.util.List.of(
            "§c§l LOCKED CHUNK",
            "",
            "§7" + materialName,
            "",
            createStatusText(hasItems, playerCount, requiredCount),
            "§a§l RIGHT-CLICK TO UNLOCK"
        );
    }
}
