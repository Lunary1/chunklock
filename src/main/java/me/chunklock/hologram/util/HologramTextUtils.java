package me.chunklock.hologram.util;

import me.chunklock.config.LanguageKeys;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.util.item.MaterialUtil;
import me.chunklock.util.message.MessageUtil;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

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

        String name = MaterialUtil.formatMaterialName(material);
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
        String lockedTitle = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_LOCKED_TITLE);
        String materialLine = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_MATERIAL_LINE);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("material", materialName);
        materialLine = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_MATERIAL_LINE, placeholders);
        
        String statusText = hasItems ? 
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_STATUS_HAVE) :
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_STATUS_MISSING);
        placeholders.put("player_count", String.valueOf(playerCount));
        placeholders.put("required_count", String.valueOf(requiredCount));
        statusText = MessageUtil.getMessage(hasItems ? LanguageKeys.HOLOGRAM_STATUS_HAVE : LanguageKeys.HOLOGRAM_STATUS_MISSING, placeholders);
        
        String clickToUnlock = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CLICK_TO_UNLOCK);
        
        return java.util.List.of(
            lockedTitle,
            "",
            materialLine,
            "",
            statusText,
            clickToUnlock
        );
    }
    
    /**
     * Creates hologram text lines for multiple items (vanilla + custom).
     */
    public static java.util.List<String> createChunkHologramLinesForMultipleItems(
            java.util.List<ItemRequirement> requirements, boolean hasAllItems) {
        
        java.util.List<String> lines = new java.util.ArrayList<>();
        String lockedTitle = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_LOCKED_TITLE);
        lines.add(lockedTitle);
        lines.add("");
        
        // Add each requirement as a line
        Map<String, String> placeholders = new HashMap<>();
        for (ItemRequirement req : requirements) {
            String itemName = req.getDisplayName();
            int amount = req.getAmount();
            placeholders.put("material", itemName);
            placeholders.put("amount", String.valueOf(amount));
            String materialLine = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_MATERIAL_LINE, placeholders);
            String line = materialLine + " §8x" + amount;
            lines.add(line);
        }
        
        lines.add("");
        String statusMsg = hasAllItems ? 
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CAN_AFFORD) :
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CANNOT_AFFORD);
        lines.add(statusMsg);
        String clickToUnlock = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CLICK_TO_UNLOCK);
        lines.add(clickToUnlock);
        
        return lines;
    }
    
    /**
     * Creates hologram text lines for money-based unlocking (Vault economy mode).
     */
    public static java.util.List<String> createChunkHologramLinesForMoney(String formattedCost, 
                                                                          boolean canAfford) {
        String lockedTitle = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_LOCKED_TITLE);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("cost", formattedCost);
        String costLine = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_COST_LINE, placeholders);
        String statusMsg = canAfford ? 
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CAN_AFFORD) :
            MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CANNOT_AFFORD);
        String clickToUnlock = MessageUtil.getMessage(LanguageKeys.HOLOGRAM_CLICK_TO_UNLOCK);
        
        return java.util.List.of(
            lockedTitle,
            "",
            costLine,
            "",
            statusMsg,
            clickToUnlock
        );
    }
}
