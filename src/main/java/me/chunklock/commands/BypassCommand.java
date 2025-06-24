package me.chunklock.commands;

import me.chunklock.managers.ChunkLockManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the bypass command - allows admins to toggle bypass mode for players.
 * Bypass mode allows players to move through locked chunks without restrictions.
 */
public class BypassCommand extends SubCommand {

    private final ChunkLockManager chunkLockManager;

    public BypassCommand(ChunkLockManager chunkLockManager) {
        super("bypass", "chunklock.bypass", false);
        this.chunkLockManager = chunkLockManager;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        // Determine target player
        Player target;
        if (args.length >= 1) {
            // Target specified
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' not found or not online.")
                    .color(NamedTextColor.RED));
                return true;
            }
        } else {
            // No target specified - must be a player using on themselves
            if (!(sender instanceof Player)) {
                sender.sendMessage(Component.text("Usage: /chunklock bypass <player>")
                    .color(NamedTextColor.YELLOW));
                return true;
            }
            target = (Player) sender;
        }

        // Toggle bypass mode
        boolean currentlyBypassing = chunkLockManager.isBypassing(target);
        chunkLockManager.setBypassing(target, !currentlyBypassing);

        // Send feedback messages
        if (!currentlyBypassing) {
            // Now bypassing
            target.sendMessage(Component.text("✓ Bypass mode ENABLED - you can now move through locked chunks!")
                .color(NamedTextColor.GREEN));
            
            if (!sender.equals(target)) {
                sender.sendMessage(Component.text("✓ Enabled bypass mode for " + target.getName())
                    .color(NamedTextColor.GREEN));
            }
        } else {
            // No longer bypassing
            target.sendMessage(Component.text("✗ Bypass mode DISABLED - normal chunk restrictions apply.")
                .color(NamedTextColor.YELLOW));
            
            if (!sender.equals(target)) {
                sender.sendMessage(Component.text("✗ Disabled bypass mode for " + target.getName())
                    .color(NamedTextColor.YELLOW));
            }
        }

        return true;
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
        return "/chunklock bypass [player]";
    }

    @Override
    public String getDescription() {
        return "Admin: Toggle bypass mode";
    }
}