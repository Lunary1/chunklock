package me.chunklock.ui;

import me.chunklock.managers.BiomeUnlockRegistry;
import me.chunklock.managers.ChunkEvaluator;
import me.chunklock.models.Difficulty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class UnlockGuiBuilder {
    public Inventory build(Player player, Chunk chunk, ChunkEvaluator.ChunkValueData eval,
                           BiomeUnlockRegistry.UnlockRequirement requirement) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(UnlockGui.GUI_TITLE));
        addChunkInfoItem(inv, chunk, eval);
        addRequirementItems(inv, player, requirement);
        addUnlockButton(inv, player, requirement);
        return inv;
    }

    private void addChunkInfoItem(Inventory inv, Chunk chunk, ChunkEvaluator.ChunkValueData eval) {
        ItemStack chunkInfo = new ItemStack(Material.MAP);
        ItemMeta meta = chunkInfo.getItemMeta();
        meta.displayName(Component.text("Chunk Information")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Location: " + chunk.getX() + ", " + chunk.getZ())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Biome: " + BiomeUnlockRegistry.getBiomeDisplayName(eval.biome))
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Difficulty: " + eval.difficulty)
            .color(getDifficultyColor(eval.difficulty))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Score: " + eval.score)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        chunkInfo.setItemMeta(meta);
        inv.setItem(4, chunkInfo);
    }

    private void addRequirementItems(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        int requiredAmount = requirement.amount();
        int playerHas = countPlayerItems(player, requirement.material());
        boolean hasEnough = playerHas >= requiredAmount;
        if (requiredAmount <= 64) {
            addSingleRequirementItem(inv, 10, requirement, playerHas, hasEnough);
        } else {
            addMultipleRequirementItems(inv, requirement, playerHas, hasEnough);
        }
    }

    private void addSingleRequirementItem(Inventory inv, int slot, BiomeUnlockRegistry.UnlockRequirement requirement,
                                           int playerHas, boolean hasEnough) {
        ItemStack stack = new ItemStack(requirement.material(), Math.min(64, requirement.amount()));
        ItemMeta meta = stack.getItemMeta();
        Component displayName = hasEnough ?
            Component.text("\u2713 " + formatMaterialName(requirement.material())).color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
            : Component.text("\u2717 " + formatMaterialName(requirement.material())).color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Required: " + requirement.amount()).color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("You have: " + playerHas)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        if (!hasEnough) {
            int needed = requirement.amount() - playerHas;
            lore.add(Component.text("Still need: " + needed)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        stack.setItemMeta(meta);
        inv.setItem(slot, stack);
    }

    private void addMultipleRequirementItems(Inventory inv, BiomeUnlockRegistry.UnlockRequirement requirement,
                                             int playerHas, boolean hasEnough) {
        int requiredAmount = requirement.amount();
        int fullStacks = requiredAmount / 64;
        int remainder = requiredAmount % 64;
        int slot = 9;
        for (int i = 0; i < Math.min(fullStacks, 2); i++) {
            ItemStack stack = new ItemStack(requirement.material(), 64);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("Stack " + (i + 1) + " (64 items)")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Part of total requirement")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            stack.setItemMeta(meta);
            inv.setItem(slot++, stack);
        }
        if (remainder > 0 && slot <= 11) {
            ItemStack stack = new ItemStack(requirement.material(), remainder);
            ItemMeta meta = stack.getItemMeta();
            String stackLabel = fullStacks > 2 ? "... + " + remainder + " more" : "Final stack (" + remainder + " items)";
            meta.displayName(Component.text(stackLabel)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
            inv.setItem(slot, stack);
        }
        ItemStack summary = new ItemStack(requirement.material(), Math.min(64, requiredAmount));
        ItemMeta summaryMeta = summary.getItemMeta();
        Component displayName = hasEnough ?
            Component.text("\u2713 " + formatMaterialName(requirement.material()) + " (TOTAL)")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true)
            : Component.text("\u2717 " + formatMaterialName(requirement.material()) + " (TOTAL)")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
        summaryMeta.displayName(displayName);
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(Component.text("Total Required: " + requiredAmount)
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));
        summaryLore.add(Component.text("You have: " + playerHas)
            .color(hasEnough ? NamedTextColor.GREEN : NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false));
        if (!hasEnough) {
            int needed = requiredAmount - playerHas;
            summaryLore.add(Component.text("Still need: " + needed)
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }
        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        inv.setItem(13, summary);
    }

    private void addUnlockButton(Inventory inv, Player player, BiomeUnlockRegistry.UnlockRequirement requirement) {
        boolean hasEnough = countPlayerItems(player, requirement.material()) >= requirement.amount();
        ItemStack unlock = hasEnough ? new ItemStack(Material.EMERALD_BLOCK) : new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = unlock.getItemMeta();
        if (hasEnough) {
            meta.displayName(Component.text("\u2713 Click to Unlock Chunk!")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        } else {
            meta.displayName(Component.text("\u2717 Cannot Unlock Yet")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        }
        unlock.setItemMeta(meta);
        inv.setItem(22, unlock);
    }

    private String formatMaterialName(Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }

    private NamedTextColor getDifficultyColor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> NamedTextColor.GREEN;
            case NORMAL -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.RED;
            case IMPOSSIBLE -> NamedTextColor.DARK_PURPLE;
        };
    }

    private int countPlayerItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            count += offHand.getAmount();
        }
        return count;
    }
}
