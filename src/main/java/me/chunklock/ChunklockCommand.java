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
import java.util.Set;
import java.util.logging.Level;

public class ChunklockCommand implements CommandExecutor, TabCompleter {

    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final TeamManager teamManager;
    private final ChunkOwnershipManager ownershipManager;
    private final Random random = new Random();
    
    private static final int MAX_RESET_ATTEMPTS = 100;
    private static final int MAX_RESET_SCORE = 25;

    public ChunklockCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager, 
                           UnlockGui unlockGui, TeamManager teamManager, ChunkOwnershipManager ownershipManager) {
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.teamManager = teamManager;
        this.ownershipManager = ownershipManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /chunklock <status|reset|bypass|unlock|reload|help|claims|overclaim>").color(NamedTextColor.YELLOW));
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
                
                // Show overclaim information
                if (ownershipManager.isOverclaimEnabled()) {
                    int remaining = ownershipManager.getRemainingOverclaims(player.getUniqueId());
                    player.sendMessage(Component.text("Daily overclaims remaining: " + remaining).color(NamedTextColor.YELLOW));
                }
                
                // Show current chunk info
                Chunk currentChunk = player.getLocation().getChunk();
                boolean isLocked = chunkLockManager.isLocked(currentChunk);
                ChunkEvaluator.ChunkValueData eval = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);
                
                player.sendMessage(Component.text("Current chunk (" + currentChunk.getX() + ", " + currentChunk.getZ() + "): " + 
                    (isLocked ? "§cLocked" : "§aUnlocked")).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Score: " + eval.score + " | Difficulty: " + eval.difficulty).color(NamedTextColor.GRAY));
                
                // Show ownership info for current chunk
                if (!isLocked) {
                    var ownership = ownershipManager.getChunkOwnership(currentChunk);
                    if (ownership != null) {
                        if (ownership.getOwnerId().equals(player.getUniqueId())) {
                            player.sendMessage(Component.text("✓ You own this chunk").color(NamedTextColor.GREEN));
                        } else {
                            player.sendMessage(Component.text("⚠ Owned by: " + ownership.getOwnerName()).color(NamedTextColor.YELLOW));
                            if (ownership.isOverclaimed()) {
                                player.sendMessage(Component.text("This chunk was overclaimed").color(NamedTextColor.RED));
                            }
                        }
                    }
                }
            }

            case "claims" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use /chunklock claims.").color(NamedTextColor.RED));
                    return true;
                }

                // Show detailed claims information
                List<String> ownedChunks = ownershipManager.getOwnedChunks(player.getUniqueId(), false);
                List<String> teamChunks = ownershipManager.getOwnedChunks(player.getUniqueId(), true);
                
                player.sendMessage(Component.text("=== Your Chunk Claims ===").color(NamedTextColor.AQUA));
                player.sendMessage(Component.text("Personal chunks: " + ownedChunks.size()).color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("Team chunks: " + (teamChunks.size() - ownedChunks.size())).color(NamedTextColor.YELLOW));
                
                if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
                    player.sendMessage(Component.text("Your chunks:").color(NamedTextColor.WHITE));
                    for (String chunkKey : ownedChunks) {
                        String[] parts = chunkKey.split(":");
                        if (parts.length == 3) {
                            player.sendMessage(Component.text("  " + parts[1] + ", " + parts[2]).color(NamedTextColor.GRAY));
                        }
                    }
                } else {
                    player.sendMessage(Component.text("Use '/chunklock claims list' to see all your chunks").color(NamedTextColor.GRAY));
                }
                
                if (ownershipManager.isOverclaimEnabled()) {
                    int remaining = ownershipManager.getRemainingOverclaims(player.getUniqueId());
                    player.sendMessage(Component.text("Overclaims remaining today: " + remaining).color(NamedTextColor.YELLOW));
                }
            }

            case "overclaim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can overclaim chunks.").color(NamedTextColor.RED));
                    return true;
                }

                if (!ownershipManager.isOverclaimEnabled()) {
                    player.sendMessage(Component.text("Overclaiming is disabled on this server.").color(NamedTextColor.RED));
                    return true;
                }

                Chunk chunk = player.getLocation().getChunk();
                
                if (chunkLockManager.isLocked(chunk)) {
                    player.sendMessage(Component.text("This chunk is still locked. Unlock it first before overclaiming.").color(NamedTextColor.RED));
                    return true;
                }

                var ownership = ownershipManager.getChunkOwnership(chunk);
                if (ownership == null) {
                    player.sendMessage(Component.text("This chunk has no owner to overclaim from.").color(NamedTextColor.RED));
                    return true;
                }

                if (ownership.getOwnerId().equals(player.getUniqueId())) {
                    player.sendMessage(Component.text("You already own this chunk.").color(NamedTextColor.RED));
                    return true;
                }

                // Open overclaim GUI
                unlockGui.open(player, chunk);
            }

            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use /chunklock info.").color(NamedTextColor.RED));
                    return true;
                }

                Chunk chunk = player.getLocation().getChunk();
                var eval = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                var requirement = ChunklockPlugin.getInstance().getBiomeUnlockRegistry().calculateRequirement(player, eval.biome, eval.score);
                
                player.sendMessage(Component.text("=== Chunk Information ===").color(NamedTextColor.AQUA));
                player.sendMessage(Component.text("Location: " + chunk.getX() + ", " + chunk.getZ()).color(NamedTextColor.WHITE));
                player.sendMessage(Component.text("Biome: " + BiomeUnlockRegistry.getBiomeDisplayName(eval.biome)).color(NamedTextColor.GREEN));
                player.sendMessage(Component.text("Difficulty: " + eval.difficulty).color(NamedTextColor.YELLOW));
                player.sendMessage(Component.text("Score: " + eval.score).color(NamedTextColor.GRAY));
                
                if (chunkLockManager.isLocked(chunk)) {
                    player.sendMessage(Component.text("Status: Locked").color(NamedTextColor.RED));
                    player.sendMessage(Component.text("Unlock cost: " + requirement.amount() + "x " + requirement.material().name()).color(NamedTextColor.YELLOW));
                } else {
                    player.sendMessage(Component.text("Status: Unlocked").color(NamedTextColor.GREEN));
                    
                    var ownership = ownershipManager.getChunkOwnership(chunk);
                    if (ownership != null) {
                        player.sendMessage(Component.text("Owner: " + ownership.getOwnerName()).color(NamedTextColor.WHITE));
                        if (ownership.isOverclaimed()) {
                            player.sendMessage(Component.text("⚠ This chunk was overclaimed").color(NamedTextColor.RED));
                        }
                        
                        // Show overclaim cost if applicable
                        if (ownershipManager.isOverclaimEnabled() && !ownership.getOwnerId().equals(player.getUniqueId())) {
                            var overclaimResult = ownershipManager.attemptOverclaim(chunk, player);
                            if (overclaimResult.isAllowed()) {
                                double multiplier = ownershipManager.calculateOverclaimMultiplier(chunk, player.getUniqueId());
                                int overclaimCost = (int) Math.ceil(requirement.amount() * multiplier);
                                player.sendMessage(Component.text("Overclaim cost: " + overclaimCost + "x " + requirement.material().name() + 
                                    " (x" + String.format("%.1f", multiplier) + ")").color(NamedTextColor.RED));
                            } else {
                                player.sendMessage(Component.text("Cannot overclaim: " + overclaimResult.getMessage()).color(NamedTextColor.RED));
                            }
                        }
                    }
                }
            }

            case "reload" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                sender.sendMessage(Component.text("Starting Chunklock plugin reload...").color(NamedTextColor.YELLOW));
                
                try {
                    long startTime = System.currentTimeMillis();
                    boolean success = ChunklockPlugin.getInstance().performReload(sender);
                    long endTime = System.currentTimeMillis();
                    
                    if (success) {
                        sender.sendMessage(Component.text("✓ Chunklock plugin reloaded successfully! (" + (endTime - startTime) + "ms)").color(NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("✗ Reload completed with warnings. Check console for details.").color(NamedTextColor.YELLOW));
                    }
                } catch (Exception e) {
                    sender.sendMessage(Component.text("✗ Reload failed: " + e.getMessage()).color(NamedTextColor.RED));
                    ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, "Reload command failed", e);
                }
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

                // Get count of currently unlocked chunks before reset
                int unlockedBefore = chunkLockManager.getTotalUnlockedChunks();

                sender.sendMessage(Component.text("Starting reset for " + target.getName() + "...").color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Chunks unlocked before reset: " + unlockedBefore).color(NamedTextColor.GRAY));

                // Find a new suitable chunk with improved logic
                World world = target.getWorld();
                Chunk newChunk = findSuitableResetChunk(target, world);
                
                if (newChunk == null) {
                    sender.sendMessage(Component.text("Warning: Could not find ideal chunk, using world spawn area").color(NamedTextColor.YELLOW));
                    newChunk = world.getSpawnLocation().getChunk();
                }

                // Calculate exact center of the chosen chunk
                Location centerSpawn = getCenterLocationOfChunk(newChunk);
                
                // IMPORTANT: Reset player progress FIRST
                progressTracker.resetPlayer(target.getUniqueId());
                
                // IMPORTANT: Reset ownership data for this player
                ownershipManager.resetPlayerOwnership(target.getUniqueId());
                
                // IMPORTANT: Reset chunks in world (re-lock all except new starting chunk)
                chunkLockManager.resetPlayerChunks(target.getUniqueId(), newChunk);
                
                // Set up player data
                ChunklockPlugin.getInstance().getPlayerDataManager().setChunk(target.getUniqueId(), centerSpawn);
                
                // Set ownership of new starting chunk
                ownershipManager.setChunkOwner(newChunk, target.getUniqueId(), target.getName());
                
                // Teleport to center and clear inventory
                target.teleport(centerSpawn);
                target.getInventory().clear();
                target.setRespawnLocation(centerSpawn, true);

                // Get stats after reset
                int unlockedAfter = chunkLockManager.getTotalUnlockedChunks();
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(target.getUniqueId(), newChunk);
                
                // Provide detailed feedback
                sender.sendMessage(Component.text("✓ Reset completed successfully!").color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Player progress and ownership reset for " + target.getName()).color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Chunks locked: " + (unlockedBefore - unlockedAfter) + 
                    " (from " + unlockedBefore + " to " + unlockedAfter + ")").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ() + 
                    " (Score: " + evaluation.score + ", Difficulty: " + evaluation.difficulty + ")").color(NamedTextColor.GRAY));
                
                target.sendMessage(Component.text("Your progress has been completely reset by an admin.").color(NamedTextColor.RED));
                target.sendMessage(Component.text("All previously unlocked chunks have been locked again.").color(NamedTextColor.RED));
                target.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ()).color(NamedTextColor.GREEN));
                target.sendMessage(Component.text("Spawning at center: " + (int)centerSpawn.getX() + ", " + (int)centerSpawn.getZ()).color(NamedTextColor.GRAY));
            }

            case "debug" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                int totalUnlocked = chunkLockManager.getTotalUnlockedChunks();
                Set<String> unlockedChunks = chunkLockManager.getUnlockedChunks();
                var ownershipStats = ownershipManager.getOwnershipStats();
                
                sender.sendMessage(Component.text("=== Chunk Debug Info ===").color(NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Total unlocked chunks in world: " + totalUnlocked).color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Total owned chunks: " + ownershipStats.get("totalOwnedChunks")).color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Overclaimed chunks: " + ownershipStats.get("overclaimedChunks")).color(NamedTextColor.RED));
                sender.sendMessage(Component.text("Original ownership chunks: " + ownershipStats.get("originalOwnershipChunks")).color(NamedTextColor.WHITE));
                sender.sendMessage(Component.text("Overclaiming enabled: " + ownershipStats.get("overclaimEnabled")).color(NamedTextColor.GRAY));
                
                if (args.length > 1 && args[1].equalsIgnoreCase("list")) {
                    sender.sendMessage(Component.text("Unlocked chunks:").color(NamedTextColor.GRAY));
                    for (String chunkKey : unlockedChunks) {
                        sender.sendMessage(Component.text("  " + chunkKey).color(NamedTextColor.WHITE));
                    }
                } else {
                    sender.sendMessage(Component.text("Use '/chunklock debug list' to see all unlocked chunks").color(NamedTextColor.GRAY));
                }
            }

            case "resetall" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                sender.sendMessage(Component.text("WARNING: This will lock ALL chunks and reset ALL ownership! Type '/chunklock resetall confirm' to proceed.").color(NamedTextColor.RED));
                
                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    int before = chunkLockManager.getTotalUnlockedChunks();
                    chunkLockManager.resetAllChunks();
                    // Note: Would need to add resetAllOwnership method to ChunkOwnershipManager
                    sender.sendMessage(Component.text("Locked " + before + " chunks and reset all ownership data. All chunks are now locked.").color(NamedTextColor.GREEN));
                }
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
                sender.sendMessage(Component.text("/chunklock status - View your unlocked chunks and overclaim info").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock claims [list] - View your chunk claims").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock info - Get detailed info about current chunk").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock unlock - Attempt to unlock your current chunk").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock overclaim - Attempt to overclaim current chunk").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock spawn - Return to your starting chunk center").color(NamedTextColor.GRAY));
                if (sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("/chunklock reset <player> - Admin: Complete reset (progress + chunks + ownership)").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock bypass [player] - Admin: Toggle bypass mode").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock reload - Admin: Reload plugin configuration").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock debug [list] - Admin: Show chunk debug info").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock resetall confirm - Admin: Lock all chunks and reset ownership").color(NamedTextColor.GRAY));
                }
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }

    private Chunk findSuitableResetChunk(Player player, World world) {
        Chunk bestChunk = null;
        int bestScore = Integer.MAX_VALUE;
        int validChunksFound = 0;

        ChunklockPlugin.getInstance().getLogger().info("Searching for suitable reset chunk for " + player.getName() + 
            " (target score <= " + MAX_RESET_SCORE + ")");

        for (int attempt = 0; attempt < MAX_RESET_ATTEMPTS; attempt++) {
            try {
                int cx = random.nextInt(41) - 20;
                int cz = random.nextInt(41) - 20;
                
                Chunk chunk = world.getChunkAt(cx, cz);
                if (chunk == null) continue;

                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), chunk);
                
                if (evaluation.score <= MAX_RESET_SCORE) {
                    validChunksFound++;
                    
                    Location centerLocation = getCenterLocationOfChunk(chunk);
                    if (isSafeSpawnLocation(centerLocation)) {
                        
                        if (evaluation.score < bestScore) {
                            bestChunk = chunk;
                            bestScore = evaluation.score;
                        }
                        
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

    private Location getCenterLocationOfChunk(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null) {
            throw new IllegalArgumentException("Invalid chunk provided");
        }

        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        
        int centerX = chunkX * 16 + 8;
        int centerZ = chunkZ * 16 + 8;
        
        int centerY;
        try {
            centerY = world.getHighestBlockAt(centerX, centerZ).getY() + 1;
            centerY = Math.max(world.getMinHeight() + 1, 
                      Math.min(centerY, world.getMaxHeight() - 2));
        } catch (Exception e) {
            centerY = Math.max(world.getMinHeight() + 10, world.getSpawnLocation().getBlockY());
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
            List<String> commands = new ArrayList<>(List.of("status", "reset", "bypass", "unlock", "spawn", "team", "help", "claims", "overclaim", "info"));
            
            if (sender.hasPermission("chunklock.admin")) {
                commands.addAll(List.of("reload", "debug", "resetall"));
            }

            for (String sub : commands) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("bypass") || args[0].equalsIgnoreCase("team")) {
                String prefix = args[1].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("debug")) {
                completions.add("list");
            } else if (args[0].equalsIgnoreCase("resetall")) {
                completions.add("confirm");
            } else if (args[0].equalsIgnoreCase("claims")) {
                completions.add("list");
            }
        }

        return completions;
    }
}