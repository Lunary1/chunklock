package me.chunklock.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Configuration section for team-related settings.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public class TeamConfig {
    
    private final FileConfiguration config;
    
    public TeamConfig(FileConfiguration config) {
        this.config = config;
    }
    
    /**
     * Checks if teams are enabled.
     * 
     * @return true if teams are enabled
     */
    public boolean isTeamsEnabled() {
        return config.getBoolean("teams.enabled", true);
    }
    
    /**
     * Gets the maximum number of players per team.
     * 
     * @return Maximum players per team
     */
    public int getMaxPlayersPerTeam() {
        return config.getInt("teams.max-players", 4);
    }
    
    /**
     * Checks if team leaders can invite players.
     * 
     * @return true if leaders can invite
     */
    public boolean canLeadersInvite() {
        return config.getBoolean("teams.leaders-can-invite", true);
    }
    
    /**
     * Checks if team members share unlocked chunks.
     * 
     * @return true if chunks are shared
     */
    public boolean isChunkSharingEnabled() {
        return config.getBoolean("teams.shared-chunks", true);
    }
    
    /**
     * Checks if friendly fire is disabled within teams.
     * 
     * @return true if friendly fire is disabled
     */
    public boolean isFriendlyFireDisabled() {
        return config.getBoolean("teams.disable-friendly-fire", true);
    }
    
    /**
     * Gets the team creation cost.
     * 
     * @return Team creation cost
     */
    public double getTeamCreationCost() {
        return config.getDouble("teams.creation-cost", 0.0);
    }
    
    /**
     * Validates the team configuration section.
     * 
     * @return true if configuration is valid, false otherwise
     */
    public boolean validate() {
        boolean valid = true;
        
        if (getMaxPlayersPerTeam() <= 0) {
            valid = false;
        }
        
        if (getTeamCreationCost() < 0) {
            valid = false;
        }
        
        return valid;
    }
}