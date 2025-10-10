package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerDataManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.util.chunk.ChunkUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Handles the reset command - allows admins to reset player progress completely.
 * This includes removing all unlocked chunks and assigning a new starting chunk.
 */
public class ResetCommand extends SubCommand {

    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final PlayerDataManager playerDataManager;
    private final Random random = new Random();

    private static final int MAX_RESET_ATTEMPTS = 100;
    private static final int MAX_RESET_SCORE = 25;

    public ResetCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager, 
                       PlayerDataManager playerDataManager) {
        super("reset", "chunklock.reset", false);
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
        this.playerDataManager = playerDataManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Usage: /chunklock reset <player>")
                .color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player '" + args[0] + "' not found or not online.")
                .color(NamedTextColor.RED));
            return true;
        }

        performReset(sender, target);
        return true;
    }

    private void performReset(CommandSender sender, Player target) {
        UUID targetId = target.getUniqueId();
        World world = target.getWorld();

        // Count chunks before reset
        int unlockedBefore = chunkLockManager.getTotalUnlockedChunks();

        sender.sendMessage(Component.text("Starting reset for " + target.getName() + "...")
            .color(NamedTextColor.YELLOW));

        // Find a new suitable starting chunk
        Chunk newChunk = findSuitableStartingChunk(world, targetId);
        if (newChunk == null) {
            // Final fallback: use world spawn
            newChunk = world.getSpawnLocation().getChunk();
            sender.sendMessage(Component.text("Warning: Using world spawn as fallback starting chunk")
                .color(NamedTextColor.YELLOW));
        }

        // Final safety check: ensure we're not assigning a chunk already owned by someone else
        UUID existingOwner = chunkLockManager.getChunkOwner(newChunk);
        if (existingOwner != null && !existingOwner.equals(targetId)) {
            sender.sendMessage(Component.text("Warning: Selected chunk is owned by another player, forcing assignment")
                .color(NamedTextColor.YELLOW));
            ChunklockPlugin.getInstance().getLogger().warning("Reset forcing assignment of chunk " + 
                newChunk.getX() + "," + newChunk.getZ() + " owned by " + existingOwner + " to " + targetId);
        }

        // Get chunk evaluation for the new starting chunk
        ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(targetId, newChunk);

        // Reset player progress first
        progressTracker.resetPlayer(targetId);

        // Reset chunks in world (re-lock all except new starting chunk)  
        chunkLockManager.resetPlayerChunks(targetId, newChunk);

        // Set up new starting chunk location
        Location centerSpawn = ChunkUtils.getChunkCenter(newChunk);
        
        // Set player data and teleport
        playerDataManager.setChunk(targetId, centerSpawn);
        target.teleport(centerSpawn);
        target.setRespawnLocation(centerSpawn, true);
        target.getInventory().clear(); // Clear inventory as part of reset

        // Count chunks after reset
        int unlockedAfter = chunkLockManager.getTotalUnlockedChunks();

        // Send success messages
        sender.sendMessage(Component.text("✓ Complete reset performed for " + target.getName() + "!")
            .color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Player progress reset for " + target.getName())
            .color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Chunks locked: " + (unlockedBefore - unlockedAfter) +
                " (from " + unlockedBefore + " to " + unlockedAfter + ")")
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ() +
                " (Score: " + evaluation.score + ", Difficulty: " + evaluation.difficulty + ")")
            .color(NamedTextColor.GRAY));

        target.sendMessage(Component.text("Your progress has been completely reset by an admin.")
            .color(NamedTextColor.RED));
        target.sendMessage(Component.text("All previously unlocked chunks have been locked again.")
            .color(NamedTextColor.RED));
        target.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ())
            .color(NamedTextColor.GREEN));
        target.sendMessage(Component.text("Spawning at center: " + (int) centerSpawn.getX() + ", " + (int) centerSpawn.getZ())
            .color(NamedTextColor.GRAY));

        // Regenerate borders for the player immediately
        ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
        if (borderManager != null) {
            borderManager.updateBordersForPlayer(target);
        }
    }

    private Chunk findSuitableStartingChunk(World world, UUID playerId) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        ChunklockPlugin.getInstance().getLogger().info("Searching for suitable starting chunk for player " + playerId + 
            " (target score <= " + MAX_RESET_SCORE + ")");

        for (int attempt = 0; attempt < MAX_RESET_ATTEMPTS; attempt++) {
            try {
                // Search in area around spawn
                int cx = random.nextInt(41) - 20; // -20 to +20 chunk range
                int cz = random.nextInt(41) - 20;

                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) {
                    ChunklockPlugin.getInstance().getLogger().fine("Attempt " + attempt + ": chunk is null");
                    continue;
                }

                // Check if chunk is already owned by another player
                UUID currentOwner = chunkLockManager.getChunkOwner(chunk);
                if (currentOwner != null && !currentOwner.equals(playerId)) {
                    ChunklockPlugin.getInstance().getLogger().fine("Attempt " + attempt + ": chunk " + 
                        chunk.getX() + "," + chunk.getZ() + " is already owned by " + currentOwner);
                    continue; // Skip chunks owned by other players
                }

                // Evaluate chunk score
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(playerId, chunk);
                
                ChunklockPlugin.getInstance().getLogger().fine("Attempt " + attempt + ": chunk " + 
                    chunk.getX() + "," + chunk.getZ() + " has score " + evaluation.score + 
                    " (threshold: " + MAX_RESET_SCORE + ")");

                if (evaluation.score <= MAX_RESET_SCORE) {
                    validChunksFound++;
                    
                    if (evaluation.score < bestScore) {
                        bestScore = evaluation.score;
                        bestChunk = chunk;
                        
                        ChunklockPlugin.getInstance().getLogger().info("Found better chunk at " + 
                            chunk.getX() + "," + chunk.getZ() + " with score " + evaluation.score);
                    }

                    // If we find a really good chunk, stop searching
                    if (evaluation.score <= 10) {
                        ChunklockPlugin.getInstance().getLogger().info("Found excellent chunk, stopping search");
                        break;
                    }
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().warning("Error in attempt " + attempt + ": " + e.getMessage());
                continue;
            }
        }

        ChunklockPlugin.getInstance().getLogger().info("Search completed: found " + validChunksFound + 
            " valid chunks, best score: " + (bestChunk != null ? bestScore : "none"));

        // Fallback: if no chunk found, try world spawn area
        if (bestChunk == null) {
            ChunklockPlugin.getInstance().getLogger().warning("No suitable chunk found, trying world spawn as fallback");
            try {
                Chunk spawnChunk = world.getSpawnLocation().getChunk();
                
                // Check if world spawn is already owned by another player
                UUID spawnOwner = chunkLockManager.getChunkOwner(spawnChunk);
                if (spawnOwner != null && !spawnOwner.equals(playerId)) {
                    ChunklockPlugin.getInstance().getLogger().warning("World spawn chunk is owned by " + spawnOwner + 
                        ", searching for alternative...");
                    
                    // Try a few chunks around spawn as backup
                    for (int dx = -2; dx <= 2 && bestChunk == null; dx++) {
                        for (int dz = -2; dz <= 2 && bestChunk == null; dz++) {
                            if (dx == 0 && dz == 0) continue; // Skip spawn chunk itself
                            
                            Chunk altChunk = world.getChunkAt(spawnChunk.getX() + dx, spawnChunk.getZ() + dz);
                            UUID altOwner = chunkLockManager.getChunkOwner(altChunk);
                            
                            if (altOwner == null || altOwner.equals(playerId)) {
                                bestChunk = altChunk;
                                ChunklockPlugin.getInstance().getLogger().info("Using alternative chunk near spawn: " + 
                                    altChunk.getX() + "," + altChunk.getZ());
                                break;
                            }
                        }
                    }
                }
                
                if (bestChunk == null) {
                    // If still no chunk found, use spawn regardless (last resort)
                    bestChunk = spawnChunk;
                    ChunklockPlugin.getInstance().getLogger().warning("Using world spawn as absolute last resort");
                }
                
                ChunkEvaluator.ChunkValueData spawnEval = chunkLockManager.evaluateChunk(playerId, bestChunk);
                ChunklockPlugin.getInstance().getLogger().info("Fallback chunk (" + bestChunk.getX() + 
                    "," + bestChunk.getZ() + ") score: " + spawnEval.score);
                
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().severe("Even fallback chunk selection failed: " + e.getMessage());
            }
        }

        return bestChunk;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }

    @Override
    public String getUsage() {
        return "/chunklock reset <player>";
    }

    @Override
    public String getDescription() {
        return "Admin: Complete reset (progress + chunks)";
    }
}