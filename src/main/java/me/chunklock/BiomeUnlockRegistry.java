package me.chunklock;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

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

    public BiomeUnlockRegistry(JavaPlugin plugin, PlayerProgressTracker progressTracker) {
        this.progressTracker = progressTracker;
        File file = new File(plugin.getDataFolder(), "biome_costs.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("biome_costs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String biomeKey : config.getKeys(false)) {
            try {
                Biome biome = Biome.valueOf(biomeKey.toUpperCase(Locale.ROOT));
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
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid biome format in biome_costs.yml: " + biomeKey);
            }
        }
    }

    public UnlockRequirement calculateRequirement(Player player, Biome biome, int score) {
        List<UnlockOption> options = unlockOptions.getOrDefault(biome, List.of(new UnlockOption(Material.DIRT, 1)));

        int index = Math.min(score / 40, options.size() - 1);
        UnlockOption option = options.get(index);

        int unlocked = progressTracker.getUnlockedChunkCount(player.getUniqueId());
        double multiplier = 1.0 + unlocked / 10.0 + score / 50.0;
        int amount = (int) Math.ceil(option.baseAmount * multiplier);

        return new UnlockRequirement(option.material, amount);
    }

    public boolean hasRequiredItems(Player player, Biome biome, int score) {
        UnlockRequirement req = calculateRequirement(player, biome, score);
        return player.getInventory().containsAtLeast(new ItemStack(req.material(), 1), req.amount());
    }

    public void consumeRequiredItem(Player player, Biome biome, int score) {
        UnlockRequirement req = calculateRequirement(player, biome, score);
        player.getInventory().removeItem(new ItemStack(req.material(), req.amount()));
    }
}
