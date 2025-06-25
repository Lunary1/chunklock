package me.chunklock.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import me.chunklock.util.ChunkUtils;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chunklock.commands.BasicTeamCommandHandler;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.managers.PlayerProgressTracker;
import me.chunklock.managers.TeamManager;
import me.chunklock.ui.UnlockGui;
import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.managers.ChunkBorderManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class ChunklockCommand implements CommandExecutor, TabCompleter {

    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final TeamManager teamManager;
    
    // 🔧 FIX: Add BiomeUnlockRegistry as a field
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    
    // Enhanced team command handler
    private final BasicTeamCommandHandler teamCommandHandler;
    
    private final Random random = new Random();

    private static final int MAX_RESET_ATTEMPTS = 100;
    private static final int MAX_RESET_SCORE = 25;

    // 🔧 FIX: Add BiomeUnlockRegistry to constructor
    public ChunklockCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager,
                           UnlockGui unlockGui, TeamManager teamManager, 
                           BasicTeamCommandHandler teamCommandHandler, BiomeUnlockRegistry biomeUnlockRegistry) {
        this.progressTracker = progressTracker;
        this.chunkLockManager = chunkLockManager;
        this.unlockGui = unlockGui;
        this.teamManager = teamManager;
        this.teamCommandHandler = teamCommandHandler;
        this.biomeUnlockRegistry = biomeUnlockRegistry; // 🔧 FIX: Store the reference
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /chunklock <status|reset|bypass|unlock|reload|team|borders|help>").color(NamedTextColor.YELLOW));
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

                // Show current chunk info
                Chunk currentChunk = player.getLocation().getChunk();
                boolean isLocked = chunkLockManager.isLocked(currentChunk);
                ChunkEvaluator.ChunkValueData eval = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);

                player.sendMessage(Component.text("Current chunk (" + currentChunk.getX() + ", " + currentChunk.getZ() + "): " +
                        (isLocked ? "§cLocked" : "§aUnlocked")).color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Score: " + eval.score + " | Difficulty: " + eval.difficulty).color(NamedTextColor.GRAY));
                
                // Show team info if player is in a team
                if (teamCommandHandler != null) {
                    try {
                        var teamManager = ChunklockPlugin.getInstance().getEnhancedTeamManager();
                        var team = teamManager.getPlayerTeam(player.getUniqueId());
                        if (team != null) {
                            player.sendMessage(Component.text("Team: " + team.getTeamName() + " (" + team.getTotalMembers() + " members)")
                                .color(NamedTextColor.AQUA));
                            double teamMultiplier = teamManager.getChunkCostMultiplier(player.getUniqueId());
                            if (teamMultiplier > 1.0) {
                                player.sendMessage(Component.text("Team cost multiplier: " + String.format("%.1fx", teamMultiplier))
                                    .color(NamedTextColor.YELLOW));
                            }
                        }
                    } catch (Exception e) {
                        // Team info not available - that's okay
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

            case "resetall" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                sender.sendMessage(Component.text("WARNING: This will lock ALL chunks! Type '/chunklock resetall confirm' to proceed.").color(NamedTextColor.RED));

                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    int before = chunkLockManager.getTotalUnlockedChunks();
                    chunkLockManager.resetAllChunks();
                    sender.sendMessage(Component.text("Locked " + before + " chunks. All chunks are now locked.").color(NamedTextColor.GREEN));
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

            case "borders" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /chunklock borders <refresh [player]|info|clear>").color(NamedTextColor.YELLOW));
                    return true;
                }

                try {
                    ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                    
                    switch (args[1].toLowerCase()) {
                        case "refresh" -> {
                            if (args.length >= 3) {
                                // Refresh borders for specific player
                                Player target = Bukkit.getPlayer(args[2]);
                                if (target == null) {
                                    sender.sendMessage(Component.text("Player not found or not online.").color(NamedTextColor.RED));
                                    return true;
                                }
                                borderManager.refreshBordersForPlayer(target);
                                sender.sendMessage(Component.text("✓ Refreshed glass borders for " + target.getName() + ".").color(NamedTextColor.GREEN));
                            } else {
                                // Refresh borders for all players
                                borderManager.refreshAllBorders();
                                sender.sendMessage(Component.text("✓ Refreshed glass borders for all online players.").color(NamedTextColor.GREEN));
                            }
                        }
                        case "info" -> {
                            var stats = borderManager.getBorderStats();
                            sender.sendMessage(Component.text("=== Glass Border Statistics ===").color(NamedTextColor.AQUA));
                            sender.sendMessage(Component.text("Border system enabled: " + stats.get("enabled")).color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Border material: " + stats.get("borderMaterial")).color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Scan range: " + stats.get("scanRange") + " chunks").color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Border height: " + stats.get("borderHeight") + " blocks").color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Players with borders: " + stats.get("playersWithBorders")).color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Total border blocks: " + stats.get("totalBorderBlocks")).color(NamedTextColor.YELLOW));
                            sender.sendMessage(Component.text("Border mappings: " + stats.get("borderToChunkMappings")).color(NamedTextColor.YELLOW));
                            
                            // Show configuration details
                            @SuppressWarnings("unchecked")
                            var config = (java.util.Map<String, Object>) stats.get("config");
                            if (config != null) {
                                sender.sendMessage(Component.text("Auto-update on movement: " + config.get("autoUpdateOnMovement")).color(NamedTextColor.GRAY));
                                sender.sendMessage(Component.text("Show for bypass players: " + config.get("showForBypassPlayers")).color(NamedTextColor.GRAY));
                                sender.sendMessage(Component.text("Restore original blocks: " + config.get("restoreOriginalBlocks")).color(NamedTextColor.GRAY));
                                sender.sendMessage(Component.text("Update cooldown: " + config.get("updateCooldown")).color(NamedTextColor.GRAY));
                            }
                        }
                        case "clear" -> {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                borderManager.removeBordersForPlayer(player);
                            }
                            sender.sendMessage(Component.text("✓ Cleared all glass borders.").color(NamedTextColor.GREEN));
                        }
                        default -> {
                            sender.sendMessage(Component.text("Usage: /chunklock borders <refresh [player]|info|clear>").color(NamedTextColor.YELLOW));
                        }
                    }
                } catch (Exception e) {
                    sender.sendMessage(Component.text("Error managing borders: " + e.getMessage()).color(NamedTextColor.RED));
                    ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Error in borders command", e);
                }
            }

            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED));
                    return true;
                }

                Location savedLoc = ChunklockPlugin.getInstance().getPlayerDataManager().getChunkSpawn(player.getUniqueId());
                if (savedLoc != null) {
                    // Check for optional "center" argument
                    boolean forceCenter = args.length > 1 && args[1].equalsIgnoreCase("center");

                    if (forceCenter) {
                        // Teleport to exact center
                        Location centerLoc = ChunkUtils.getChunkCenter(savedLoc.getChunk());
                        player.teleport(centerLoc);
                        player.sendMessage(Component.text("Teleported to center of your starting chunk.").color(NamedTextColor.GREEN));
                    } else {
                        // Teleport to saved spawn location (may not be center)
                        player.teleport(savedLoc);
                        player.sendMessage(Component.text("Teleported to your starting chunk spawn point.").color(NamedTextColor.GREEN));
                        player.sendMessage(Component.text("Use '/chunklock spawn center' to go to the exact center.").color(NamedTextColor.GRAY));
                    }
                } else {
                    player.sendMessage(Component.text("No starting chunk recorded.").color(NamedTextColor.RED));
                }
            }

            case "team" -> {
                // Use enhanced team command handler if available, fallback to legacy
                if (teamCommandHandler != null) {
                    return teamCommandHandler.handleTeamCommand(sender, args);
                } else {
                    // Legacy team handling
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
            }

            case "help" -> {
                sender.sendMessage(Component.text("Chunklock Commands:").color(NamedTextColor.AQUA));
                sender.sendMessage(Component.text("/chunklock status - View your unlocked chunks").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock team - Team management commands").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock unlock - Attempt to unlock your current chunk").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock spawn - Return to your starting chunk center").color(NamedTextColor.GRAY));
                if (sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("=== Admin Commands ===").color(NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("/chunklock reset <player> - Admin: Complete reset (progress + chunks)").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock bypass [player] - Admin: Toggle bypass mode").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock reload - Admin: Reload plugin configuration").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock borders <refresh [player]|info|clear> - Admin: Manage glass borders").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock resetall confirm - Admin: Lock all chunks").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock debug - Admin: Debugging tools").color(NamedTextColor.GRAY));
                }
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }

    // Helper method for counting items (same as in UnlockGui)
    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        try {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.getType() == material) {
                    count += item.getAmount();
                }
            }
            
            // Also check off-hand
            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() == material) {
                count += offHand.getAmount();
            }
            
        } catch (Exception e) {
            ChunklockPlugin.getInstance().getLogger().warning("Error counting items for " + player.getName() + ": " + e.getMessage());
        }
        
        return count;
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

                    Location centerLocation = ChunkUtils.getChunkCenter(chunk);
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
            List<String> commands = new ArrayList<>(List.of("status", "reset", "bypass", "unlock", "spawn", "team", "help"));

            if (sender.hasPermission("chunklock.admin")) {
                commands.addAll(List.of("reload", "debug", "resetall", "borders", "bordertest", "borderdebug", "guidebug", "testunlock"));
            }

            for (String sub : commands) {
                if (sub.startsWith(prefix)) {
                    completions.add(sub);
                }
            }
            return completions;
        }

        // Handle enhanced team command tab completion
        if (args.length >= 2 && args[0].equalsIgnoreCase("team")) {
            if (teamCommandHandler != null) {
                return teamCommandHandler.getTabCompletions(sender, args);
            } else {
                // Legacy team tab completion
                if (args.length == 2) {
                    String prefix = args[1].toLowerCase();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(p.getName());
                        }
                    }
                }
            }
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();

            switch (subCommand) {
                case "bypass" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(p.getName());
                        }
                    }
                }
                case "debug" -> {
                    if ("list".startsWith(prefix)) {
                        completions.add("list");
                    }
                }
                case "resetall" -> {
                    if ("confirm".startsWith(prefix)) {
                        completions.add("confirm");
                    }
                }
                case "borders" -> {
                    List<String> borderCommands = List.of("refresh", "info", "clear");
                    for (String cmd : borderCommands) {
                        if (cmd.startsWith(prefix)) {
                            completions.add(cmd);
                        }
                    }
                }
                case "bordertest" -> {
                    List<String> testCommands = List.of("place", "clear", "check");
                    for (String cmd : testCommands) {
                        if (cmd.startsWith(prefix)) {
                            completions.add(cmd);
                        }
                    }
                }
                case "guidebug" -> {
                    if ("open".startsWith(prefix)) {
                        completions.add("open");
                    }
                }
                case "testunlock" -> {
                    List<String> unlockCommands = List.of("give", "force");
                    for (String cmd : unlockCommands) {
                        if (cmd.startsWith(prefix)) {
                            completions.add(cmd);
                        }
                    }
                }
                case "spawn" -> {
                    if ("center".startsWith(prefix)) {
                        completions.add("center");
                    }
                }
            }
        }
        
        if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            String action = args[1].toLowerCase();
            String prefix = args[2].toLowerCase();

            if (subCommand.equals("borders") && action.equals("refresh")) {
                // Tab complete player names for borders refresh command
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
            }
        }

        return completions;
    }
}