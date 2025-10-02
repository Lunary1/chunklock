package me.chunklock.services;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.WorldManager;
import me.chunklock.managers.EnhancedTeamManager;
import me.chunklock.models.Team;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Service that integrates the per-player world system with the team system.
 * Handles automatic world access management when players join/leave teams.
 */
public class WorldTeamIntegrationService implements Listener {
    
    private final ChunklockPlugin plugin;
    private final WorldManager worldManager;
    private final EnhancedTeamManager teamManager;
    
    public WorldTeamIntegrationService(ChunklockPlugin plugin, WorldManager worldManager, EnhancedTeamManager teamManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.teamManager = teamManager;
    }
    
    /**
     * Called when a player joins a team - grants them access to the team leader's world
     */
    public void onPlayerJoinTeam(UUID playerId, Team team) {
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return;
        }
        
        try {
            UUID teamOwnerId = team.getOwnerId();
            
            // Add the new member to the team leader's world
            boolean success = worldManager.addTeamMemberToWorld(teamOwnerId, playerId);
            
            if (success) {
                plugin.getLogger().info("Granted world access to player " + playerId + " for team " + team.getTeamName());
                
                // Notify the player if they're online
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§aYou now have access to your team's private world! Use §e/chunklock start §ato visit it.");
                }
            } else {
                plugin.getLogger().warning("Failed to grant world access to player " + playerId + " for team " + team.getTeamName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error granting world access for team join", e);
        }
    }
    
    /**
     * Called when a player leaves a team - removes their access to the team world
     */
    public void onPlayerLeaveTeam(UUID playerId, Team team) {
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return;
        }
        
        try {
            UUID teamOwnerId = team.getOwnerId();
            
            // Don't remove access if this player is the team owner
            if (playerId.equals(teamOwnerId)) {
                return;
            }
            
            // Remove the member from the team leader's world
            boolean success = worldManager.removeTeamMemberFromWorld(teamOwnerId, playerId);
            
            if (success) {
                plugin.getLogger().info("Removed world access from player " + playerId + " for team " + team.getTeamName());
                
                // Notify the player if they're online
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§cYou no longer have access to your former team's world.");
                    
                    // If they're currently in the team world, teleport them out
                    String teamWorldName = worldManager.getPlayerWorldName(teamOwnerId);
                    if (teamWorldName != null && player.getWorld().getName().equals(teamWorldName)) {
                        player.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
                        player.sendMessage("§eYou have been moved to the main world.");
                    }
                }
            } else {
                plugin.getLogger().warning("Failed to remove world access from player " + playerId + " for team " + team.getTeamName());
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing world access for team leave", e);
        }
    }
    
    /**
     * Called when a team is disbanded - handles world ownership transfer or cleanup
     */
    public void onTeamDisband(Team team) {
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return;
        }
        
        try {
            UUID teamOwnerId = team.getOwnerId();
            
            // Remove all non-owner members from the world
            for (UUID memberId : team.getMembers()) {
                if (!memberId.equals(teamOwnerId)) {
                    worldManager.removeTeamMemberFromWorld(teamOwnerId, memberId);
                    
                    // Notify the member if they're online
                    Player member = plugin.getServer().getPlayer(memberId);
                    if (member != null) {
                        member.sendMessage("§cTeam disbanded. You no longer have access to the team world.");
                        
                        // If they're in the team world, teleport them out
                        String teamWorldName = worldManager.getPlayerWorldName(teamOwnerId);
                        if (teamWorldName != null && member.getWorld().getName().equals(teamWorldName)) {
                            member.teleport(plugin.getServer().getWorld("world").getSpawnLocation());
                            member.sendMessage("§eYou have been moved to the main world.");
                        }
                    }
                }
            }
            
            plugin.getLogger().info("Cleaned up world access for disbanded team " + team.getTeamName());
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error cleaning up world access for team disband", e);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if player should have access to any team worlds
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return;
        }
        
        try {
            Team team = teamManager.getPlayerTeam(playerId);
            if (team != null && !team.getOwnerId().equals(playerId)) {
                // Ensure they have access to the team world
                worldManager.addTeamMemberToWorld(team.getOwnerId(), playerId);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking team world access for joining player", e);
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Update world access time for any worlds they own
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return;
        }
        
        try {
            String worldName = worldManager.getPlayerWorldName(playerId);
            if (worldName != null) {
                // The world access time is automatically updated by WorldManager
                plugin.getLogger().fine("Player " + player.getName() + " left - world " + worldName + " access updated");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error updating world access for leaving player", e);
        }
    }
    
    /**
     * Check if a player should be automatically teleported to their team world
     */
    public boolean shouldTeleportToTeamWorld(Player player) {
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return false;
        }
        
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            return false;
        }
        
        // Check if they have access to a team world
        String teamWorldName = worldManager.getPlayerWorldName(team.getOwnerId());
        return teamWorldName != null && worldManager.hasWorldAccess(player.getUniqueId(), teamWorldName);
    }
    
    /**
     * Get the world name that a player should be using (their own or their team's)
     */
    public String getActiveWorldForPlayer(UUID playerId) {
        if (!worldManager.isPerPlayerWorldsEnabled()) {
            return null;
        }
        
        // First check if they have their own world
        String ownWorld = worldManager.getPlayerWorldName(playerId);
        if (ownWorld != null) {
            return ownWorld;
        }
        
        // Check if they're in a team and can access the team world
        Team team = teamManager.getPlayerTeam(playerId);
        if (team != null) {
            String teamWorld = worldManager.getPlayerWorldName(team.getOwnerId());
            if (teamWorld != null && worldManager.hasWorldAccess(playerId, teamWorld)) {
                return teamWorld;
            }
        }
        
        return null;
    }
}
