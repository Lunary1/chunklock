package me.chunklock.util;

import org.bukkit.entity.Player;

/**
 * Utility class to handle messaging with Adventure API compatibility.
 * Provides fallback support for servers without Adventure API.
 * Enhanced compatibility for Pufferfish, Purpur, and other Paper forks.
 */
public final class MessageUtil {
    
    private MessageUtil() {}
    
    /**
     * Sends a message to a player with the best available method.
     */
    public static void sendMessage(Player player, String message) {
        if (player == null || message == null) return;
        
        // Try Adventure API if available on Paper-based servers
        if (ServerCompatibility.isAdventureAvailable() && ServerCompatibility.isPaperBased()) {
            try {
                sendAdventureMessage(player, message);
                return;
            } catch (Exception e) {
                // Fall back to legacy if Adventure fails
            }
        }
        
        // Legacy fallback with color code support
        player.sendMessage(ColorUtil.processColors(message));
    }
    
    /**
     * Sends a message using Adventure API with MiniMessage support.
     */
    private static void sendAdventureMessage(Player player, String message) {
        try {
            // Try MiniMessage first (more flexible format)
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            
            // Check if message contains MiniMessage tags
            if (message.contains("<") && message.contains(">")) {
                // Use MiniMessage
                Class<?> miniMessageClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
                Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
                Object component = miniMessageClass.getMethod("deserialize", String.class).invoke(miniMessage, message);
                player.getClass().getMethod("sendMessage", componentClass).invoke(player, component);
            } else {
                // Use legacy serializer for & color codes
                Class<?> legacySerializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                Object serializer = legacySerializerClass.getMethod("legacyAmpersand").invoke(null);
                Object component = legacySerializerClass.getMethod("deserialize", String.class).invoke(serializer, message);
                player.getClass().getMethod("sendMessage", componentClass).invoke(player, component);
            }
        } catch (Exception e) {
            // Fall back to legacy
            player.sendMessage(ColorUtil.processColors(message));
        }
    }
    
    /**
     * Sends an action bar message with the best available method.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null) return;
        
        // Try Adventure API first
        if (ServerCompatibility.isAdventureAvailable() && ServerCompatibility.isPaperBased()) {
            try {
                sendAdventureActionBar(player, message);
                return;
            } catch (Exception e) {
                // Fall back if Adventure fails
            }
        }
        
        // Try Spigot's action bar method
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
            
            Object actionBarType = chatMessageTypeClass.getField("ACTION_BAR").get(null);
            Object textComponent = textComponentClass.getConstructor(String.class)
                    .newInstance(ColorUtil.processColors(message));
            
            player.getClass().getMethod("spigot").invoke(player).getClass()
                    .getMethod("sendMessage", chatMessageTypeClass, textComponentClass)
                    .invoke(player.getClass().getMethod("spigot").invoke(player), actionBarType, textComponent);
            return;
        } catch (Exception e) {
            // Spigot action bar failed
        }
        
        // Final fallback - send as regular message
        sendMessage(player, message);
    }
    
    /**
     * Sends an action bar using Adventure API.
     */
    private static void sendAdventureActionBar(Player player, String message) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            
            // Check for MiniMessage format
            Object component;
            if (message.contains("<") && message.contains(">")) {
                Class<?> miniMessageClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
                Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
                component = miniMessageClass.getMethod("deserialize", String.class).invoke(miniMessage, message);
            } else {
                Class<?> legacySerializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
                Object serializer = legacySerializerClass.getMethod("legacyAmpersand").invoke(null);
                component = legacySerializerClass.getMethod("deserialize", String.class).invoke(serializer, message);
            }
            
            player.getClass().getMethod("sendActionBar", componentClass).invoke(player, component);
        } catch (Exception e) {
            throw new RuntimeException("Adventure action bar failed", e);
        }
    }
    
    /**
     * Sends a title with subtitle.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        
        // Try Adventure API first
        if (ServerCompatibility.isAdventureAvailable() && ServerCompatibility.isPaperBased()) {
            try {
                sendAdventureTitle(player, title, subtitle, fadeIn, stay, fadeOut);
                return;
            } catch (Exception e) {
                // Fall back if Adventure fails
            }
        }
        
        // Try legacy Bukkit title method
        try {
            String titleText = title != null ? ColorUtil.processColors(title) : "";
            String subtitleText = subtitle != null ? ColorUtil.processColors(subtitle) : "";
            
            player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                    .invoke(player, titleText, subtitleText, fadeIn, stay, fadeOut);
        } catch (Exception e) {
            // Title failed, send as message instead
            if (title != null && !title.isEmpty()) {
                sendMessage(player, title);
            }
            if (subtitle != null && !subtitle.isEmpty()) {
                sendMessage(player, subtitle);
            }
        }
    }
    
    /**
     * Sends a title using Adventure API.
     */
    private static void sendAdventureTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            Class<?> titleClass = Class.forName("net.kyori.adventure.title.Title");
            Class<?> timesClass = Class.forName("net.kyori.adventure.title.Title$Times");
            Class<?> durationClass = Class.forName("java.time.Duration");
            
            // Create components
            Object titleComponent = createAdventureComponent(title);
            Object subtitleComponent = createAdventureComponent(subtitle);
            
            // Create times
            Object fadeInDuration = durationClass.getMethod("ofMillis", long.class).invoke(null, fadeIn * 50L);
            Object stayDuration = durationClass.getMethod("ofMillis", long.class).invoke(null, stay * 50L);
            Object fadeOutDuration = durationClass.getMethod("ofMillis", long.class).invoke(null, fadeOut * 50L);
            
            Object times = timesClass.getMethod("times", durationClass, durationClass, durationClass)
                    .invoke(null, fadeInDuration, stayDuration, fadeOutDuration);
            
            // Create title
            Object titleObj = titleClass.getMethod("title", componentClass, componentClass, timesClass)
                    .invoke(null, titleComponent, subtitleComponent, times);
            
            // Send title
            player.getClass().getMethod("showTitle", titleClass).invoke(player, titleObj);
        } catch (Exception e) {
            throw new RuntimeException("Adventure title failed", e);
        }
    }
    
    /**
     * Creates an Adventure Component from a string.
     */
    private static Object createAdventureComponent(String text) throws Exception {
        if (text == null || text.isEmpty()) {
            Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            return componentClass.getMethod("empty").invoke(null);
        }
        
        if (text.contains("<") && text.contains(">")) {
            // MiniMessage format
            Class<?> miniMessageClass = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
            Object miniMessage = miniMessageClass.getMethod("miniMessage").invoke(null);
            return miniMessageClass.getMethod("deserialize", String.class).invoke(miniMessage, text);
        } else {
            // Legacy format
            Class<?> legacySerializerClass = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
            Object serializer = legacySerializerClass.getMethod("legacyAmpersand").invoke(null);
            return legacySerializerClass.getMethod("deserialize", String.class).invoke(serializer, text);
        }
    }
    
    /**
     * Converts a legacy color code string to plain text.
     */
    public static String stripColors(String message) {
        return ColorUtil.stripColors(message);
    }
    
    /**
     * Converts legacy color codes (&) to Minecraft color codes (§).
     */
    public static String convertLegacyColors(String message) {
        return ColorUtil.translateColorCodes(message);
    }
    
    /**
     * Gets compatibility information for debugging.
     */
    public static String getCompatibilityInfo() {
        return "MessageUtil - Adventure: " + ServerCompatibility.isAdventureAvailable() + 
               ", Server: " + ServerCompatibility.getServerType().getDisplayName();
    }
}
