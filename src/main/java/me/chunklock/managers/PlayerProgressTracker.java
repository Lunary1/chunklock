package me.chunklock.managers;

import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.services.PlayerStore;
import me.chunklock.ChunklockPlugin;

import java.util.UUID;

public class PlayerProgressTracker {
    
    private final PlayerStore playerDatabase;
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

    /**
     * The team's unlocked-chunk count, repairing it from the chunk data when it looks stale.
     *
     * <p><strong>Why the repair exists.</strong> This counter is a running tally, and until
     * September 5 nothing ever incremented it - {@code unlockChunk} only flips a chunk's locked
     * flag, and the one comment claiming otherwise was wrong. So it read 0 for every player on
     * every existing world, which pinned everyone to the tier-3 progression cap, flattened the
     * progression cost multiplier, and made {@code /chunklock info} report no chunks.</p>
     *
     * <p>Fixing the increment alone would only help new unlocks; a world that had been played
     * would stay wrong forever. So a zero counter is treated as "never tallied" and recomputed
     * once from the chunk database, which is the actual source of truth for ownership.</p>
     *
     * <p>Zero is a safe trigger: a team with no unlocked chunks recomputes to 0 and stores 0,
     * so the recount happens at most once per team and costs nothing thereafter. A team that
     * genuinely owns nothing is indistinguishable from an untallied one, and both answers are
     * the same.</p>
     */
    public int getUnlockedChunkCount(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        int stored = playerDatabase.getUnlockedChunks(teamId);
        if (stored > 0) {
            return stored;
        }

        int actual = recountFromChunkData(teamId);
        if (actual > 0) {
            playerDatabase.setUnlockedChunks(teamId, actual);
        }
        return actual;
    }

    /**
     * Recompute a team's unlocked chunks from the chunk database.
     *
     * <p>Never allowed to break pricing: any failure returns 0, which is exactly the value the
     * counter had before this repair existed.</p>
     */
    private int recountFromChunkData(UUID teamId) {
        try {
            ChunkLockManager chunkLockManager = ChunklockPlugin.getInstance().getChunkLockManager();
            return chunkLockManager == null ? 0 : chunkLockManager.countUnlockedChunksForOwner(teamId);
        } catch (Exception e) {
            return 0;
        }
    }

    public void resetPlayer(UUID playerId) {
        UUID teamId = teamManager.getTeamLeader(playerId);
        playerDatabase.setUnlockedChunks(teamId, 0);
    }
}
