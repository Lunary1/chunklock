package me.chunklock.hologram.provider;

import me.chunklock.hologram.api.Hologram;
import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.core.HologramData;
import me.chunklock.hologram.core.HologramId;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Implementation of HologramProvider for CMI plugin.
 * Supports per-player hologram visibility using CMI's hologram API.
 */
public final class CMIHologramsProvider implements HologramProvider {

    private static final String PROVIDER_NAME = "CMI";

    private final boolean available;
    private final Map<String, CMIHologram> managedHolograms = new ConcurrentHashMap<>();
    private Object hologramManager;

    /**
     * Static method to check if CMI plugin is available without full initialization.
     * Use this before creating an instance to avoid unnecessary reflection overhead.
     */
    public static boolean isPluginAvailable() {
        try {
            // Quick check if CMI plugin is loaded
            return Bukkit.getPluginManager().getPlugin("CMI") != null;
        } catch (Exception e) {
            return false;
        }
    }

    public CMIHologramsProvider() {
        this.available = initializeCMI();

        if (available) {
            ChunklockPlugin.getInstance().getLogger().info("✅ CMI Holograms provider initialized successfully");
        } else {
            ChunklockPlugin.getInstance().getLogger().warning("⚠️ CMI plugin not found or hologram support unavailable");
        }
    }

    private boolean initializeCMI() {
        try {
            // Check if CMI plugin is loaded
            if (Bukkit.getPluginManager().getPlugin("CMI") == null) {
                ChunklockPlugin.getInstance().getLogger().info("CMI plugin not detected");
                return false;
            }

            // Try to access CMI API
            Class<?> cmiAPIClass = Class.forName("com.Zrips.CMI.CMIAPI");
            Object cmiAPI = cmiAPIClass.getMethod("getInstance").invoke(null);
            
            // Get hologram manager
            this.hologramManager = cmiAPIClass.getMethod("getHologramManager").invoke(cmiAPI);
            
            if (hologramManager == null) {
                ChunklockPlugin.getInstance().getLogger().warning("CMI HologramManager is null");
                return false;
            }

            return true;
        } catch (ClassNotFoundException e) {
            ChunklockPlugin.getInstance().getLogger().fine("CMI API classes not found: " + e.getMessage());
            return false;
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to initialize CMI hologram support", e);
            return false;
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
            
            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().fine("Creating CMI hologram " + hologramData.getId());
            }

            // Create CMI hologram
            Object cmiHologram = createCMIHologram(hologramData);
            if (cmiHologram == null) {
                plugin.getLogger().warning("Failed to create CMI hologram for " + hologramData.getId());
                return Optional.empty();
            }

            // Create wrapper
            CMIHologram hologram = new CMIHologram(hologramData.getId().getId(), cmiHologram, hologramData.getLocation());
            managedHolograms.put(hologramData.getId().getId(), hologram);

            // Set up player-specific visibility
            setupPlayerVisibility(cmiHologram, hologramData);

            if (plugin.getLogger().isLoggable(Level.FINE)) {
                plugin.getLogger().fine("Successfully created CMI hologram " + hologramData.getId());
            }
            
            return Optional.of(hologram);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Critical error creating CMI hologram: " + hologramData.getId(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean removeHologram(Hologram hologram) {
        if (!available || !(hologram instanceof CMIHologram cmiHologram)) {
            return false;
        }

        try {
            ChunklockPlugin.getInstance().getLogger().fine("Removing CMI hologram: " + cmiHologram.getId());
            
            // Delete the CMI hologram
            Object wrappedHologram = cmiHologram.getWrappedHologram();
            wrappedHologram.getClass().getMethod("remove").invoke(wrappedHologram);
            
            // Remove from tracking
            managedHolograms.remove(cmiHologram.getId());
            
            ChunklockPlugin.getInstance().getLogger().fine("Successfully removed CMI hologram: " + cmiHologram.getId());
            return true;

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to remove CMI hologram: " + hologram.getId(), e);
            return false;
        }
    }

    @Override
    public boolean updateHologram(Hologram hologram, HologramData newData) {
        if (!available || !(hologram instanceof CMIHologram cmiHologram)) {
            return false;
        }

        try {
            Object wrappedHologram = cmiHologram.getWrappedHologram();
            
            // Update lines
            wrappedHologram.getClass().getMethod("setLines", java.util.List.class)
                .invoke(wrappedHologram, newData.getLines());
            
            // Update the display
            wrappedHologram.getClass().getMethod("update").invoke(wrappedHologram);
            
            if (ChunklockPlugin.getInstance().getLogger().isLoggable(Level.FINE)) {
                ChunklockPlugin.getInstance().getLogger().fine("Updated CMI hologram lines for " + hologram.getId());
            }
            
            return true;

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to update CMI hologram: " + hologram.getId(), e);
            return false;
        }
    }

    @Override
    public void cleanup() {
        if (!available) {
            return;
        }

        try {
            // Remove all managed holograms
            for (CMIHologram hologram : managedHolograms.values()) {
                try {
                    Object wrappedHologram = hologram.getWrappedHologram();
                    wrappedHologram.getClass().getMethod("remove").invoke(wrappedHologram);
                } catch (Exception e) {
                    ChunklockPlugin.getInstance().getLogger().fine("Failed to cleanup hologram: " + hologram.getId());
                }
            }
            managedHolograms.clear();
            
            ChunklockPlugin.getInstance().getLogger().info("CMI Holograms provider cleaned up");
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Error during CMI holograms cleanup", e);
        }
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("provider", PROVIDER_NAME);
        stats.put("available", available);
        stats.put("managedHolograms", managedHolograms.size());
        return stats;
    }

    /**
     * Creates a CMI hologram using reflection to avoid hard dependencies.
     */
    private Object createCMIHologram(HologramData data) {
        try {
            // Get CMIHologram class
            Class<?> cmiHologramClass = Class.forName("com.Zrips.CMI.Modules.Holograms.CMIHologram");
            
            // Create hologram: new CMIHologram(name, location)
            Object cmiHologram = cmiHologramClass
                .getConstructor(String.class, Location.class)
                .newInstance(data.getId().getId(), data.getLocation());
            
            // Set lines
            cmiHologram.getClass().getMethod("setLines", java.util.List.class)
                .invoke(cmiHologram, data.getLines());
            
            // Save and update hologram
            cmiHologram.getClass().getMethod("update").invoke(cmiHologram);
            
            return cmiHologram;
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Failed to create CMI hologram object", e);
            return null;
        }
    }

    /**
     * Sets up player-specific visibility for the hologram.
     */
    private void setupPlayerVisibility(Object cmiHologram, HologramData data) {
        try {
            // Extract player UUID from hologram ID
            UUID playerUuid = HologramId.extractPlayerUUID(data.getId().getId());
            if (playerUuid == null) {
                return;
            }

            Player player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                return;
            }

            // Try to show hologram to specific player using CMI's visibility methods
            try {
                cmiHologram.getClass().getMethod("show", Player.class).invoke(cmiHologram, player);
            } catch (NoSuchMethodException e) {
                // CMI might handle visibility differently, just log and continue
                ChunklockPlugin.getInstance().getLogger().fine("CMI hologram show method not available");
            }

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Could not set up player visibility: " + e.getMessage());
        }
    }

    /**
     * Wrapper implementation for CMI holograms.
     */
    private static final class CMIHologram implements Hologram {
        private final String id;
        private final Object wrappedHologram;
        private final Location location;
        private volatile boolean visible = true;

        public CMIHologram(String id, Object wrappedHologram, Location location) {
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
            try {
                if (visible) {
                    wrappedHologram.getClass().getMethod("enable").invoke(wrappedHologram);
                } else {
                    wrappedHologram.getClass().getMethod("disable").invoke(wrappedHologram);
                }
            } catch (Exception e) {
                // Visibility control might not be available in all CMI versions
                ChunklockPlugin.getInstance().getLogger().fine("Could not change visibility: " + e.getMessage());
            }
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void updateText(java.util.List<String> lines) {
            try {
                wrappedHologram.getClass().getMethod("setLines", java.util.List.class)
                    .invoke(wrappedHologram, lines);
                wrappedHologram.getClass().getMethod("update").invoke(wrappedHologram);
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                    "Failed to update CMI hologram text: " + id, e);
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
