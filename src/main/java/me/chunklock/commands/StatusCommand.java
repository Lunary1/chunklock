// src/main/java/me/chunklock/commands/StatusCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.LanguageKeys;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.util.message.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles the status command - shows player's chunk progress and current chunk info.
 */
public class StatusCommand extends SubCommand {
    
    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    
    public StatusCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager) {
        super("status", "chunklock.use", true);
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        Player player = asPlayer(sender);
        if (player == null) {
            return false; // Should not happen due to requiresPlayer = true
        }
        
        try {
            // Show unlocked chunk count
            int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("unlocked", String.valueOf(unlocked));
            String message = MessageUtil.getMessage(LanguageKeys.COMMAND_STATUS_CHUNKS, placeholders);
            player.sendMessage(Component.text(message).color(NamedTextColor.GREEN));
            
            // Show current chunk info
            showCurrentChunkInfo(player);
            
            // Show team info if available
            showTeamInfo(player);
            
            return true;
            
        } catch (Exception e) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", e.getMessage());
            String message = MessageUtil.getMessage(LanguageKeys.ERROR_GENERIC, placeholders);
            player.sendMessage(Component.text(message).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().warning(
                "Error in status command for " + player.getName() + ": " + e.getMessage());
            return true;
        }
    }
    
    private void showCurrentChunkInfo(Player player) {
        try {
            Chunk currentChunk = player.getLocation().getChunk();
            boolean isLocked = chunkLockManager.isLocked(currentChunk);
            ChunkEvaluator.ChunkValueData eval = chunkLockManager.evaluateChunk(
                player.getUniqueId(), currentChunk);
            
            player.sendMessage(Component.text("Current chunk (" + currentChunk.getX() + 
                ", " + currentChunk.getZ() + "): " + (isLocked ? "§cLocked" : "§aUnlocked"))
                .color(NamedTextColor.GRAY));
            player.sendMessage(Component.text("Score: " + eval.score + 
                " | Difficulty: " + eval.difficulty)
                .color(NamedTextColor.GRAY));
                
        } catch (Exception e) {
            player.sendMessage(Component.text("Could not retrieve current chunk information.")
                .color(NamedTextColor.YELLOW));
        }
    }
    
    private void showTeamInfo(Player player) {
        try {
            // Try to get enhanced team manager info
            var enhancedTeamManager = ChunklockPlugin.getInstance().getEnhancedTeamManager();
            if (enhancedTeamManager != null) {
                var team = enhancedTeamManager.getPlayerTeam(player.getUniqueId());
                if (team != null) {
                    player.sendMessage(Component.text("Team: " + team.getTeamName() + 
                        " (" + team.getTotalMembers() + " members)")
                        .color(NamedTextColor.AQUA));
                    
                    double teamMultiplier = enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
                    if (teamMultiplier > 1.0) {
                        player.sendMessage(Component.text("Team cost multiplier: " + 
                            String.format("%.1fx", teamMultiplier))
                            .color(NamedTextColor.YELLOW));
                    }
                }
            }
        } catch (Exception e) {
            // Team info not available or error occurred - that's okay, just skip it
            ChunklockPlugin.getInstance().getLogger().fine(
                "Could not retrieve team info for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        // Status command has no additional arguments
        return Collections.emptyList();
    }
    
    @Override
    public String getUsage() {
        return "/chunklock status";
    }
    
    @Override
    public String getDescription() {
        return "View your unlocked chunks and current chunk info";
    }
}