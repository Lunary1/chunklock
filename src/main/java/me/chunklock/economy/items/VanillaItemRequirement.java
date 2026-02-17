package me.chunklock.economy.items;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import me.chunklock.util.item.MaterialUtil;

/**
 * Vanilla Minecraft item requirement.
 */
public class VanillaItemRequirement implements ItemRequirement {
    
    private final Material material;
    private final int amount;
    
    public VanillaItemRequirement(Material material, int amount) {
        this.material = material;
        this.amount = amount;
    }
    
    @Override
    public String getDisplayName() {
        return MaterialUtil.formatMaterialName(material);
    }
    
    @Override
    public int getAmount() {
        return amount;
    }
    
    @Override
    public boolean hasInInventory(Player player) {
        int total = 0;
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
                if (total >= amount) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public void consumeFromInventory(Player player) {
        int remaining = amount;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType() != material) {
                continue;
            }

            int stackAmount = item.getAmount();
            if (stackAmount <= remaining) {
                remaining -= stackAmount;
                inventory.setItem(slot, null);
            } else {
                item.setAmount(stackAmount - remaining);
                inventory.setItem(slot, item);
                remaining = 0;
            }
        }
    }
    
    @Override
    public ItemStack getRepresentativeStack() {
        return new ItemStack(material);
    }
    
    @Override
    public boolean isValid() {
        return material != null && material != Material.AIR;
    }
    
    @Override
    public String getType() {
        return "vanilla";
    }
    
    public Material getMaterial() {
        return material;
    }
}
