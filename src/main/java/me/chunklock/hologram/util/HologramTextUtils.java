package me.chunklock.hologram.util;

import org.bukkit.Material;
import me.chunklock.economy.items.ItemRequirement;

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
    
    /**
     * Creates hologram text lines for multiple items (vanilla + custom).
     */
    public static java.util.List<String> createChunkHologramLinesForMultipleItems(
            java.util.List<ItemRequirement> requirements, boolean hasAllItems) {
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§c§l LOCKED CHUNK");
        lines.add("");
        
        // Add each requirement as a line
        for (ItemRequirement req : requirements) {
            String itemName = req.getDisplayName();
            int amount = req.getAmount();
            String line = "§7" + itemName + " §8x" + amount;
            lines.add(line);
        }
        
        lines.add("");
        lines.add(hasAllItems ? "§a✓ §a§lYou have all items" : "§c✗ §c§lMissing items");
        lines.add("§a§l RIGHT-CLICK TO UNLOCK");
        
        return lines;
    }
    
    /**
     * Creates hologram text lines for money-based unlocking (Vault economy mode).
     */
    public static java.util.List<String> createChunkHologramLinesForMoney(String formattedCost, 
                                                                          boolean canAfford) {
        return java.util.List.of(
            "§c§l LOCKED CHUNK",
            "",
            "§6💰 Cost: " + formattedCost,
            "",
            canAfford ? "§a✓ You can afford this" : "§c✗ Insufficient funds",
            "§a§l RIGHT-CLICK TO UNLOCK"
        );
    }
}
