package me.chunklock.managers;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.models.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EnhancedTeamManager {
    private final JavaPlugin plugin;
    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToTeam = new ConcurrentHashMap<>();
    
    // Configuration
    private int maxTeamSize = 6;
    private int maxTeamsPerServer = 100;
    private boolean allowSoloTeams = true;
    private int joinRequestTtlHours = 72;
    private double teamCostMultiplier = 0.15;
    
    // Files for persistence
    private final File teamsFile;
    private final File configFile;
    
    public EnhancedTeamManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.teamsFile = new File(plugin.getDataFolder(), "teams.yml");
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        
        loadConfiguration();
        loadTeams();
        
        // Start cleanup task for expired join requests
        startCleanupTask();
    }
    
    // Team Creation and Management
    public TeamResult createTeam(UUID ownerId, String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            return TeamResult.error("Team name cannot be empty");
        }
        
        if (teamName.length() > 20) {
            return TeamResult.error("Team name too long (max 20 characters)");
        }
        
        if (!isValidTeamName(teamName)) {
            return TeamResult.error("Invalid team name (alphanumeric and spaces only)");
        }
        
        if (getPlayerTeam(ownerId) != null) {
            return TeamResult.error("You are already in a team");
        }
        
        if (teams.size() >= maxTeamsPerServer) {
            return TeamResult.error("Server team limit reached");
        }
        
        if (isTeamNameTaken(teamName)) {
            return TeamResult.error("Team name already taken");
        }
        
        String teamId = generateTeamId();
        Team team = new Team(teamId, teamName, ownerId);
        team.setMaxMembers(maxTeamSize);
        
        teams.put(teamId, team);
        playerToTeam.put(ownerId, teamId);
        
        saveTeams();
        plugin.getLogger().info("Team '" + teamName + "' created by " + ownerId);
        
        return TeamResult.success("Team '" + teamName + "' created successfully!", team);
    }
    
    public TeamResult disbandTeam(UUID requesterId) {
        Team team = getPlayerTeam(requesterId);
        if (team == null) {
            return TeamResult.error("You are not in a team");
        }
        
        if (!team.isOwner(requesterId)) {
            return TeamResult.error("Only the team owner can disband the team");
        }
        
        // Remove all members from team mapping
        for (UUID memberId : team.getMembers()) {
            playerToTeam.remove(memberId);
        }
        
        // Clean up data
        teams.remove(team.getTeamId());
        
        saveTeams();
        plugin.getLogger().info("Team '" + team.getTeamName() + "' disbanded by owner");
        
        return TeamResult.success("Team disbanded successfully!");
    }
    
    public TeamResult joinTeam(UUID playerId, String teamName) {
        if (getPlayerTeam(playerId) != null) {
            return TeamResult.error("You are already in a team");
        }
        
        Team team = findTeamByName(teamName);
        if (team == null) {
            return TeamResult.error("Team not found");
        }
        
        if (team.getTotalMembers() >= team.getMaxMembers()) {
            return TeamResult.error("Team is full");
        }
        
        if (team.isOpenJoin()) {
            // Direct join
            team.addMember(playerId);
            playerToTeam.put(playerId, team.getTeamId());
            saveTeams();
            
            notifyTeamMembers(team, "§a" + getPlayerName(playerId) + " joined the team!");
            return TeamResult.success("Successfully joined team " + team.getTeamName() + "!");
        } else {
            // Request to join
            team.addJoinRequest(playerId);
            saveTeams();
            
            notifyTeamOfficers(team, "§e" + getPlayerName(playerId) + " requested to join the team. Use /chunklock team accept " + getPlayerName(playerId));
            return TeamResult.success("Join request sent to team " + team.getTeamName() + "!");
        }
    }
    
    public TeamResult leaveTeam(UUID playerId) {
        Team team = getPlayerTeam(playerId);
        if (team == null) {
            return TeamResult.error("You are not in a team");
        }
        
        if (team.isOwner(playerId)) {
            if (team.getTotalMembers() == 1) {
                // Last member leaving, disband team
                return disbandTeam(playerId);
            } else {
                return TeamResult.error("Transfer ownership before leaving, or disband the team");
            }
        }
        
        team.removeMember(playerId);
        playerToTeam.remove(playerId);
        saveTeams();
        
        notifyTeamMembers(team, "§c" + getPlayerName(playerId) + " left the team.");
        return TeamResult.success("Left team " + team.getTeamName() + " successfully!");
    }
    
    public TeamResult acceptJoinRequest(UUID officerId, String playerName) {
        Team team = getPlayerTeam(officerId);
        if (team == null) {
            return TeamResult.error("You are not in a team");
        }
        
        if (!team.canManageTeam(officerId)) {
            return TeamResult.error("You don't have permission to accept join requests");
        }
        
        UUID requesterId = getPlayerUUID(playerName);
        if (requesterId == null) {
            return TeamResult.error("Player not found");
        }
        
        if (!team.getJoinRequests().containsKey(requesterId)) {
            return TeamResult.error("No pending join request from " + playerName);
        }
        
        if (team.getTotalMembers() >= team.getMaxMembers()) {
            return TeamResult.error("Team is full");
        }
        
        team.addMember(requesterId);
        playerToTeam.put(requesterId, team.getTeamId());
        saveTeams();
        
        notifyPlayer(requesterId, "§aYour request to join " + team.getTeamName() + " was accepted!");
        notifyTeamMembers(team, "§a" + playerName + " joined the team!");
        
        return TeamResult.success("Accepted " + playerName + "'s join request!");
    }
    
    public TeamResult denyJoinRequest(UUID officerId, String playerName) {
        Team team = getPlayerTeam(officerId);
        if (team == null) {
            return TeamResult.error("You are not in a team");
        }
        
        if (!team.canManageTeam(officerId)) {
            return TeamResult.error("You don't have permission to deny join requests");
        }
        
        UUID requesterId = getPlayerUUID(playerName);
        if (requesterId == null) {
            return TeamResult.error("Player not found");
        }
        
        if (!team.getJoinRequests().containsKey(requesterId)) {
            return TeamResult.error("No pending join request from " + playerName);
        }
        
        team.removeJoinRequest(requesterId);
        saveTeams();
        
        notifyPlayer(requesterId, "§cYour request to join " + team.getTeamName() + " was denied.");
        
        return TeamResult.success("Denied " + playerName + "'s join request.");
    }
    
    // Team Chat System
    public TeamResult sendTeamMessage(UUID senderId, String message) {
        Team team = getPlayerTeam(senderId);
        if (team == null) {
            return TeamResult.error("You are not in a team");
        }
        
        if (!team.getSettings().isTeamChatEnabled()) {
            return TeamResult.error("Team chat is disabled for your team");
        }
        
        String senderName = getPlayerName(senderId);
        String formattedMessage = "§b[TEAM] §f" + senderName + "§7: §f" + message;
        
        // Send to all online team members
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(formattedMessage);
            }
        }
        
        return TeamResult.success("");
    }
    
    // Integration with existing chunk system
    public double getChunkCostMultiplier(UUID playerId) {
        Team team = getPlayerTeam(playerId);
        if (team == null) {
            return 1.0; // No team = no multiplier
        }
        
        return team.getCostMultiplier();
    }
    
    public void recordChunkUnlock(UUID playerId, String biome) {
        Team team = getPlayerTeam(playerId);
        if (team == null) return;
        
        // For now, just increment the counter
        team.setTotalChunksUnlocked(team.getTotalChunksUnlocked() + 1);
        saveTeams();
    }
    
    // Utility methods
    public Team getPlayerTeam(UUID playerId) {
        String teamId = playerToTeam.get(playerId);
        return teamId != null ? teams.get(teamId) : null;
    }
    
    public Team findTeamByName(String teamName) {
        return teams.values().stream()
            .filter(team -> team.getTeamName().equalsIgnoreCase(teamName))
            .findFirst()
            .orElse(null);
    }
    
    public Collection<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }
    
    private boolean isTeamNameTaken(String teamName) {
        return findTeamByName(teamName) != null;
    }
    
    private boolean isValidTeamName(String teamName) {
        return teamName.matches("^[a-zA-Z0-9 ]{1,20}$");
    }
    
    private String generateTeamId() {
        return "team_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
    
    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null ? player.getName() : "Unknown";
    }
    
    private UUID getPlayerUUID(String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        return player != null ? player.getUniqueId() : null;
    }
    
    private void notifyPlayer(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(message);
        }
    }
    
    private void notifyTeamMembers(Team team, String message) {
        for (UUID memberId : team.getMembers()) {
            notifyPlayer(memberId, message);
        }
    }
    
    private void notifyTeamOfficers(Team team, String message) {
        for (UUID officerId : team.getOfficers()) {
            notifyPlayer(officerId, message);
        }
    }
    
    // Configuration and Persistence (Simplified for now)
    private void loadConfiguration() {
        if (!configFile.exists()) {
            saveDefaultConfiguration();
        }

        FileConfiguration root = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = root.getConfigurationSection("team-settings");
        if (section == null) {
            section = root.createSection("team-settings");
            saveDefaultConfiguration();
        }

        maxTeamSize = section.getInt("max-team-size", 6);
        maxTeamsPerServer = section.getInt("max-teams-per-server", 100);
        allowSoloTeams = section.getBoolean("allow-solo-teams", true);
        joinRequestTtlHours = section.getInt("join-request-ttl-hours", 72);
        teamCostMultiplier = section.getDouble("team-cost-multiplier", 0.15);
    }

    private void saveDefaultConfiguration() {
        FileConfiguration root;
        if (configFile.exists()) {
            root = YamlConfiguration.loadConfiguration(configFile);
        } else {
            root = new YamlConfiguration();
        }
        ConfigurationSection section = root.createSection("team-settings");
        section.set("max-team-size", 6);
        section.set("max-teams-per-server", 100);
        section.set("allow-solo-teams", true);
        section.set("join-request-ttl-hours", 72);
        section.set("team-cost-multiplier", 0.15);

        try {
            root.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save team configuration", e);
        }
    }
    
    private void loadTeams() {
        if (!teamsFile.exists()) return;
        
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
            
            for (String teamId : config.getKeys(false)) {
                String teamName = config.getString(teamId + ".name");
                String ownerIdStr = config.getString(teamId + ".owner");
                
                if (teamName == null || ownerIdStr == null) continue;
                
                UUID ownerId = UUID.fromString(ownerIdStr);
                Team team = new Team(teamId, teamName, ownerId);
                
                // Load basic properties
                team.setMaxMembers(config.getInt(teamId + ".maxMembers", 6));
                team.setOpenJoin(config.getBoolean(teamId + ".openJoin", false));
                team.setTotalChunksUnlocked(config.getInt(teamId + ".chunksUnlocked", 0));
                
                // Load members
                List<String> memberList = config.getStringList(teamId + ".members");
                for (String memberStr : memberList) {
                    UUID memberId = UUID.fromString(memberStr);
                    team.addMember(memberId);
                    playerToTeam.put(memberId, teamId);
                }
                
                // Load officers
                List<String> officerList = config.getStringList(teamId + ".officers");
                for (String officerStr : officerList) {
                    UUID officerId = UUID.fromString(officerStr);
                    if (team.isMember(officerId)) {
                        team.promoteToOfficer(officerId);
                    }
                }
                
                teams.put(teamId, team);
            }
            
            plugin.getLogger().info("Loaded " + teams.size() + " teams from teams.yml");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading teams", e);
        }
    }
    
    public void saveTeams() {
        try {
            FileConfiguration config = new YamlConfiguration();
            
            for (Team team : teams.values()) {
                String teamId = team.getTeamId();
                config.set(teamId + ".name", team.getTeamName());
                config.set(teamId + ".owner", team.getOwnerId().toString());
                config.set(teamId + ".maxMembers", team.getMaxMembers());
                config.set(teamId + ".openJoin", team.isOpenJoin());
                config.set(teamId + ".chunksUnlocked", team.getTotalChunksUnlocked());
                
                // Save members
                List<String> memberList = team.getMembers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
                config.set(teamId + ".members", memberList);
                
                // Save officers
                List<String> officerList = team.getOfficers().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
                config.set(teamId + ".officers", officerList);
            }
            
            config.save(teamsFile);
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save teams", e);
        }
    }
    
    private void startCleanupTask() {
        // Start a task that runs every hour to clean up expired join requests
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredRequests, 20L * 3600L, 20L * 3600L);
    }
    
    private void cleanupExpiredRequests() {
        long cutoffTime = System.currentTimeMillis() - (joinRequestTtlHours * 60 * 60 * 1000L);
        
        for (Team team : teams.values()) {
            team.getJoinRequests().entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
        }
        
        saveTeams();
    }
    
    // Result classes
    public static class TeamResult {
        private final boolean success;
        private final String message;
        private final Team team;
        
        private TeamResult(boolean success, String message, Team team) {
            this.success = success;
            this.message = message;
            this.team = team;
        }
        
        public static TeamResult success(String message) {
            return new TeamResult(true, message, null);
        }
        
        public static TeamResult success(String message, Team team) {
            return new TeamResult(true, message, team);
        }
        
        public static TeamResult error(String message) {
            return new TeamResult(false, message, null);
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Team getTeam() { return team; }
    }
}