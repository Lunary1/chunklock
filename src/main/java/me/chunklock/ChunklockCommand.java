package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.Chunk;

import me.chunklock.UnlockGui;
import me.chunklock.TeamManager;

public class ChunklockCommand implements CommandExecutor, TabCompleter {

    private final PlayerProgressTracker progressTracker;
    private final ChunkLockManager chunkLockManager;
    private final UnlockGui unlockGui;
    private final TeamManager teamManager;

    public ChunklockCommand(PlayerProgressTracker progressTracker, ChunkLockManager chunkLockManager, UnlockGui unlockGui, TeamManager teamManager) {
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

                progressTracker.resetPlayer(target.getUniqueId());

                World world = target.getWorld();
                int border = (int) world.getWorldBorder().getSize() / 2;
                int min = (int) world.getWorldBorder().getCenter().getX() - border;
                int max = (int) world.getWorldBorder().getCenter().getX() + border;

                int randomX = min + (int) (Math.random() * (max - min));
                int randomZ = min + (int) (Math.random() * (max - min));
                int y = world.getHighestBlockYAt(randomX, randomZ);

                Location randomSpawn = new Location(world, randomX + 0.5, y + 1, randomZ + 0.5);
                target.teleport(randomSpawn);
                target.getInventory().clear(); // Clear inventory as part of reset
                target.setBedSpawnLocation(randomSpawn, true);

                sender.sendMessage(Component.text("Reset chunk progress and spawn for " + target.getName()).color(NamedTextColor.GREEN));
                target.sendMessage(Component.text("Your chunk progress and spawn were reset by an admin.").color(NamedTextColor.RED));
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
                Location loc = ChunklockPlugin.getInstance().getPlayerDataManager().getChunkSpawn(player.getUniqueId());
                if (loc != null) {
                    player.teleport(loc);
                    player.sendMessage(Component.text("Teleported to starting chunk.").color(NamedTextColor.GREEN));
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
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase();

            for (String sub : List.of("status", "reset", "bypass", "unlock", "spawn", "team", "help")) {
            for (String sub : List.of("status", "reset", "bypass", "unlock", "help")) {

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
