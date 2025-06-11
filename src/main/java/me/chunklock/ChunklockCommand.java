package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

public class ChunklockCommand implements CommandExecutor, TabCompleter {

    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final TeamManager teamManager;
    private final Random random = new Random();
    
    private static final int MAX_RESET_ATTEMPTS = 100;
    private static final int MAX_RESET_SCORE = 25; // Same threshold as initial spawn

    public ChunklockCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager, 
                           UnlockGui unlockGui, TeamManager teamManager) {
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /chunklock <status|reset|bypass|unlock|help>").color(NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use /chunklock status.").color(NamedTextColor.RED));
                    return true;
                }

                int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
                player.sendMessage(Component.text("You have unlocked " + unlocked + " chunks.").color(NamedTextColor.GREEN));
            }

            case "reset" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /chunklock reset <player>").color(NamedTextColor.YELLOW));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found or not online.").color(NamedTextColor.RED));
                    return true;
                }

                // Reset player progress
                progressTracker.resetPlayer(target.getUniqueId());

                // Find a new suitable chunk with improved logic
                World world = target.getWorld();
                Chunk newChunk = findSuitableResetChunk(target, world);
                
                if (newChunk == null) {
                    sender.sendMessage(Component.text("Warning: Could not find ideal chunk, using world spawn area").color(NamedTextColor.YELLOW));
                    newChunk = world.getSpawnLocation().getChunk();
                }

                // Calculate exact center of the chosen chunk
                Location centerSpawn = getCenterLocationOfChunk(newChunk);
                
                // Unlock the chunk and set up player
                chunkLockManager.unlockChunk(newChunk);
                ChunklockPlugin.getInstance().getPlayerDataManager().setChunk(target.getUniqueId(), centerSpawn);
                
                // Teleport to center and clear inventory
                target.teleport(centerSpawn);
                target.getInventory().clear();
                target.setRespawnLocation(centerSpawn, true);

                // Provide feedback
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(target.getUniqueId(), newChunk);
                
                sender.sendMessage(Component.text("Reset chunk progress and assigned new spawn for " + target.getName()).color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("New chunk: " + newChunk.getX() + ", " + newChunk.getZ() + 
                    " (Score: " + evaluation.score + ", Difficulty: " + evaluation.difficulty + ")").color(NamedTextColor.GRAY));
                
                target.sendMessage(Component.text("Your chunk progress was reset by an admin.").color(NamedTextColor.RED));
                target.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ()).color(NamedTextColor.GREEN));
                target.sendMessage(Component.text("Spawning at center coordinates: " + (int)centerSpawn.getX() + ", " + (int)centerSpawn.getZ()).color(NamedTextColor.GRAY));
            }

            case "bypass" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player not found or not online.").color(NamedTextColor.RED));
                        return true;
                    }
                } else {
                    if (!(sender instanceof Player p)) {
                        sender.sendMessage(Component.text("Only players can toggle their own bypass.").color(NamedTextColor.RED));
                        return true;
                    }
                    target = p;
                }

                chunkLockManager.setBypassing(target, !chunkLockManager.isBypassing(target));
                boolean state = chunkLockManager.isBypassing(target);
                sender.sendMessage(Component.text("Chunklock bypass " + (state ? "enabled" : "disabled") + " for " + target.getName()).color(NamedTextColor.GREEN));
                if (sender != target) {
                    target.sendMessage(Component.text("Chunklock bypass " + (state ? "enabled" : "disabled") + " by admin.").color(NamedTextColor.YELLOW));
                }
            }

            case "unlock" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can unlock chunks.").color(NamedTextColor.RED));
                    return true;
                }
                Chunk chunk = player.getLocation().getChunk();
                unlockGui.open(player, chunk);
            }

            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
                    return true;
                }
                Location savedLoc = ChunklockPlugin.getInstance().getPlayerDataManager().getChunkSpawn(player.getUniqueId());
                if (savedLoc != null) {
                    // Ensure they teleport to the center of their assigned chunk
                    Location centerLoc = getCenterLocationOfChunk(savedLoc.getChunk());
                    player.teleport(centerLoc);
                    player.sendMessage(Component.text("Teleported to center of your starting chunk.").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("No starting chunk recorded.").color(NamedTextColor.RED));
                }
            }

            case "team" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can join teams.").color(NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /chunklock team <player>").color(NamedTextColor.YELLOW));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
                    return true;
                }
                teamManager.setTeamLeader(player.getUniqueId(), teamManager.getTeamLeader(target.getUniqueId()));
                player.sendMessage(Component.text("Joined " + target.getName() + "'s team!").color(NamedTextColor.GREEN));
            }

            case "help" -> {
                sender.sendMessage(Component.text("Chunklock Commands:").color(NamedTextColor.AQUA));
                sender.sendMessage(Component.text("/chunklock status - View your unlocked chunks").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock reset <player> - Admin: Reset a player's chunks and spawn").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock bypass [player] - Admin: Toggle bypass mode").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock unlock - Attempt to unlock your current chunk").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock spawn - Return to your starting chunk center").color(NamedTextColor.GRAY));
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }

    /**
     * Finds a suitable chunk for reset with score below threshold
     */
    private Chunk findSuitableResetChunk(Player player, World world) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        ChunklockPlugin.getInstance().getLogger().info("Searching for suitable reset chunk for " + player.getName() + 
            " (target score <= " + MAX_RESET_SCORE + ")");

        for (int attempt = 0; attempt < MAX_RESET_ATTEMPTS; attempt++) {
            try {
                // Search in a reasonable area around spawn
                int cx = random.nextInt(41) - 20; // -20 to +20 chunk range  
                int cz = random.nextInt(41) - 20;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;

                // Evaluate chunk score
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                // Check if this chunk meets our criteria
                if (evaluation.score <= MAX_RESET_SCORE) {
                    validChunksFound++;
                    
                    // Additional safety check
                    Location centerLocation = getCenterLocationOfChunk(chunk);
                    if (isSafeSpawnLocation(centerLocation)) {
                        
                        // Prefer the chunk with the lowest score
                        if (evaluation.score < bestScore) {
                            bestChunk = chunk;
                            bestScore = evaluation.score;
                        }
                        
                        // If we found an excellent chunk, use it
                        if (evaluation.score <= 10) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                ChunklockPlugin.getInstance().getLogger().log(Level.FINE, "Error in reset chunk evaluation attempt " + attempt, e);
                continue;
            }
        }

        ChunklockPlugin.getInstance().getLogger().info("Reset chunk search completed for " + player.getName() + 
            ": found " + validChunksFound + " valid chunks, selected chunk with score " + 
            (bestChunk != null ? bestScore : "none"));

        return bestChunk;
    }

    /**
     * Calculates the exact center location of a chunk
     */
    private Location getCenterLocationOfChunk(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            throw new IllegalArgumentException("Invalid chunk provided");
        }

        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        // Calculate exact center coordinates
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        
        // Get the highest solid block at center
        int centerY;
        try {
            centerY = world.getHighestBlockAt(centerX, centerZ).getY() + 1;
            centerY = Math.max(world.getMinHeight() + 1, 
                      Math.min(centerY, world.getMaxHeight() - 2));
        } catch (Exception e) {
            centerY = world.getSpawnLocation().getBlockY();
        }
        
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    private boolean isSafeSpawnLocation(Location location) {
        try {
            if (location == null || location.getWorld() == null) return false;
            
            World world = location.getWorld();
            int x = location.getBlockX();
            int y = location.getBlockY();
            int z = location.getBlockZ();
            
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 10) return false;
            
            var blockAt = world.getBlockAt(x, y, z);
            var blockBelow = world.getBlockAt(x, y - 1, z);
            var blockAbove = world.getBlockAt(x, y + 1, z);
            
            return blockBelow != null && blockBelow.getType().isSolid() && 
                   blockAt != null && (blockAt.getType().isAir() || !blockAt.getType().isSolid()) && 
                   blockAbove != null && (blockAbove.getType().isAir() || !blockAbove.getType().isSolid()) &&
                   !blockBelow.isLiquid();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();

            for (String sub : List.of("status", "reset", "bypass", "unlock", "spawn", "team", "help")) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("bypass") || args[0].equalsIgnoreCase("team"))) {
            String prefix = args[1].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add(p.getName());
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }
}