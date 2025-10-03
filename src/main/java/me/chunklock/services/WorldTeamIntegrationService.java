package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;
import me.chunklock.models.Team;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * World-Team Integration Service (disabled for single world system)
 * This service was designed for per-player worlds and is not needed for the single world system.
 */
public class WorldTeamIntegrationService {
    
    private final WorldManager worldManager;
    
    public WorldTeamIntegrationService(ChunklockPlugin plugin, WorldManager worldManager) {
        this.worldManager = worldManager;
    }
    
    /**
     * Called when a player joins a team (disabled for single world system)
     */
    public void onPlayerJoinTeam(UUID playerId, Team team) {
        // World team integration disabled for single world system
    }
    
    /**
     * Called when a player leaves a team (disabled for single world system)
     */
    public void onPlayerLeaveTeam(UUID playerId, Team team) {
        // World team integration disabled for single world system
    }
    
    /**
     * Called when a team is disbanded (disabled for single world system)
     */
    public void onTeamDisband(Team team) {
        // World team integration disabled for single world system
    }
    
    /**
     * Handle player teleporting to team world (disabled for single world system)
     */
    public void handleTeamWorldTeleport(Player player, Team team) {
        // World team integration disabled for single world system
    }
    
    /**
     * Check if player has access to their team's world (disabled for single world system)
     */
    public boolean playerHasTeamWorldAccess(Player player) {
        // World team integration disabled for single world system
        return true; // All players have access to the single world
    }
    
    /**
     * Check if player has access to any team world (disabled for single world system)
     */
    public boolean playerHasAnyTeamWorldAccess(UUID playerId) {
        // World team integration disabled for single world system
        return true; // All players have access to the single world
    }
    
    /**
     * Get list of worlds player has access to through teams (disabled for single world system)
     */
    public List<String> getTeamWorldsForPlayer(UUID playerId) {
        // World team integration disabled for single world system
        return worldManager.getEnabledWorlds(); // Return the single enabled world
    }
}