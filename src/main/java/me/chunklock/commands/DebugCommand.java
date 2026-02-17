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
            case "deps":
            case "dependencies":
                debugDependencies(player);
                break;
            case "vault":
                testVaultIntegration(player);
                break;
            case "db":
            case "database":
                debugDatabase(player);
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
    
    private void debugDependencies(Player player) {
        player.sendMessage(Component.text("=== Dependency Check ===")
            .color(NamedTextColor.YELLOW));
        
        try {
            ChunklockPlugin.getInstance().getDependencyChecker().checkAndLogDependencies();
            player.sendMessage(Component.text("✅ Dependency check completed - check console for details")
                .color(NamedTextColor.GREEN));
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Error during dependency check: " + e.getMessage())
                .color(NamedTextColor.RED));
        }
    }
    
    private void testVaultIntegration(Player player) {
        player.sendMessage(Component.text("=== Vault Integration Test ===")
            .color(NamedTextColor.YELLOW));
        
        try {
            boolean isWorking = ChunklockPlugin.getInstance().getDependencyChecker().testVaultIntegration();
            
            if (isWorking) {
                player.sendMessage(Component.text("✅ Vault integration is working properly")
                    .color(NamedTextColor.GREEN));
                
                // Test balance retrieval if player is involved
                var economyManager = ChunklockPlugin.getInstance().getEconomyManager();
                if (economyManager != null && economyManager.isVaultAvailable()) {
                    double balance = economyManager.getVaultService().getBalance(player);
                    String formattedBalance = economyManager.getVaultService().format(balance);
                    player.sendMessage(Component.text("Your current balance: " + formattedBalance)
                        .color(NamedTextColor.GRAY));
                }
            } else {
                player.sendMessage(Component.text("❌ Vault integration is not working - check console for details")
                    .color(NamedTextColor.RED));
            }
        } catch (Exception e) {
            player.sendMessage(Component.text("❌ Error testing Vault integration: " + e.getMessage())
                .color(NamedTextColor.RED));
        }
    }
    
    private void debugDatabase(Player player) {
        player.sendMessage(Component.text("=== Database Debug ===")
            .color(NamedTextColor.YELLOW));
        
        ChunklockPlugin plugin = ChunklockPlugin.getInstance();
        String storageType = plugin.getConfigManager().getDatabaseConfig().getType();
        boolean isMySql = "mysql".equalsIgnoreCase(storageType);
        
        player.sendMessage(Component.text("Storage Backend: " + storageType.toUpperCase())
            .color(isMySql ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        
        if (isMySql) {
            var provider = plugin.getMySqlConnectionProvider();
            if (provider != null) {
                try (var connection = provider.getConnection()) {
                    long start = System.currentTimeMillis();
                    connection.createStatement().execute("SELECT 1");
                    long queryTime = System.currentTimeMillis() - start;
                    
                    player.sendMessage(Component.text("✓ MySQL Connection: Active")
                        .color(NamedTextColor.GREEN));
                    player.sendMessage(Component.text("  Query Response: " + queryTime + "ms")
                        .color(queryTime < 50 ? NamedTextColor.GREEN : 
                               queryTime < 200 ? NamedTextColor.YELLOW : NamedTextColor.RED));
                    
                    // Get pool stats
                    try {
                        java.lang.reflect.Field dsField = provider.getClass().getDeclaredField("dataSource");
                        dsField.setAccessible(true);
                        com.zaxxer.hikari.HikariDataSource dataSource = 
                            (com.zaxxer.hikari.HikariDataSource) dsField.get(provider);
                        
                        if (dataSource != null) {
                            var poolBean = dataSource.getHikariPoolMXBean();
                            player.sendMessage(Component.text("  Active Connections: " + poolBean.getActiveConnections())
                                .color(NamedTextColor.GRAY));
                            player.sendMessage(Component.text("  Idle Connections: " + poolBean.getIdleConnections())
                                .color(NamedTextColor.GRAY));
                            player.sendMessage(Component.text("  Total Connections: " + poolBean.getTotalConnections())
                                .color(NamedTextColor.GRAY));
                            
                            if (poolBean.getThreadsAwaitingConnection() > 0) {
                                player.sendMessage(Component.text("  ⚠ Threads Waiting: " + 
                                    poolBean.getThreadsAwaitingConnection())
                                    .color(NamedTextColor.RED));
                            }
                        }
                    } catch (Exception e) {
                        player.sendMessage(Component.text("  (Pool stats unavailable)")
                            .color(NamedTextColor.YELLOW));
                    }
                    
                } catch (Exception e) {
                    player.sendMessage(Component.text("✗ MySQL Connection Failed: " + e.getMessage())
                        .color(NamedTextColor.RED));
                }
            } else {
                player.sendMessage(Component.text("✗ MySQL provider not initialized")
                    .color(NamedTextColor.RED));
            }
        } else {
            // MapDB mode
            java.io.File chunksDb = new java.io.File(plugin.getDataFolder(), "chunks.db");
            java.io.File playersDb = new java.io.File(plugin.getDataFolder(), "players.db");
            
            player.sendMessage(Component.text("  Chunks DB: " + 
                (chunksDb.exists() ? formatFileSize(chunksDb.length()) : "Not Found"))
                .color(chunksDb.exists() ? NamedTextColor.GREEN : NamedTextColor.RED));
            player.sendMessage(Component.text("  Players DB: " + 
                (playersDb.exists() ? formatFileSize(playersDb.length()) : "Not Found"))
                .color(playersDb.exists() ? NamedTextColor.GREEN : NamedTextColor.RED));
        }
        
        player.sendMessage(Component.text("For full database info, use: /chunklock database")
            .color(NamedTextColor.GRAY));
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private void showUsage(Player player) {
        player.sendMessage(Component.text("Usage: /chunklock debug <world|holograms|full|fix-ownership|deps|vault|database>")
            .color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("  world - Test world detection")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  holograms - Test hologram eligibility")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  full - Run full debug suite")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  fix-ownership - Fix chunk ownership issues")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  deps - Check plugin dependencies")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  vault - Test Vault economy integration")
            .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("  database - Check database status and connection")
            .color(NamedTextColor.GRAY));
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("world", "holograms", "full", "fix-ownership", "deps", "dependencies", "vault", "database", "db");
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