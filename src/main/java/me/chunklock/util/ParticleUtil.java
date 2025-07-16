package me.chunklock.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import java.util.logging.Level;
import me.chunklock.ChunklockPlugin;

/**
 * Utility for cross-version particle compatibility.
 * Handles particle name changes between Minecraft versions.
 */
public final class ParticleUtil {
    
    private ParticleUtil() {}
    
    /**
     * Spawns firework particles in a compatible way.
     */
    public static void spawnFireworkParticles(Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        try {
            // Try modern name first
            spawnParticle(location, "FIREWORK", count, offsetX, offsetY, offsetZ, speed, null);
        } catch (Exception e) {
            try {
                // Try legacy name
                spawnParticle(location, "FIREWORKS_SPARK", count, offsetX, offsetY, offsetZ, speed, null);
            } catch (Exception ex) {
                // Fallback to a basic particle
                spawnParticle(location, "FLAME", count, offsetX, offsetY, offsetZ, speed, null);
            }
        }
    }
    
    /**
     * Spawns dust particles with color in a compatible way.
     */
    public static void spawnDustParticles(Location location, int count, double offsetX, double offsetY, double offsetZ, Color color, float size) {
        try {
            // Try to create DustOptions
            Class<?> dustOptionsClass = Class.forName("org.bukkit.Particle$DustOptions");
            Object dustOptions = dustOptionsClass.getConstructor(Color.class, float.class).newInstance(color, size);
            
            spawnParticle(location, "DUST", count, offsetX, offsetY, offsetZ, 0, dustOptions);
        } catch (Exception e) {
            try {
                // Try legacy dust particle without color
                spawnParticle(location, "REDSTONE", count, offsetX, offsetY, offsetZ, 0, null);
            } catch (Exception ex) {
                // Fallback to basic particles
                spawnParticle(location, "FLAME", count, offsetX, offsetY, offsetZ, 0, null);
            }
        }
    }
    
    /**
     * Spawns happy villager particles in a compatible way.
     */
    public static void spawnHappyVillagerParticles(Location location, int count, double offsetX, double offsetY, double offsetZ, double speed) {
        try {
            spawnParticle(location, "HAPPY_VILLAGER", count, offsetX, offsetY, offsetZ, speed, null);
        } catch (Exception e) {
            try {
                // Try alternative name
                spawnParticle(location, "VILLAGER_HAPPY", count, offsetX, offsetY, offsetZ, speed, null);
            } catch (Exception ex) {
                // Fallback to heart particles
                spawnParticle(location, "HEART", count, offsetX, offsetY, offsetZ, speed, null);
            }
        }
    }
    
    /**
     * Generic particle spawning with reflection for maximum compatibility.
     */
    private static void spawnParticle(Location location, String particleName, int count, double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        try {
            World world = location.getWorld();
            if (world == null) return;
            
            // Try to get the particle enum value
            Particle particle = Particle.valueOf(particleName);
            
            if (data != null) {
                // Use the data overload
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed, data);
            } else {
                // Use the basic overload
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed);
            }
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().log(Level.FINE, 
                "Could not spawn particle " + particleName + ": " + e.getMessage());
        }
    }
    
    /**
     * Gets a safe particle for effects that should always work.
     */
    public static Particle getSafeParticle() {
        try {
            // These particles should exist in most versions
            String[] safeParticles = {"FLAME", "SMOKE_NORMAL", "CLOUD", "SPELL"};
            
            for (String particleName : safeParticles) {
                try {
                    return Particle.valueOf(particleName);
                } catch (Exception e) {
                    // Try next particle
                }
            }
            
            // If all else fails, get the first available particle
            Particle[] particles = Particle.values();
            if (particles.length > 0) {
                return particles[0];
            }
        } catch (Exception e) {
            // Unable to get any particle
        }
        
        return null;
    }
}
