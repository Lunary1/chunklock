package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import me.chunklock.teams.BasicTeamCommandHandler;

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

                // IMPORTANT: Reset chunks in world (re-lock all except new starting chunk)
                chunkLockManager.resetPlayerChunks(target.getUniqueId(), newChunk);

                // Set up player data
                ChunklockPlugin.getInstance().getPlayerDataManager().setChunk(target.getUniqueId(), centerSpawn);

                // Teleport to center and clear inventory
                target.teleport(centerSpawn);
                target.getInventory().clear();
                target.setRespawnLocation(centerSpawn, true);

                // Get stats after reset
                int unlockedAfter = chunkLockManager.getTotalUnlockedChunks();
                ChunkEvaluator.ChunkValueData evaluation = chunkLockManager.evaluateChunk(target.getUniqueId(), newChunk);

                // Provide detailed feedback
                sender.sendMessage(Component.text("✓ Reset completed successfully!").color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Player progress reset for " + target.getName()).color(NamedTextColor.GREEN));
                sender.sendMessage(Component.text("Chunks locked: " + (unlockedBefore - unlockedAfter) +
                        " (from " + unlockedBefore + " to " + unlockedAfter + ")").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ() +
                        " (Score: " + evaluation.score + ", Difficulty: " + evaluation.difficulty + ")").color(NamedTextColor.GRAY));

                target.sendMessage(Component.text("Your progress has been completely reset by an admin.").color(NamedTextColor.RED));
                target.sendMessage(Component.text("All previously unlocked chunks have been locked again.").color(NamedTextColor.RED));
                target.sendMessage(Component.text("New starting chunk: " + newChunk.getX() + ", " + newChunk.getZ()).color(NamedTextColor.GREEN));
                target.sendMessage(Component.text("Spawning at center: " + (int) centerSpawn.getX() + ", " + (int) centerSpawn.getZ()).color(NamedTextColor.GRAY));

                // Regenerate borders for the player immediately
                ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                if (borderManager != null) {
                    borderManager.updateBordersForPlayer(target);
                }
            }

            case "debug" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }

                int totalUnlocked = chunkLockManager.getTotalUnlockedChunks();
                Set<String> unlockedChunks = chunkLockManager.getUnlockedChunks();

                sender.sendMessage(Component.text("=== Chunk Debug Info ===").color(NamedTextColor.AQUA));
                sender.sendMessage(Component.text("Total unlocked chunks in world: " + totalUnlocked).color(NamedTextColor.YELLOW));

                // Show team system debug info
                if (teamCommandHandler != null) {
                    try {
                        var enhancedTeamManager = ChunklockPlugin.getInstance().getEnhancedTeamManager();
                        var allTeams = enhancedTeamManager.getAllTeams();
                        sender.sendMessage(Component.text("Total teams: " + allTeams.size()).color(NamedTextColor.YELLOW));
                        
                        int totalTeamMembers = allTeams.stream().mapToInt(team -> team.getTotalMembers()).sum();
                        int totalTeamChunks = allTeams.stream().mapToInt(team -> team.getTotalChunksUnlocked()).sum();
                        sender.sendMessage(Component.text("Total team members: " + totalTeamMembers).color(NamedTextColor.YELLOW));
                        sender.sendMessage(Component.text("Total team chunks unlocked: " + totalTeamChunks).color(NamedTextColor.YELLOW));
                    } catch (Exception e) {
                        sender.sendMessage(Component.text("Team debug info not available").color(NamedTextColor.GRAY));
                    }
                }

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

                sender.sendMessage(Component.text("WARNING: This will lock ALL chunks! Type '/chunklock resetall confirm' to proceed.").color(NamedTextColor.RED));

                if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                    int before = chunkLockManager.getTotalUnlockedChunks();
                    chunkLockManager.resetAllChunks();
                    sender.sendMessage(Component.text("Locked " + before + " chunks. All chunks are now locked.").color(NamedTextColor.GREEN));
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

            // 🔧 NEW: Border testing commands
            case "bordertest" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can test borders.").color(NamedTextColor.RED));
                    return true;
                }
                
                handleBorderTest(player, args);
            }

            case "borderdebug" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can debug borders.").color(NamedTextColor.RED));
                    return true;
                }
                
                handleBorderDebug(player);
            }

            // 🔧 NEW: GUI debugging commands
            case "guidebug" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can debug GUI.").color(NamedTextColor.RED));
                    return true;
                }
                
                handleGuiDebug(player, args);
            }

            case "testunlock" -> {
                if (!sender.hasPermission("chunklock.admin")) {
                    sender.sendMessage(Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                    return true;
                }
                
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can test unlock.").color(NamedTextColor.RED));
                    return true;
                }
                
                handleTestUnlock(player, args);
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
                        Location centerLoc = getCenterLocationOfChunk(savedLoc.getChunk());
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
                    sender.sendMessage(Component.text("/chunklock debug [list] - Admin: Show chunk debug info").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock borders <refresh [player]|info|clear> - Admin: Manage glass borders").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock resetall confirm - Admin: Lock all chunks").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("=== Debug Commands ===").color(NamedTextColor.YELLOW));
                    sender.sendMessage(Component.text("/chunklock bordertest <place|clear|check> - Test border system").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock borderdebug - Debug border state").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock guidebug [open] - Debug unlock GUI").color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/chunklock testunlock [give|force] - Test unlock system").color(NamedTextColor.GRAY));
                }
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }

    // 🔧 NEW: Border testing methods
    private void handleBorderTest(Player player, String[] args) {
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /chunklock bordertest <place|clear|check>").color(NamedTextColor.YELLOW));
                return;
            }
            
            String action = args[1].toLowerCase();
            
            switch (action) {
                case "place" -> {
                    // Force place borders for testing
                    borderManager.scheduleBorderUpdate(player);
                    player.sendMessage(Component.text("✓ Forced border placement around your position.").color(NamedTextColor.GREEN));
                }
                
                case "clear" -> {
                    // Clear all borders for player
                    borderManager.removeBordersForPlayer(player);
                    player.sendMessage(Component.text("✓ Cleared all your borders.").color(NamedTextColor.GREEN));
                }
                
                case "check" -> {
                    // Check block player is looking at
                    Block targetBlock = player.getTargetBlockExact(5);
                    if (targetBlock == null) {
                        player.sendMessage(Component.text("Look at a block to check.").color(NamedTextColor.RED));
                        return;
                    }
                    
                    debugBorderBlock(player, targetBlock);
                }
                
                default -> {
                    player.sendMessage(Component.text("Usage: /chunklock bordertest <place|clear|check>").color(NamedTextColor.YELLOW));
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in border test: " + e.getMessage()).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Border test error", e);
        }
    }

    private void handleBorderDebug(Player player) {
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            var stats = borderManager.getBorderStats();
            
            player.sendMessage(Component.text("=== Border System Debug ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("System enabled: " + stats.get("enabled")).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Border material: " + stats.get("borderMaterial")).color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Your UUID: " + player.getUniqueId()).color(NamedTextColor.GRAY));
            
            // Check if player has borders using reflection
            try {
                java.lang.reflect.Field playerBordersField = borderManager.getClass().getDeclaredField("playerBorders");
                playerBordersField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<UUID, java.util.Map<org.bukkit.Location, org.bukkit.block.data.BlockData>> playerBorders = 
                    (java.util.Map<UUID, java.util.Map<org.bukkit.Location, org.bukkit.block.data.BlockData>>) playerBordersField.get(borderManager);
                
                UUID playerId = player.getUniqueId();
                if (playerBorders.containsKey(playerId)) {
                    int borderCount = playerBorders.get(playerId).size();
                    player.sendMessage(Component.text("Your border blocks: " + borderCount).color(NamedTextColor.GREEN));
                    
                    // Show first few border locations
                    int shown = 0;
                    for (org.bukkit.Location loc : playerBorders.get(playerId).keySet()) {
                        if (shown >= 5) break;
                        player.sendMessage(Component.text("  - " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ())
                            .color(NamedTextColor.WHITE));
                        shown++;
                    }
                    if (borderCount > 5) {
                        player.sendMessage(Component.text("  ... and " + (borderCount - 5) + " more").color(NamedTextColor.GRAY));
                    }
                } else {
                    player.sendMessage(Component.text("You have no border blocks").color(NamedTextColor.RED));
                }
            } catch (Exception e) {
                player.sendMessage(Component.text("Could not access border data: " + e.getMessage()).color(NamedTextColor.RED));
            }
            
            // Check current chunk status
            org.bukkit.Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            boolean isLocked = chunkLockManager.isLocked(currentChunk);
            
            player.sendMessage(Component.text("Current chunk (" + currentChunk.getX() + "," + currentChunk.getZ() + "): " + 
                (isLocked ? "LOCKED" : "UNLOCKED")).color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in border debug: " + e.getMessage()).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Border debug error", e);
        }
    }

    private void debugBorderBlock(Player player, Block block) {
        ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
        org.bukkit.Location location = block.getLocation();
        
        player.sendMessage(Component.text("=== Block Debug ===").color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("Location: " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ())
            .color(NamedTextColor.WHITE));
        player.sendMessage(Component.text("Block Type: " + block.getType()).color(NamedTextColor.WHITE));
        
        try {
            // Check border material using reflection
            java.lang.reflect.Field borderMaterialField = borderManager.getClass().getDeclaredField("borderMaterial");
            borderMaterialField.setAccessible(true);
            org.bukkit.Material expectedMaterial = (org.bukkit.Material) borderMaterialField.get(borderManager);
            
            player.sendMessage(Component.text("Expected Material: " + expectedMaterial).color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Material Match: " + (block.getType() == expectedMaterial))
                .color(block.getType() == expectedMaterial ? NamedTextColor.GREEN : NamedTextColor.RED));
            
            // Check chunk mapping
            java.lang.reflect.Field borderToChunkField = borderManager.getClass().getDeclaredField("borderToChunk");
            borderToChunkField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<org.bukkit.Location, Object> borderToChunk = 
                (java.util.Map<org.bukkit.Location, Object>) borderToChunkField.get(borderManager);
            
            Object chunkCoord = borderToChunk.get(location);
            player.sendMessage(Component.text("Chunk Mapping: " + (chunkCoord != null ? chunkCoord.toString() : "NONE"))
                .color(chunkCoord != null ? NamedTextColor.GREEN : NamedTextColor.RED));
            
            // Check if any player has this border
            java.lang.reflect.Field playerBordersField = borderManager.getClass().getDeclaredField("playerBorders");
            playerBordersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<java.util.UUID, java.util.Map<org.bukkit.Location, org.bukkit.block.data.BlockData>> playerBorders = 
                (java.util.Map<java.util.UUID, java.util.Map<org.bukkit.Location, org.bukkit.block.data.BlockData>>) playerBordersField.get(borderManager);
            
            int playersWithBorder = 0;
            for (java.util.Map<org.bukkit.Location, org.bukkit.block.data.BlockData> borders : playerBorders.values()) {
                if (borders.containsKey(location)) {
                    playersWithBorder++;
                }
            }
            
            player.sendMessage(Component.text("Players with this border: " + playersWithBorder)
                .color(playersWithBorder > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error accessing border data: " + e.getMessage()).color(NamedTextColor.RED));
        }
    }

    // 🔧 NEW: GUI debugging methods
    private void handleGuiDebug(Player player, String[] args) {
        try {
            player.sendMessage(Component.text("=== UnlockGui Debug ===").color(NamedTextColor.AQUA));
            
            // Check current chunk status
            Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            boolean isLocked = chunkLockManager.isLocked(currentChunk);
            
            player.sendMessage(Component.text("Current chunk locked: " + isLocked)
                .color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN));
            
            if (isLocked) {
                var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);
                // 🔧 FIX: Use stored biomeUnlockRegistry field instead of getInstance()
                var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
                
                player.sendMessage(Component.text("Required: " + requirement.amount() + "x " + requirement.material())
                    .color(NamedTextColor.YELLOW));
                
                int playerHas = countPlayerItems(player, requirement.material());
                player.sendMessage(Component.text("You have: " + playerHas)
                    .color(playerHas >= requirement.amount() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            
            // Test GUI opening
            if (args.length > 1 && args[1].equalsIgnoreCase("open")) {
                if (isLocked) {
                    player.sendMessage(Component.text("Opening unlock GUI for current chunk...").color(NamedTextColor.YELLOW));
                    unlockGui.open(player, currentChunk);
                } else {
                    player.sendMessage(Component.text("Current chunk is not locked").color(NamedTextColor.RED));
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in GUI debug: " + e.getMessage()).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "GUI debug error", e);
        }
    }

    private void handleTestUnlock(Player player, String[] args) {
        try {
            Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            
            if (!chunkLockManager.isLocked(currentChunk)) {
                player.sendMessage(Component.text("Current chunk is already unlocked").color(NamedTextColor.RED));
                return;
            }
            
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);
            // 🔧 FIX: Use stored biomeUnlockRegistry field instead of getInstance()
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            
            player.sendMessage(Component.text("=== Test Unlock ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Chunk: " + currentChunk.getX() + "," + currentChunk.getZ()).color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Required: " + requirement.amount() + "x " + requirement.material()).color(NamedTextColor.YELLOW));
            
            if (args.length > 1 && args[1].equalsIgnoreCase("give")) {
                // Give required items
                ItemStack items = new ItemStack(requirement.material(), requirement.amount());
                player.getInventory().addItem(items);
                player.sendMessage(Component.text("✓ Given required items").color(NamedTextColor.GREEN));
                
            } else if (args.length > 1 && args[1].equalsIgnoreCase("force")) {
                // Force unlock without items
                UUID teamId = chunkLockManager.getTeamManager().getTeamLeader(player.getUniqueId());
                chunkLockManager.unlockChunk(currentChunk, teamId);
                progressTracker.incrementUnlockedChunks(player.getUniqueId());
                
                player.sendMessage(Component.text("✓ Force unlocked chunk").color(NamedTextColor.GREEN));
                
                // Update systems
                try {
                    ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                    if (borderManager != null) {
                        borderManager.onChunkUnlocked(player, currentChunk);
                    }
                } catch (Exception e) {
                    player.sendMessage(Component.text("Warning: Border update failed").color(NamedTextColor.YELLOW));
                }
                
            } else {
                int playerHas = countPlayerItems(player, requirement.material());
                player.sendMessage(Component.text("You have: " + playerHas).color(NamedTextColor.WHITE));
                
                if (playerHas >= requirement.amount()) {
                    player.sendMessage(Component.text("✓ You have enough items!").color(NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("✗ Need " + (requirement.amount() - playerHas) + " more").color(NamedTextColor.RED));
                }
                
                player.sendMessage(Component.text("Use '/chunklock testunlock give' to get items").color(NamedTextColor.GRAY));
                player.sendMessage(Component.text("Use '/chunklock testunlock force' to force unlock").color(NamedTextColor.GRAY));
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in test unlock: " + e.getMessage()).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Test unlock error", e);
        }
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
                case "reset", "bypass" -> {
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