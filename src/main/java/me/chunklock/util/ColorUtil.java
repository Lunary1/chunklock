package me.chunklock.util;

import java.util.regex.Pattern;

/**
 * Cross-compatible color utility that works with both legacy and modern servers.
 * Handles color code translation without using deprecated ChatColor methods.
 */
public final class ColorUtil {
    
    private static final Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}");
    private static final Pattern LEGACY_PATTERN = Pattern.compile("&[0-9a-fk-or]");
    private static final Pattern COLOR_PATTERN = Pattern.compile("§[0-9a-fk-or]");
    
    private ColorUtil() {}
    
    /**
     * Translates color codes from & to § format.
     * Safe alternative to deprecated ChatColor.translateAlternateColorCodes.
     */
    public static String translateColorCodes(String message) {
        if (message == null) return "";
        
        // Replace & color codes with § color codes
        StringBuilder result = new StringBuilder();
        char[] chars = message.toCharArray();
        
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                char next = chars[i + 1];
                if (isValidColorCode(next)) {
                    result.append('§').append(next);
                    i++; // Skip the next character
                    continue;
                }
            }
            result.append(chars[i]);
        }
        
        return result.toString();
    }
    
    /**
     * Strips all color codes from a message.
     * Safe alternative to deprecated ChatColor.stripColor.
     */
    public static String stripColors(String message) {
        if (message == null) return "";
        
        // Remove both § and & color codes
        return message.replaceAll("§[0-9a-fk-or]", "")
                      .replaceAll("&[0-9a-fk-or]", "");
    }
    
    /**
     * Checks if a character is a valid color code.
     */
    private static boolean isValidColorCode(char c) {
        return (c >= '0' && c <= '9') || 
               (c >= 'a' && c <= 'f') || 
               (c >= 'A' && c <= 'F') || 
               c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o' || c == 'r' ||
               c == 'K' || c == 'L' || c == 'M' || c == 'N' || c == 'O' || c == 'R';
    }
    
    /**
     * Converts hex color codes (&#RRGGBB) to legacy format for older servers.
     */
    public static String convertHexToLegacy(String message) {
        if (message == null) return "";
        
        // For older servers, convert hex colors to closest legacy equivalent
        return HEX_PATTERN.matcher(message).replaceAll("§f"); // Default to white
    }
    
    /**
     * Processes a message with the best color support for the current server.
     */
    public static String processColors(String message) {
        if (message == null) return "";
        
        // Always translate & codes to § codes
        String processed = translateColorCodes(message);
        
        // If not on a modern server, convert hex to legacy
        if (!ServerCompatibility.supportsModernFeatures()) {
            processed = convertHexToLegacy(processed);
        }
        
        return processed;
    }
}
