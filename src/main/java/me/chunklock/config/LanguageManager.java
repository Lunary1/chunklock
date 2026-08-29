package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages language files and provides message retrieval with placeholder replacement.
 * Supports multi-file language system (lang/en.yml, lang/de.yml, etc.) with fallback to default language.
 * 
 * @author Chunklock Team
 * @version 2.0.0
 */
public class LanguageManager {
    
    private static final String DEFAULT_LANGUAGE = "en";
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([a-zA-Z0-9_]+)%");
    
    private final Plugin plugin;
    private final java.util.logging.Logger logger;
    private String currentLanguage;
    private FileConfiguration currentLangConfig;
    private FileConfiguration defaultLangConfig;
    private final Map<String, String> messageCache;
    
    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messageCache = new HashMap<>();
        load();
    }
    
    /**
     * Loads language files from the lang directory.
     * Creates default language file if it doesn't exist.
     */
    public void load() {
        messageCache.clear();
        
        // Get language setting from config
        String languageSetting = getLanguageFromConfig();
        this.currentLanguage = languageSetting != null && !languageSetting.isEmpty() ? languageSetting : DEFAULT_LANGUAGE;
        
        // Ensure lang directory exists
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        
        // Load default language (en.yml) - always required for fallback
        File defaultLangFile = new File(langDir, DEFAULT_LANGUAGE + ".yml");
        if (!defaultLangFile.exists()) {
            // Copy default language file from resources
            plugin.saveResource("lang/" + DEFAULT_LANGUAGE + ".yml", false);
        }
        defaultLangConfig = YamlConfiguration.loadConfiguration(defaultLangFile);

        // Back the on-disk file with the one bundled in the jar, so keys added by an update
        // resolve on servers that already have a lang/en.yml. The file is only written when
        // absent - by design, since admins edit it - which means a server that has ever run
        // an older build keeps that older file forever. Without this fallback every message
        // added after a server's first start renders as its raw key.
        applyBundledDefaults(defaultLangConfig, "lang/" + DEFAULT_LANGUAGE + ".yml");
        
        // Load current language file
        if (currentLanguage.equals(DEFAULT_LANGUAGE)) {
            currentLangConfig = defaultLangConfig;
        } else {
            File currentLangFile = new File(langDir, currentLanguage + ".yml");
            if (!currentLangFile.exists()) {
                logger.warning("Language file '" + currentLanguage + ".yml' not found. Using default language (en).");
                currentLangConfig = defaultLangConfig;
                this.currentLanguage = DEFAULT_LANGUAGE;
            } else {
                currentLangConfig = YamlConfiguration.loadConfiguration(currentLangFile);
                applyBundledDefaults(currentLangConfig, "lang/" + currentLanguage + ".yml");
            }
        }

        logger.info("Language system loaded: " + currentLanguage);
    }

    /**
     * Fall back to the copy of a language file bundled in the jar for any key the on-disk
     * file does not define.
     *
     * <p>Language files are written to the data folder only when absent, because admins are
     * expected to edit them. The consequence is that a server which has run any earlier build
     * keeps its original file indefinitely, so every message key introduced by a later update
     * is missing from it and renders as the raw key - for example
     * {@code gui.builder.reroll-title} appearing verbatim in an item's lore.</p>
     *
     * <p>Bukkit's {@code setDefaults} resolves exactly this: the admin's file keeps priority
     * for everything it defines, and anything it lacks falls through to the bundled copy.
     * Admin edits are preserved and new keys work without touching their file.</p>
     */
    private void applyBundledDefaults(FileConfiguration config, String resourcePath) {
        if (config == null) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                return; // No bundled copy for this language - nothing to fall back to.
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                config.setDefaults(YamlConfiguration.loadConfiguration(reader));
                config.options().copyDefaults(false);
            }
        } catch (Exception e) {
            logger.warning("Could not load bundled defaults for " + resourcePath
                + "; messages added since this server's language file was created may show as "
                + "raw keys: " + e.getMessage());
        }
    }
    
    /**
     * Gets the language setting from config.yml.
     */
    private String getLanguageFromConfig() {
        if (plugin instanceof me.chunklock.ChunklockPlugin) {
            ConfigManager configManager = ((me.chunklock.ChunklockPlugin) plugin).getConfigManager();
            if (configManager != null) {
                return configManager.getString("language", DEFAULT_LANGUAGE);
            }
        }
        // Fallback: read directly from config
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (configFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            return config.getString("language", DEFAULT_LANGUAGE);
        }
        return DEFAULT_LANGUAGE;
    }
    
    /**
     * Gets a message by key, with placeholder replacement.
     * 
     * @param key The language key (e.g., "commands.unlock.usage")
     * @param placeholders Map of placeholder names to values (e.g., {"player": "Steve", "cost": "100"})
     * @return The formatted message with placeholders replaced
     */
    public String getMessage(String key, Map<String, String> placeholders) {
        String message = getMessage(key);
        if (message == null || placeholders == null || placeholders.isEmpty()) {
            return message != null ? message : key;
        }
        
        return replacePlaceholders(message, placeholders);
    }
    
    /**
     * Gets a message by key without placeholders.
     * 
     * @param key The language key
     * @return The message, or the key itself if not found
     */
    public String getMessage(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        
        // Check cache first
        if (messageCache.containsKey(key)) {
            return messageCache.get(key);
        }
        
        String message = null;
        
        // Try current language first
        if (currentLangConfig != null && currentLangConfig.contains(key)) {
            Object value = currentLangConfig.get(key);
            if (value != null) {
                message = value.toString();
            }
        }
        
        // Fallback to default language if not found
        if (message == null && defaultLangConfig != null && defaultLangConfig.contains(key)) {
            Object value = defaultLangConfig.get(key);
            if (value != null) {
                message = value.toString();
                logger.fine("Using fallback message for key: " + key);
            }
        }
        
        // If still not found, log warning and return key
        if (message == null) {
            logger.warning("Missing language key: " + key + " (language: " + currentLanguage + ")");
            message = key; // Return key as fallback
        }
        
        // Cache the message
        messageCache.put(key, message);
        
        return message;
    }
    
    /**
     * Gets a list of messages (for multi-line messages like help text).
     * 
     * @param key The language key
     * @return List of messages, or empty list if not found
     */
    public java.util.List<String> getMessageList(String key) {
        if (key == null || key.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        
        java.util.List<String> messages = null;
        
        // Try current language first
        if (currentLangConfig != null && currentLangConfig.contains(key)) {
            if (currentLangConfig.isList(key)) {
                messages = currentLangConfig.getStringList(key);
            } else if (currentLangConfig.isString(key)) {
                // Single string - convert to list
                messages = new java.util.ArrayList<>();
                messages.add(currentLangConfig.getString(key));
            }
        }
        
        // Fallback to default language
        if (messages == null && defaultLangConfig != null && defaultLangConfig.contains(key)) {
            if (defaultLangConfig.isList(key)) {
                messages = defaultLangConfig.getStringList(key);
            } else if (defaultLangConfig.isString(key)) {
                messages = new java.util.ArrayList<>();
                messages.add(defaultLangConfig.getString(key));
            }
        }
        
        if (messages == null) {
            logger.warning("Missing language key (list): " + key + " (language: " + currentLanguage + ")");
            return new java.util.ArrayList<>();
        }
        
        return messages;
    }
    
    /**
     * Replaces placeholders in a message string.
     * Placeholders use format: %placeholder%
     * 
     * @param message The message template
     * @param placeholders Map of placeholder names to values
     * @return Message with placeholders replaced
     */
    private String replacePlaceholders(String message, Map<String, String> placeholders) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        
        String result = message;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            String replacement = placeholders.getOrDefault(placeholderName, "%" + placeholderName + "%");
            result = result.replace("%" + placeholderName + "%", replacement);
        }
        
        return result;
    }
    
    /**
     * Gets the current language code.
     * 
     * @return Current language code (e.g., "en", "de")
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * Reloads language files from disk.
     */
    public void reload() {
        load();
        logger.info("Language files reloaded");
    }
    
    /**
     * Clears the message cache.
     * Useful after reloading language files.
     */
    public void clearCache() {
        messageCache.clear();
    }
    
    /**
     * Gets the raw FileConfiguration for the current language.
     * Use with caution - prefer getMessage() methods.
     * 
     * @return Current language FileConfiguration
     */
    public FileConfiguration getRawConfig() {
        return currentLangConfig;
    }
}

