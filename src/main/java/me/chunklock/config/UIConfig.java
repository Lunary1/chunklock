package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration section for UI-related settings.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class UIConfig {
    
    private final FileConfiguration config;
    
    public UIConfig(FileConfiguration config) {
        this.config = config;
    }
    
    /**
     * Gets the GUI update interval in ticks.
     * 
     * @return Update interval in ticks
     */
    public int getGuiUpdateInterval() {
        return config.getInt("ui.gui-update-interval", 20);
    }
    
    /**
     * Checks if GUI animations are enabled.
     * 
     * @return true if animations are enabled
     */
    public boolean areAnimationsEnabled() {
        return config.getBoolean("ui.animations.enabled", true);
    }
    
    /**
     * Gets the animation speed.
     * 
     * @return Animation speed (1-10)
     */
    public int getAnimationSpeed() {
        return config.getInt("ui.animations.speed", 5);
    }
    
    /**
     * Checks if sound effects are enabled.
     * 
     * @return true if sounds are enabled
     */
    public boolean areSoundsEnabled() {
        return config.getBoolean("ui.sounds.enabled", true);
    }
    
    /**
     * Gets the default sound volume.
     * 
     * @return Sound volume (0.0 to 1.0)
     */
    public float getSoundVolume() {
        return (float) config.getDouble("ui.sounds.volume", 0.5);
    }
    
    /**
     * Checks if particle effects are enabled.
     * 
     * @return true if particles are enabled
     */
    public boolean areParticlesEnabled() {
        return config.getBoolean("ui.particles.enabled", true);
    }
    
    /**
     * Gets the particle density.
     * 
     * @return Particle density (1-10)
     */
    public int getParticleDensity() {
        return config.getInt("ui.particles.density", 5);
    }
    
    /**
     * Checks if action bar messages are enabled.
     * 
     * @return true if action bar is enabled
     */
    public boolean isActionBarEnabled() {
        return config.getBoolean("ui.action-bar.enabled", true);
    }
    
    /**
     * Gets the action bar display duration in ticks.
     * 
     * @return Display duration in ticks
     */
    public int getActionBarDuration() {
        return config.getInt("ui.action-bar.duration", 60);
    }
    
    /**
     * Checks if boss bar progress is enabled.
     * 
     * @return true if boss bar is enabled
     */
    public boolean isBossBarEnabled() {
        return config.getBoolean("ui.boss-bar.enabled", true);
    }
    
    /**
     * Gets the boss bar color.
     * 
     * @return Boss bar color
     */
    public String getBossBarColor() {
        return config.getString("ui.boss-bar.color", "BLUE");
    }
    
    /**
     * Validates the UI configuration section.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean valid = true;
        
        if (getGuiUpdateInterval() <= 0) {
            valid = false;
        }
        
        int animSpeed = getAnimationSpeed();
        if (animSpeed < 1 || animSpeed > 10) {
            valid = false;
        }
        
        float soundVol = getSoundVolume();
        if (soundVol < 0.0f || soundVol > 1.0f) {
            valid = false;
        }
        
        int particleDensity = getParticleDensity();
        if (particleDensity < 1 || particleDensity > 10) {
            valid = false;
        }
        
        if (getActionBarDuration() <= 0) {
            valid = false;
        }
        
        return valid;
    }
}