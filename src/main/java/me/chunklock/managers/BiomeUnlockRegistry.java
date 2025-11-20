package me.chunklock.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.economy.items.ItemRequirementFactory;
import me.chunklock.economy.items.providers.CustomItemRegistry;

import java.io.File;
import java.util.*;

public class BiomeUnlockRegistry {
    public record UnlockRequirement(Material material, int amount) {}

    private final Map<Biome, List<ItemRequirement>> itemRequirements = new HashMap<>();
    private final PlayerProgressTracker progressTracker;
    private final JavaPlugin plugin;
    private final CustomItemRegistry customItemRegistry;
    private final ItemRequirementFactory requirementFactory;
    private EnhancedTeamManager enhancedTeamManager;

    public BiomeUnlockRegistry(JavaPlugin plugin, PlayerProgressTracker progressTracker) {
        this.plugin = plugin;
        this.progressTracker = progressTracker;
        this.customItemRegistry = new CustomItemRegistry(plugin);
        this.requirementFactory = new ItemRequirementFactory(plugin, customItemRegistry);
        loadBiomeUnlocks();
        
        if (plugin instanceof ChunklockPlugin) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                try {
                    this.enhancedTeamManager = ((ChunklockPlugin) plugin).getEnhancedTeamManager();
                } catch (Exception e) {
                    plugin.getLogger().warning("Team integration not available");
                }
            }, 20L);
        }
    }
    
    private void loadBiomeUnlocks() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("config.yml", false);
        }
        FileConfiguration root = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection config = root.getConfigurationSection("biome-unlocks");
        if (config == null) return;
        
        for (String biomeKey : config.getKeys(false)) {
            try {
                Biome biome = getBiomeFromString(biomeKey);
                if (biome == null) continue;
                ConfigurationSection section = config.getConfigurationSection(biomeKey);
                if (section == null) continue;
                List<ItemRequirement> requirements = requirementFactory.parseRequirements(biomeKey, section);
                if (!requirements.isEmpty()) {
                    itemRequirements.put(biome, requirements);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Error loading biome: " + ex.getMessage());
            }
        }
    }

    private Biome getBiomeFromString(String biomeKey) {
        return me.chunklock.util.world.BiomeUtil.getBiomeFromString(biomeKey);
    }

    public UnlockRequirement calculateRequirement(Player player, Biome biome, int score) {
        List<ItemRequirement> requirements = itemRequirements.getOrDefault(biome, new ArrayList<>());
        if (requirements.isEmpty()) return new UnlockRequirement(Material.DIRT, 1);
        
        // Find first vanilla item requirement (for backwards compatibility with single-item system)
        ItemRequirement firstVanillaReq = null;
        for (ItemRequirement req : requirements) {
            if (req instanceof me.chunklock.economy.items.VanillaItemRequirement) {
                firstVanillaReq = req;
                break;
            }
        }
        
        Material material = Material.DIRT;
        int baseAmount = 1;
        
        if (firstVanillaReq != null && firstVanillaReq instanceof me.chunklock.economy.items.VanillaItemRequirement vanillaReq) {
            material = vanillaReq.getMaterial();
            baseAmount = firstVanillaReq.getAmount();
        }

        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = 1.0 + unlocked / 10.0 + score / 50.0;
        
        if (enhancedTeamManager != null) {
            try {
                multiplier *= enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        int amount = (int) Math.ceil(baseAmount * multiplier);
        return new UnlockRequirement(material, amount);
    }

    public boolean hasRequiredItems(Player player, Biome biome, int score) {
        List<ItemRequirement> requirements = itemRequirements.getOrDefault(biome, new ArrayList<>());
        if (requirements.isEmpty()) return true;
        
        for (ItemRequirement req : requirements) {
            if (!req.hasInInventory(player)) return false;
        }
        return true;
    }

    public void consumeRequiredItem(Player player, Biome biome, int score) {
        List<ItemRequirement> requirements = itemRequirements.getOrDefault(biome, new ArrayList<>());
        if (requirements.isEmpty()) return;
        
        for (ItemRequirement req : requirements) {
            if (req.hasInInventory(player)) {
                req.consumeFromInventory(player);
            }
        }
        
        if (enhancedTeamManager != null) {
            try {
                enhancedTeamManager.recordChunkUnlock(player.getUniqueId(), getBiomeDisplayName(biome));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public List<ItemRequirement> getRequirementsForBiome(Biome biome) {
        return itemRequirements.getOrDefault(biome, new ArrayList<>());
    }

    public static String getBiomeDisplayName(Biome biome) {
        if (biome == null) return "Unknown";
        NamespacedKey key = biome.getKey();
        return key.getKey().replace("_", " ");
    }
    
    public void setEnhancedTeamManager(EnhancedTeamManager enhancedTeamManager) {
        this.enhancedTeamManager = enhancedTeamManager;
    }
    
    public boolean isTeamIntegrationActive() {
        return enhancedTeamManager != null;
    }
    
    public double getTeamCostMultiplier(Player player) {
        if (enhancedTeamManager != null) {
            try {
                return enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
            } catch (Exception e) {
                return 1.0;
            }
        }
        return 1.0;
    }
}