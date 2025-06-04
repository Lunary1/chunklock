package me.chunklock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChunklockCommand implements CommandExecutor {

    private final PlayerProgressTracker progressTracker;

    public ChunklockCommand(PlayerProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /chunklock <status|reset|help>").color(NamedTextColor.YELLOW));
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

            case "help" -> {
                sender.sendMessage(Component.text("Chunklock Commands:").color(NamedTextColor.AQUA));
                sender.sendMessage(Component.text("/chunklock status - View your unlocked chunks").color(NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/chunklock reset <player> - Admin: Reset a player's chunks and spawn").color(NamedTextColor.GRAY));
            }

            default -> {
                sender.sendMessage(Component.text("Unknown subcommand. Use /chunklock help for options.").color(NamedTextColor.RED));
            }
        }

        return true;
    }
}
