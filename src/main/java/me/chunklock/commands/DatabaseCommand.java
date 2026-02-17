package me.chunklock.commands;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import me.chunklock.ChunklockPlugin;
import me.chunklock.services.MySqlConnectionProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Database command for checking storage backend status and MySQL connection details.
 * Provides administrators with visibility into database operations.
 */
public class DatabaseCommand extends SubCommand {
    
    public DatabaseCommand() {
        super("database", "chunklock.admin", false);
    }
    
    @Override
    public boolean execute(CommandSender sender, String[] args) {
        ChunklockPlugin plugin = ChunklockPlugin.getInstance();
        
        sender.sendMessage(Component.text("=== Chunklock Database Status ===")
            .color(NamedTextColor.GOLD));
        sender.sendMessage(Component.empty());
        
        // Storage backend type
        String storageType = plugin.getConfigManager().getDatabaseConfig().getType();
        boolean isMySql = "mysql".equalsIgnoreCase(storageType);
        
        sender.sendMessage(Component.text("Storage Backend: ")
            .color(NamedTextColor.GRAY)
            .append(Component.text(storageType.toUpperCase())
                .color(isMySql ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
        
        if (isMySql) {
            showMySqlStatus(sender, plugin);
        } else {
            showMapDbStatus(sender, plugin);
        }
        
        sender.sendMessage(Component.empty());
        
        return true;
    }
    
    private void showMySqlStatus(CommandSender sender, ChunklockPlugin plugin) {
        MySqlConnectionProvider provider = plugin.getMySqlConnectionProvider();
        
        if (provider == null) {
            sender.sendMessage(Component.text("✗ MySQL provider not initialized")
                .color(NamedTextColor.RED));
            return;
        }
        
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("MySQL Configuration:")
            .color(NamedTextColor.AQUA));
        
        var config = plugin.getConfigManager().getDatabaseConfig();
        sender.sendMessage(Component.text("  Host: " + config.getMySqlHost() + ":" + config.getMySqlPort())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Database: " + config.getMySqlDatabase())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  Username: " + config.getMySqlUsername())
            .color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  SSL: " + (config.isMySqlUseSsl() ? "Enabled" : "Disabled"))
            .color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Connection Pool:")
            .color(NamedTextColor.AQUA));
        
        // Test connection and get pool stats
        try (Connection connection = provider.getConnection()) {
            sender.sendMessage(Component.text("✓ Connection Status: Active")
                .color(NamedTextColor.GREEN));
            
            // Get HikariCP pool statistics
            try {
                java.lang.reflect.Field dsField = provider.getClass().getDeclaredField("dataSource");
                dsField.setAccessible(true);
                HikariDataSource dataSource = (HikariDataSource) dsField.get(provider);
                
                if (dataSource != null) {
                    HikariPoolMXBean poolBean = dataSource.getHikariPoolMXBean();
                    
                    sender.sendMessage(Component.text("  Active Connections: " + poolBean.getActiveConnections())
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Idle Connections: " + poolBean.getIdleConnections())
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Total Connections: " + poolBean.getTotalConnections())
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Threads Awaiting Connection: " + poolBean.getThreadsAwaitingConnection())
                        .color(NamedTextColor.GRAY));
                    
                    sender.sendMessage(Component.empty());
                    sender.sendMessage(Component.text("Pool Configuration:")
                        .color(NamedTextColor.AQUA));
                    sender.sendMessage(Component.text("  Max Pool Size: " + config.getMySqlPoolMaxSize())
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Min Idle: " + config.getMySqlPoolMinIdle())
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Connection Timeout: " + config.getMySqlPoolConnectionTimeoutMs() + "ms")
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Idle Timeout: " + config.getMySqlPoolIdleTimeoutMs() + "ms")
                        .color(NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("  Max Lifetime: " + config.getMySqlPoolMaxLifetimeMs() + "ms")
                        .color(NamedTextColor.GRAY));
                }
            } catch (Exception e) {
                sender.sendMessage(Component.text("  (Pool statistics unavailable)")
                    .color(NamedTextColor.YELLOW));
            }
            
            // Test query performance
            long startTime = System.currentTimeMillis();
            connection.createStatement().execute("SELECT 1");
            long queryTime = System.currentTimeMillis() - startTime;
            
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("Performance:")
                .color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  Query Response Time: " + queryTime + "ms")
                .color(queryTime < 50 ? NamedTextColor.GREEN : 
                       queryTime < 200 ? NamedTextColor.YELLOW : NamedTextColor.RED));
            
        } catch (SQLException e) {
            sender.sendMessage(Component.text("✗ Connection Failed: " + e.getMessage())
                .color(NamedTextColor.RED));
            sender.sendMessage(Component.text("  Check your database configuration in database.yml")
                .color(NamedTextColor.YELLOW));
        }
        
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Cache:")
            .color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  Cache TTL: " + config.getMySqlCacheTtlMs() + "ms")
            .color(NamedTextColor.GRAY));
        
        // Migration status
        java.io.File migrationMarker = new java.io.File(plugin.getDataFolder(), ".mysql_migration_completed");
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Migration:")
            .color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  MapDB to MySQL: " + (migrationMarker.exists() ? "Completed" : "Not Yet Run"))
            .color(migrationMarker.exists() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }
    
    private void showMapDbStatus(CommandSender sender, ChunklockPlugin plugin) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("MapDB Configuration:")
            .color(NamedTextColor.AQUA));
        
        java.io.File chunksDb = new java.io.File(plugin.getDataFolder(), "chunks.db");
        java.io.File playersDb = new java.io.File(plugin.getDataFolder(), "players.db");
        
        sender.sendMessage(Component.text("  Chunks Database: " + 
            (chunksDb.exists() ? formatFileSize(chunksDb.length()) : "Not Created"))
            .color(chunksDb.exists() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        
        sender.sendMessage(Component.text("  Players Database: " + 
            (playersDb.exists() ? formatFileSize(playersDb.length()) : "Not Created"))
            .color(playersDb.exists() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Performance:")
            .color(NamedTextColor.AQUA));
        sender.sendMessage(Component.text("  In-Memory Cache: 5 minute TTL")
            .color(NamedTextColor.GRAY));
        
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Note: ")
            .color(NamedTextColor.YELLOW)
            .append(Component.text("To switch to MySQL, configure database.yml")
                .color(NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("      and reload or restart the server.")
            .color(NamedTextColor.GRAY));
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
    
    @Override
    public String getUsage() {
        return "/chunklock database - Show database status and connection details";
    }
    
    @Override
    public String getDescription() {
        return "View storage backend status, MySQL connection pool details, and performance metrics";
    }
}
