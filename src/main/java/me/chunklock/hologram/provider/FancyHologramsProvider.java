package me.chunklock.hologram.provider;

import me.chunklock.hologram.api.Hologram;
import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.core.HologramData;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Implementation of HologramProvider for FancyHolograms plugin.
 * Optimized for per-player world hologram visibility.
 */
public final class FancyHologramsProvider implements HologramProvider {

    private static final String PROVIDER_NAME = "FancyHolograms";

    private final boolean available;
    private final ReflectionHelper reflection;
    private final Map<String, FancyHologram> managedHolograms = new ConcurrentHashMap<>();

    public FancyHologramsProvider() {
        this.reflection = new ReflectionHelper();
        this.available = reflection.isInitialized();

        if (available) {
            ChunklockPlugin.getInstance().getLogger().info("✅ FancyHolograms provider initialized successfully");
        } else {
            ChunklockPlugin.getInstance().getLogger().warning("❌ FancyHolograms provider initialization failed");
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Optional<Hologram> createHologram(HologramData hologramData) {
        if (!available) {
            return Optional.empty();
        }

        try {
            ChunklockPlugin plugin = ChunklockPlugin.getInstance();
            
            // Only log creation attempts, not every step
            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().fine("Creating hologram " + hologramData.getId());
            }
            
            // Create hologram data with proper settings for per-player worlds
            Object textHologramData = createHologramData(hologramData);
            if (textHologramData == null) {
                return Optional.empty();
            }

            // Create the hologram through FancyHolograms manager
            Object wrappedHologram = reflection.createHologram(textHologramData);
            if (wrappedHologram == null) {
                plugin.getLogger().warning("Failed to create hologram object for " + hologramData.getId());
                return Optional.empty();
            }

            // Register with FancyHolograms manager
            if (!reflection.addHologram(wrappedHologram)) {
                plugin.getLogger().warning("Failed to register hologram with manager: " + hologramData.getId());
                return Optional.empty();
            }

            // Create our wrapper
            FancyHologram hologram = new FancyHologram(hologramData.getId().getId(), wrappedHologram, hologramData.getLocation());
            managedHolograms.put(hologramData.getId().getId(), hologram);

            // CRITICAL: Handle per-player world visibility after registration
            schedulePlayerVisibilitySetup(wrappedHologram, hologramData.getId().getId());

            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().fine("Successfully created and registered hologram " + hologramData.getId());
            }
            return Optional.of(hologram);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Critical error creating FancyHologram: " + hologramData.getId(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean removeHologram(Hologram hologram) {
        if (!available || !(hologram instanceof FancyHologram fancyHologram)) {
            ChunklockPlugin.getInstance().getLogger().fine("removeHologram failed: not available or wrong type");
            return false;
        }

        try {
            ChunklockPlugin.getInstance().getLogger().fine("Attempting to remove hologram: " + fancyHologram.getId());
            
            // Remove from FancyHolograms manager first
            boolean removed = reflection.removeHologram(fancyHologram.getWrappedHologram());
            
            // Remove from our tracking
            managedHolograms.remove(fancyHologram.getId());
            
            if (removed) {
                ChunklockPlugin.getInstance().getLogger().fine("Successfully removed hologram: " + fancyHologram.getId());
            } else {
                ChunklockPlugin.getInstance().getLogger().fine("Failed to remove hologram from FancyHolograms manager: " + fancyHologram.getId());
            }
            return removed;

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to remove FancyHologram: " + hologram.getId(), e);
            return false;
        }
    }

    @Override
    public boolean updateHologram(Hologram hologram, HologramData newData) {
        if (!available || !(hologram instanceof FancyHologram fancyHologram)) {
            return false;
        }

        try {
            // Try to update text directly on the existing hologram
            Object wrappedHologram = fancyHologram.getWrappedHologram();
            
            if (reflection.updateHologramText(wrappedHologram, newData.getLines())) {
                // Update successful - text updated without recreating
                if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                    ChunklockPlugin.getInstance().getLogger().fine("Updated hologram text for " + hologram.getId());
                }
                return true;
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                "Direct text update failed for " + hologram.getId() + ", falling back to recreation", e);
        }

        // Fallback: Force recreation by removing tracking and letting the display service recreate
        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Force recreating hologram " + hologram.getId() + " - removing old completely");
        }
        
        // Remove the hologram completely and return false to trigger recreation
        removeHologram(hologram);
        return false; // This will cause the DisplayService to recreate the hologram
    }

    @Override
    public void cleanup() {
        if (!available) return;

        if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
            ChunklockPlugin.getInstance().getLogger().fine("Cleaning up " + managedHolograms.size() + " holograms");
        }
        
        for (FancyHologram hologram : managedHolograms.values()) {
            try {
                reflection.removeHologram(hologram.getWrappedHologram());
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                    "Error removing hologram during cleanup: " + hologram.getId(), e);
            }
        }
        managedHolograms.clear();
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("provider", PROVIDER_NAME);
        stats.put("available", available);
        stats.put("managedHolograms", managedHolograms.size());
        stats.put("reflectionInitialized", reflection.isInitialized());
        return stats;
    }

    /**
     * Creates and configures hologram data for per-player world compatibility.
     */
    private Object createHologramData(HologramData data) {
        try {
            // Create base hologram data
            Object hologramData = reflection.createTextHologramData(data.getId().getId(), data.getLocation());
            if (hologramData == null) {
                return null;
            }

            // Set text content
            if (!reflection.setHologramText(hologramData, data.getLines())) {
                ChunklockPlugin.getInstance().getLogger().warning("DEBUG: Failed to set hologram text");
            }

            // Configure for per-player world visibility
            configureHologramSettings(hologramData, data);

            if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                ChunklockPlugin.getInstance().getLogger().fine("Configured hologram data for " + data.getId());
            }
            return hologramData;

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to create hologram data for: " + data.getId(), e);
            return null;
        }
    }

    /**
     * Configures hologram settings optimized for per-player worlds.
     */
    private void configureHologramSettings(Object hologramData, HologramData data) {
        // Set visibility distance
        reflection.setVisibilityDistance(hologramData, (int) data.getViewDistance());

        // Use MANUAL visibility mode for per-player world control
        if (reflection.setManualVisibility(hologramData)) {
            // Only log if fine logging is enabled
            ChunklockPlugin plugin = ChunklockPlugin.getInstance();
            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().fine("Set MANUAL visibility mode for hologram");
            }
        }

        // Set persistent to true for manual control
        reflection.setPersistent(hologramData, true);

        // Configure billboard and rotation for proper display
        setBillboardAndRotation(hologramData, data);
    }

    /**
     * Sets billboard and rotation for proper hologram orientation.
     */
    private void setBillboardAndRotation(Object hologramData, HologramData data) {
        try {
            // Use FIXED billboard for stationary display (no rotation with player)
            reflection.setFixedBillboard(hologramData);

            // Set transparent background and disable shadow to reduce visual clutter
            reflection.setTransparentBackground(hologramData);
            reflection.disableShadow(hologramData);

            // Set rotation to face the chunk (custom orientation)
            reflection.setRotation(hologramData, data.getYaw(), data.getPitch());

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Non-critical error setting billboard/rotation: " + e.getMessage());
        }
    }

    /**
     * Schedules player visibility setup after hologram registration.
     * This is critical for per-player world hologram visibility.
     */
    private void schedulePlayerVisibilitySetup(Object wrappedHologram, String hologramId) {
        // Run on next tick to ensure hologram is fully registered
        new BukkitRunnable() {
            @Override
            public void run() {
                setupPlayerVisibility(wrappedHologram, hologramId);
            }
        }.runTaskLater(ChunklockPlugin.getInstance(), 1L);
    }

    /**
     * Sets up player-specific visibility for per-player world holograms.
     */
    private void setupPlayerVisibility(Object wrappedHologram, String hologramId) {
        try {
            ChunklockPlugin plugin = ChunklockPlugin.getInstance();
            
            // Extract player UUID from hologram ID (format: "playerUUID_...")
            UUID playerUuid = extractPlayerUUID(hologramId);
            if (playerUuid == null) {
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Could not extract player UUID from hologram ID: " + hologramId);
                }
                return;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Player not found or offline: " + playerUuid);
                }
                return;
            }

            // Get hologram name for manual visibility
            String hologramName = reflection.getHologramName(wrappedHologram);
            if (hologramName == null) {
                plugin.getLogger().warning("Could not get hologram name for visibility setup");
                return;
            }

            // Use FancyHolograms manual visibility system
            boolean visibilitySet = false;
            
            // Method 1: Manual visibility distant viewer
            if (reflection.addDistantViewer(hologramName, playerUuid)) {
                visibilitySet = true;
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Added distant viewer for hologram " + hologramName);
                }
            }

            // Method 2: Force show hologram
            if (reflection.forceShowHologram(wrappedHologram, player)) {
                visibilitySet = true;
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Force showed hologram to player " + player.getName());
                }
            }

            // Method 3: Update shown state
            if (reflection.updateShownState(wrappedHologram, player)) {
                visibilitySet = true;
                if (plugin.getLogger().isLoggable(Level.FINE)) {
                    plugin.getLogger().fine("Updated shown state for player " + player.getName());
                }
            }

            if (!visibilitySet) {
                plugin.getLogger().warning("All visibility methods failed for hologram " + hologramId);
            }

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Critical error setting up player visibility for hologram: " + hologramId, e);
        }
    }

    /**
     * Extracts player UUID from hologram ID.
     */
    private UUID extractPlayerUUID(String hologramId) {
        return me.chunklock.hologram.core.HologramId.extractPlayerUUID(hologramId);
    }

    /**
     * Reflection helper class for FancyHolograms API interactions.
     */
    private static class ReflectionHelper {
        private boolean initialized = false;
        private Object hologramManager;
        private Class<?> textHologramDataClass;
        private Class<?> hologramClass;
        private Constructor<?> textHologramDataConstructor;
        
        // Cached methods for performance
        private Method createHologramMethod;
        private Method addHologramMethod;
        private Method removeHologramMethod;
        private Method setTextMethod;
        private Method setVisibilityDistanceMethod;
        private Method setVisibilityMethod;
        private Method setPersistentMethod;
        private Method setBillboardMethod;
        private Method setRotationMethod;
        private Method getNameMethod;
        private Method forceShowHologramMethod;
        private Method updateShownStateMethod;
        private Method addDistantViewerMethod;
        private Method setBackgroundMethod;
        private Method setShadowMethod;
        
        // Cached classes and objects
        private Class<?> visibilityClass;
        private Object manualVisibility;
        private Class<?> billboardClass;
        private Object fixedBillboard;
        private Object centerBillboard;
        private Object verticalBillboard;

        public ReflectionHelper() {
            initialize();
        }

        private void initialize() {
            try {
                // Check plugin availability
                if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
                    ChunklockPlugin.getInstance().getLogger().warning("FancyHolograms plugin not found");
                    return;
                }

                // Load core classes
                Class<?> fancyPluginClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
                textHologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
                hologramClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
                Class<?> hologramManagerClass = Class.forName("de.oliver.fancyholograms.api.HologramManager");
                Class<?> hologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData");

                // Get plugin instance and manager
                Object pluginInstance = fancyPluginClass.getMethod("get").invoke(null);
                hologramManager = fancyPluginClass.getMethod("getHologramManager").invoke(pluginInstance);

                // Cache constructor
                textHologramDataConstructor = textHologramDataClass.getConstructor(String.class, Location.class);

                // Cache core methods
                createHologramMethod = hologramManagerClass.getMethod("create", hologramDataClass);
                addHologramMethod = hologramManagerClass.getMethod("addHologram", hologramClass);
                removeHologramMethod = hologramManagerClass.getMethod("removeHologram", hologramClass);
                setTextMethod = textHologramDataClass.getMethod("setText", java.util.List.class);
                getNameMethod = hologramClass.getMethod("getName");

                // Cache optional methods (may not exist in all versions)
                cacheOptionalMethods();

                // Cache visibility classes and objects
                cacheVisibilityComponents();

                // Cache billboard components
                cacheBillboardComponents();

                initialized = true;
                if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                    ChunklockPlugin.getInstance().getLogger().fine("ReflectionHelper initialized successfully");
                }

            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                    "Failed to initialize FancyHolograms reflection", e);
                initialized = false;
            }
        }

        private void cacheOptionalMethods() {
            // These methods may not exist in all FancyHolograms versions
            try {
                setVisibilityDistanceMethod = textHologramDataClass.getMethod("setVisibilityDistance", int.class);
            } catch (Exception e) { /* Ignore */ }

            try {
                setPersistentMethod = textHologramDataClass.getMethod("setPersistent", boolean.class);
            } catch (Exception e) { /* Ignore */ }

            try {
                setRotationMethod = textHologramDataClass.getMethod("setRotation", float.class, float.class);
            } catch (Exception e) { /* Ignore */ }

            try {
                forceShowHologramMethod = hologramClass.getMethod("forceShowHologram", Player.class);
            } catch (Exception e) { /* Ignore */ }

            try {
                updateShownStateMethod = hologramClass.getMethod("updateShownStateFor", Player.class);
            } catch (Exception e) { /* Ignore */ }

            // Try to find background/transparency methods
            try {
                setBackgroundMethod = textHologramDataClass.getMethod("setBackground", int.class);
            } catch (Exception e) { /* Background method not available */ }

            try {
                setShadowMethod = textHologramDataClass.getMethod("setShadow", boolean.class);
            } catch (Exception e) { /* Shadow method not available */ }
        }

        private void cacheVisibilityComponents() {
            try {
                visibilityClass = Class.forName("de.oliver.fancyholograms.api.data.property.Visibility");
                manualVisibility = visibilityClass.getField("MANUAL").get(null);
                setVisibilityMethod = textHologramDataClass.getMethod("setVisibility", visibilityClass);

                // Manual visibility methods
                Class<?> manualVisibilityClass = Class.forName("de.oliver.fancyholograms.api.data.property.Visibility$ManualVisibility");
                addDistantViewerMethod = manualVisibilityClass.getMethod("addDistantViewer", String.class, UUID.class);

            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Could not cache visibility components: " + e.getMessage());
            }
        }

        private void cacheBillboardComponents() {
            try {
                billboardClass = Class.forName("org.bukkit.entity.Display$Billboard");
                fixedBillboard = billboardClass.getField("FIXED").get(null);
                centerBillboard = billboardClass.getField("CENTER").get(null);
                verticalBillboard = billboardClass.getField("VERTICAL").get(null);
                setBillboardMethod = textHologramDataClass.getMethod("setBillboard", billboardClass);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Could not cache billboard components: " + e.getMessage());
            }
        }

        public boolean isInitialized() {
            return initialized;
        }

        public Object createTextHologramData(String id, Location location) {
            try {
                return textHologramDataConstructor.newInstance(id, location);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Failed to create TextHologramData", e);
                return null;
            }
        }

        public boolean setHologramText(Object hologramData, java.util.List<String> lines) {
            try {
                setTextMethod.invoke(hologramData, lines);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to set hologram text", e);
                return false;
            }
        }

        public boolean updateHologramText(Object hologram, java.util.List<String> lines) {
            // Try to find and invoke an update text method on the hologram itself
            try {
                Method updateTextMethod = hologramClass.getMethod("updateText", java.util.List.class);
                updateTextMethod.invoke(hologram, lines);
                ChunklockPlugin.getInstance().getLogger().fine("Successfully updated hologram text using updateText method");
                return true;
            } catch (NoSuchMethodException e) {
                // Method doesn't exist, try alternative
                try {
                    Method setTextMethod = hologramClass.getMethod("setText", java.util.List.class);
                    setTextMethod.invoke(hologram, lines);
                    ChunklockPlugin.getInstance().getLogger().fine("Successfully updated hologram text using setText method");
                    return true;
                } catch (NoSuchMethodException ex2) {
                    // Try getting the data and updating that
                    try {
                        Method getDataMethod = hologramClass.getMethod("getData");
                        Object hologramData = getDataMethod.invoke(hologram);
                        if (hologramData != null) {
                            setTextMethod.invoke(hologramData, lines);
                            // Force hologram refresh
                            Method refreshMethod = hologramClass.getMethod("refresh");
                            refreshMethod.invoke(hologram);
                            ChunklockPlugin.getInstance().getLogger().fine("Successfully updated hologram text using getData->setText->refresh method");
                            return true;
                        }
                    } catch (Exception ex3) {
                        ChunklockPlugin.getInstance().getLogger().fine("Direct text update methods not available - will fallback to recreate");
                        return false;
                    }
                } catch (Exception ex) {
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to update hologram text with setText", ex);
                    return false;
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to update hologram text with updateText", e);
                return false;
            }
            return false;
        }

        public Object createHologram(Object hologramData) {
            try {
                return createHologramMethod.invoke(hologramManager, hologramData);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Failed to create hologram", e);
                return null;
            }
        }

        public boolean addHologram(Object hologram) {
            try {
                addHologramMethod.invoke(hologramManager, hologram);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Failed to add hologram to manager", e);
                return false;
            }
        }

        public boolean removeHologram(Object hologram) {
            try {
                removeHologramMethod.invoke(hologramManager, hologram);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Failed to remove hologram", e);
                return false;
            }
        }

        public boolean setVisibilityDistance(Object hologramData, int distance) {
            if (setVisibilityDistanceMethod == null) return false;
            try {
                setVisibilityDistanceMethod.invoke(hologramData, distance);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean setManualVisibility(Object hologramData) {
            if (setVisibilityMethod == null || manualVisibility == null) return false;
            try {
                setVisibilityMethod.invoke(hologramData, manualVisibility);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean setPersistent(Object hologramData, boolean persistent) {
            if (setPersistentMethod == null) return false;
            try {
                setPersistentMethod.invoke(hologramData, persistent);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean setFixedBillboard(Object hologramData) {
            if (setBillboardMethod == null || fixedBillboard == null) return false;
            try {
                setBillboardMethod.invoke(hologramData, fixedBillboard);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        public boolean setCenterBillboard(Object hologramData) {
            if (setBillboardMethod == null || centerBillboard == null) return false;
            try {
                setBillboardMethod.invoke(hologramData, centerBillboard);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unused")
        public boolean setVerticalBillboard(Object hologramData) {
            if (setBillboardMethod == null || verticalBillboard == null) return false;
            try {
                setBillboardMethod.invoke(hologramData, verticalBillboard);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean setTransparentBackground(Object hologramData) {
            if (setBackgroundMethod == null) return false;
            try {
                // Set background to transparent (0x00000000) or fully transparent
                setBackgroundMethod.invoke(hologramData, 0x00000000);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean disableShadow(Object hologramData) {
            if (setShadowMethod == null) return false;
            try {
                setShadowMethod.invoke(hologramData, false);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean setRotation(Object hologramData, float yaw, float pitch) {
            if (setRotationMethod == null) return false;
            try {
                setRotationMethod.invoke(hologramData, yaw, pitch);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        public String getHologramName(Object hologram) {
            try {
                return (String) getNameMethod.invoke(hologram);
            } catch (Exception e) {
                return null;
            }
        }

        public boolean addDistantViewer(String hologramName, UUID playerUuid) {
            if (addDistantViewerMethod == null) return false;
            try {
                addDistantViewerMethod.invoke(null, hologramName, playerUuid);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Failed to add distant viewer: " + e.getMessage());
                return false;
            }
        }

        public boolean forceShowHologram(Object hologram, Player player) {
            if (forceShowHologramMethod == null) return false;
            try {
                forceShowHologramMethod.invoke(hologram, player);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Failed to force show hologram: " + e.getMessage());
                return false;
            }
        }

        public boolean updateShownState(Object hologram, Player player) {
            if (updateShownStateMethod == null) return false;
            try {
                updateShownStateMethod.invoke(hologram, player);
                return true;
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().fine("Failed to update shown state: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Wrapper implementation for FancyHolograms.
     */
    private static final class FancyHologram implements Hologram {
        private final String id;
        private final Object wrappedHologram;
        private final Location location;
        private volatile boolean visible = true;

        public FancyHologram(String id, Object wrappedHologram, Location location) {
            this.id = id;
            this.wrappedHologram = wrappedHologram;
            this.location = location.clone();
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Location getLocation() {
            return location.clone();
        }

        @Override
        public void setVisible(boolean visible) {
            this.visible = visible;
            // Note: FancyHolograms manages visibility through its own system
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void updateText(java.util.List<String> lines) {
            try {
                Method setTextMethod = wrappedHologram.getClass().getMethod("setText", java.util.List.class);
                setTextMethod.invoke(wrappedHologram, lines);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                    "Failed to update hologram text: " + id, e);
            }
        }

        @Override
        public Object getWrappedHologram() {
            return wrappedHologram;
        }

        @Override
        public boolean isValid() {
            return wrappedHologram != null;
        }
    }
}
