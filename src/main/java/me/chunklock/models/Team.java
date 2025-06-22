package me.chunklock.models;

import java.util.*;

public class Team {
    private final String teamId;
    private String teamName;
    private UUID ownerId;
    private final Set<UUID> officers = new HashSet<>();
    private final Set<UUID> members = new HashSet<>();
    private final Map<UUID, Long> joinRequests = new HashMap<>();
    
    private long createdTime;
    private int maxMembers;
    private boolean openJoin;
    private String teamDescription;
    private TeamSettings settings;
    
    // Team statistics
    private int totalChunksUnlocked;
    private long lastActivity;
    
    public Team(String teamId, String teamName, UUID ownerId) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.ownerId = ownerId;
        this.createdTime = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
        this.maxMembers = 6; // Default from config
        this.openJoin = false;
        this.settings = new TeamSettings();
        
        // Owner is automatically an officer and member
        this.officers.add(ownerId);
        this.members.add(ownerId);
    }
    
    // Member management
    public boolean addMember(UUID playerId) {
        if (members.size() >= maxMembers) return false;
        if (members.contains(playerId)) return false;
        
        members.add(playerId);
        joinRequests.remove(playerId);
        updateActivity();
        return true;
    }
    
    public boolean removeMember(UUID playerId) {
        if (playerId.equals(ownerId)) return false; // Can't remove owner
        
        members.remove(playerId);
        officers.remove(playerId);
        joinRequests.remove(playerId);
        updateActivity();
        return true;
    }
    
    public boolean promoteToOfficer(UUID playerId) {
        if (!members.contains(playerId)) return false;
        officers.add(playerId);
        updateActivity();
        return true;
    }
    
    public boolean demoteFromOfficer(UUID playerId) {
        if (playerId.equals(ownerId)) return false; // Can't demote owner
        officers.remove(playerId);
        updateActivity();
        return true;
    }
    
    // Join request management
    public void addJoinRequest(UUID playerId) {
        if (!members.contains(playerId) && members.size() < maxMembers) {
            joinRequests.put(playerId, System.currentTimeMillis());
        }
    }
    
    public void removeJoinRequest(UUID playerId) {
        joinRequests.remove(playerId);
    }
    
    // Utility methods
    public int getTotalMembers() {
        return members.size();
    }
    
    public boolean canManageTeam(UUID playerId) {
        return ownerId.equals(playerId) || officers.contains(playerId);
    }
    
    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }
    
    public boolean isOfficer(UUID playerId) {
        return officers.contains(playerId);
    }
    
    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }
    
    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    // Cost scaling calculation
    public double getCostMultiplier() {
        int memberCount = getTotalMembers();
        return settings.getBaseCostMultiplier() + (memberCount - 1) * settings.getPerMemberCostIncrease();
    }
    
    // Getters and setters
    public String getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; updateActivity(); }
    public UUID getOwnerId() { return ownerId; }
    public Set<UUID> getOfficers() { return new HashSet<>(officers); }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public Map<UUID, Long> getJoinRequests() { return new HashMap<>(joinRequests); }
    public long getCreatedTime() { return createdTime; }
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; updateActivity(); }
    public boolean isOpenJoin() { return openJoin; }
    public void setOpenJoin(boolean openJoin) { this.openJoin = openJoin; updateActivity(); }
    public String getTeamDescription() { return teamDescription; }
    public void setTeamDescription(String description) { this.teamDescription = description; updateActivity(); }
    public TeamSettings getSettings() { return settings; }
    public int getTotalChunksUnlocked() { return totalChunksUnlocked; }
    public void setTotalChunksUnlocked(int count) { this.totalChunksUnlocked = count; updateActivity(); }
    public long getLastActivity() { return lastActivity; }
}