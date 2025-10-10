package me.chunklock.hologram.debug;

import me.chunklock.ChunklockPlugin;
import me.chunklock.hologram.util.HologramLocationUtils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Debug command to test and verify hologram side resolution.
 * Usage: /hologram-debug-sides
 */
public class HologramSideDebugCommand implements CommandExecutor {
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        
        Player player = (Player) sender;
        Chunk chunk = player.getLocation().getChunk();
        
        player.sendMessage("§6=== Hologram Side Debug for Chunk " + chunk.getX() + "," + chunk.getZ() + " ===");
        
        // Test all four sides
        for (HologramLocationUtils.WallSide side : HologramLocationUtils.WallSide.getOrderedSides()) {
            Location location = HologramLocationUtils.calculateWallHologramLocation(
                chunk, side, 1.0, 8.0, 2.0, 64);
            
            String message = String.format("§a%s: §7X=%.2f Z=%.2f Y=%.2f", 
                side.name(), location.getX(), location.getZ(), location.getY());
            player.sendMessage(message);
            
            // Log to console for debugging
            ChunklockPlugin.getInstance().getLogger().info("Side " + side.name() + 
                " for chunk(" + chunk.getX() + "," + chunk.getZ() + "): " +
                "location=(" + String.format("%.2f", location.getX()) + "," + 
                String.format("%.2f", location.getY()) + "," + 
                String.format("%.2f", location.getZ()) + ")");
        }
        
        // Verify chunk boundaries
        int chunkMinX = chunk.getX() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() * 16;
        int chunkMaxZ = chunkMinZ + 15;
        
        player.sendMessage("§e=== Chunk Boundaries ===");
        player.sendMessage("§7X range: " + chunkMinX + " to " + chunkMaxX);
        player.sendMessage("§7Z range: " + chunkMinZ + " to " + chunkMaxZ);
        
        return true;
    }
}
