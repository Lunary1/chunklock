package me.chunklock.managers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import me.chunklock.ChunklockPlugin;
import me.chunklock.economy.items.ItemRequirement;
import me.chunklock.economy.items.ItemRequirementFactory;
import me.chunklock.economy.items.providers.CustomItemRegistry;

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
        // Use modular config system
        me.chunklock.config.modular.BiomeUnlocksConfig biomeUnlocksConfig = null;
        if (plugin instanceof ChunklockPlugin) {
            biomeUnlocksConfig = ((ChunklockPlugin) plugin).getConfigManager().getBiomeUnlocksConfig();
        } else {
            biomeUnlocksConfig = new me.chunklock.config.modular.BiomeUnlocksConfig(plugin);
        }
        
        if (biomeUnlocksConfig == null) {
            plugin.getLogger().warning("Failed to load biome-unlocks.yml");
            return;
        }
        
        ConfigurationSection config = biomeUnlocksConfig.getRootSection();
        if (config == null) return;

        Set<Biome> loadedBiomes = new HashSet<>();
        int endExclusiveMaterialRequirements = 0;
        
        for (String biomeKey : config.getKeys(false)) {
            try {
                Biome biome = getBiomeFromString(biomeKey);
                if (biome == null) continue;
                ConfigurationSection section = config.getConfigurationSection(biomeKey);
                if (section == null) continue;
                List<ItemRequirement> requirements = requirementFactory.parseRequirements(biomeKey, section);
                if (!requirements.isEmpty()) {
                    itemRequirements.put(biome, requirements);
                    loadedBiomes.add(biome);

                    for (ItemRequirement requirement : requirements) {
                        if (requirement instanceof me.chunklock.economy.items.VanillaItemRequirement vanilla
                                && isEndExclusiveMaterial(vanilla.getMaterial())) {
                            endExclusiveMaterialRequirements++;
                        }
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Error loading biome: " + ex.getMessage());
            }
        }

        warnPotentiallyUnreachableRequirements(loadedBiomes, endExclusiveMaterialRequirements);
    }

    private void warnPotentiallyUnreachableRequirements(Set<Biome> loadedBiomes, int endExclusiveMaterialRequirements) {
        if (!(plugin instanceof ChunklockPlugin chunklockPlugin)) {
            return;
        }

        try {
            WorldManager worldManager = chunklockPlugin.getWorldManager();
            if (worldManager == null) {
                return;
            }

            List<String> enabledWorlds = worldManager.getEnabledWorlds();
            boolean singleWorldMode = enabledWorlds.size() <= 1;
            if (!singleWorldMode) {
                return;
            }

            boolean hasEndBiomeRequirements = loadedBiomes.stream().anyMatch(this::isEndBiome);
            if (!hasEndBiomeRequirements && endExclusiveMaterialRequirements == 0) {
                return;
            }

            plugin.getLogger().warning("⚠️ Potential progression softlock risk detected in biome-unlocks.yml:");
            plugin.getLogger().warning("   - Single-world configuration is active (enabled worlds: " + enabledWorlds + ")");
            if (hasEndBiomeRequirements) {
                plugin.getLogger().warning("   - End biome requirements are configured while running in single-world mode");
            }
            if (endExclusiveMaterialRequirements > 0) {
                plugin.getLogger().warning("   - Found " + endExclusiveMaterialRequirements + " End-exclusive material requirement(s)");
            }
            plugin.getLogger().warning("   Review biome-unlocks.yml and ensure all required items are reachable for this world setup.");
        } catch (Exception ignored) {
            // Guardrail warnings should never interrupt plugin startup
        }
    }

    private boolean isEndBiome(Biome biome) {
        if (biome == null) {
            return false;
        }
        if (biome == Biome.THE_END) {
            return true;
        }
        return biome.getKey().getKey().toUpperCase(Locale.ROOT).contains("END");
    }

    private boolean isEndExclusiveMaterial(Material material) {
        if (material == null) {
            return false;
        }

        String name = material.name();
        return name.equals("DRAGON_EGG")
            || name.equals("ELYTRA")
            || name.equals("END_CRYSTAL")
            || name.startsWith("CHORUS")
            || name.startsWith("PURPUR")
            || name.startsWith("END_STONE")
            || name.startsWith("END_ROD")
            || name.startsWith("SHULKER");
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

        double multiplier = calculateMultiplier(player, score);
        int amount = (int) Math.ceil(baseAmount * multiplier);
        return new UnlockRequirement(material, amount);
    }
    
    /**
     * Calculate the cost multiplier based on player progress and score.
     * This should be called consistently to ensure display and validation match.
     */
    private double calculateMultiplier(Player player, int score) {
        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = 1.0 + unlocked / 10.0 + score / 50.0;
        
        if (enhancedTeamManager != null) {
            try {
                multiplier *= enhancedTeamManager.getChunkCostMultiplier(player.getUniqueId());
            } catch (Exception e) {
                // Ignore
            }
        }
        
        return multiplier;
    }

    public boolean hasRequiredItems(Player player, Biome biome, int score) {
        List<ItemRequirement> requirements = itemRequirements.getOrDefault(biome, new ArrayList<>());
        if (requirements.isEmpty()) return true;
        
        double multiplier = calculateMultiplier(player, score);
        
        for (ItemRequirement req : requirements) {
            // Check if player has the multiplied amount
            if (req instanceof me.chunklock.economy.items.VanillaItemRequirement vanillaReq) {
                int adjustedAmount = (int) Math.ceil(vanillaReq.getAmount() * multiplier);
                ItemStack required = new ItemStack(vanillaReq.getMaterial(), adjustedAmount);
                if (!player.getInventory().containsAtLeast(required, adjustedAmount)) {
                    return false;
                }
            } else {
                // For custom items, apply multiplier check
                if (!req.hasInInventory(player)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void consumeRequiredItem(Player player, Biome biome, int score) {
        List<ItemRequirement> requirements = itemRequirements.getOrDefault(biome, new ArrayList<>());
        if (requirements.isEmpty()) return;
        
        double multiplier = calculateMultiplier(player, score);
        
        for (ItemRequirement req : requirements) {
            if (req instanceof me.chunklock.economy.items.VanillaItemRequirement vanillaReq) {
                // Consume the multiplied amount
                int adjustedAmount = (int) Math.ceil(vanillaReq.getAmount() * multiplier);
                player.getInventory().removeItem(new ItemStack(vanillaReq.getMaterial(), adjustedAmount));
            } else {
                // For custom items, consume the standard amount
                if (req.hasInInventory(player)) {
                    req.consumeFromInventory(player);
                }
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