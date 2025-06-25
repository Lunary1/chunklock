package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.managers.ChunkLockManager;
import me.chunklock.ui.UnlockGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Comprehensive debug command for troubleshooting all aspects of the Chunklock system.
 * Includes chunk debugging, border testing, GUI debugging, and unlock testing.
 */
public class DebugCommand extends SubCommand {

    private final ChunkLockManager chunkLockManager;
    private final BiomeUnlockRegistry biomeUnlockRegistry;
    private final UnlockGui unlockGui;

    public DebugCommand(ChunkLockManager chunkLockManager, BiomeUnlockRegistry biomeUnlockRegistry, UnlockGui unlockGui) {
        super("debug", "chunklock.admin", true);
        this.chunkLockManager = chunkLockManager;
        this.biomeUnlockRegistry = biomeUnlockRegistry;
        this.unlockGui = unlockGui;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        Player player = asPlayer(sender);
        if (player == null) {
            return false; // Should not happen due to requiresPlayer = true
        }

        if (args.length == 0) {
            showDebugHelp(player);
            return true;
        }

        String debugType = args[0].toLowerCase();
        
        switch (debugType) {
            case "chunk", "info" -> handleChunkDebug(player, args);
            case "bordertest" -> handleBorderTest(player, args);
            case "borderdebug" -> handleBorderDebug(player);
            case "guidebug" -> handleGuiDebug(player, args);
            case "testunlock" -> handleTestUnlock(player, args);
            case "list" -> handleListDebug(player);
            default -> showDebugHelp(player);
        }

        return true;
    }

    private void showDebugHelp(Player player) {
        player.sendMessage(Component.text("=== Debug Commands ===").color(NamedTextColor.AQUA));
        player.sendMessage(Component.text("/chunklock debug chunk - Current chunk info").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock debug list - List nearby chunks").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock debug bordertest <place|clear|check> - Test borders").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock debug borderdebug - Border system stats").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock debug guidebug [open] - GUI debugging").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("/chunklock debug testunlock [give|force] - Unlock testing").color(NamedTextColor.GRAY));
    }

    private void handleChunkDebug(Player player, String[] args) {
        try {
            Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);
            boolean isLocked = chunkLockManager.isLocked(currentChunk);
            UUID owner = chunkLockManager.getChunkOwner(currentChunk);
            
            player.sendMessage(Component.text("=== Chunk Debug Info ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Location: " + currentChunk.getX() + ", " + currentChunk.getZ())
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Biome: " + BiomeUnlockRegistry.getBiomeDisplayName(evaluation.biome))
                .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Difficulty: " + evaluation.difficulty)
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Score: " + evaluation.score)
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Status: " + (isLocked ? "LOCKED" : "UNLOCKED"))
                .color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN));
            player.sendMessage(Component.text("Owner: " + (owner != null ? owner.toString() : "None"))
                .color(NamedTextColor.GRAY));
                
            // Show unlock requirements if locked
            if (isLocked) {
                var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
                player.sendMessage(Component.text("Required: " + requirement.amount() + "x " + 
                    formatMaterialName(requirement.material())).color(NamedTextColor.YELLOW));
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in chunk debug: " + e.getMessage()).color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Chunk debug error", e);
        }
    }

    private void handleListDebug(Player player) {
        try {
            Chunk centerChunk = player.getLocation().getChunk();
            int range = 3; // 7x7 grid around player
            
            player.sendMessage(Component.text("=== Nearby Chunks (7x7) ===").color(NamedTextColor.AQUA));
            
            for (int dz = -range; dz <= range; dz++) {
                StringBuilder row = new StringBuilder();
                for (int dx = -range; dx <= range; dx++) {
                    try {
                        Chunk chunk = player.getWorld().getChunkAt(centerChunk.getX() + dx, centerChunk.getZ() + dz);
                        chunkLockManager.initializeChunk(chunk, player.getUniqueId());
                        
                        String symbol;
                        NamedTextColor color;
                        
                        if (dx == 0 && dz == 0) {
                            symbol = "◉"; // Player position
                            color = NamedTextColor.BLUE;
                        } else if (chunkLockManager.isLocked(chunk)) {
                            symbol = "▉"; // Locked
                            color = NamedTextColor.RED;
                        } else {
                            symbol = "□"; // Unlocked
                            color = NamedTextColor.GREEN;
                        }
                        
                        if (row.length() > 0) row.append(" ");
                        row.append(symbol);
                    } catch (Exception e) {
                        row.append("?");
                    }
                }
                player.sendMessage(Component.text(row.toString()).color(NamedTextColor.WHITE));
            }
            
            player.sendMessage(Component.text("Legend: ◉=You, ▉=Locked, □=Unlocked").color(NamedTextColor.GRAY));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in list debug: " + e.getMessage()).color(NamedTextColor.RED));
        }
    }

    private void handleBorderTest(Player player, String[] args) {
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /chunklock debug bordertest <place|clear|check>")
                    .color(NamedTextColor.YELLOW));
                return;
            }
            
            String action = args[1].toLowerCase();
            
            switch (action) {
                case "place" -> {
                    borderManager.scheduleBorderUpdate(player);
                    player.sendMessage(Component.text("✓ Forced border placement around your position.")
                        .color(NamedTextColor.GREEN));
                }
                
                case "clear" -> {
                    borderManager.removeBordersForPlayer(player);
                    player.sendMessage(Component.text("✓ Cleared all your borders.")
                        .color(NamedTextColor.GREEN));
                }
                
                case "check" -> {
                    Block targetBlock = player.getTargetBlockExact(5);
                    if (targetBlock == null) {
                        player.sendMessage(Component.text("Look at a block to check.")
                            .color(NamedTextColor.RED));
                        return;
                    }
                    
                    debugBorderBlock(player, targetBlock);
                }
                
                default -> {
                    player.sendMessage(Component.text("Usage: /chunklock debug bordertest <place|clear|check>")
                        .color(NamedTextColor.YELLOW));
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in border test: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Border test error", e);
        }
    }

    private void handleBorderDebug(Player player) {
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            var stats = borderManager.getBorderStats();
            
            player.sendMessage(Component.text("=== Border System Debug ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("System enabled: " + stats.get("enabled"))
                .color(NamedTextColor.YELLOW));
            player.sendMessage(Component.text("Border material: " + stats.get("borderMaterial"))
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Scan range: " + stats.get("scanRange"))
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Use full height: " + stats.get("useFullHeight"))
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Players with borders: " + stats.get("playersWithBorders"))
                .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Total border blocks: " + stats.get("totalBorderBlocks"))
                .color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("Update queue: " + stats.get("updateQueue"))
                .color(NamedTextColor.GRAY));
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in border debug: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Border debug error", e);
        }
    }

    private void debugBorderBlock(Player player, Block block) {
        try {
            ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
            Location location = block.getLocation();
            
            player.sendMessage(Component.text("=== Block Debug ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Location: " + location.getBlockX() + "," + 
                location.getBlockY() + "," + location.getBlockZ()).color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Block Type: " + block.getType()).color(NamedTextColor.WHITE));
            
            boolean isBorderBlock = borderManager.isBorderBlock(block);
            player.sendMessage(Component.text("Is Border Block: " + isBorderBlock)
                .color(isBorderBlock ? NamedTextColor.GREEN : NamedTextColor.RED));
            
            if (isBorderBlock) {
                Chunk borderChunk = borderManager.getBorderChunk(block);
                if (borderChunk != null) {
                    boolean isLocked = chunkLockManager.isLocked(borderChunk);
                    player.sendMessage(Component.text("Protected Chunk: " + borderChunk.getX() + "," + borderChunk.getZ())
                        .color(NamedTextColor.YELLOW));
                    player.sendMessage(Component.text("Chunk Status: " + (isLocked ? "LOCKED" : "UNLOCKED"))
                        .color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN));
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error debugging block: " + e.getMessage())
                .color(NamedTextColor.RED));
        }
    }

    private void handleGuiDebug(Player player, String[] args) {
        try {
            Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            boolean isLocked = chunkLockManager.isLocked(currentChunk);
            
            player.sendMessage(Component.text("=== GUI Debug ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Current chunk: " + currentChunk.getX() + "," + currentChunk.getZ())
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Chunk locked: " + isLocked)
                .color(isLocked ? NamedTextColor.RED : NamedTextColor.GREEN));
            
            // Show GUI stats if available
            try {
                var guiStats = unlockGui.getStats();
                player.sendMessage(Component.text("GUI State: " + guiStats.toString())
                    .color(NamedTextColor.GRAY));
            } catch (Exception e) {
                player.sendMessage(Component.text("Could not get GUI stats: " + e.getMessage())
                    .color(NamedTextColor.YELLOW));
            }
            
            // Test GUI opening
            if (args.length > 1 && args[1].equalsIgnoreCase("open")) {
                if (isLocked) {
                    player.sendMessage(Component.text("Opening unlock GUI for current chunk...")
                        .color(NamedTextColor.YELLOW));
                    unlockGui.open(player, currentChunk);
                } else {
                    player.sendMessage(Component.text("Current chunk is not locked")
                        .color(NamedTextColor.RED));
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in GUI debug: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "GUI debug error", e);
        }
    }

    private void handleTestUnlock(Player player, String[] args) {
        try {
            Chunk currentChunk = player.getLocation().getChunk();
            chunkLockManager.initializeChunk(currentChunk, player.getUniqueId());
            
            if (!chunkLockManager.isLocked(currentChunk)) {
                player.sendMessage(Component.text("Current chunk is already unlocked")
                    .color(NamedTextColor.RED));
                return;
            }
            
            var evaluation = chunkLockManager.evaluateChunk(player.getUniqueId(), currentChunk);
            var requirement = biomeUnlockRegistry.calculateRequirement(player, evaluation.biome, evaluation.score);
            
            player.sendMessage(Component.text("=== Test Unlock ===").color(NamedTextColor.AQUA));
            player.sendMessage(Component.text("Chunk: " + currentChunk.getX() + "," + currentChunk.getZ())
                .color(NamedTextColor.WHITE));
            player.sendMessage(Component.text("Required: " + requirement.amount() + "x " + 
                formatMaterialName(requirement.material())).color(NamedTextColor.YELLOW));
            
            if (args.length > 1) {
                String action = args[1].toLowerCase();
                switch (action) {
                    case "give" -> {
                        ItemStack items = new ItemStack(requirement.material(), requirement.amount());
                        player.getInventory().addItem(items);
                        player.sendMessage(Component.text("✓ Given required items")
                            .color(NamedTextColor.GREEN));
                    }
                    
                    case "force" -> {
                        UUID teamId = chunkLockManager.getTeamManager().getTeamLeader(player.getUniqueId());
                        chunkLockManager.unlockChunk(currentChunk, teamId);
                        player.sendMessage(Component.text("✓ Force unlocked chunk")
                            .color(NamedTextColor.GREEN));
                        
                        // Update borders
                        ChunkBorderManager borderManager = ChunklockPlugin.getInstance().getChunkBorderManager();
                        if (borderManager != null) {
                            borderManager.onChunkUnlocked(player, currentChunk);
                        }
                    }
                    
                    default -> {
                        player.sendMessage(Component.text("Usage: /chunklock debug testunlock [give|force]")
                            .color(NamedTextColor.YELLOW));
                    }
                }
            }
            
        } catch (Exception e) {
            player.sendMessage(Component.text("Error in test unlock: " + e.getMessage())
                .color(NamedTextColor.RED));
            ChunklockPlugin.getInstance().getLogger().log(Level.WARNING, "Test unlock error", e);
        }
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<String> debugTypes = List.of("chunk", "list", "bordertest", "borderdebug", "guidebug", "testunlock");
            for (String type : debugTypes) {
                if (type.startsWith(prefix)) {
                    completions.add(type);
                }
            }
        } else if (args.length == 2) {
            String debugType = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            
            switch (debugType) {
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
            }
        }
        
        return completions;
    }

    @Override
    public String getUsage() {
        return "/chunklock debug <chunk|list|bordertest|borderdebug|guidebug|testunlock>";
    }

    @Override
    public String getDescription() {
        return "Admin: Comprehensive debugging tools";
    }
}