package me.chunklock.util;

import org.bukkit.Bukkit;

/**
 * Utility to detect server type and version for compatibility handling.
 * Supports Paper, Pufferfish, Purpur, Spigot, CraftBukkit and other forks.
 */
public final class ServerCompatibility {
    
    private static final ServerType SERVER_TYPE;
    private static final String SERVER_VERSION;
    private static final boolean ADVENTURE_AVAILABLE;
    private static final boolean MODERN_BIOME_API;
    
    static {
        String serverName = Bukkit.getName().toLowerCase();
        String version = Bukkit.getVersion();
        SERVER_VERSION = version;
        
        // Detect server type
        if (serverName.contains("pufferfish")) {
            SERVER_TYPE = ServerType.PUFFERFISH;
        } else if (serverName.contains("purpur")) {
            SERVER_TYPE = ServerType.PURPUR;
        } else if (serverName.contains("paper")) {
            SERVER_TYPE = ServerType.PAPER;
        } else if (serverName.contains("spigot")) {
            SERVER_TYPE = ServerType.SPIGOT;
        } else if (serverName.contains("craftbukkit") || serverName.contains("bukkit")) {
            SERVER_TYPE = ServerType.CRAFTBUKKIT;
        } else {
            SERVER_TYPE = ServerType.UNKNOWN;
        }
        
        // Check Adventure API availability
        boolean adventureFound = false;
        try {
            Class.forName("net.kyori.adventure.text.Component");
            // Try to actually use Adventure to make sure it works
            Class.forName("net.kyori.adventure.text.format.NamedTextColor");
            adventureFound = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Adventure not available
        }
        ADVENTURE_AVAILABLE = adventureFound;
        
        // Check if modern Biome API is available
        boolean modernBiome = false;
        try {
            Class.forName("org.bukkit.Registry");
            // Check if Registry.BIOME exists
            Class<?> registryClass = Class.forName("org.bukkit.Registry");
            registryClass.getField("BIOME");
            modernBiome = true;
        } catch (Exception e) {
            // Modern Biome API not available
        }
        MODERN_BIOME_API = modernBiome;
    }
    
    private ServerCompatibility() {}
    
    public enum ServerType {
        PAPER("Paper"),
        PUFFERFISH("Pufferfish"), 
        PURPUR("Purpur"),
        SPIGOT("Spigot"),
        CRAFTBUKKIT("CraftBukkit"),
        UNKNOWN("Unknown");
        
        private final String displayName;
        
        ServerType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    /**
     * Gets the detected server type.
     */
    public static ServerType getServerType() {
        return SERVER_TYPE;
    }
    
    /**
     * Gets the server version string.
     */
    public static String getServerVersion() {
        return SERVER_VERSION;
    }
    
    /**
     * Checks if this is a Paper-based server (Paper, Pufferfish, Purpur, etc.).
     */
    public static boolean isPaperBased() {
        return SERVER_TYPE == ServerType.PAPER || 
               SERVER_TYPE == ServerType.PUFFERFISH || 
               SERVER_TYPE == ServerType.PURPUR;
    }
    
    /**
     * Checks if Adventure API is available and functional.
     */
    public static boolean isAdventureAvailable() {
        return ADVENTURE_AVAILABLE;
    }
    
    /**
     * Checks if modern Biome API (Registry-based) is available.
     */
    public static boolean isModernBiomeAPI() {
        return MODERN_BIOME_API;
    }
    
    /**
     * Checks if the server supports modern features.
     */
    public static boolean supportsModernFeatures() {
        return isPaperBased() && ADVENTURE_AVAILABLE;
    }
    
    /**
     * Gets a compatibility summary for debugging.
     */
    public static String getCompatibilitySummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Server: ").append(SERVER_TYPE.getDisplayName()).append("\n");
        sb.append("Version: ").append(SERVER_VERSION).append("\n");
        sb.append("Paper-based: ").append(isPaperBased()).append("\n");
        sb.append("Adventure API: ").append(ADVENTURE_AVAILABLE).append("\n");
        sb.append("Modern Biome API: ").append(MODERN_BIOME_API).append("\n");
        sb.append("Modern Features: ").append(supportsModernFeatures());
        return sb.toString();
    }
    
    /**
     * Gets the Minecraft version from the server version string.
     */
    public static String getMinecraftVersion() {
        try {
            String version = Bukkit.getBukkitVersion();
            if (version.contains("-")) {
                return version.split("-")[0];
            }
            return version;
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * Checks if the server version is at least the specified version.
     */
    public static boolean isVersionAtLeast(String targetVersion) {
        try {
            String currentVersion = getMinecraftVersion();
            return compareVersions(currentVersion, targetVersion) >= 0;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Compares two version strings (e.g., "1.20.4" vs "1.21.0").
     * Returns: negative if v1 < v2, zero if v1 == v2, positive if v1 > v2
     */
    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int maxLength = Math.max(parts1.length, parts2.length);
        
        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            
            if (num1 != num2) {
                return num1 - num2;
            }
        }
        
        return 0;
    }
}
