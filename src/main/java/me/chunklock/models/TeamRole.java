package me.chunklock.models;

public enum TeamRole {
    OWNER("Owner"),
    OFFICER("Officer"), 
    MEMBER("Member");
    
    private final String displayName;
    
    TeamRole(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean canInviteMembers() {
        return this == OWNER || this == OFFICER;
    }
    
    public boolean canKickMembers() {
        return this == OWNER || this == OFFICER;
    }
    
    public boolean canPromoteMembers() {
        return this == OWNER;
    }
    
    public boolean canModifySettings() {
        return this == OWNER;
    }
}