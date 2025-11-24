// src/main/java/me/chunklock/commands/ReloadCommand.java
package me.chunklock.commands;

import me.chunklock.ChunklockPlugin;
import me.chunklock.config.LanguageKeys;
import me.chunklock.managers.ChunkBorderManager;
import me.chunklock.util.message.MessageUtil;
import me.chunklock.util.validation.ConfigValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles the reload command - allows admins to reload plugin configuration and components.
 * Performs a hot reload without restarting the server.
 */
public class ReloadCommand extends SubCommand {

    public ReloadCommand() {
        super("reload", "chunklock.admin", false);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                .color(NamedTextColor.RED));
            return true;
        }

        // Warn about potential risks of hot reloading
        if (args.length > 0 && args[0].equalsIgnoreCase("force")) {
            performReload(sender);
        } else {
            sender.sendMessage(Component.text("⚠️ Plugin reload will restart all systems!")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("This may cause temporary interruptions for players.")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Glass borders will be temporarily removed and recreated.")
                .color(NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Use '/chunklock reload force' to proceed.")
                .color(NamedTextColor.YELLOW));
        }

        return true;
    }

    /**
     * Performs the actual reload operation with enhanced border restoration.
     */
    private void performReload(CommandSender sender) {
        try {
            sender.sendMessage(Component.text("🔄 Starting Chunklock plugin reload...")
                .color(NamedTextColor.YELLOW));

            long startTime = System.currentTimeMillis();
            
            // Get the plugin instance and perform reload
            ChunklockPlugin plugin = ChunklockPlugin.getInstance();
            if (plugin == null) {
                sender.sendMessage(Component.text("❌ Plugin instance not available for reload.")
                    .color(NamedTextColor.RED));
                return;
            }

            // STEP 1: Validate and fix config BEFORE reloading other systems
            sender.sendMessage(Component.text("📋 Validating configuration...")
                .color(NamedTextColor.BLUE));
            
            try {
                ConfigValidator validator = new ConfigValidator(plugin);
                validator.validateAndEnsureComplete();
                sender.sendMessage(Component.text("✅ Configuration validated and updated")
                    .color(NamedTextColor.GREEN));
            } catch (Exception e) {
                sender.sendMessage(Component.text("⚠️ Config validation had issues: " + e.getMessage())
                    .color(NamedTextColor.YELLOW));
                plugin.getLogger().warning("Config validation error during reload: " + e.getMessage());
            }

            // STEP 2: Now perform the main plugin reload
            sender.sendMessage(Component.text("🔄 Reloading plugin systems...")
                .color(NamedTextColor.BLUE));
            
            boolean success = plugin.performReload(sender);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            // Send completion message
            if (success) {
                String successMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_RELOAD_SUCCESS);
                sender.sendMessage(Component.text(successMsg).color(NamedTextColor.GREEN));
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("duration", String.valueOf(duration));
                String durationMsg = "⏱️ Reload completed in " + duration + "ms";
                sender.sendMessage(Component.text(durationMsg).color(NamedTextColor.GRAY));
                
                // ENHANCED: Force border regeneration after reload
                postReloadBorderFix(sender, plugin);
                
                // Log successful reload
                plugin.getLogger().info("Plugin reload completed successfully by " + 
                    sender.getName() + " in " + duration + "ms");
            } else {
                sender.sendMessage(Component.text("⚠️ Reload completed with warnings.")
                    .color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Check console for details. Duration: " + duration + "ms")
                    .color(NamedTextColor.GRAY));
                
                // Still try to fix borders even with warnings
                postReloadBorderFix(sender, plugin);
                
                // Log reload with warnings
                plugin.getLogger().warning("Plugin reload completed with warnings by " + 
                    sender.getName() + " in " + duration + "ms");
            }

        } catch (Exception e) {
            // Handle critical reload failures
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("error", e.getMessage());
            String errorMsg = MessageUtil.getMessage(LanguageKeys.COMMAND_RELOAD_ERROR, placeholders);
            sender.sendMessage(Component.text(errorMsg).color(NamedTextColor.RED));
            sender.sendMessage(Component.text("Check console for full error details.")
                .color(NamedTextColor.GRAY));
            
            // Log the error
            ChunklockPlugin.getInstance().getLogger().log(Level.SEVERE, 
                "Critical error during reload command by " + sender.getName(), e);
        }
    }

    /**
     * Enhanced border restoration after reload.
     */
    private void postReloadBorderFix(CommandSender sender, ChunklockPlugin plugin) {
        try {
            sender.sendMessage(Component.text("🔧 Restoring glass borders...")
                .color(NamedTextColor.YELLOW));

            ChunkBorderManager borderManager = plugin.getChunkBorderManager();
            if (borderManager == null) {
                sender.sendMessage(Component.text("⚠️ Border manager not available after reload")
                    .color(NamedTextColor.YELLOW));
                return;
            }

            if (!borderManager.isEnabled()) {
                sender.sendMessage(Component.text("ℹ️ Glass borders are disabled in configuration")
                    .color(NamedTextColor.GRAY));
                return;
            }

            int onlinePlayerCount = Bukkit.getOnlinePlayers().size();
            sender.sendMessage(Component.text("Regenerating borders for " + onlinePlayerCount + " online players...")
                .color(NamedTextColor.GRAY));

            // Delay border regeneration slightly to ensure all systems are ready
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    // Count players first (outside lambda scope)
                    final int totalPlayers = (int) Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p != null && p.isOnline())
                        .count();
                    
                    // Force refresh borders for all online players with multiple attempts
                    final java.util.concurrent.atomic.AtomicInteger playerCounter = 
                        new java.util.concurrent.atomic.AtomicInteger(0);
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (player != null && player.isOnline()) {
                            // Schedule border update for each player
                            borderManager.scheduleBorderUpdate(player);
                            
                            final int currentPlayerIndex = playerCounter.incrementAndGet();
                            final Player finalPlayer = player; // Make effectively final for lambda
                            
                            // Small delay between players to prevent overwhelming the system
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (finalPlayer.isOnline()) {
                                    borderManager.refreshBordersForPlayer(finalPlayer);
                                }
                            }, currentPlayerIndex * 2L); // 2 tick delay per player
                        }
                    }

                    // Send final confirmation using the pre-calculated count
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        sender.sendMessage(Component.text("✅ Glass borders restored for " + totalPlayers + " players")
                            .color(NamedTextColor.GREEN));
                        
                        // Additional safety: Force one more refresh after everything settles
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player != null && player.isOnline()) {
                                    borderManager.scheduleBorderUpdate(player);
                                }
                            }
                            plugin.getLogger().info("Post-reload border restoration completed");
                        }, 60L); // 3 second final delay
                        
                    }, 40L); // 2 second delay for confirmation message
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during post-reload border restoration", e);
                    sender.sendMessage(Component.text("⚠️ Some borders may not have been restored properly")
                        .color(NamedTextColor.YELLOW));
                }
            }, 20L); // 1 second initial delay

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error in post-reload border fix", e);
            sender.sendMessage(Component.text("⚠️ Border restoration encountered issues")
                .color(NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("force".startsWith(prefix)) {
                return List.of("force");
            }
        }
        
        return Collections.emptyList();
    }

    @Override
    public String getUsage() {
        return "/chunklock reload [force]";
    }

    @Override
    public String getDescription() {
        return "Admin: Reload plugin configuration and restart systems";
    }
}