package me.chunklock;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class BiomeUnlockRegistry {

    private final Map<Biome, List<Material>> unlockItems = new HashMap<>();

    public BiomeUnlockRegistry(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "biome_costs.yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("biome_costs.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        for (String biomeKey : config.getKeys(false)) {
            try {
                Biome biome = Biome.valueOf(biomeKey.toUpperCase(Locale.ROOT));
                // If the biome is not found, it will return null
                if (biome == null) {
                    plugin.getLogger().warning("Invalid biome in config: " + biomeKey);
                    continue;
                }

                List<String> items = config.getStringList(biomeKey);
                List<Material> materials = new ArrayList<>();
                for (String itemName : items) {
                    try {
                        materials.add(Material.valueOf(itemName.toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Invalid material for biome " + biomeKey + ": " + itemName);
                    }
                }
                unlockItems.put(biome, materials);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid biome format in biome_costs.yml: " + biomeKey);
            }
        }
    }

    public List<Material> getRequiredItems(Biome biome) {
        return unlockItems.getOrDefault(biome, List.of(Material.TRIPWIRE_HOOK)); // fallback
    }

    public boolean hasRequiredItems(Player player, Biome biome) {
        List<Material> required = getRequiredItems(biome);
        for (Material mat : required) {
            if (player.getInventory().contains(mat)) {
                return true;
            }
        }
        return false;
    }

    public void consumeRequiredItem(Player player, Biome biome) {
        List<Material> required = getRequiredItems(biome);
        for (Material mat : required) {
            if (player.getInventory().contains(mat)) {
                player.getInventory().removeItem(new ItemStack(mat, 1));
                return;
            }
        }
    }
}
