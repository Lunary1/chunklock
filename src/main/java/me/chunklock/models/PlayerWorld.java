package me.chunklock.models;

import java.util.*;

/**
 * Model representing a player's world data including ownership, team access, and metadata.
 */
public class PlayerWorld {
    
    private final String worldName;
    private final UUID ownerId;
    private final Set<UUID> teamMembers;
    private final long createdTime;
    private long lastAccessTime;
    private boolean loaded;
    private int chunksUnlocked;
    private String difficulty;
    
    public PlayerWorld(String worldName, UUID ownerId) {
        this.worldName = worldName;
        this.ownerId = ownerId;
        this.teamMembers = new HashSet<>();
        this.createdTime = System.currentTimeMillis();
        this.lastAccessTime = System.currentTimeMillis();
        this.loaded = false;
        this.chunksUnlocked = 0;
        this.difficulty = "normal";
    }
    
    // Getters and Setters
    public String getWorldName() {
        return worldName;
    }
    
    public UUID getOwnerId() {
        return ownerId;
    }
    
    public Set<UUID> getTeamMembers() {
        return new HashSet<>(teamMembers);
    }
    
    public void addTeamMember(UUID playerId) {
        teamMembers.add(playerId);
    }
    
    public void removeTeamMember(UUID playerId) {
        teamMembers.remove(playerId);
    }
    
    public boolean hasAccess(UUID playerId) {
        return ownerId.equals(playerId) || teamMembers.contains(playerId);
    }
    
    public long getCreatedTime() {
        return createdTime;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }
    
    public boolean isLoaded() {
        return loaded;
    }
    
    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }
    
    public int getChunksUnlocked() {
        return chunksUnlocked;
    }
    
    public void setChunksUnlocked(int chunksUnlocked) {
        this.chunksUnlocked = chunksUnlocked;
    }
    
    public String getDifficulty() {
        return difficulty;
    }
    
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }
    
    @Override
    public String toString() {
        return "PlayerWorld{" +
                "worldName='" + worldName + '\'' +
                ", ownerId=" + ownerId +
                ", teamMembers=" + teamMembers.size() +
                ", loaded=" + loaded +
                ", chunksUnlocked=" + chunksUnlocked +
                '}';
    }
}
