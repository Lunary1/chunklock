package me.chunklock;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.teams.EnhancedTeamManager;

import java.io.File;
import java.util.*;

public class BiomeUnlockRegistry {

    public record UnlockRequirement(Material material, int amount) {}

    private static class UnlockOption {
        final Material material;
        final int baseAmount;
        UnlockOption(Material material, int baseAmount) {
            this.material = material;
            this.baseAmount = baseAmount;
        }
    }

    private final Map<Biome, List<UnlockOption>> unlockOptions = new HashMap<>();
    private final PlayerProgressTracker progressTracker;
    private final JavaPlugin plugin;
    
    // NEW: Enhanced team manager for cost scaling
    private EnhancedTeamManager enhancedTeamManager;

    public BiomeUnlockRegistry(JavaPlugin plugin, PlayerProgressTracker progressTracker) {
        this.plugin = plugin;
        this.progressTracker = progressTracker;
        
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }

        FileConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection config = root.getConfigurationSection("biome-unlocks");
        if (config == null) {
            plugin.getLogger().warning("BiomeUnlockRegistry: missing biome-unlocks section in config.yml");
            return;
        }

        for (String biomeKey : config.getKeys(false)) {
            try {
                // Use the key string directly to match biome names
                Biome biome = getBiomeFromString(biomeKey);
                if (biome == null) {
                    plugin.getLogger().warning("Invalid biome in config: " + biomeKey);
                    continue;
                }

                ConfigurationSection section = config.getConfigurationSection(biomeKey);
                if (section == null) continue;

                List<UnlockOption> list = new ArrayList<>();
                for (String itemKey : section.getKeys(false)) {
                    try {
                        Material mat = Material.valueOf(itemKey.toUpperCase());
                        int amt = section.getInt(itemKey, 1);
                        list.add(new UnlockOption(mat, amt));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Invalid material for biome " + biomeKey + ": " + itemKey);
                    }
                }

                if (!list.isEmpty()) {
                    unlockOptions.put(biome, list);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Invalid biome format in config.yml biome-unlocks: " + biomeKey);
            }
        }
        
        // NEW: Initialize team manager reference after plugin is fully loaded
        // We do this in a delayed task to ensure all components are initialized
        if (plugin instanceof ChunklockPlugin) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    this.enhancedTeamManager = ((ChunklockPlugin) plugin).getEnhancedTeamManager();
                    plugin.getLogger().info("BiomeUnlockRegistry: Enhanced team manager integration enabled");
                } catch (Exception e) {
                    plugin.getLogger().warning("BiomeUnlockRegistry: Team integration not available yet");
                    // This is fine - team features will be available after full initialization
                }
            }, 20L); // 1 second delay
        }
    }

    private Biome getBiomeFromString(String biomeKey) {
        try {
            // First try direct enum name match
            return Biome.valueOf(biomeKey.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Try matching by key
            for (Biome biome : Biome.values()) {
                NamespacedKey key = biome.getKey();
                if (key.getKey().equalsIgnoreCase(biomeKey) || 
                    key.toString().equalsIgnoreCase(biomeKey)) {
                    return biome;
                }
            }
            return null;
        }
    }

    public UnlockRequirement calculateRequirement(Player player, Biome biome, int score) {
        List<UnlockOption> options = unlockOptions.getOrDefault(biome, List.of(new UnlockOption(Material.DIRT, 1)));

        int index = Math.min(score / 40, options.size() - 1);
        UnlockOption option = options.get(index);

        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = 1.0 + unlocked / 10.0 + score / 50.0;
        
        // NEW: Apply team cost scaling if enhanced team manager is available
        if (enhancedTeamManager != null) {
            try {
                double teamMultiplier = enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
                multiplier *= teamMultiplier;
                
                // Optional: Log team cost scaling for debugging
                if (teamMultiplier > 1.0) {
                    plugin.getLogger().fine("Applied team cost multiplier " + String.format("%.2f", teamMultiplier) + 
                        " for player " + player.getName());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error applying team cost multiplier for " + player.getName() + ": " + e.getMessage());
                // Continue with base multiplier if team calculation fails
            }
        }
        
        int amount = (int) Math.ceil(option.baseAmount * multiplier);

        // FIX: Corrected the typo here - was "UnlockO" instead of "UnlockRequirement"
        return new UnlockRequirement(option.material, amount);
    }

    public boolean hasRequiredItems(Player player, Biome biome, int score) {
        UnlockRequirement req = calculateRequirement(player, biome, score);
        return player.getInventory().containsAtLeast(new ItemStack(req.material(), 1), req.amount());
    }

    public void consumeRequiredItem(Player player, Biome biome, int score) {
        UnlockRequirement req = calculateRequirement(player, biome, score);
        player.getInventory().removeItem(new ItemStack(req.material(), req.amount()));
        
        // NEW: Record team statistics if enhanced team manager is available
        if (enhancedTeamManager != null) {
            try {
                enhancedTeamManager.recordChunkUnlock(player.getUniqueId(), getBiomeDisplayName(biome));
                plugin.getLogger().fine("Recorded chunk unlock for team statistics: " + player.getName() + " in " + getBiomeDisplayName(biome));
            } catch (Exception e) {
                plugin.getLogger().warning("Error recording team chunk unlock for " + player.getName() + ": " + e.getMessage());
                // Continue even if team recording fails
            }
        }
    }

    // Helper method to get biome display name
    public static String getBiomeDisplayName(Biome biome) {
        if (biome == null) return "Unknown";
        NamespacedKey key = biome.getKey();
        return key.getKey().replace("_", " ");
    }
    
    // NEW: Method to manually set enhanced team manager (for testing or late initialization)
    public void setEnhancedTeamManager(EnhancedTeamManager enhancedTeamManager) {
        this.enhancedTeamManager = enhancedTeamManager;
        plugin.getLogger().info("BiomeUnlockRegistry: Enhanced team manager manually set");
    }
    
    // NEW: Method to check if team integration is active
    public boolean isTeamIntegrationActive() {
        return enhancedTeamManager != null;
    }
    
    // NEW: Get team cost multiplier for display purposes
    public double getTeamCostMultiplier(Player player) {
        if (enhancedTeamManager != null) {
            try {
                return enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
            } catch (Exception e) {
                plugin.getLogger().fine("Error getting team cost multiplier for " + player.getName() + ": " + e.getMessage());
            }
        }
        return 1.0; // No team or no team integration
    }
}