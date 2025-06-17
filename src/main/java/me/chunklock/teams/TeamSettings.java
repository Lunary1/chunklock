package me.chunklock.teams;

public class TeamSettings {
    private boolean allowOfficerInvites = true;
    private boolean allowOfficerKicks = true;
    private boolean allowMemberInvites = false;
    private boolean teamChatEnabled = true;
    private boolean shareProgress = true;
    private double baseCostMultiplier = 1.0;
    private double perMemberCostIncrease = 0.15; // 15% increase per additional member
    private int joinRequestTtlHours = 72; // Join requests expire after 72 hours
    
    // Getters and setters
    public boolean isAllowOfficerInvites() { return allowOfficerInvites; }
    public void setAllowOfficerInvites(boolean allow) { this.allowOfficerInvites = allow; }
    public boolean isAllowOfficerKicks() { return allowOfficerKicks; }
    public void setAllowOfficerKicks(boolean allow) { this.allowOfficerKicks = allow; }
    public boolean isAllowMemberInvites() { return allowMemberInvites; }
    public void setAllowMemberInvites(boolean allow) { this.allowMemberInvites = allow; }
    public boolean isTeamChatEnabled() { return teamChatEnabled; }
    public void setTeamChatEnabled(boolean enabled) { this.teamChatEnabled = enabled; }
    public boolean isShareProgress() { return shareProgress; }
    public void setShareProgress(boolean share) { this.shareProgress = share; }
    public double getBaseCostMultiplier() { return baseCostMultiplier; }
    public void setBaseCostMultiplier(double multiplier) { this.baseCostMultiplier = multiplier; }
    public double getPerMemberCostIncrease() { return perMemberCostIncrease; }
    public void setPerMemberCostIncrease(double increase) { this.perMemberCostIncrease = increase; }
    public int getJoinRequestTtlHours() { return joinRequestTtlHours; }
    public void setJoinRequestTtlHours(int hours) { this.joinRequestTtlHours = hours; }
}