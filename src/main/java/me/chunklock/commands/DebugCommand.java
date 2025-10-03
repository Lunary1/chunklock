package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.debug.HologramDebugDemo;
import me.chunklock.managers.ChunkLockManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Debug command for testing hologram functionality and world detection.
 */
public class DebugCommand extends SubCommand {
    
    public DebugCommand() {
        super("debug", "chunklock.admin", true);
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.")
                .color(NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showUsage(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "world":
                debugWorldDetection(player);
                break;
            case "holograms":
                debugHolograms(player);
                break;
            case "full":
                debugFull(player);
                break;
            case "fix-ownership":
                fixChunkOwnership(player);
                break;
            default:
                showUsage(player);
                break;
        }
        
        return true;
    }
    
    private void debugWorldDetection(Player player) {
        player.sendMessage(Component.text("=== World Detection Debug ===")
            .color(NamedTextColor.YELLOW));
        
        HologramDebugDemo debugDemo = new HologramDebugDemo(ChunklockPlugin.getInstance());
        debugDemo.demonstrateWorldDetection(player);
        
        player.sendMessage(Component.text("Check console for detailed debug output.")
            .color(NamedTextColor.GRAY));
    }
    
    private void debugHolograms(Player player) {
        player.sendMessage(Component.text("=== Hologram Eligibility Debug ===")
            .color(NamedTextColor.YELLOW));
        
        HologramDebugDemo debugDemo = new HologramDebugDemo(ChunklockPlugin.getInstance());
        debugDemo.demonstrateEligibilityFix(player);
        
        player.sendMessage(Component.text("Check console for detailed debug output.")
            .color(NamedTextColor.GRAY));
    }
    
    private void debugFull(Player player) {
        player.sendMessage(Component.text("=== Full Hologram Debug ===")
            .color(NamedTextColor.YELLOW));
        
        HologramDebugDemo debugDemo = new HologramDebugDemo(ChunklockPlugin.getInstance());
        debugDemo.runFullDemo(player);
        
        player.sendMessage(Component.text("Check console for detailed debug output.")
            .color(NamedTextColor.GRAY));
    }
    
    private void fixChunkOwnership(Player player) {
        player.sendMessage(Component.text("=== Fixing Chunk Ownership ===")
            .color(NamedTextColor.YELLOW));
        
        ChunkLockManager chunkLockManager = ChunklockPlugin.getInstance().getChunkLockManager();
        UUID playerId = player.getUniqueId();
        Chunk currentChunk = player.getLocation().getChunk();
        
        // Check current chunk ownership
        boolean isLocked = chunkLockManager.isLocked(currentChunk);
        UUID owner = chunkLockManager.getChunkOwner(currentChunk);
        
        player.sendMessage(Component.text("Current chunk (" + currentChunk.getX() + ", " + currentChunk.getZ() + "):")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Locked: " + isLocked)
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Owner: " + (owner != null ? owner.toString() : "null"))
            .color(NamedTextColor.GRAY));
            
        // If chunk is unlocked but has no owner, assign it to the player
        if (!isLocked && owner == null) {
            chunkLockManager.unlockChunk(currentChunk, playerId);
            player.sendMessage(Component.text("✅ Fixed ownership: Assigned current chunk to you")
                .color(NamedTextColor.GREEN));
        } else if (!isLocked && playerId.equals(owner)) {
            player.sendMessage(Component.text("✅ Chunk ownership is already correct")
                .color(NamedTextColor.GREEN));
        } else if (isLocked) {
            player.sendMessage(Component.text("⚠ Chunk is locked - cannot fix ownership")
                .color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("⚠ Chunk is owned by someone else")
                .color(NamedTextColor.YELLOW));
        }
    }
    
    private void showUsage(Player player) {
        player.sendMessage(Component.text("Usage: /chunklock debug <world|holograms|full|fix-ownership>")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  world - Test world detection")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  holograms - Test hologram eligibility")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  full - Run full debug suite")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  fix-ownership - Fix chunk ownership issues")
            .color(NamedTextColor.GRAY));
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("world", "holograms", "full", "fix-ownership");
        }
        return List.of();
    }
    
    @Override
    public String getDescription() {
        return "Debug commands for testing hologram functionality";
    }
    
    @Override
    public String getUsage() {
        return "/chunklock debug <world|holograms|full|fix-ownership>";
    }
}