package me.chunklock.managers;

import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.managers.TeamManager;
import me.chunklock.services.PlayerDatabase;
import me.chunklock.ChunklockPlugin;

import java.util.UUID;

public class PlayerProgressTracker {
    
    private final PlayerDatabase playerDatabase;
    private final TeamManager teamManager;

    public PlayerProgressTracker(JavaPlugin plugin, TeamManager teamManager) {
        this.teamManager = teamManager;
        
        // Get PlayerDatabase from plugin instance
        if (plugin instanceof ChunklockPlugin) {
            this.playerDatabase = ((ChunklockPlugin) plugin).getPlayerDatabase();
        } else {
            throw new IllegalStateException("PlayerProgressTracker requires ChunklockPlugin instance");
        }
    }

    public void incrementUnlockedChunks(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        playerDatabase.incrementUnlockedChunks(teamId);
    }

    // Note: Contested claims functionality is skipped for now as per plan
    // These methods are kept for backward compatibility but do nothing
    public void incrementContestedClaims(UUID teamId) {
        // Skipped - team functionality not implemented yet
    }

    public boolean canClaimContested(UUID teamId, int maxPerDay) {
        // Skipped - team functionality not implemented yet
        return true;
    }

    public int getContestedClaimCount(UUID teamId) {
        // Skipped - team functionality not implemented yet
        return 0;
    }

    public int getUnlockedChunkCount(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        return playerDatabase.getUnlockedChunks(teamId);
    }

    public void resetPlayer(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        playerDatabase.setUnlockedChunks(teamId, 0);
    }
}