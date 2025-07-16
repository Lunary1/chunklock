package me.chunklock.hologram.provider;

import me.chunklock.hologram.api.Hologram;
import me.chunklock.hologram.api.HologramProvider;
import me.chunklock.hologram.display.HologramData;
import me.chunklock.ChunklockPlugin;

import org.bukkit.Location;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Implementation of HologramProvider for FancyHolograms plugin.
 * Uses reflection to interact with the FancyHolograms API.
 */
public final class FancyHologramsProvider implements HologramProvider {

    private static final String PROVIDER_NAME = "FancyHolograms";

    private final boolean available;
    private final Object hologramManager;
    
    // Reflection components
    private final Class<?> textHologramDataClass;
    private final Class<?> hologramClass;
    private final Method createHologramMethod;
    private final Method addHologramMethod;
    private final Method removeHologramMethod;
    private final Constructor<?> textHologramDataConstructor;

    private final Map<String, FancyHologram> managedHolograms = new ConcurrentHashMap<>();

    public FancyHologramsProvider() {
        ReflectionResult result = initializeReflection();
        this.available = result.success;
        this.hologramManager = result.hologramManager;
        this.textHologramDataClass = result.textHologramDataClass;
        this.hologramClass = result.hologramClass;
        this.createHologramMethod = result.createHologramMethod;
        this.addHologramMethod = result.addHologramMethod;
        this.removeHologramMethod = result.removeHologramMethod;
        this.textHologramDataConstructor = result.textHologramDataConstructor;

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
            // Create text hologram data
            Object textHologramData = textHologramDataConstructor.newInstance(
                hologramData.getId(), 
                hologramData.getLocation()
            );

            // Set text content
            Method setTextMethod = textHologramDataClass.getMethod("setText", java.util.List.class);
            setTextMethod.invoke(textHologramData, hologramData.getLines());

            // Apply settings
            applyHologramSettings(textHologramData, hologramData);

            // Create the hologram
            Object wrappedHologram = createHologramMethod.invoke(hologramManager, textHologramData);

            // Set non-persistent
            try {
                Method setPersistentMethod = hologramClass.getMethod("setPersistent", boolean.class);
                setPersistentMethod.invoke(wrappedHologram, hologramData.isPersistent());
            } catch (Exception e) {
                // Not critical if this fails
            }

            // Add to manager
            addHologramMethod.invoke(hologramManager, wrappedHologram);

            // Create wrapper
            FancyHologram hologram = new FancyHologram(hologramData.getId(), wrappedHologram, hologramData.getLocation());
            managedHolograms.put(hologramData.getId(), hologram);

            return Optional.of(hologram);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to create FancyHologram: " + hologramData.getId(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean removeHologram(Hologram hologram) {
        if (!available || !(hologram instanceof FancyHologram fancyHologram)) {
            return false;
        }

        try {
            // Hide immediately if possible
            try {
                Method setVisibilityMethod = hologramClass.getMethod("setVisibility", boolean.class);
                setVisibilityMethod.invoke(fancyHologram.getWrappedHologram(), false);
            } catch (Exception e) {
                // Not critical
            }

            // Remove from manager
            removeHologramMethod.invoke(hologramManager, fancyHologram.getWrappedHologram());
            managedHolograms.remove(fancyHologram.getId());

            // Small delay to ensure proper removal
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return true;

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to remove FancyHologram: " + hologram.getId(), e);
            return false;
        }
    }

    @Override
    public boolean updateHologram(Hologram hologram, HologramData newData) {
        // For FancyHolograms, we typically need to recreate
        // This could be optimized in the future if FancyHolograms supports direct updates
        if (removeHologram(hologram)) {
            return createHologram(newData).isPresent();
        }
        return false;
    }

    @Override
    public void cleanup() {
        if (!available) return;

        for (FancyHologram hologram : managedHolograms.values()) {
            try {
                removeHologramMethod.invoke(hologramManager, hologram.getWrappedHologram());
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
        return stats;
    }

    private void applyHologramSettings(Object hologramData, HologramData data) {
        try {
            // Set view distance
            try {
                Method setVisibilityDistanceMethod = textHologramDataClass.getMethod("setVisibilityDistance", int.class);
                setVisibilityDistanceMethod.invoke(hologramData, data.getViewDistance());
            } catch (Exception e) {
                // Not critical
            }

            // Set location with rotation
            try {
                Method setLocationMethod = textHologramDataClass.getMethod("setLocation", Location.class);
                setLocationMethod.invoke(hologramData, data.getLocation());
            } catch (Exception e) {
                // Not critical
            }

            // Set billboard and rotation
            setBillboardAndRotation(hologramData, data);

        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().fine("Error applying hologram settings: " + e.getMessage());
        }
    }

    private void setBillboardAndRotation(Object hologramData, HologramData data) {
        try {
            // Use FIXED billboard for custom rotation
            Class<?> billboardClass = Class.forName("org.bukkit.entity.Display$Billboard");
            Object billboard = billboardClass.getField("FIXED").get(null);
            
            Method setBillboardMethod = textHologramDataClass.getMethod("setBillboard", billboardClass);
            setBillboardMethod.invoke(hologramData, billboard);

            // Try various rotation methods
            if (!setRotationQuaternion(hologramData, data)) {
                if (!setRotationTransformation(hologramData, data)) {
                    setRotationLegacy(hologramData, data);
                }
            }

        } catch (Exception e) {
            // Billboard setting failed, continue without it
        }
    }

    private boolean setRotationQuaternion(Object hologramData, HologramData data) {
        try {
            Class<?> quaternionClass = Class.forName("org.joml.Quaternionf");
            Constructor<?> quaternionConstructor = quaternionClass.getConstructor();
            Method rotateYMethod = quaternionClass.getMethod("rotateY", float.class);
            Method setRotationMethod = textHologramDataClass.getMethod("setRotation", quaternionClass);
            
            Object quaternion = quaternionConstructor.newInstance();
            rotateYMethod.invoke(quaternion, Math.toRadians(data.getYaw()));
            setRotationMethod.invoke(hologramData, quaternion);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean setRotationTransformation(Object hologramData, HologramData data) {
        try {
            Class<?> transformationClass = Class.forName("org.bukkit.util.Transformation");
            Class<?> vector3fClass = Class.forName("org.joml.Vector3f");
            Class<?> quaternionfClass = Class.forName("org.joml.Quaternionf");
            
            Object translation = vector3fClass.getConstructor(float.class, float.class, float.class)
                .newInstance(0f, 0f, 0f);
            Object scale = vector3fClass.getConstructor(float.class, float.class, float.class)
                .newInstance(1f, 1f, 1f);
            
            Object leftRotation = quaternionfClass.getConstructor().newInstance();
            Method rotateYMethod = quaternionfClass.getMethod("rotateY", float.class);
            rotateYMethod.invoke(leftRotation, Math.toRadians(data.getYaw()));
            
            Object rightRotation = quaternionfClass.getConstructor().newInstance();
            
            Object transformation = transformationClass.getConstructor(
                vector3fClass, quaternionfClass, vector3fClass, quaternionfClass)
                .newInstance(translation, leftRotation, scale, rightRotation);
            
            Method setTransformationMethod = textHologramDataClass.getMethod("setTransformation", transformationClass);
            setTransformationMethod.invoke(hologramData, transformation);
            
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean setRotationLegacy(Object hologramData, HologramData data) {
        try {
            Method setRotationMethod = textHologramDataClass.getMethod("setRotation", float.class, float.class);
            setRotationMethod.invoke(hologramData, data.getYaw(), data.getPitch());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static ReflectionResult initializeReflection() {
        ReflectionResult result = new ReflectionResult();

        try {
            // Check if FancyHolograms is available
            if (org.bukkit.Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
                return result; // success remains false
            }

            // Load classes
            Class<?> fancyPluginClass = Class.forName("de.oliver.fancyholograms.api.FancyHologramsPlugin");
            result.textHologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            result.hologramClass = Class.forName("de.oliver.fancyholograms.api.hologram.Hologram");
            Class<?> hologramManagerClass = Class.forName("de.oliver.fancyholograms.api.HologramManager");
            
            // Get methods
            Method getPluginInstanceMethod = fancyPluginClass.getMethod("get");
            Method getHologramManagerMethod = fancyPluginClass.getMethod("getHologramManager");
            
            Class<?> hologramDataClass = Class.forName("de.oliver.fancyholograms.api.data.HologramData");
            result.createHologramMethod = hologramManagerClass.getMethod("create", hologramDataClass);
            result.addHologramMethod = hologramManagerClass.getMethod("addHologram", result.hologramClass);
            result.removeHologramMethod = hologramManagerClass.getMethod("removeHologram", result.hologramClass);
            
            result.textHologramDataConstructor = result.textHologramDataClass.getConstructor(String.class, Location.class);
            
            // Get instances
            Object pluginInstance = getPluginInstanceMethod.invoke(null);
            result.hologramManager = getHologramManagerMethod.invoke(pluginInstance);
            
            result.success = true;
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, 
                "Failed to initialize FancyHolograms reflection", e);
        }

        return result;
    }

    private static class ReflectionResult {
        boolean success = false;
        Object hologramManager;
        Class<?> textHologramDataClass;
        Class<?> hologramClass;
        Method createHologramMethod;
        Method addHologramMethod;
        Method removeHologramMethod;
        Constructor<?> textHologramDataConstructor;
    }

    /**
     * Wrapper implementation for FancyHolograms.
     */
    private static final class FancyHologram implements Hologram {
        private final String id;
        private final Object wrappedHologram;
        private final Location location;
        private boolean visible = true;

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
            try {
                Method setVisibilityMethod = wrappedHologram.getClass().getMethod("setVisibility", boolean.class);
                setVisibilityMethod.invoke(wrappedHologram, visible);
            } catch (Exception e) {
                // Ignore if not supported
            }
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
