package me.chunklock.economy.items;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
        return player.getInventory().containsAtLeast(new ItemStack(material), amount);
    }
    
    @Override
    public void consumeFromInventory(Player player) {
        player.getInventory().removeItem(new ItemStack(material, amount));
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
