package me.chunklock.api.services;

import me.chunklock.models.Team;
import me.chunklock.models.TeamRole;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for team management operations.
 * Provides methods for creating, managing, and querying teams.
 * 
 * @author Chunklock Team
 * @version 1.3.0
 * @since 1.3.0
 */
public interface TeamService extends BaseService {
    
    /**
     * Creates a new team with the specified leader.
     * 
     * @param leader The player who will lead the team
     * @param teamName The name of the team
     * @return The created team, or null if creation failed
     */
    Team createTeam(Player leader, String teamName);
    
    /**
     * Gets a team by its unique identifier.
     * 
     * @param teamId The unique identifier of the team
     * @return Optional containing the team if it exists
     */
    Optional<Team> getTeam(UUID teamId);
    
    /**
     * Gets a team by its name.
     * 
     * @param teamName The name of the team
     * @return Optional containing the team if it exists
     */
    Optional<Team> getTeamByName(String teamName);
    
    /**
     * Gets the team that a player is a member of.
     * 
     * @param player The player to check
     * @return Optional containing the team if the player is a member
     */
    Optional<Team> getPlayerTeam(Player player);
    
    /**
     * Gets the team that a player is a member of by UUID.
     * 
     * @param playerUuid The UUID of the player to check
     * @return Optional containing the team if the player is a member
     */
    Optional<Team> getPlayerTeam(UUID playerUuid);
    
    /**
     * Adds a player to a team with the specified role.
     * 
     * @param team The team to add the player to
     * @param player The player to add
     * @param role The role to assign to the player
     * @return true if the player was successfully added, false otherwise
     */
    boolean addPlayerToTeam(Team team, Player player, TeamRole role);
    
    /**
     * Removes a player from their current team.
     * 
     * @param player The player to remove
     * @return true if the player was successfully removed, false otherwise
     */
    boolean removePlayerFromTeam(Player player);
    
    /**
     * Changes a player's role within their team.
     * 
     * @param player The player whose role to change
     * @param newRole The new role to assign
     * @return true if the role was successfully changed, false otherwise
     */
    boolean changePlayerRole(Player player, TeamRole newRole);
    
    /**
     * Checks if a player has permission to perform a specific action.
     * 
     * @param player The player to check
     * @param permission The permission to check for
     * @return true if the player has the permission, false otherwise
     */
    boolean hasTeamPermission(Player player, String permission);
    
    /**
     * Gets all members of a team.
     * 
     * @param team The team to get members for
     * @return List of all team member UUIDs
     */
    List<UUID> getTeamMembers(Team team);
    
    /**
     * Gets all online members of a team.
     * 
     * @param team The team to get online members for
     * @return List of online team members
     */
    List<Player> getOnlineTeamMembers(Team team);
    
    /**
     * Disbands a team.
     * 
     * @param team The team to disband
     * @return true if the team was successfully disbanded, false otherwise
     */
    boolean disbandTeam(Team team);
    
    /**
     * Transfers leadership of a team to another member.
     * 
     * @param team The team to transfer leadership for
     * @param newLeader The new leader of the team
     * @return true if leadership was successfully transferred, false otherwise
     */
    boolean transferLeadership(Team team, Player newLeader);
    
    /**
     * Sends a message to all online members of a team.
     * 
     * @param team The team to send the message to
     * @param message The message to send
     */
    void sendTeamMessage(Team team, String message);
    
    /**
     * Checks if a team name is available.
     * 
     * @param teamName The team name to check
     * @return true if the name is available, false otherwise
     */
    boolean isTeamNameAvailable(String teamName);
    
    /**
     * Gets all teams in the system.
     * 
     * @return List of all teams
     */
    List<Team> getAllTeams();
    
    /**
     * Gets a player's role within their team.
     * 
     * @param player The player to check
     * @return The player's team role, or null if not in a team
     */
    TeamRole getPlayerRole(Player player);
    
    /**
     * Checks if a player is the leader of their team.
     * 
     * @param player The player to check
     * @return true if the player is a team leader, false otherwise
     */
    boolean isTeamLeader(Player player);
    
    /**
     * Saves team data to storage.
     * 
     * @param team The team to save
     */
    void saveTeam(Team team);
    
    /**
     * Loads all teams from storage.
     */
    void loadTeams();
}